package com.qingricheng.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Message.meta carries attachment metadata and the exact text sent to the model. */
object MessageMeta {
    fun encode(attachments: List<Attachment>, prompt: String): String {
        val arr = JSONArray()
        attachments.forEach { a ->
            arr.put(
                JSONObject()
                    .put("id", a.id)
                    .put("kind", a.kind)
                    .put("name", a.name)
                    .put("mime", a.mime)
                    .put("path", a.path)
                    .put("size", a.size)
            )
        }
        return JSONObject().put("attachments", arr).put("prompt", prompt).toString()
    }

    fun attachments(meta: String): List<Attachment> {
        if (meta.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONObject(meta).optJSONArray("attachments") ?: return emptyList()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                Attachment(
                    id = o.optString("id"),
                    kind = o.optString("kind", "file"),
                    name = o.optString("name"),
                    mime = o.optString("mime"),
                    path = o.optString("path"),
                    size = o.optLong("size")
                )
            }
        }.getOrDefault(emptyList())
    }

    fun prompt(meta: String): String? {
        if (meta.isBlank()) return null
        return runCatching { JSONObject(meta).optString("prompt").takeIf { it.isNotBlank() } }.getOrNull()
    }
}
