package com.qingricheng.app.ui.chat

import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import org.json.JSONObject
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingricheng.app.data.Attachment
import com.qingricheng.app.data.ChatMessage
import com.qingricheng.app.data.MessageMeta
import com.qingricheng.app.data.SearchResult
import com.qingricheng.app.ui.theme.Accent
import com.qingricheng.app.ui.theme.AccentSoft
import com.qingricheng.app.ui.theme.Ink
import com.qingricheng.app.ui.theme.Line
import com.qingricheng.app.ui.theme.Muted
import com.qingricheng.app.ui.theme.Paper
import com.qingricheng.app.ui.theme.Cream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatScreen(
    state: ChatUiState,
    onSend: (String) -> Unit,
    onStop: () -> Unit,
    onClear: () -> Unit,
    onOpenPersonas: () -> Unit,
    onDismissError: () -> Unit,
    onAttach: (Uri, String) -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onDismissAttachmentError: () -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }
    var attachMenu by remember { mutableStateOf(false) }
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onAttach(uri, "image")
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) onAttach(uri, "file")
    }
    val primary = state.personas.firstOrNull()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size, state.streamingText, state.sending) {
        val count = state.messages.size + if (state.streamingText.isNotBlank() || state.sending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    Column(Modifier.fillMaxSize()) {
        ChatHeader(
            title = primary?.name ?: "轻日程",
            subtitle = if (state.personas.size > 1) state.personas.joinToString("、") { it.name }
            else primary?.tagline.orEmpty().ifBlank { "陪你聊天，也帮你安排生活" },
            avatar = primary?.avatar ?: "🌙",
            onOpenPersonas = onOpenPersonas,
            onClear = { confirmClear = true }
        )
        state.error?.let { error ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(error, color = MaterialError, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissError) { Text("知道了") }
            }
        }
        state.attachmentError?.let { error ->
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(error, color = MaterialError, fontSize = 12.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = onDismissAttachmentError) { Text("知道了") }
            }
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.messages.size) { index ->
                val message = state.messages[index]
                MessageBubble(message = message, isUser = message.role == "user", avatar = primary?.avatar ?: "🌙")
            }
            if (state.streamingText.isNotBlank() || state.sending) {
                item {
                    StreamingBubble(
                        text = state.streamingText,
                        status = state.status,
                        toolNotes = state.toolNotes,
                        sources = state.sources,
                        avatar = primary?.avatar ?: "🌙"
                    )
                }
            }
        }
        Composer(
            draft = draft,
            onDraftChange = { draft = it },
            sending = state.sending,
            attachments = state.pendingAttachments,
            processingAttachment = state.processingAttachment,
            onAttachClick = { attachMenu = true },
            onRemoveAttachment = onRemoveAttachment,
            onSend = {
                if (draft.isNotBlank() || state.pendingAttachments.isNotEmpty()) {
                    onSend(draft)
                    draft = ""
                }
            },
            onStop = onStop
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空这段对话？") },
            text = { Text("聊天记录会删除，人设卡和日程/便签不受影响。") },
            confirmButton = {
                TextButton(onClick = { confirmClear = false; onClear() }) { Text("清空") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("取消") } }
        )
    }
    if (attachMenu) {
        AlertDialog(
            onDismissRequest = { attachMenu = false },
            title = { Text("添加附件") },
            text = { Text("图片支持 jpg/png/webp；文档支持 pdf、docx、md、txt、csv、json、html。旧版 .doc 请另存为 docx 或 pdf。") },
            confirmButton = {
                TextButton(onClick = { attachMenu = false; imagePicker.launch("image/*") }) { Text("图片") }
            },
            dismissButton = {
                TextButton(onClick = {
                    attachMenu = false
                    filePicker.launch(
                        arrayOf(
                            "application/pdf",
                            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                            "text/markdown", "text/plain", "text/csv", "application/json", "text/html",
                            "application/octet-stream"
                        )
                    )
                }) { Text("文档") }
            }
        )
    }
}

private val MaterialError = Color(0xFFB3261E)

