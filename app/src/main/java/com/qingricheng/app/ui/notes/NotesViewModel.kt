package com.qingricheng.app.ui.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.qingricheng.app.AppContainer
import com.qingricheng.app.data.StickyItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

data class NotesUiState(
    val stickies: List<StickyItem> = emptyList(),
    val error: String? = null
)

class NotesViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow(NotesUiState())
    val state: StateFlow<NotesUiState> = _state.asStateFlow()

    init { reload() }

    fun reload() {
        viewModelScope.launch(Dispatchers.IO) {
            val list = container.notes.list()
            withContext(Dispatchers.Main) { _state.value = _state.value.copy(stickies = list) }
        }
    }

    fun add(text: String, color: String) {
        val value = text.trim()
        if (value.isEmpty()) return
        val now = System.currentTimeMillis()
        viewModelScope.launch(Dispatchers.IO) {
            container.notes.upsert(
                StickyItem(
                    id = "sticky_" + UUID.randomUUID().toString().take(12),
                    text = value,
                    color = color,
                    createdAt = now,
                    updatedAt = now
                )
            )
            reload()
        }
    }

    fun toggle(sticky: StickyItem) {
        viewModelScope.launch(Dispatchers.IO) {
            container.notes.upsert(sticky.copy(done = !sticky.done, updatedAt = System.currentTimeMillis()))
            reload()
        }
    }

    fun delete(sticky: StickyItem) {
        viewModelScope.launch(Dispatchers.IO) {
            container.notes.delete(sticky.id)
            reload()
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }
}
