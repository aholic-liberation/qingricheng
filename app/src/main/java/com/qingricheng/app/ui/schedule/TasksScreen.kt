package com.qingricheng.app.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingricheng.app.data.TaskItem
import com.qingricheng.app.ui.theme.Ink
import com.qingricheng.app.ui.theme.Muted
import java.time.LocalDate

@Composable
fun TasksScreen(
    state: ScheduleUiState,
    onAddTask: (String) -> Unit,
    onToggleTask: (TaskItem) -> Unit,
    onDeleteTask: (TaskItem) -> Unit,
    onGoToday: () -> Unit
) {
    var draft by remember { mutableStateOf("") }
    val selected = state.selected
    val tasks = state.selectedTasks

    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Row(
            Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("待办", style = MaterialTheme.typography.titleLarge, color = Ink)
                Text(
                    "${selected.monthValue} 月 ${selected.dayOfMonth} 日 · ${weekdayName(selected)}",
                    fontSize = 12.sp,
                    color = Muted
                )
            }
            TextButton(
                onClick = onGoToday,
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) { Text("今天", fontSize = 12.sp, color = Ink) }
        }

        Row(
            Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("给这一天留一件事…", fontSize = 12.sp, color = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (draft.isNotBlank()) {
                        onAddTask(draft)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) { Text("+", fontSize = 20.sp) }
        }

        Text(
            "${tasks.size} 件待办",
            fontSize = 11.sp,
            color = Muted,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (tasks.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 64.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("这一天还没有待办", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "写下第一件小事，或对 AI 说“记一件待办”。",
                            fontSize = 12.5.sp,
                            color = Muted
                        )
                    }
                }
            }
            items(tasks, key = { it.id }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { onToggleTask(task) },
                    onDelete = { onDeleteTask(task) }
                )
            }
        }
    }
}

@Composable
private fun TaskRow(task: TaskItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = task.done,
            onCheckedChange = { onToggle() },
            modifier = Modifier.size(36.dp)
        )
        Text(
            task.title,
            fontSize = 14.sp,
            color = if (task.done) Muted else Ink,
            textDecoration = if (task.done) TextDecoration.LineThrough else null,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onDelete,
            contentPadding = PaddingValues(0.dp),
            modifier = Modifier.size(30.dp)
        ) { Text("×", fontSize = 16.sp, color = Muted) }
    }
}

private fun weekdayName(date: LocalDate): String = when (date.dayOfWeek.value) {
    1 -> "星期一"
    2 -> "星期二"
    3 -> "星期三"
    4 -> "星期四"
    5 -> "星期五"
    6 -> "星期六"
    else -> "星期日"
}
