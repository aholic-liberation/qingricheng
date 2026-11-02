package com.qingricheng.app.data

/** A persona card imported from JSON. Multiple cards can be active together;
 *  the first active card is the primary voice. */
data class PersonaCard(
    val id: String,
    val name: String,
    val avatar: String = "🌙",
    val tagline: String = "",
    val description: String = "",
    val systemPrompt: String = "",
    val greeting: String = "",
    val traits: List<String> = emptyList(),
    val speakingStyle: String = "",
    val boundaries: List<String> = emptyList(),
    val exampleDialogues: List<Dialogue> = emptyList(),
    val memorySeeds: List<String> = emptyList(),
    val tags: List<String> = emptyList(),
    val author: String = "",
    val version: String = "1.0",
    val builtin: Boolean = false,
    val rawJson: String = ""
) {
    data class Dialogue(val user: String = "", val assistant: String = "")
}

data class ChatMessage(
    val id: Long = 0,
    val role: String,          // user | assistant
    val content: String,
    val createdAt: Long = System.currentTimeMillis(),
    val meta: String = ""      // JSON summary of tool calls for the assistant turn
)

data class EventItem(
    val id: String,
    val uid: String = "",
    val href: String = "",
    val etag: String = "",
    val calendarUrl: String = "",
    val title: String,
    val date: String,          // yyyy-MM-dd
    val start: String = "",    // HH:mm, empty for all-day
    val end: String = "",
    val endDate: String = "",
    val allDay: Boolean = false,
    val location: String = "",
    val description: String = "",
    val local: Boolean = true,
    val updatedAt: Long = System.currentTimeMillis()
)

data class TaskItem(
    val id: String,
    val title: String,
    val date: String,          // yyyy-MM-dd
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class StickyItem(
    val id: String,
    val text: String,
    val color: String = "butter",
    val done: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class ProfileItem(
    val id: String,
    val label: String,
    val value: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

data class MemoryItem(
    val id: Long,
    val text: String,
    val source: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

data class SearchResult(val title: String, val url: String, val snippet: String)

/** A local attachment copied into the app's files directory. */
data class Attachment(
    val id: String,
    val kind: String,          // image | file
    val name: String,
    val mime: String,
    val path: String,
    val size: Long,
    val extractedText: String = "",
    val imageDataUrl: String = ""
)
