package com.qingricheng.app.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PersonaFormatException(message: String) : Exception(message)

object PersonaParser {
    fun parseMany(text: String): List<PersonaCard> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) throw PersonaFormatException("人设卡文件是空的")
        return try {
            when (trimmed.first()) {
                '[' -> {
                    val arr = JSONArray(trimmed)
                    (0 until arr.length()).mapNotNull { i ->
                        arr.optJSONObject(i)?.let { parseObject(it, trimmed) }
                    }.ifEmpty { throw PersonaFormatException("人设卡数组里没有有效卡片") }
                }
                '{' -> {
                    val obj = JSONObject(trimmed)
                    val nested = obj.optJSONArray("personas")
                    if (nested != null) {
                        (0 until nested.length()).mapNotNull { i ->
                            nested.optJSONObject(i)?.let { parseObject(it, trimmed) }
                        }.ifEmpty { throw PersonaFormatException("personas 数组里没有有效卡片") }
                    } else {
                        listOf(parseObject(obj, trimmed))
                    }
                }
                else -> throw PersonaFormatException("不是 JSON 人设卡")
            }
        } catch (e: PersonaFormatException) {
            throw e
        } catch (e: Exception) {
            throw PersonaFormatException("JSON 解析失败：${e.message ?: "格式错误"}")
        }
    }

    fun parse(text: String): PersonaCard? = try {
        parseMany(text).firstOrNull()
    } catch (_: Exception) {
        null
    }

    private fun parseObject(obj: JSONObject, raw: String): PersonaCard {
        val name = obj.optString("name").trim().ifEmpty { "未命名人设" }
        val id = obj.optString("id").trim().ifEmpty { "persona_" + UUID.randomUUID().toString().take(8) }
        val avatar = obj.optString("avatar").trim().ifEmpty { "🌙" }
        val tagline = obj.optString("tagline").trim()
        val description = obj.optString("description").trim()
        val systemPrompt = firstString(obj, "systemPrompt", "system_prompt", "prompt", "instruction").trim()
        val speakingStyle = firstString(obj, "speakingStyle", "speaking_style", "style").trim()
        val traits = strings(obj.optJSONArray("traits"))
        val boundaries = strings(obj.optJSONArray("boundaries"))
        val memorySeeds = strings(obj.optJSONArray("memorySeeds"))
        val tags = strings(obj.optJSONArray("tags"))
        val examples = ArrayList<PersonaCard.Dialogue>()
        val dialogues = obj.optJSONArray("exampleDialogues") ?: obj.optJSONArray("examples")
        if (dialogues != null) {
            for (i in 0 until dialogues.length()) {
                val d = dialogues.optJSONObject(i) ?: continue
                examples += PersonaCard.Dialogue(
                    user = d.optString("user"),
                    assistant = d.optString("assistant")
                )
            }
        }
        val greeting = obj.optString("greeting").trim()
            .ifEmpty { "你好，我是$name。今天想聊点什么？" }
        val builtPrompt = systemPrompt.ifEmpty {
            buildString {
                append("你是「$name」")
                if (tagline.isNotEmpty()) append("，$tagline")
                append("。")
                if (description.isNotEmpty()) append("\n\n$description")
                if (traits.isNotEmpty()) append("\n\n你的特质：").append(traits.joinToString("、"))
                if (speakingStyle.isNotEmpty()) append("\n\n说话方式：").append(speakingStyle)
                if (boundaries.isNotEmpty()) append("\n\n边界：").append(boundaries.joinToString("；"))
            }
        }
        return PersonaCard(
            id = id,
            name = name,
            avatar = avatar,
            tagline = tagline,
            description = description,
            systemPrompt = builtPrompt,
            greeting = greeting,
            traits = traits,
            speakingStyle = speakingStyle,
            boundaries = boundaries,
            exampleDialogues = examples,
            memorySeeds = memorySeeds,
            tags = tags,
            author = obj.optString("author"),
            version = obj.optString("version").ifEmpty { "1.0" },
            rawJson = raw
        )
    }

    private fun firstString(obj: JSONObject, vararg keys: String): String {
        for (key in keys) {
            val value = obj.optString(key)
            if (value.isNotBlank()) return value
        }
        return ""
    }

    private fun strings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        val out = ArrayList<String>()
        for (i in 0 until array.length()) {
            val value = array.optString(i).trim()
            if (value.isNotEmpty()) out += value
        }
        return out
    }
}
