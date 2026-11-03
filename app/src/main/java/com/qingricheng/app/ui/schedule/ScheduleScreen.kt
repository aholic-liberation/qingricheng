package com.qingricheng.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingricheng.app.data.EventItem
import com.qingricheng.app.data.TaskItem
import com.qingricheng.app.domain.TimeUtils
import com.qingricheng.app.ui.theme.Accent
import com.qingricheng.app.ui.theme.Cream
import com.qingricheng.app.ui.theme.Ink
import com.qingricheng.app.ui.theme.Line
import com.qingricheng.app.ui.theme.Muted
import com.qingricheng.app.ui.theme.Paper
import com.qingricheng.app.ui.theme.Sage
import com.qingricheng.app.ui.theme.Remote
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun ScheduleScreen(
    state: ScheduleUiState,
    onSelectDate: (LocalDate) -> Unit,
    onShiftMonth: (Long) -> Unit,
    onToday: () -> Unit,
    onSync: () -> Unit,
    onCreateEvent: (String, String, String, String, Boolean, String, String) -> Unit,
    onDeleteEvent: (EventItem) -> Unit,
    onClearError: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showEventDialog by remember { mutableStateOf(false) }
    var deleteEvent by remember { mutableStateOf<EventItem?>(null) }
    val today = LocalDate.now()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("日程", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(
                    "${state.month.year} 年 ${state.month.monthValue} 月",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            TextButton(onClick = { onShiftMonth(-1) }) { Text("‹", fontSize = 22.sp) }
            TextButton(onClick = { onShiftMonth(1) }) { Text("›", fontSize = 22.sp) }
            TextButton(onClick = onToday) { Text("今天", fontSize = 12.sp) }
            Button(
                onClick = onSync,
                enabled = !state.syncing,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier.height(34.dp)
            ) {
                if (state.syncing) {
                    CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 1.5.dp, color = Color.White)
                } else {
                    Text("同步", fontSize = 12.sp)
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(7.dp).background(if (state.caldavConfigured) Sage else Muted, CircleShape)
            )
            Spacer(Modifier.width(5.dp))
            Text(
                if (state.caldavConfigured) "CalDAV 已连接" else "CalDAV 未连接",
                fontSize = 11.sp,
                color = Muted
            )
            if (!state.caldavConfigured) {
                TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("去设置", fontSize = 11.sp, color = Ink, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                }
            }
            Spacer(Modifier.weight(1f))
            state.lastSync?.let {
                Text("上次同步 ${TimeUtils.prettyDateTime(TimeUtils.instantToDate(java.time.Instant.ofEpochMilli(it)), TimeUtils.instantToTime(java.time.Instant.ofEpochMilli(it)))}", fontSize = 11.sp, color = Muted)
            }
        }
        state.error?.let { error ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(error, fontSize = 11.5.sp, color = Color(0xFFB3261E), modifier = Modifier.weight(1f))
                TextButton(onClick = onClearError) { Text("知道了", fontSize = 11.sp) }
            }
        }

        MonthGrid(
            month = state.month,
            selected = state.selected,
            today = today,
            events = state.events,
            tasks = state.rangeTasks,
            onSelect = onSelectDate
        )

        Column(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(
                    "${state.selected.monthValue}月${state.selected.dayOfMonth}日 · ${weekdayName(state.selected)}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Ink,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { showEventDialog = true }) { Text("+ 日程", fontSize = 12.sp) }
            }
            val dayEvents = state.events.filter { it.date == TimeUtils.dateKey(state.selected) }
            if (dayEvents.isEmpty()) {
                Text(
                    "这一天还没有安排",
                    fontSize = 13.sp,
                    color = Muted,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 20.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
            dayEvents.forEach { event ->
                EventRow(event = event, onDelete = { deleteEvent = event })
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEventDialog) {
        EventDialog(
            date = TimeUtils.dateKey(state.selected),
            onDismiss = { showEventDialog = false },
            onSave = { title, date, start, end, allDay, location, description ->
                onCreateEvent(title, date, start, end, allDay, location, description)
                showEventDialog = false
            }
        )
    }
    deleteEvent?.let { event ->
        AlertDialog(
            onDismissRequest = { deleteEvent = null },
            title = { Text("删除这条日程？") },
            text = { Text("${event.title}\n${TimeUtils.prettyDateTime(event.date, event.start)}") },
            confirmButton = {
                TextButton(onClick = { onDeleteEvent(event); deleteEvent = null }) { Text("删除") }
            },
            dismissButton = { TextButton(onClick = { deleteEvent = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun MonthGrid(
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    events: List<EventItem>,
    tasks: List<TaskItem>,
    onSelect: (LocalDate) -> Unit
) {
    val days = TimeUtils.gridDays(month)
    val eventsByDate = events.groupBy { it.date }
    val tasksByDate = tasks.groupBy { it.date }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                Text(
                    label,
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 11.sp,
                    color = Muted,
                    lineHeight = 24.sp
                )
            }
        }
        for (row in 0..5) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0..6) {
                    val date = days[row * 7 + col]
                    val key = TimeUtils.dateKey(date)
                    val isSelected = date == selected
                    val isToday = date == today
                    val outside = date.month != month.month
                    val hasEvent = eventsByDate[key].orEmpty().isNotEmpty()
                    val hasTask = tasksByDate[key].orEmpty().isNotEmpty()
                    Box(
                        Modifier
                            .weight(1f)
                            .aspectRatio(1f)
                            .padding(1.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Ink else Color.Transparent)
                            .then(
                                if (isToday && !isSelected) Modifier.border(1.2.dp, Accent, RoundedCornerShape(10.dp))
                                else Modifier
                            )
                            .clickable { onSelect(date) },
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                date.dayOfMonth.toString(),
                                fontSize = 14.sp,
                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                color = when {
                                    isSelected -> Color.White
                                    outside -> Muted.copy(alpha = 0.35f)
                                    isToday -> Ink
                                    date.dayOfWeek.value >= 6 -> Muted
                                    else -> Ink
                                }
                            )
                            Row(Modifier.height(5.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                if (hasEvent) Box(Modifier.size(4.dp).background(if (isSelected) Color.White else Remote, CircleShape))
                                if (hasTask) Box(Modifier.size(4.dp).background(if (isSelected) Color.White else Sage, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EventRow(event: EventItem, onDelete: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(Paper, RoundedCornerShape(14.dp))
            .border(0.7.dp, Line, RoundedCornerShape(14.dp))
            .padding(start = 0.dp, end = 10.dp, top = 9.dp, bottom = 9.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            Modifier
                .width(4.dp)
                .height(40.dp)
                .background(if (event.local) Sage else Remote, RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(9.dp))
        Text(
            if (event.allDay) "全天" else event.start.ifBlank { "—" },
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.width(44.dp).padding(top = 2.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(event.title, fontSize = 14.5.sp, color = Ink, fontWeight = FontWeight.Medium)
            if (event.location.isNotBlank()) {
                Text(event.location, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (event.local) {
                Text("本地日程", fontSize = 11.sp, color = Muted)
            }
        }
        TextButton(onClick = onDelete, contentPadding = PaddingValues(0.dp), modifier = Modifier.size(30.dp)) {
            Text("×", fontSize = 17.sp, color = Muted)
        }
    }
}

@Composable
internal fun EventDialog(
    date: String,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String, Boolean, String, String) -> Unit,
    initial: EventItem? = null
) {
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var dateValue by remember(initial?.id) { mutableStateOf(initial?.date ?: date) }
    var start by remember(initial?.id) { mutableStateOf(initial?.start?.ifBlank { "09:00" } ?: "09:00") }
    var end by remember(initial?.id) { mutableStateOf(initial?.end?.ifBlank { "10:00" } ?: "10:00") }
    var allDay by remember(initial?.id) { mutableStateOf(initial?.allDay ?: false) }
    var location by remember(initial?.id) { mutableStateOf(initial?.location.orEmpty()) }
    var description by remember(initial?.id) { mutableStateOf(initial?.description.orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "新建日程" else "修改日程") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("标题") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = dateValue, onValueChange = { dateValue = it }, label = { Text("日期 yyyy-MM-dd") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = allDay, onCheckedChange = { allDay = it })
                    Text("全天", fontSize = 13.sp)
                }
                if (!allDay) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = start, onValueChange = { start = it }, label = { Text("开始 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = end, onValueChange = { end = it }, label = { Text("结束 HH:mm") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = location, onValueChange = { location = it }, label = { Text("地点（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("备注（可选）") }, minLines = 2, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(title, dateValue, start, end, allDay, location, description) },
                enabled = title.isNotBlank() && dateValue.isNotBlank()
            ) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}

private fun weekdayName(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "星期一"; 2 -> "星期二"; 3 -> "星期三"; 4 -> "星期四"; 5 -> "星期五"; 6 -> "星期六"; else -> "星期日"
}
