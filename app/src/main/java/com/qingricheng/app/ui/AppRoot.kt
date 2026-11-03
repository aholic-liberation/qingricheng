package com.qingricheng.app.ui

import android.net.Uri
import android.graphics.RenderEffect
import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.RectangleShape
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.effect
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingricheng.app.AppContainer
import com.qingricheng.app.ui.chat.ChatScreen
import com.qingricheng.app.ui.chat.Composer
import com.qingricheng.app.ui.chat.ChatViewModel
import com.qingricheng.app.ui.glass.GlassSpec
import com.qingricheng.app.ui.schedule.ScheduleScreen
import com.qingricheng.app.ui.schedule.TasksScreen
import com.qingricheng.app.ui.schedule.ScheduleViewModel
import com.qingricheng.app.ui.schedule.WeekScreen
import com.qingricheng.app.ui.settings.SettingsScreen
import com.qingricheng.app.ui.settings.SettingsViewModel
import com.qingricheng.app.ui.theme.CreamDeep
import com.qingricheng.app.ui.theme.Cream
import com.qingricheng.app.ui.theme.Ink
import com.qingricheng.app.ui.theme.Line
import com.qingricheng.app.ui.theme.Muted
import com.qingricheng.app.ui.theme.Paper

private data class Tab(val glyph: String, val label: String)