@Composable
private fun ChatHeader(
    title: String,
    subtitle: String,
    avatar: String,
    onOpenPersonas: () -> Unit,
    onClear: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(40.dp).background(AccentSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(avatar, fontSize = 21.sp) }
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = androidx.compose.material3.MaterialTheme.typography.titleMedium, color = Ink)
            Text(
                subtitle,
                fontSize = 11.5.sp,
                color = Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        TextButton(onClick = onOpenPersonas) { Text("人设", fontSize = 12.sp) }
        TextButton(onClick = onClear) { Text("新对话", fontSize = 12.sp, color = Muted) }
    }
}

@Composable
private fun MessageBubble(message: ChatMessage, isUser: Boolean, avatar: String) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            Box(
                Modifier.size(30.dp).background(AccentSoft, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(avatar, fontSize = 16.sp) }
            Spacer(Modifier.width(8.dp))
        }
        Column(
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            val attachments = MessageMeta.attachments(message.meta)
            if (attachments.isNotEmpty()) {
                AttachmentList(attachments)
                Spacer(Modifier.height(4.dp))
            }
            if (message.content.isNotBlank() && message.content != "（附件）") {
                val bubbleShape = if (isUser) {
                    RoundedCornerShape(20.dp, 20.dp, 20.dp, 6.dp)
                } else {
                    RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp)
                }
                Box(
                    Modifier
                        .background(if (isUser) AccentSoft else Paper, bubbleShape)
                        .border(0.7.dp, if (isUser) Color.Transparent else Line, bubbleShape)
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Text(
                        message.content,
                        color = Ink,
                        fontSize = 15.sp,
                        lineHeight = 23.sp
                    )
                }
            }
            val meta = runCatching { JSONObject(message.meta) }.getOrNull()
            val toolNotes = meta?.optJSONArray("tools")?.let { array ->
                (0 until array.length()).mapNotNull { array.optJSONObject(it)?.optString("summary")?.takeIf { s -> s.isNotBlank() } }
            }.orEmpty()
            val sources = meta?.optJSONArray("sources")?.let { array ->
                (0 until array.length()).mapNotNull { index ->
                    val item = array.optJSONObject(index) ?: return@mapNotNull null
                    SearchResult(item.optString("title"), item.optString("url"), item.optString("snippet"))
                }
            }.orEmpty()
            if (toolNotes.isNotEmpty()) {
                Column(Modifier.padding(top = 5.dp)) {
                    toolNotes.forEach { note -> Text("· $note", fontSize = 11.5.sp, color = Muted, lineHeight = 17.sp) }
                }
            }
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(5.dp))
                SourceCards(sources)
            }
            Text(
                formatTime(message.createdAt),
                fontSize = 11.sp,
                color = Muted,
                modifier = Modifier.padding(top = 4.dp, start = 4.dp, end = 4.dp)
            )
        }
    }
}

@Composable
private fun StreamingBubble(
    text: String,
    status: String,
    toolNotes: List<String>,
    sources: List<SearchResult>,
    avatar: String
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(30.dp).background(AccentSoft, CircleShape),
            contentAlignment = Alignment.Center
        ) { Text(avatar, fontSize = 16.sp) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.widthIn(max = 310.dp)) {
            Box(
                Modifier
                    .background(Paper, RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .border(0.7.dp, Line, RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp)
            ) {
                Text(
                    text.ifBlank { if (status.isNotBlank()) status else "…" },
                    color = Ink,
                    fontSize = 15.sp,
                    lineHeight = 23.sp
                )
            }
            if (toolNotes.isNotEmpty()) {
                Column(Modifier.padding(top = 6.dp)) {
                    toolNotes.forEach { note ->
                        Text("· $note", fontSize = 11.5.sp, color = Muted, lineHeight = 17.sp)
                    }
                }
            }
            if (sources.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                SourceCards(sources)
            }
        }
    }
}


