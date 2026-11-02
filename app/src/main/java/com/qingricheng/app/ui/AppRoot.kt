package com.qingricheng.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.qingricheng.app.AppContainer
import com.qingricheng.app.ui.chat.ChatScreen
import com.qingricheng.app.ui.chat.ChatViewModel
import com.qingricheng.app.ui.notes.NotesScreen
import com.qingricheng.app.ui.notes.NotesViewModel
import com.qingricheng.app.ui.schedule.ScheduleScreen
import com.qingricheng.app.ui.schedule.WeekScreen
import com.qingricheng.app.ui.schedule.ScheduleViewModel
import com.qingricheng.app.ui.settings.SettingsScreen
import com.qingricheng.app.ui.settings.SettingsViewModel
import com.qingricheng.app.ui.theme.Accent
import com.qingricheng.app.ui.theme.AccentSoft
import com.qingricheng.app.ui.theme.Cream
import com.qingricheng.app.ui.theme.CreamDeep
import com.qingricheng.app.ui.theme.Ink
import com.qingricheng.app.ui.theme.Line
import com.qingricheng.app.ui.theme.Muted
import com.qingricheng.app.ui.theme.Paper

private data class Tab(val glyph: String, val label: String)

private val TABS = listOf(
    Tab("聊", "对话"),
    Tab("日", "日程"),
    Tab("周", "周视图"),
    Tab("贴", "便签"),
    Tab("设", "设置")
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AppRoot(container: AppContainer) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    val chatVm: ChatViewModel = viewModel(
        key = "chat",
        factory = viewModelFactory { initializer { ChatViewModel(container) } }
    )
    val scheduleVm: ScheduleViewModel = viewModel(
        key = "schedule",
        factory = viewModelFactory { initializer { ScheduleViewModel(container) } }
    )
    val notesVm: NotesViewModel = viewModel(
        key = "notes",
        factory = viewModelFactory { initializer { NotesViewModel(container) } }
    )
    val settingsVm: SettingsViewModel = viewModel(
        key = "settings",
        factory = viewModelFactory { initializer { SettingsViewModel(container) } }
    )
    val chatState by chatVm.state.collectAsState()
    val scheduleState by scheduleVm.state.collectAsState()
    val notesState by notesVm.state.collectAsState()
    val settingsState by settingsVm.state.collectAsState()
    val imeVisible = WindowInsets.isImeVisible

    LaunchedEffect(tab) {
        when (tab) {
            0 -> chatVm.refresh()
            1 -> scheduleVm.reload()
            2 -> scheduleVm.reload()
            3 -> notesVm.reload()
            4 -> settingsVm.reload()
        }
    }

    Scaffold(
        containerColor = Cream,
        contentWindowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
        bottomBar = {
            if (!imeVisible) BottomTabs(selected = tab, onSelect = { tab = it })
        }
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding).imePadding()) {
            when (tab) {
                0 -> ChatScreen(
                    state = chatState,
                    onSend = chatVm::send,
                    onStop = chatVm::stop,
                    onClear = chatVm::clearChat,
                    onOpenPersonas = { tab = 4 },
                    onDismissError = chatVm::dismissError,
                    onAttach = chatVm::attach,
                    onRemoveAttachment = chatVm::removeAttachment,
                    onDismissAttachmentError = chatVm::dismissAttachmentError
                )
                1 -> ScheduleScreen(
                    state = scheduleState,
                    onSelectDate = scheduleVm::selectDate,
                    onShiftMonth = scheduleVm::shiftMonth,
                    onToday = scheduleVm::goToday,
                    onSync = scheduleVm::sync,
                    onCreateEvent = scheduleVm::createEvent,
                    onAddTask = scheduleVm::addTask,
                    onToggleTask = scheduleVm::toggleTask,
                    onDeleteTask = scheduleVm::deleteTask,
                    onDeleteEvent = scheduleVm::deleteEvent,
                    onClearError = scheduleVm::clearError,
                    onOpenSettings = { tab = 4 }
                )
                2 -> WeekScreen(
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
                3 -> NotesScreen(
                    state = notesState,
                    onAdd = notesVm::add,
                    onToggle = notesVm::toggle,
                    onDelete = notesVm::delete
                )
                else -> SettingsScreen(
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
}


@Composable
private fun BottomTabs(selected: Int, onSelect: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth().background(CreamDeep).navigationBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 6.dp, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TABS.forEachIndexed { index, item ->
                val active = selected == index
                val bg by animateColorAsState(
                    targetValue = if (active) Ink else Color.Transparent,
                    animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                    label = "tab-bg"
                )
                val frame by animateColorAsState(
                    targetValue = if (active) Color.Transparent else Line.copy(alpha = 0.35f),
                    animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                    label = "tab-frame"
                )
                val fg by animateColorAsState(
                    targetValue = if (active) Paper else Muted,
                    animationSpec = spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow),
                    label = "tab-fg"
                )
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onSelect(index) }
                        .semantics { contentDescription = item.label },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        Modifier
                            .size(38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(bg)
                            .border(0.8.dp, frame, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(item.glyph, fontSize = 17.sp, color = fg)
                    }
                }
            }
        }
    }
}