private val TABS = listOf(
    Tab("聊", "对话"),
    Tab("日", "日程"),
    Tab("周", "周视图"),
    Tab("办", "待办"),
    Tab("设", "设置")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRoot(container: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var confirmClear by remember { mutableStateOf(false) }
    var draft by rememberSaveable { mutableStateOf("") }
    var attachMenu by remember { mutableStateOf(false) }
    val chatVm: ChatViewModel = viewModel(
        key = "chat",
        factory = viewModelFactory { initializer { ChatViewModel(container) } }
    )
    val scheduleVm: ScheduleViewModel = viewModel(
        key = "schedule",
        factory = viewModelFactory { initializer { ScheduleViewModel(container) } }
    )
    val settingsVm: SettingsViewModel = viewModel(
        key = "settings",
        factory = viewModelFactory { initializer { SettingsViewModel(container) } }
    )
    val chatState by chatVm.state.collectAsState()
    val scheduleState by scheduleVm.state.collectAsState()
    val settingsState by settingsVm.state.collectAsState()
    val imeVisible = WindowInsets.isImeVisible
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) chatVm.attach(uri, "image")
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) chatVm.attach(uri, "file")
    }

    LaunchedEffect(tab) {
        when (tab) {
            0 -> chatVm.refresh()
            1 -> scheduleVm.reload()
            2 -> scheduleVm.reload()
            3 -> scheduleVm.reload()
            4 -> settingsVm.reload()
        }
    }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).imePadding()) {
            val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
            val bandHeight = minOf(GlassSpec.ProgressiveBand, maxHeight)

            val backdrop = rememberLayerBackdrop {
                drawRect(Cream)
                drawContent()
            }

            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .layerBackdrop(backdrop)
                ) {
                    when (tab) {
                        0 -> ChatScreen(state = chatState)
                        1 -> Box(Modifier.fillMaxSize().padding(bottom = GlassSpec.ContentBottomPadding)) {
                            ScheduleScreen(
                                state = scheduleState,
                                onSelectDate = scheduleVm::selectDate,
                                onShiftMonth = scheduleVm::shiftMonth,
                                onToday = scheduleVm::goToday,
                                onSync = scheduleVm::sync,
                                onCreateEvent = scheduleVm::createEvent,
                                onDeleteEvent = scheduleVm::deleteEvent,
                                onClearError = scheduleVm::clearError,
                                onOpenSettings = { tab = 4 }
                            )
                        }
                        2 -> Box(Modifier.fillMaxSize().padding(bottom = GlassSpec.ContentBottomPadding)) {
                            WeekScreen(
                                state = scheduleState,
                                onSelectDate = scheduleVm::selectDate,
                                onShiftWeek = scheduleVm::shiftWeek,
                                onToday = scheduleVm::goToday,
                                onSync = scheduleVm::sync,
                                onCreateEvent = scheduleVm::createEvent,
                                onUpdateEvent = scheduleVm::updateEvent,
                                onDeleteEvent = scheduleVm::deleteEvent,
                                onClearError = scheduleVm::clearError,
                                onOpenSettings = { tab = 4 }
                            )
                        }
                        3 -> Box(Modifier.fillMaxSize().padding(bottom = GlassSpec.ContentBottomPadding)) {
                            TasksScreen(
                                state = scheduleState,
                                onAddTask = scheduleVm::addTask,
                                onToggleTask = scheduleVm::toggleTask,
                                onDeleteTask = scheduleVm::deleteTask,
                                onGoToday = scheduleVm::goToday
                            )
                        }
                        else -> Box(Modifier.fillMaxSize().padding(bottom = GlassSpec.ContentBottomPadding)) {
                            SettingsScreen(
                                state = settingsState,
                                onTogglePersona = settingsVm::togglePersona,
                                onImportPersona = settingsVm::importPersona,
                                onDeletePersona = settingsVm::deletePersona,
                                onSaveLlm = settingsVm::saveLlm,
                                onSaveCalDav = settingsVm::saveCalDav,
                                onTestCalDav = settingsVm::testCalDav,
                                onSaveSearch = settingsVm::saveSearch,
                                onSaveReminderLead = settingsVm::saveReminderLead,
                                onSaveProfile = settingsVm::saveProfile,
                                onDeleteProfile = settingsVm::deleteProfile,
                                onClearFeedback = settingsVm::clearFeedback,
                                exampleJson = settingsVm::exampleJson
                            )
                        }
                    }
                }

                if (!imeVisible) {
                    Box(
                        Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(bandHeight)
                            .drawPlainBackdrop(
                                backdrop = backdrop,
                                shape = { RectangleShape },
                                effects = {
                                    blur(GlassSpec.MaxBlur.toPx())
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                        effect(
                                            RenderEffect.createRuntimeShaderEffect(
                                                obtainRuntimeShader(
                                                    "ProgressiveBlur",
                                                    PROGRESSIVE_BLUR_SHADER
                                                ).apply {
                                                    setFloatUniform("size", size.width, size.height)
                                                },
                                                "content"
                                            )
                                        )
                                    }
                                },
                                onDrawBehind = {
                                    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                                        drawRect(Cream.copy(alpha = 0.12f))
                                    }
                                }
                            )
                    )
                }

                GlassBottomPanel(
                    selected = tab,
                    onSelect = { tab = it },
                    backdrop = backdrop,
                    showTabs = !imeVisible,
                    showComposer = tab == 0,
                    composer = {
                        Column(Modifier.fillMaxWidth()) {
                            chatState.error?.let { error ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        error,
                                        color = Color(0xFFB3261E),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = chatVm::dismissError) { Text("知道了") }
                                }
                            }
                            chatState.attachmentError?.let { error ->
                                Row(
                                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        error,
                                        color = Color(0xFFB3261E),
                                        fontSize = 12.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = chatVm::dismissAttachmentError) { Text("知道了") }
                                }
                            }
                            Composer(
                                draft = draft,
                                onDraftChange = { draft = it },
                                sending = chatState.sending,
                                attachments = chatState.pendingAttachments,
                                processingAttachment = chatState.processingAttachment,
                                onAttachClick = { attachMenu = true },
                                onRemoveAttachment = chatVm::removeAttachment,
                                onSend = {
                                    if (draft.isNotBlank() || chatState.pendingAttachments.isNotEmpty()) {
                                        chatVm.send(draft)
                                        draft = ""
                                    }
                                },
                                onStop = chatVm::stop,
                                onGlass = true,
                                containerColor = Color.Transparent
                            )
                        }
                    },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(
                            start = GlassSpec.BarMargin,
                            end = GlassSpec.BarMargin,
                            bottom = navBottom + GlassSpec.BarGap
                        )
                )

                if (tab == 0) {
                    GlassChatHeader(
                        title = chatState.personas.firstOrNull()?.name ?: "轻日程",
                        subtitle = if (chatState.personas.size > 1) {
                            chatState.personas.joinToString("、") { it.name }
                        } else {
                            chatState.personas.firstOrNull()?.tagline?.ifBlank { "陪你聊天，也帮你安排生活" }
                                ?: "陪你聊天，也帮你安排生活"
                        },
                        avatar = chatState.personas.firstOrNull()?.avatar ?: "🌙",
                        backdrop = backdrop,
                        onOpenPersonas = { tab = 4 },
                        onClear = { confirmClear = true },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(start = GlassSpec.BarMargin, end = GlassSpec.BarMargin, top = 8.dp)
                    )
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("清空这段对话？") },
            text = { Text("聊天记录会删除，人设卡和日程/便签不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClear = false
                    chatVm.clearChat()
                }) { Text("清空") }
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