@Composable
private fun SourceCards(sources: List<SearchResult>) {
    val uriHandler = LocalUriHandler.current
    Column(
        Modifier.fillMaxWidth().background(Cream, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        sources.take(5).forEach { source ->
            Column(
                Modifier.fillMaxWidth().clickable {
                    runCatching { uriHandler.openUri(source.url) }
                }.padding(2.dp)
            ) {
                Text(source.title.ifBlank { source.url }, fontSize = 12.5.sp, color = Accent, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(source.url, fontSize = 11.sp, color = Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}


@Composable
private fun AttachmentList(attachments: List<Attachment>) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        attachments.forEach { attachment ->
            if (attachment.kind == "image") {
                AttachmentThumb(
                    attachment = attachment,
                    modifier = Modifier.fillMaxWidth().height(180.dp)
                )
            } else {
                FileChip(attachment)
            }
        }
    }
}

@Composable
private fun AttachmentChip(attachment: Attachment, onRemove: () -> Unit) {
    Row(
        Modifier.background(Paper, RoundedCornerShape(10.dp)).border(0.7.dp, Line, RoundedCornerShape(10.dp)).padding(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (attachment.kind == "image") {
            AttachmentThumb(attachment, Modifier.size(38.dp))
        } else {
            Box(
                Modifier.size(32.dp).background(AccentSoft, RoundedCornerShape(7.dp)),
                contentAlignment = Alignment.Center
            ) { Text(fileBadge(attachment.name), fontSize = 10.5.sp, color = Accent, fontWeight = FontWeight.SemiBold) }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.widthIn(max = 130.dp)) {
            Text(attachment.name, fontSize = 11.5.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatSize(attachment.size), fontSize = 11.sp, color = Muted)
        }
        TextButton(onClick = onRemove, modifier = Modifier.size(24.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("×", fontSize = 15.sp, color = Muted)
        }
    }
}

@Composable
private fun FileChip(attachment: Attachment) {
    Row(
        Modifier.fillMaxWidth().background(Paper, RoundedCornerShape(10.dp)).border(0.7.dp, Line, RoundedCornerShape(10.dp)).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(32.dp).background(AccentSoft, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) { Text(fileBadge(attachment.name), fontSize = 10.5.sp, color = Accent, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text(attachment.name, fontSize = 12.5.sp, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(formatSize(attachment.size), fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun AttachmentThumb(attachment: Attachment, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, attachment.path) {
        value = withContext(Dispatchers.IO) {
            runCatching { BitmapFactory.decodeFile(File(attachment.path).absolutePath) }.getOrNull()
        }
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = attachment.name,
            modifier = modifier.clip(RoundedCornerShape(10.dp)),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier.background(AccentSoft, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) { Text("图片", fontSize = 11.sp, color = Muted) }
    }
}

private fun fileBadge(name: String): String =
    name.substringAfterLast('.', "").uppercase().take(4).ifBlank { "FILE" }

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.CHINA, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.CHINA, "%.0f KB", bytes / 1024.0)
    else -> "$bytes B"
}

@Composable
private fun Composer(
    draft: String,
    onDraftChange: (String) -> Unit,
    sending: Boolean,
    attachments: List<Attachment>,
    processingAttachment: Boolean,
    onAttachClick: () -> Unit,
    onRemoveAttachment: (String) -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().background(Cream).padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 12.dp)
    ) {
        if (attachments.isNotEmpty()) {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                attachments.forEach { attachment ->
                    AttachmentChip(attachment = attachment, onRemove = { onRemoveAttachment(attachment.id) })
                }
            }
        }
        if (processingAttachment) {
            Text("正在读取附件…", fontSize = 11.sp, color = Muted, modifier = Modifier.padding(bottom = 4.dp))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(
                onClick = onAttachClick,
                modifier = Modifier.size(44.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)
            ) { Text("＋", fontSize = 24.sp, color = Ink) }
            Spacer(Modifier.width(4.dp))
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier.weight(1f),
                minLines = 1,
                maxLines = 4,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )
            Spacer(Modifier.width(8.dp))
            if (sending) {
                Button(
                    onClick = onStop,
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Muted)
                ) { Text("停", fontSize = 13.sp) }
            } else {
                Button(
                    onClick = onSend,
                    enabled = draft.isNotBlank() || attachments.isNotEmpty(),
                    modifier = Modifier.size(44.dp),
                    shape = CircleShape,
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent)
                ) { Text("↑", fontSize = 19.sp, color = Color.White) }
            }
        }
    }
}

private fun formatTime(millis: Long): String =
    SimpleDateFormat("M月d日 HH:mm", Locale.CHINA).format(Date(millis))
