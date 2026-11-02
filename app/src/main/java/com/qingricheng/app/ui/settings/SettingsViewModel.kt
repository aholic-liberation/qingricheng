package com.qingricheng.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingricheng.app.AppContainer
import com.qingricheng.app.data.PersonaCard
import com.qingricheng.app.data.ProfileItem
import com.qingricheng.app.net.CalDavClient
import com.qingricheng.app.net.SearchClient
import com.qingricheng.app.ui.chat.ChatViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val loaded: Boolean = false,
    val personas: List<PersonaCard> = emptyList(),
    val activeIds: List<String> = emptyList(),
    val llmBaseUrl: String = "",
    val llmModel: String = "",
    val llmKeySet: Boolean = false,
    val caldavUrl: String = "",
    val caldavUser: String = "",
    val caldavTimezone: String = "Asia/Shanghai",
    val caldavPasswordSet: Boolean = false,
    val searchProvider: String = "tavily",
    val searchKeySet: Boolean = false,
    val reminderLeadMinutes: Int = 15,
    val profile: List<ProfileItem> = emptyList(),
    val testing: Boolean = false,
    val message: String? = null,
    val error: String? = null
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(SettingsUiState())
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    init { reload() }

    fun reload(notice: String? = null, errorMsg: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            val personas = container.personas.list()
            val active = container.personas.activeIds()
            val settings = container.settings
            val state = SettingsUiState(
                loaded = true,
                personas = personas,
                activeIds = active,
                llmBaseUrl = settings.llmBaseUrl,
                llmModel = settings.llmModel,
                llmKeySet = !container.secrets.get(ChatViewModel.KEY_LLM).isNullOrBlank(),
                caldavUrl = settings.caldavUrl,
                caldavUser = settings.caldavUser,
                caldavTimezone = settings.caldavTimezone,
                caldavPasswordSet = !container.secrets.get(CalDavClient.KEY_PASSWORD).isNullOrBlank(),
                searchProvider = settings.searchProvider,
                searchKeySet = !container.secrets.get(SearchClient.KEY_SEARCH).isNullOrBlank(),
                reminderLeadMinutes = settings.reminderLeadMinutes,
                profile = container.profile.list(),
                message = notice,
                error = errorMsg
            )
            withContext(Dispatchers.Main) { _state.value = state }
        }
    }

    fun togglePersona(id: String, checked: Boolean) {
        container.personas.toggleActive(id, checked)
        reload(notice = if (checked) "已启用这张人设卡" else "已停用这张人设卡")
    }

    fun importPersona(json: String) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { container.personas.importJson(json) }
                .onSuccess { cards ->
                    reload(notice = "已导入 ${cards.size} 张人设卡")
                }
                .onFailure { e ->
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(error = e.message ?: "导入失败")
                    }
                }
        }
    }

    fun deletePersona(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            container.personas.delete(id)
            reload(notice = "已删除这张人设卡")
        }
    }

    fun exampleJson(): String = container.personas.exampleJson()

    fun saveLlm(baseUrl: String, model: String, apiKey: String) {
        container.settings.llmBaseUrl = baseUrl
        container.settings.llmModel = model
        if (apiKey.isNotBlank()) container.secrets.put(ChatViewModel.KEY_LLM, apiKey)
        reload(notice = "模型设置已保存")
    }

    fun saveCalDav(url: String, user: String, password: String, timezone: String, test: Boolean) {
        container.settings.caldavUrl = url
        container.settings.caldavUser = user
        container.settings.caldavTimezone = timezone.ifBlank { "Asia/Shanghai" }
        if (password.isNotBlank()) container.secrets.put(CalDavClient.KEY_PASSWORD, password)
        if (!test) {
            reload(notice = "CalDAV 设置已保存")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(testing = true) }
            val result = container.calDav.testConnection()
            reload(
                notice = if (result.isSuccess) "CalDAV 连接成功：${result.getOrNull()}" else null,
                errorMsg = result.exceptionOrNull()?.message
            )
        }
    }

    fun testCalDav() {
        if (container.calDav.config() == null) {
            _state.value = _state.value.copy(error = "请先填写并保存 CalDAV 地址、账号和密码")
            return
        }
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(testing = true) }
            val result = container.calDav.testConnection()
            reload(
                notice = if (result.isSuccess) "CalDAV 连接成功：${result.getOrNull()}" else null,
                errorMsg = result.exceptionOrNull()?.message
            )
        }
    }

    fun saveProfile(existing: ProfileItem?, label: String, value: String) {
        val cleanLabel = label.trim()
        val cleanValue = value.trim()
        if (cleanLabel.isEmpty() || cleanValue.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            if (existing != null && !existing.label.equals(cleanLabel, ignoreCase = true)) {
                container.profile.delete(existing.id)
            }
            container.profile.set(cleanLabel, cleanValue)
            reload(notice = "已保存关于我的资料")
        }
    }

    fun deleteProfile(item: ProfileItem) {
        viewModelScope.launch(Dispatchers.IO) {
            container.profile.delete(item.id)
            reload(notice = "已删除关于我的资料")
        }
    }

    fun saveReminderLead(minutes: Int) {
        container.settings.reminderLeadMinutes = minutes
        container.rescheduleReminders()
        reload(notice = "提醒时间已保存：提前 $minutes 分钟")
    }

    fun saveSearch(provider: String, apiKey: String) {
        container.settings.searchProvider = provider
        if (apiKey.isNotBlank()) container.secrets.put(SearchClient.KEY_SEARCH, apiKey)
        reload(notice = "搜索设置已保存")
    }

    fun clearFeedback() {
        _state.value = _state.value.copy(message = null, error = null)
    }
}
