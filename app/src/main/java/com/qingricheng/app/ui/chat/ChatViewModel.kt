package com.qingricheng.app.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingricheng.app.AppContainer
import com.qingricheng.app.data.Attachment
import com.qingricheng.app.data.ChatMessage
import com.qingricheng.app.data.MessageMeta
import com.qingricheng.app.data.PersonaCard
import com.qingricheng.app.data.SearchResult
import com.qingricheng.app.domain.PersonaPrompt
import com.qingricheng.app.net.ApiMessage
import com.qingricheng.app.net.ApiToolCall
import com.qingricheng.app.net.ChatRequest
import com.qingricheng.app.net.LlmException
import com.qingricheng.app.net.StreamEvent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

data class ChatUiState(
    val messages: List<ChatMessage> = emptyList(),
    val streamingText: String = "",
    val sending: Boolean = false,
    val status: String = "",
    val error: String? = null,
    val personas: List<PersonaCard> = emptyList(),
    val toolNotes: List<String> = emptyList(),
    val sources: List<SearchResult> = emptyList(),
    val pendingAttachments: List<Attachment> = emptyList(),
    val processingAttachment: Boolean = false,
    val attachmentError: String? = null
)

class ChatViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.personas.seedExampleIfNeeded() }
            val personas = container.personas.activeCards()
            val messages = container.chat.messages()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(personas = personas, messages = messages)
            }
        }
    }

    fun attach(uri: Uri, kind: String) {
        if (_state.value.processingAttachment) return
        _state.value = _state.value.copy(processingAttachment = true, attachmentError = null)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val imported = container.attachments.import(uri, kind)
                container.attachments.prepare(imported)
            }.onSuccess { attachment ->
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        processingAttachment = false,
                        pendingAttachments = _state.value.pendingAttachments + attachment
                    )
                }
            }.onFailure { e ->
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        processingAttachment = false,
                        attachmentError = e.message ?: "附件读取失败"
                    )
                }
            }
        }
    }

    fun removeAttachment(id: String) {
        _state.value = _state.value.copy(
            pendingAttachments = _state.value.pendingAttachments.filterNot { it.id == id }
        )
    }

    fun dismissAttachmentError() {
        _state.value = _state.value.copy(attachmentError = null)
    }

    fun send(text: String) {
        val trimmed = text.trim()
        val attachments = _state.value.pendingAttachments
        if ((trimmed.isEmpty() && attachments.isEmpty()) || _state.value.sending) return
        val apiKey = container.secrets.get(KEY_LLM) ?: ""
        if (apiKey.isBlank()) {
            _state.value = _state.value.copy(error = "请先在设置里填写 DeepSeek API Key")
            return
        }
        val personas = container.personas.activeCards()
        val personasSnapshot = personas
        val memories = container.memory.list()
        val profile = container.profile.list()
        val system = PersonaPrompt.systemPrompt(personasSnapshot, memories, profile)
        _state.value = _state.value.copy(
            sending = true,
            error = null,
            status = "",
            toolNotes = emptyList(),
            sources = emptyList(),
            pendingAttachments = emptyList(),
            attachmentError = null
        )
        job = viewModelScope.launch(Dispatchers.IO) {
            var collectedAll = StringBuilder()
            val toolNotes = ArrayList<String>()
            val sources = ArrayList<SearchResult>()
            try {
                val history = container.chat.messages().takeLast(24)
                val promptText = buildString {
                    if (trimmed.isNotEmpty()) append(trimmed)
                    attachments.forEach { a ->
                        if (a.kind == "image") {
                            append("\n[图片：${a.name}]")
                        } else {
                            append("\n\n[附件：${a.name}]\n")
                            append(a.extractedText)
                        }
                    }
                    if (isEmpty()) append("请查看附件。")
                }.take(60_000)
                val displayText = trimmed.ifBlank { if (attachments.isNotEmpty()) "（附件）" else "" }
                container.chat.add(
                    "user",
                    displayText,
                    MessageMeta.encode(attachments, promptText)
                )
                val imageUrls = attachments.filter { it.kind == "image" }
                    .mapNotNull { it.imageDataUrl.takeIf { url -> url.isNotBlank() } }
                val apiMessages = ArrayList<ApiMessage>()
                apiMessages += ApiMessage("system", system)
                history.forEach { m ->
                    val content = (MessageMeta.prompt(m.meta) ?: m.content).take(20_000)
                    apiMessages += ApiMessage(m.role, content)
                }
                apiMessages += ApiMessage("user", promptText, imageDataUrls = imageUrls)
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        messages = container.chat.messages(),
                        streamingText = "",
                        personas = personasSnapshot
                    )
                }
                var rounds = 0
                var finished = false
                while (rounds < 4 && !finished) {
                    val roundText = StringBuilder()
                    var calls: List<ApiToolCall> = emptyList()
                    container.llm.stream(
                        ChatRequest(
                            apiKey = apiKey,
                            baseUrl = container.settings.llmBaseUrl,
                            model = container.settings.llmModel,
                            messages = apiMessages,
                            tools = container.tools.definitions()
                        )
                    ).collect { event ->
                        when (event) {
                            is StreamEvent.Text -> {
                                roundText.append(event.delta)
                                collectedAll.append(event.delta)
                                withContext(Dispatchers.Main) {
                                    _state.value = _state.value.copy(
                                        streamingText = collectedAll.toString(),
                                        status = ""
                                    )
                                }
                            }
                            is StreamEvent.ToolCalls -> calls = event.calls
                            is StreamEvent.Finished -> Unit
                        }
                    }
                    if (calls.isEmpty()) {
                        finished = true
                        break
                    }
                    apiMessages += ApiMessage("assistant", roundText.toString(), toolCalls = calls)
                    for (call in calls) {
                        withContext(Dispatchers.Main) {
                            _state.value = _state.value.copy(status = toolStatus(call.name))
                        }
                        val result = container.tools.execute(call)
                        toolNotes += result.summary
                        result.sources.forEach { source ->
                            if (sources.none { it.url == source.url }) sources += source
                        }
                        apiMessages += ApiMessage(
                            role = "tool",
                            content = result.payload,
                            toolCallId = call.id,
                            name = call.name
                        )
                    }
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(
                            streamingText = collectedAll.toString(),
                            status = "",
                            toolNotes = toolNotes.toList(),
                            sources = sources.toList()
                        )
                    }
                    rounds++
                }
                val finalText = collectedAll.toString().ifBlank {
                    if (toolNotes.isNotEmpty()) toolNotes.joinToString("\n") else "好的。"
                }
                val meta = JSONObject().apply {
                    put("tools", JSONArray().apply {
                        toolNotes.forEach { put(JSONObject().put("summary", it)) }
                    })
                    if (sources.isNotEmpty()) {
                        put("sources", JSONArray().apply {
                            sources.forEach { s ->
                                put(JSONObject().put("title", s.title).put("url", s.url).put("snippet", s.snippet))
                            }
                        })
                    }
                }.toString()
                container.chat.add("assistant", finalText, meta)
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        messages = container.chat.messages(),
                        streamingText = "",
                        toolNotes = emptyList(),
                        sources = emptyList(),
                        status = "",
                        sending = false
                    )
                }
            } catch (e: CancellationException) {
                if (collectedAll.isNotEmpty()) {
                    container.chat.add("assistant", collectedAll.toString() + "\n\n（已停止）")
                }
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        messages = container.chat.messages(),
                        streamingText = "",
                        sending = false,
                        status = ""
                    )
                }
                throw e
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    _state.value = _state.value.copy(
                        sending = false,
                        streamingText = "",
                        status = "",
                        error = if (e is LlmException) e.message else (e.message ?: "请求失败")
                    )
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun clearChat() {
        viewModelScope.launch(Dispatchers.IO) {
            container.chat.clear()
            container.chat.ensureGreeting(container.personas.activeCards())
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    messages = container.chat.messages(),
                    streamingText = "",
                    pendingAttachments = emptyList()
                )
            }
        }
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }

    private fun toolStatus(name: String): String = when (name) {
        "create_event" -> "正在写日程…"
        "list_events" -> "正在查日程…"
        "create_task" -> "正在记待办…"
        "list_tasks" -> "正在查待办…"
        "update_task" -> "正在更新待办…"
        "create_sticky" -> "正在记便签…"
        "list_stickies" -> "正在查便签…"
        "web_search" -> "正在联网搜索…"
        "fetch_url" -> "正在读取网页…"
        "remember", "recall" -> "正在整理记忆…"
        "get_current_time" -> "正在看时间…"
        else -> "正在处理…"
    }

    companion object {
        const val KEY_LLM = "llm_api_key"
    }
}
