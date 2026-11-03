package com.qingricheng.app.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingricheng.app.data.EventItem
import com.qingricheng.app.data.TaskItem
import com.qingricheng.app.domain.TimeUtils
import com.qingricheng.app.ui.theme.Accent
import com.qingricheng.app.ui.theme.CreamDeep
import com.qingricheng.app.ui.theme.Cream
import com.qingricheng.app.ui.theme.Ink
import com.qingricheng.app.ui.theme.Line
import com.qingricheng.app.ui.theme.Muted
import com.qingricheng.app.ui.theme.Paper
import com.qingricheng.app.ui.theme.Sage
import com.qingricheng.app.ui.theme.Remote
import com.qingricheng.app.ui.theme.RemoteSoft
import java.time.LocalDate
import java.time.LocalTime

private val HourHeight = 54.dp
private const val StartHour = 7
private const val EndHour = 23

@Composable
fun WeekScreen(
    state: ScheduleUiState,
    onSelectDate: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit,
    onToday: () -> Unit,
    onSync: () -> Unit,
    onCreateEvent: (String, String, String, String, Boolean, String, String) -> Unit,
    onUpdateEvent: (EventItem, String, String, String, String, Boolean, String, String) -> Unit,
    onDeleteEvent: (EventItem) -> Unit,
    onClearError: () -> Unit,
    onOpenSettings: () -> Unit
) {
    var showEventDialog by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<EventItem?>(null) }
    var detailEvent by remember { mutableStateOf<EventItem?>(null) }
    val today = LocalDate.now()
    val weekStart = state.selected.minusDays((state.selected.dayOfWeek.value - 1).toLong())
    val days = (0..6).map { weekStart.plusDays(it.toLong()) }
    val weekEnd = days.last()

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 18.dp, end = 10.dp, top = 12.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("周视图", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(
                    "${weekStart.monthValue}月${weekStart.dayOfMonth}日 - ${weekEnd.monthValue}月${weekEnd.dayOfMonth}日",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            TextButton(onClick = { onShiftWeek(-1) }) { Text("‹", fontSize = 22.sp) }
            TextButton(onClick = { onShiftWeek(1) }) { Text("›", fontSize = 22.sp) }
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
            Box(Modifier.size(7.dp).background(if (state.caldavConfigured) Sage else Muted, CircleShape))
            Spacer(Modifier.width(5.dp))
            Text(if (state.caldavConfigured) "CalDAV 已连接" else "CalDAV 未连接", fontSize = 11.sp, color = Muted)
            if (!state.caldavConfigured) {
                TextButton(onClick = onOpenSettings, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text("去设置", fontSize = 11.sp, color = Ink, textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline)
                }
            }
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showEventDialog = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                Text("+ 日程", fontSize = 11.sp)
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

        WeekHeader(
            days = days,
            selected = state.selected,
            today = today,
            events = state.events,
            onSelect = onSelectDate
        )
        Row(
            Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())
        ) {
            HourLabels()
            days.forEach { day ->
                DayColumn(
                    day = day,
                    events = state.events.filter { it.date == TimeUtils.dateKey(day) },
                    tasks = state.rangeTasks.filter { it.date == TimeUtils.dateKey(day) },
                    onEventClick = { detailEvent = it }
                )
            }
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
    detailEvent?.let { event ->
        EventDetailDialog(
            event = event,
            onDismiss = { detailEvent = null },
            onEdit = {
                detailEvent = null
                editingEvent = event
            },
            onDelete = {
                onDeleteEvent(event)
                detailEvent = null
            }
        )
    }

    editingEvent?.let { event ->
        EventDialog(
            date = event.date,
            initial = event,
            onDismiss = { editingEvent = null },
            onSave = { title, date, start, end, allDay, location, description ->
                onUpdateEvent(event, title, date, start, end, allDay, location, description)
                editingEvent = null
            }
        )
    }
}

@Composable
private fun EventDetailDialog(
    event: EventItem,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                event.title,
                style = MaterialTheme.typography.titleLarge,
                color = Ink
            )
        },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                val timeText = if (event.allDay) "全天" else {
                    when {
                        event.start.isNotBlank() && event.end.isNotBlank() -> "${event.start} - ${event.end}"
                        event.start.isNotBlank() -> event.start
                        else -> ""
                    }
                }
                Text(
                    TimeUtils.prettyDate(event.date) + if (timeText.isNotBlank()) " · $timeText" else "",
                    fontSize = 12.5.sp,
                    color = Ink
                )
                if (event.location.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text("地点", fontSize = 11.sp, color = Muted)
                    Text(event.location, fontSize = 13.sp, color = Ink, lineHeight = 19.sp)
                }
                Spacer(Modifier.height(10.dp))
                Text("备注", fontSize = 11.sp, color = Muted)
                Text(
                    event.description.ifBlank { "暂无备注" },
                    fontSize = 13.sp,
                    color = if (event.description.isBlank()) Muted else Ink,
                    lineHeight = 20.sp
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) { Text("修改", fontSize = 13.sp, color = Ink) }
        },
        dismissButton = {
            TextButton(onClick = onDelete) { Text("删除", fontSize = 13.sp, color = Color(0xFFB3261E)) }
        }
    )
}

