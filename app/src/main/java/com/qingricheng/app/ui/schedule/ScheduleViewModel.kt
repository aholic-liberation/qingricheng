package com.qingricheng.app.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingricheng.app.AppContainer
import com.qingricheng.app.data.EventItem
import com.qingricheng.app.data.TaskItem
import com.qingricheng.app.domain.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID

data class ScheduleUiState(
    val month: YearMonth = YearMonth.now(),
    val selected: LocalDate = LocalDate.now(),
    val events: List<EventItem> = emptyList(),
    val rangeTasks: List<TaskItem> = emptyList(),
    val selectedTasks: List<TaskItem> = emptyList(),
    val syncing: Boolean = false,
    val error: String? = null,
    val caldavConfigured: Boolean = false,
    val lastSync: Long? = null
)

class ScheduleViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(ScheduleUiState())
    val state: StateFlow<ScheduleUiState> = _state.asStateFlow()

    init {
        reload()
        if (container.calDav.isConfigured()) {
            viewModelScope.launch(Dispatchers.IO) { syncInternal(silent = true) }
        }
    }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val current = _state.value
            val start = TimeUtils.dateKey(TimeUtils.gridStart(current.month).minusDays(7))
            val end = TimeUtils.dateKey(TimeUtils.gridStart(current.month).plusDays(48))
            val events = container.schedule.events(start, end)
            val rangeTasks = container.schedule.tasksBetween(start, end)
            val selectedTasks = container.schedule.tasks(TimeUtils.dateKey(current.selected))
            withContext(Dispatchers.Main) {
                _state.value = current.copy(
                    events = events,
                    rangeTasks = rangeTasks,
                    selectedTasks = selectedTasks,
                    caldavConfigured = container.calDav.isConfigured()
                )
            }
        }
    }

    fun selectDate(date: LocalDate) {
        val monthChanged = YearMonth.from(date) != _state.value.month
        _state.value = _state.value.copy(selected = date, month = YearMonth.from(date))
        if (monthChanged) reload()
        viewModelScope.launch(Dispatchers.IO) {
            val tasks = container.schedule.tasks(TimeUtils.dateKey(date))
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(selectedTasks = tasks) }
        }
    }

    fun shiftWeek(delta: Long) {
        val next = _state.value.selected.plusWeeks(delta)
        _state.value = _state.value.copy(selected = next, month = YearMonth.from(next))
        reload()
    }

    fun shiftMonth(delta: Long) {
        val current = _state.value
        val month = current.month.plusMonths(delta)
        val day = current.selected.dayOfMonth.coerceAtMost(month.lengthOfMonth())
        _state.value = current.copy(month = month, selected = month.atDay(day))
        reload()
    }

    fun goToday() {
        val today = LocalDate.now()
        _state.value = _state.value.copy(month = YearMonth.from(today), selected = today)
        reload()
    }

    fun sync() {
        if (!container.calDav.isConfigured()) {
            _state.value = _state.value.copy(error = "还没有配置 CalDAV。请到设置里填写地址、账号和应用专用密码。")
            return
        }
        if (_state.value.syncing) return
        viewModelScope.launch(Dispatchers.IO) { syncInternal(silent = false) }
    }

    private suspend fun syncInternal(silent: Boolean) {
        withContext(Dispatchers.Main) {
            _state.value = _state.value.copy(syncing = true, error = if (silent) _state.value.error else null)
        }
        try {
            val start = TimeUtils.dateKey(TimeUtils.gridStart(_state.value.month).minusDays(7))
            val end = TimeUtils.dateKey(TimeUtils.gridStart(_state.value.month).plusDays(48))
            val push = pushLocalEvents(start, end)
            val result = container.calDav.listEvents(start, end)
            if (result.isFailure) throw result.exceptionOrNull() ?: RuntimeException("同步失败")
            val remote = result.getOrThrow()
            remote.forEach { container.schedule.upsertEvent(it) }
            val remoteUids = remote.map { it.uid }.toSet() + push.second
            container.schedule.events(start, end)
                .filter { !it.local && it.href.isNotBlank() && it.uid !in remoteUids }
                .forEach { container.schedule.deleteEvent(it.id) }
            val events = container.schedule.events(start, end)
            val rangeTasks = container.schedule.tasksBetween(start, end)
            val selectedTasks = container.schedule.tasks(TimeUtils.dateKey(_state.value.selected))
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    events = events,
                    rangeTasks = rangeTasks,
                    selectedTasks = selectedTasks,
                    syncing = false,
                    lastSync = System.currentTimeMillis(),
                    caldavConfigured = true,
                    error = push.first.firstOrNull()
                )
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(
                    syncing = false,
                    error = if (silent) _state.value.error else (e.message ?: "同步失败")
                )
            }
        }
    }

    /** Upload events that were created locally (for example before CalDAV was configured). */
    private fun pushLocalEvents(start: String, end: String): Pair<List<String>, List<String>> {
        val warnings = ArrayList<String>()
        val uploadedUids = ArrayList<String>()
        container.schedule.events(start, end)
            .filter { it.local && it.href.isBlank() }
            .forEach { local ->
                val result = container.calDav.createEvent(local)
                if (result.isSuccess) {
                    val remote = result.getOrThrow()
                    uploadedUids += remote.uid
                    container.schedule.deleteEvent(local.id)
                    container.schedule.upsertEvent(remote)
                } else {
                    warnings += "${local.title} 上传失败：${result.exceptionOrNull()?.message ?: "未知错误"}"
                }
            }
        return warnings to uploadedUids
    }

    fun createEvent(
        title: String,
        date: String,
        start: String,
        end: String,
        allDay: Boolean,
        location: String,
        description: String
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _state.value = _state.value.copy(error = "日程标题不能为空")
            return
        }
        if (TimeUtils.parseDate(date) == null) {
            _state.value = _state.value.copy(error = "日期格式应为 yyyy-MM-dd")
            return
        }
        val cleanStart = if (allDay) "" else start.trim().ifEmpty { "09:00" }
        var cleanEnd = if (allDay) "" else end.trim()
        if (!allDay) {
            if (TimeUtils.parseTime(cleanStart) == null) {
                _state.value = _state.value.copy(error = "开始时间格式应为 HH:mm")
                return
            }
            if (cleanEnd.isBlank()) cleanEnd = LocalTime.parse(cleanStart).plusHours(1).toString().take(5)
            if (TimeUtils.parseTime(cleanEnd) == null) {
                _state.value = _state.value.copy(error = "结束时间格式应为 HH:mm")
                return
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            var event = EventItem(
                id = "local_" + UUID.randomUUID().toString().take(12),
                title = cleanTitle,
                date = date,
                start = cleanStart,
                end = cleanEnd,
                allDay = allDay,
                location = location.trim(),
                description = description.trim(),
                local = true
            )
            var warning: String? = null
            if (container.calDav.isConfigured()) {
                val remote = container.calDav.createEvent(event)
                if (remote.isSuccess) {
                    event = remote.getOrThrow()
                } else {
                    warning = remote.exceptionOrNull()?.message ?: "CalDAV 写入失败"
                }
            }
            container.schedule.upsertEvent(event)
            reload()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(error = warning)
            }
        }
    }

    fun updateEvent(
        original: EventItem,
        title: String,
        date: String,
        start: String,
        end: String,
        allDay: Boolean,
        location: String,
        description: String
    ) {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) {
            _state.value = _state.value.copy(error = "日程标题不能为空")
            return
        }
        if (TimeUtils.parseDate(date) == null) {
            _state.value = _state.value.copy(error = "日期格式应为 yyyy-MM-dd")
            return
        }
        var cleanStart = if (allDay) "" else start.trim().ifEmpty { "09:00" }
        var cleanEnd = if (allDay) "" else end.trim()
        if (!allDay) {
            if (TimeUtils.parseTime(cleanStart) == null) {
                _state.value = _state.value.copy(error = "开始时间格式应为 HH:mm")
                return
            }
            if (cleanEnd.isBlank()) cleanEnd = LocalTime.parse(cleanStart).plusHours(1).toString().take(5)
            if (TimeUtils.parseTime(cleanEnd) == null) {
                _state.value = _state.value.copy(error = "结束时间格式应为 HH:mm")
                return
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            var updated = original.copy(
                title = cleanTitle,
                date = date,
                start = cleanStart,
                end = cleanEnd,
                allDay = allDay,
                endDate = if (allDay) "" else "",
                location = location.trim(),
                description = description.trim(),
                updatedAt = System.currentTimeMillis()
            )
            var warning: String? = null
            if (container.calDav.isConfigured()) {
                if (original.href.isBlank()) {
                    val created = container.calDav.createEvent(updated)
                    if (created.isSuccess) {
                        val remote = created.getOrThrow()
                        container.schedule.deleteEvent(original.id)
                        updated = remote
                    } else {
                        warning = created.exceptionOrNull()?.message ?: "CalDAV 上传失败"
                    }
                } else {
                    val result = container.calDav.updateEvent(updated)
                    if (result.isFailure) {
                        withContext(Dispatchers.Main) {
                            _state.value = _state.value.copy(error = result.exceptionOrNull()?.message ?: "修改失败")
                        }
                        return@launch
                    }
                    val remote = result.getOrThrow()
                    if (remote.id != original.id) container.schedule.deleteEvent(original.id)
                    updated = remote
                }
            }
            container.schedule.upsertEvent(updated)
            reload()
            withContext(Dispatchers.Main) {
                _state.value = _state.value.copy(error = warning)
            }
        }
    }

    fun addTask(title: String) {
        val text = title.trim()
        if (text.isEmpty()) return
        val date = TimeUtils.dateKey(_state.value.selected)
        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            container.schedule.upsertTask(
                TaskItem(id = "task_" + UUID.randomUUID().toString().take(12), title = text, date = date, createdAt = now, updatedAt = now)
            )
            reload()
        }
    }

    fun toggleTask(task: TaskItem) {
        viewModelScope.launch(Dispatchers.IO) {
            container.schedule.upsertTask(task.copy(done = !task.done, updatedAt = System.currentTimeMillis()))
            reload()
        }
    }

    fun deleteTask(task: TaskItem) {
        viewModelScope.launch(Dispatchers.IO) {
            container.schedule.deleteTask(task.id)
            reload()
        }
    }

    fun deleteEvent(event: EventItem) {
        viewModelScope.launch(Dispatchers.IO) {
            if (!event.local && event.href.isNotBlank()) {
                val result = container.calDav.deleteEvent(event)
                if (result.isFailure) {
                    withContext(Dispatchers.Main) {
                        _state.value = _state.value.copy(error = result.exceptionOrNull()?.message ?: "删除失败")
                    }
                    return@launch
                }
            }
            container.schedule.deleteEvent(event.id)
            reload()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun calibrateStatus() {
        _state.value = _state.value.copy(caldavConfigured = container.calDav.isConfigured())
    }
}
