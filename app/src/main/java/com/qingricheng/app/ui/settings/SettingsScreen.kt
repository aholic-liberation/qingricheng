package com.qingricheng.app.ui.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.qingricheng.app.notify.Notifications
import com.qingricheng.app.data.ProfileItem
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qingricheng.app.ui.theme.Accent
import com.qingricheng.app.ui.theme.AccentSoft
import com.qingricheng.app.ui.theme.Cream
import com.qingricheng.app.ui.theme.Ink
import com.qingricheng.app.ui.theme.Line
import com.qingricheng.app.ui.theme.Muted
import com.qingricheng.app.ui.theme.Paper
import com.qingricheng.app.ui.theme.Sage

@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onTogglePersona: (String, Boolean) -> Unit,
    onImportPersona: (String) -> Unit,
    onDeletePersona: (String) -> Unit,
    onSaveLlm: (String, String, String) -> Unit,
    onSaveCalDav: (String, String, String, String, Boolean) -> Unit,
    onTestCalDav: () -> Unit,
    onSaveSearch: (String, String) -> Unit,
    onSaveReminderLead: (Int) -> Unit,
    onSaveProfile: (ProfileItem?, String, String) -> Unit,
    onDeleteProfile: (ProfileItem) -> Unit,
    onClearFeedback: () -> Unit,
    exampleJson: () -> String
) {
    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(Notifications.canNotify(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) notificationsOn = Notifications.canNotify(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var initialized by remember { mutableStateOf(false) }
    var llmBase by remember { mutableStateOf("") }
    var llmModel by remember { mutableStateOf("") }
    var llmKey by remember { mutableStateOf("") }
    var calUrl by remember { mutableStateOf("") }
    var calUser by remember { mutableStateOf("") }
    var calPassword by remember { mutableStateOf("") }
    var calTz by remember { mutableStateOf("Asia/Shanghai") }
    var provider by remember { mutableStateOf("bing") }
    var searchKey by remember { mutableStateOf("") }

    LaunchedEffect(state.loaded) {
        if (state.loaded && !initialized) {
            llmBase = state.llmBaseUrl
            llmModel = state.llmModel
            calUrl = state.caldavUrl
            calUser = state.caldavUser
            calTz = state.caldavTimezone
            provider = state.searchProvider
            initialized = true
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()?.let(onImportPersona)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(exampleJson().toByteArray(Charsets.UTF_8))
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            "设置",
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            modifier = Modifier.padding(start = 4.dp, top = 12.dp)
        )
        state.message?.let { Feedback(it, false, onClearFeedback) }
        state.error?.let { Feedback(it, true, onClearFeedback) }

        Section("人设卡") {
            Text(
                "可以多选；排在最前面的作为主人设，其余作为补充特质。导出的 JSON 可以交给其他 AI 填写。",
                fontSize = 11.5.sp,
                color = Muted,
                lineHeight = 17.sp
            )
            Spacer(Modifier.height(4.dp))
            state.personas.forEach { card ->
                val active = state.activeIds.contains(card.id)
                val primary = state.activeIds.firstOrNull() == card.id
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(checked = active, onCheckedChange = { onTogglePersona(card.id, it) })
                    BoxAvatar(card.avatar)
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(card.name, fontSize = 14.sp, color = Ink, fontWeight = FontWeight.Medium)
                            if (primary) {
                                Spacer(Modifier.width(6.dp))
                                Box(
                                    Modifier.background(Accent, RoundedCornerShape(4.dp)).padding(horizontal = 4.dp, vertical = 1.dp),
                                    contentAlignment = Alignment.Center
                                ) { Text("主", fontSize = 10.5.sp, color = Color.White, lineHeight = 11.sp) }
                            }
                        }
                        Text(
                            card.tagline.ifBlank { card.description }.ifBlank { "自定义人设" },
                            fontSize = 11.sp,
                            color = Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { onDeletePersona(card.id) }, contentPadding = PaddingValues(0.dp)) {
                        Text("删除", fontSize = 11.sp, color = Muted)
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) { Text("导入人设卡", fontSize = 11.5.sp) }
                Button(
                    onClick = { exportLauncher.launch("example.persona.json") },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(34.dp)
                ) { Text("导出示例", fontSize = 11.5.sp) }
            }
        }

        ProfileSection(
            items = state.profile,
            onSave = onSaveProfile,
            onDelete = onDeleteProfile
        )

        Section("模型（DeepSeek 兼容接口）") {
            OutlinedTextField(value = llmBase, onValueChange = { llmBase = it }, label = { Text("Base URL") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = llmModel, onValueChange = { llmModel = it }, label = { Text("模型名") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = llmKey,
                onValueChange = { llmKey = it },
                label = { Text("API Key") },
                placeholder = { Text(if (state.llmKeySet) "已保存，留空不修改" else "sk-…", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = { onSaveLlm(llmBase, llmModel, llmKey); llmKey = "" }, modifier = Modifier.height(36.dp)) {
                Text("保存模型设置", fontSize = 12.sp)
            }
        }

        Section("通知") {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(if (notificationsOn) Sage else Muted, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(if (notificationsOn) "通知已开启" else "通知未开启", fontSize = 12.sp, color = Ink)
            }
            Spacer(Modifier.height(4.dp))
            Text("日程开始前提醒；系统通知权限关闭时不会收到提醒。", fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
            Text("提前（分钟）", fontSize = 11.sp, color = Muted)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                listOf(5, 10, 15, 30, 60).forEach { minutes ->
                    FilterChip(
                        selected = state.reminderLeadMinutes == minutes,
                        onClick = { onSaveReminderLead(minutes) },
                        label = { Text("$minutes", fontSize = 11.sp) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(
                    onClick = { Notifications.openSettings(context) },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                    modifier = Modifier.height(36.dp)
                ) { Text(if (notificationsOn) "通知设置" else "开启通知权限", fontSize = 12.sp) }
                TextButton(
                    onClick = {
                        val ok = Notifications.showTest(context)
                        notificationsOn = Notifications.canNotify(context)
                        Toast.makeText(
                            context,
                            if (ok) "测试通知已发送" else "请先开启通知权限",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                ) { Text("发送测试通知", fontSize = 11.sp) }
            }
        }

        Section("CalDAV 日历") {
            Text("钉钉日历地址形如 https://calendar.dingtalk.com/dav/<用户id>/primary/；密码建议用应用专用密码。", fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = calUrl, onValueChange = { calUrl = it }, label = { Text("CalDAV 地址") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            TextButton(
                onClick = {
                    if (calUser.isNotBlank()) {
                        calUrl = "https://calendar.dingtalk.com/dav/${calUser.trim()}/primary/"
                    }
                },
                enabled = calUser.isNotBlank(),
                contentPadding = PaddingValues(horizontal = 0.dp)
            ) { Text("钉钉账号补全地址", fontSize = 11.sp, color = Accent) }
            Spacer(Modifier.height(2.dp))
            OutlinedTextField(value = calUser, onValueChange = { calUser = it }, label = { Text("账号") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = calPassword,
                onValueChange = { calPassword = it },
                label = { Text("密码 / 应用专用密码") },
                placeholder = { Text(if (state.caldavPasswordSet) "已保存，留空不修改" else "", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            OutlinedTextField(value = calTz, onValueChange = { calTz = it }, label = { Text("时区") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Button(onClick = { onSaveCalDav(calUrl, calUser, calPassword, calTz, false); calPassword = "" }, modifier = Modifier.height(36.dp)) {
                    Text("保存", fontSize = 12.sp)
                }
                Button(onClick = { onSaveCalDav(calUrl, calUser, calPassword, calTz, true); calPassword = "" }, enabled = !state.testing, modifier = Modifier.height(36.dp)) {
                    Text(if (state.testing) "测试中…" else "保存并测试", fontSize = 12.sp)
                }
                TextButton(onClick = onTestCalDav, enabled = !state.testing) { Text("测试连接", fontSize = 11.sp) }
            }
        }

        Section("联网搜索") {
            Text("DeepSeek 官方 API 不自带联网，换 v4.1flash 也不会自动联网。Bing 网页搜索免 Key 可直接试用；Tavily/Serper 更稳定，适合正式使用。", fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = provider == "bing", onClick = { provider = "bing" }, label = { Text("Bing 网页", fontSize = 11.sp) })
                FilterChip(selected = provider == "tavily", onClick = { provider = "tavily" }, label = { Text("Tavily", fontSize = 11.sp) })
                FilterChip(selected = provider == "serper", onClick = { provider = "serper" }, label = { Text("Serper", fontSize = 11.sp) })
            }
            OutlinedTextField(
                value = searchKey,
                onValueChange = { searchKey = it },
                label = { Text("搜索 API Key") },
                placeholder = { Text(if (provider == "bing") "Bing 网页搜索不需要 Key" else if (state.searchKeySet) "已保存，留空不修改" else "tvly-… / serper key", fontSize = 12.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Button(onClick = { onSaveSearch(provider, searchKey); searchKey = "" }, modifier = Modifier.height(36.dp)) {
                Text("保存搜索设置", fontSize = 12.sp)
            }
        }

        Section("关于") {
            Text("轻日程 0.5.1", fontSize = 13.sp, color = Ink, fontWeight = FontWeight.Medium)
            Text("纯 Android 本地应用：聊天、人设卡、CalDAV 日程、便签和联网搜索。数据保存在本机。", fontSize = 11.5.sp, color = Muted, lineHeight = 17.sp)
            Spacer(Modifier.height(4.dp))
            Text("人设卡示例文件会导出为 example.persona.json；把 JSON 交给其他 AI 即可按模板创作。", fontSize = 11.sp, color = Muted, lineHeight = 16.sp)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ProfileSection(
    items: List<ProfileItem>,
    onSave: (ProfileItem?, String, String) -> Unit,
    onDelete: (ProfileItem) -> Unit
) {
    var editing by remember { mutableStateOf<ProfileItem?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    Section("关于我") {
        Text(
            "年级、身份、学校、专业、称呼、习惯等长期资料。可以自己填，也可以在聊天中让 AI 代填或删除。",
            fontSize = 11.sp,
            color = Muted,
            lineHeight = 16.sp
        )
        Spacer(Modifier.height(4.dp))
        if (items.isEmpty()) {
            Text("还没有资料", fontSize = 11.5.sp, color = Muted, modifier = Modifier.padding(vertical = 4.dp))
        }
        items.forEach { item ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(item.label, fontSize = 11.sp, color = Accent, fontWeight = FontWeight.Medium)
                    Text(item.value, fontSize = 12.5.sp, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                TextButton(
                    onClick = {
                        editing = item
                        label = item.label
                        value = item.value
                        showDialog = true
                    },
                    contentPadding = PaddingValues(horizontal = 5.dp)
                ) { Text("修改", fontSize = 11.sp, color = Muted) }
                TextButton(
                    onClick = { onDelete(item) },
                    contentPadding = PaddingValues(horizontal = 5.dp)
                ) { Text("删除", fontSize = 11.sp, color = Muted) }
            }
        }
        Spacer(Modifier.height(4.dp))
        Button(
            onClick = {
                editing = null
                label = ""
                value = ""
                showDialog = true
            },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
            modifier = Modifier.height(34.dp)
        ) { Text("+ 添加信息", fontSize = 11.5.sp) }
    }
    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(if (editing == null) "添加关于我" else "修改关于我") },
            text = {
                Column {
                    OutlinedTextField(
                        value = label,
                        onValueChange = { label = it },
                        label = { Text("资料项，如 年级 / 身份 / 学校") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = value,
                        onValueChange = { value = it },
                        label = { Text("内容") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onSave(editing, label, value)
                        showDialog = false
                    },
                    enabled = label.isNotBlank() && value.isNotBlank()
                ) { Text("保存") }
            },
            dismissButton = { TextButton(onClick = { showDialog = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Paper, RoundedCornerShape(18.dp))
            .border(0.7.dp, Line, RoundedCornerShape(18.dp))
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
private fun BoxAvatar(avatar: String) {
    androidx.compose.foundation.layout.Box(
        Modifier.size(34.dp).background(AccentSoft, CircleShape),
        contentAlignment = Alignment.Center
    ) { Text(avatar, fontSize = 17.sp) }
}

@Composable
private fun Feedback(text: String, isError: Boolean, onDismiss: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(if (isError) Color(0xFFFBE9E7) else Color(0xFFE8F0E4), RoundedCornerShape(10.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text, fontSize = 11.5.sp, color = if (isError) Color(0xFFB3261E) else Sage, modifier = Modifier.weight(1f))
        TextButton(onClick = onDismiss) { Text("关闭", fontSize = 11.sp) }
    }
}
