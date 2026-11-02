package com.qingricheng.app.ui.notes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingricheng.app.data.StickyItem
import com.qingricheng.app.ui.theme.Ink
import com.qingricheng.app.ui.theme.Line
import com.qingricheng.app.ui.theme.Muted
import com.qingricheng.app.ui.theme.Sage
import com.qingricheng.app.ui.theme.stickyColor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val COLORS = listOf("butter", "peach", "sage", "sky", "rose")

@Composable
fun NotesScreen(
    state: NotesUiState,
    onAdd: (String, String) -> Unit,
    onToggle: (StickyItem) -> Unit,
    onDelete: (StickyItem) -> Unit
) {
    var draft by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("butter") }
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("便签", style = MaterialTheme.typography.titleLarge, color = Ink, modifier = Modifier.weight(1f))
            Text("${state.stickies.size} 张", fontSize = 12.sp, color = Muted)
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("写一张新便签…", fontSize = 12.5.sp, color = Muted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    if (draft.isNotBlank()) {
                        onAdd(draft, color)
                        draft = ""
                    }
                },
                enabled = draft.isNotBlank(),
                modifier = Modifier.size(46.dp),
                shape = CircleShape,
                contentPadding = PaddingValues(0.dp)
            ) { Text("+", fontSize = 20.sp) }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("颜色", fontSize = 11.sp, color = Muted)
            COLORS.forEach { name ->
                Box(
                    Modifier
                        .size(24.dp)
                        .background(stickyColor(name), CircleShape)
                        .then(if (name == color) Modifier.border(2.dp, Sage, CircleShape) else Modifier)
                        .clickable { color = name }
                )
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state.stickies.isEmpty()) {
                item {
                    Column(
                        Modifier.fillMaxWidth().padding(top = 72.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("还没有便签", style = MaterialTheme.typography.titleMedium, color = Ink)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "写下第一件小事，或对 AI 说“记一张便签”。",
                            fontSize = 12.5.sp,
                            color = Muted
                        )
                    }
                }
            }
            items(state.stickies, key = { it.id }) { sticky ->
                StickyCard(sticky = sticky, onToggle = { onToggle(sticky) }, onDelete = { onDelete(sticky) })
            }
        }
    }
}

@Composable
private fun StickyCard(sticky: StickyItem, onToggle: () -> Unit, onDelete: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .alpha(if (sticky.done) 0.55f else 1f)
            .background(stickyColor(sticky.color), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(end = 58.dp)) {
            Text(
                sticky.text,
                fontSize = 14.5.sp,
                lineHeight = 22.sp,
                color = Ink,
                textDecoration = if (sticky.done) TextDecoration.LineThrough else null
            )
            Spacer(Modifier.height(6.dp))
            Text(
                SimpleDateFormat("M.d", Locale.CHINA).format(Date(sticky.updatedAt)),
                fontSize = 11.sp,
                color = Muted,
                modifier = Modifier.align(Alignment.End)
            )
        }
        Row(
            Modifier.align(Alignment.TopEnd),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onToggle, modifier = Modifier.size(28.dp), contentPadding = PaddingValues(0.dp)) {
                Text(if (sticky.done) "✓" else "○", fontSize = 15.sp, color = if (sticky.done) Color(0xFF5D7355) else Muted)
            }
            TextButton(onClick = onDelete, modifier = Modifier.size(28.dp), contentPadding = PaddingValues(0.dp)) {
                Text("×", fontSize = 16.sp, color = Muted)
            }
        }
    }
}
