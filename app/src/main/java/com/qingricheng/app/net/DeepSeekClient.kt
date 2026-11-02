package com.qingricheng.app.net

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.job
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

data class ApiToolCall(
    val id: String,
    val name: String,
    val arguments: String
)

data class ApiMessage(
    val role: String,
    val content: String = "",
    val toolCalls: List<ApiToolCall> = emptyList(),
    val toolCallId: String? = null,
    val name: String? = null,
    val imageDataUrls: List<String> = emptyList()
)

data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: JSONObject
)

data class ChatRequest(
    val apiKey: String,
    val baseUrl: String,
    val model: String,
    val messages: List<ApiMessage>,
    val tools: List<ToolDefinition> = emptyList(),
    val temperature: Double = 0.85
)

sealed interface StreamEvent {
    data class Text(val delta: String) : StreamEvent
    data class ToolCalls(val calls: List<ApiToolCall>) : StreamEvent
    data class Finished(val reason: String?) : StreamEvent
}

class LlmException(message: String) : IOException(message)

class DeepSeekClient {
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    fun stream(request: ChatRequest): Flow<StreamEvent> = flow {
        if (request.apiKey.isBlank()) throw LlmException("还没有填写 DeepSeek API Key，请先到设置里配置")
        val body = JSONObject().apply {
            put("model", request.model.ifBlank { "deepseek-chat" })
            put("stream", true)
            put("temperature", request.temperature)
            put("messages", JSONArray().apply {
                request.messages.forEach { put(it.toJson()) }
            })
            if (request.tools.isNotEmpty()) {
                put("tools", JSONArray().apply {
                    request.tools.forEach { tool ->
                        put(JSONObject().apply {
                            put("type", "function")
                            put("function", JSONObject().apply {
                                put("name", tool.name)
                                put("description", tool.description)
                                put("parameters", tool.parameters)
                            })
                        })
                    }
                })
            }
        }
        val httpRequest = Request.Builder()
            .url(endpoint(request.baseUrl))
            .header("Authorization", "Bearer ${request.apiKey}")
            .header("Accept", "text/event-stream")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()
        val call = client.newCall(httpRequest)
        currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        call.execute().use { response ->
            if (!response.isSuccessful) {
                val text = runCatching { response.body.string() }.getOrDefault("")
                throw LlmException(httpError(response.code, text))
            }
            val source = response.body.source()
            val toolCalls = LinkedHashMap<Int, MutableToolCall>()
            var finishedReason: String? = null
            while (true) {
                val line = source.readUtf8Line() ?: break
                if (!line.startsWith("data:")) continue
                val data = line.removePrefix("data:").trim()
                if (data.isEmpty()) continue
                if (data == "[DONE]") break
                val json = runCatching { JSONObject(data) }.getOrNull() ?: continue
                val choice = json.optJSONArray("choices")?.optJSONObject(0) ?: continue
                val delta = choice.optJSONObject("delta") ?: JSONObject()
                if (!delta.isNull("content")) {
                    val content = delta.optString("content")
                    if (content.isNotEmpty()) emit(StreamEvent.Text(content))
                }
                delta.optJSONArray("tool_calls")?.let { arr ->
                    for (i in 0 until arr.length()) {
                        val tc = arr.optJSONObject(i) ?: continue
                        val index = tc.optInt("index", i)
                        val acc = toolCalls.getOrPut(index) { MutableToolCall() }
                        tc.optString("id").takeIf { it.isNotEmpty() }?.let { acc.id = it }
                        val fn = tc.optJSONObject("function") ?: JSONObject()
                        fn.optString("name").takeIf { it.isNotEmpty() }?.let { acc.name = it }
                        fn.optString("arguments").takeIf { it.isNotEmpty() }?.let { acc.arguments.append(it) }
                    }
                }
                val reason = choice.optString("finish_reason")
                if (reason.isNotEmpty() && !choice.isNull("finish_reason")) finishedReason = reason
            }
            val calls = toolCalls.values
                .filter { it.name.isNotBlank() }
                .map { ApiToolCall(id = it.id.ifBlank { "call_${System.nanoTime()}" }, name = it.name, arguments = it.arguments.toString()) }
            if (calls.isNotEmpty()) emit(StreamEvent.ToolCalls(calls))
            emit(StreamEvent.Finished(finishedReason))
        }
    }.flowOn(Dispatchers.IO)

    private class MutableToolCall {
        var id: String = ""
        var name: String = ""
        val arguments = StringBuilder()
    }

    private fun ApiMessage.toJson(): JSONObject = JSONObject().apply {
        put("role", role)
        if (role == "tool") {
            put("content", content)
            put("tool_call_id", toolCallId ?: "")
            return@apply
        }
        if (toolCalls.isNotEmpty()) {
            put("content", content)
            put("tool_calls", JSONArray().apply {
                toolCalls.forEach { call ->
                    put(JSONObject().apply {
                        put("id", call.id)
                        put("type", "function")
                        put("function", JSONObject().apply {
                            put("name", call.name)
                            put("arguments", call.arguments)
                        })
                    })
                }
            })
        } else if (imageDataUrls.isNotEmpty()) {
            put("content", JSONArray().apply {
                if (content.isNotBlank()) put(JSONObject().put("type", "text").put("text", content))
                imageDataUrls.forEach { url ->
                    put(JSONObject().put("type", "image_url").put("image_url", JSONObject().put("url", url)))
                }
            })
        } else {
            put("content", content)
        }
        if (!name.isNullOrBlank()) put("name", name)
    }

    private fun endpoint(baseUrl: String): String {
        val base = baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) return "https://api.deepseek.com/v1/chat/completions"
        return when {
            base.endsWith("/chat/completions") -> base
            base.endsWith("/v1") -> "$base/chat/completions"
            else -> "$base/v1/chat/completions"
        }
    }

    private fun httpError(code: Int, body: String): String {
        val detail = body.replace(Regex("<[^>]+>"), " ").replace(Regex("\\s+"), " ").trim().take(200)
        return when (code) {
            401 -> "API Key 无效或已过期（HTTP 401）"
            402 -> "API 账户余额不足（HTTP 402）"
            403 -> "没有权限调用该模型（HTTP 403）"
            429 -> "请求太频繁，请稍后再试（HTTP 429）"
            in 500..599 -> "模型服务暂时不可用（HTTP $code）"
            else -> "模型请求失败（HTTP $code）${if (detail.isNotEmpty()) "：$detail" else ""}"
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()
    }
}