@Composable
private fun GlassChatHeader(
    title: String,
    subtitle: String,
    avatar: String,
    backdrop: Backdrop,
    onOpenPersonas: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(GlassSpec.BarHeight)
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(GlassSpec.BarCorner) },
                effects = {
                    vibrancy()
                    blur(6.dp.toPx())
                    lens(12.dp.toPx(), 24.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow.Default },
                innerShadow = { InnerShadow.Default },
                onDrawSurface = { drawRect(Paper.copy(alpha = 0.30f)) }
            )
    ) {
        Row(
            Modifier.fillMaxSize().padding(start = 12.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(34.dp).background(CreamDeep, CircleShape),
                contentAlignment = Alignment.Center
            ) { Text(avatar, fontSize = 18.sp) }
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    color = Ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    subtitle,
                    fontSize = 10.5.sp,
                    color = Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                onClick = onOpenPersonas,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
            ) { Text("人设", fontSize = 12.sp, color = Ink) }
            TextButton(
                onClick = onClear,
                contentPadding = PaddingValues(horizontal = 7.dp, vertical = 0.dp)
            ) { Text("新对话", fontSize = 12.sp, color = Muted) }
        }
    }
}

@Composable
private fun GlassBottomPanel(
    selected: Int,
    onSelect: (Int) -> Unit,
    backdrop: Backdrop,
    showTabs: Boolean,
    showComposer: Boolean,
    composer: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!showTabs && !showComposer) return

    Box(
        modifier
            .drawBackdrop(
                backdrop = backdrop,
                shape = { RoundedCornerShape(GlassSpec.BarCorner) },
                effects = {
                    vibrancy()
                    blur(8.dp.toPx())
                    lens(14.dp.toPx(), 28.dp.toPx(), chromaticAberration = true)
                },
                highlight = { Highlight.Default },
                shadow = { Shadow.Default },
                innerShadow = { InnerShadow.Default },
                onDrawSurface = { drawRect(Paper.copy(alpha = 0.30f)) }
            )
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (showComposer) {
                composer()
            }
            if (showTabs) {
                val tabRowHeight = GlassSpec.BarHeight
                BoxWithConstraints(Modifier.fillMaxWidth().height(tabRowHeight)) {
                    val innerPad = 6.dp
                    val tabWidth = (maxWidth - innerPad * 2) / TABS.size
                    val pillWidth = tabWidth - 4.dp
                    val pillHeight = tabRowHeight * GlassSpec.PillRatio
                    val targetX = innerPad + tabWidth * selected + 2.dp
                    val pillX by animateDpAsState(
                        targetValue = targetX,
                        animationSpec = spring(
                            dampingRatio = 0.72f,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "glass-pill"
                    )
                    val pillY = (tabRowHeight - pillHeight) / 2
                    Box(
                        Modifier
                            .offset(x = pillX, y = pillY)
                            .width(pillWidth)
                            .height(pillHeight)
                            .drawBackdrop(
                                backdrop = backdrop,
                                shape = { CircleShape },
                                effects = {
                                    vibrancy()
                                    blur(4.dp.toPx())
                                    lens(16.dp.toPx(), 32.dp.toPx(), chromaticAberration = true)
                                },
                                highlight = { Highlight(width = 1.5.dp, alpha = 1f) },
                                shadow = {
                                    Shadow(
                                        radius = 14.dp,
                                        offset = DpOffset(0.dp, 4.dp),
                                        color = Color.Black.copy(alpha = 0.18f)
                                    )
                                },
                                innerShadow = {
                                    InnerShadow(
                                        radius = 8.dp,
                                        offset = DpOffset(0.dp, 2.dp),
                                        color = Color.Black.copy(alpha = 0.12f)
                                    )
                                },
                                onDrawSurface = { drawRect(Paper.copy(alpha = 0.55f)) }
                            )
                    )

                    Row(
                        Modifier.fillMaxSize().padding(horizontal = innerPad),
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TABS.forEachIndexed { index, item ->
                            val active = selected == index
                            val tabInteraction = remember { MutableInteractionSource() }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clickable(
                                        interactionSource = tabInteraction,
                                        indication = null
                                    ) { onSelect(index) }
                                    .semantics { contentDescription = item.label },
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    Modifier
                                        .size(38.dp)
                                        .then(
                                            if (active) {
                                                Modifier
                                            } else {
                                                Modifier.border(
                                                    0.7.dp,
                                                    Line.copy(alpha = 0.24f),
                                                    RoundedCornerShape(12.dp)
                                                )
                                            }
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        item.glyph,
                                        fontSize = 17.sp,
                                        color = if (active) Ink else Muted,
                                        fontWeight = if (active) FontWeight.Medium else FontWeight.Normal
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val PROGRESSIVE_BLUR_SHADER = """
uniform shader content;
uniform float2 size;

half4 main(float2 coord) {
    float t = clamp(coord.y / max(size.y, 1.0), 0.0, 1.0);
    float alpha = t * t;
    return content.eval(coord) * alpha;
}
"""