@Composable
private fun WeekHeader(
    days: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    events: List<EventItem>,
    onSelect: (LocalDate) -> Unit
) {
    val allDayByDate = events.filter { it.allDay }.groupBy { it.date }
    Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(38.dp))
            days.forEach { day ->
                val key = TimeUtils.dateKey(day)
                val isSelected = day == selected
                val isToday = day == today
                Column(
                    Modifier.weight(1f).padding(horizontal = 1.dp).clickable { onSelect(day) },
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        weekdayShort(day),
                        fontSize = 11.sp,
                        color = if (day.dayOfWeek.value >= 6) Muted else Muted
                    )
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(34.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(if (isSelected) Ink else Color.Transparent)
                            .then(if (isToday && !isSelected) Modifier.border(1.dp, Accent, RoundedCornerShape(11.dp)) else Modifier),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            day.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium.copy(
                                platformStyle = PlatformTextStyle(includeFontPadding = false)
                            ),
                            fontSize = 14.sp,
                            lineHeight = 14.sp,
                            textAlign = TextAlign.Center,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            color = when {
                                isSelected -> Color.White
                                isToday -> Ink
                                day.dayOfWeek.value >= 6 -> Muted
                                else -> Ink
                            }
                        )
                    }
                    allDayByDate[key].orEmpty().take(1).forEach { event ->
                        Text(
                            event.title,
                            fontSize = 10.sp,
                            color = Ink,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier
                                .padding(top = 2.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(CreamDeep)
                                .padding(horizontal = 3.dp, vertical = 1.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(5.dp))
    }
}

@Composable
private fun HourLabels() {
    Column(Modifier.width(38.dp).height(HourHeight * (EndHour - StartHour))) {
        for (hour in StartHour until EndHour) {
            Box(Modifier.fillMaxWidth().height(HourHeight), contentAlignment = Alignment.TopEnd) {
                Text(
                    if (hour == StartHour) "${hour}:00" else "$hour:00",
                    fontSize = 11.sp,
                    color = Muted,
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun RowScope.DayColumn(
    day: LocalDate,
    events: List<EventItem>,
    tasks: List<TaskItem>,
    onEventClick: (EventItem) -> Unit
) {
    val timed = events.filter { !it.allDay }
    val totalHeight = HourHeight * (EndHour - StartHour)
    Box(
        Modifier
            .weight(1f)
            .height(totalHeight)
            .padding(horizontal = 1.dp)
            .background(if (day == LocalDate.now()) Ink.copy(alpha = 0.04f) else Color.Transparent)
            .border(0.5.dp, Line.copy(alpha = 0.25f))
    ) {
        Column(Modifier.fillMaxSize()) {
            for (hour in StartHour until EndHour) {
                Box(Modifier.fillMaxWidth().height(HourHeight).border(0.3.dp, Line.copy(alpha = 0.14f)))
            }
        }
        tasks.take(2).forEachIndexed { index, task ->
            Text(
                "· ${task.title}",
                fontSize = 10.sp,
                color = Sage,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 2.dp, end = 2.dp, bottom = (2 + index * 12).dp)
            )
        }
        timed.forEach { event ->
            val start = TimeUtils.parseTime(event.start) ?: LocalTime.of(9, 0)
            val end = TimeUtils.parseTime(event.end)
                ?: start.plusHours(1)
            val startMinutes = start.hour * 60 + start.minute
            val endMinutes = maxOf(end.hour * 60 + end.minute, startMinutes + 30)
            val top = ((startMinutes - StartHour * 60).coerceAtLeast(0) / 60f) * HourHeight.value
            val height = ((endMinutes - maxOf(startMinutes, StartHour * 60)).coerceAtLeast(30) / 60f) * HourHeight.value
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset(y = top.dp)
                    .height(height.dp)
                    .padding(horizontal = 1.5.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (event.local) Color(0xFFE6EFE8) else RemoteSoft)
                    .border(0.7.dp, if (event.local) Sage.copy(alpha = 0.35f) else Remote.copy(alpha = 0.35f), RoundedCornerShape(7.dp))
                    .clickable { onEventClick(event) }
                    .padding(horizontal = 4.dp, vertical = 3.dp)
            ) {
                Text(
                    (if (event.start.isBlank()) "" else event.start + " ") + event.title,
                    fontSize = 11.sp,
                    lineHeight = 12.sp,
                    color = if (event.local) Sage else Remote,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun weekdayShort(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "一"
    2 -> "二"
    3 -> "三"
    4 -> "四"
    5 -> "五"
    6 -> "六"
    else -> "日"
}
