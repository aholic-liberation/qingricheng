package com.qingricheng.app.data

class ChatRepository(private val db: AppDb) {
    fun messages(): List<ChatMessage> = db.listMessages(200)

    fun add(role: String, content: String, meta: String = ""): ChatMessage =
        db.insertMessage(role, content, meta)

    fun clear() = db.clearMessages()

    fun ensureGreeting(personas: List<PersonaCard>) {
        if (db.listMessages(1).isNotEmpty()) return
        val greeting = personas.firstOrNull()?.greeting?.trim().orEmpty().ifEmpty {
            "你好，我是轻日程。可以陪你聊天，也可以帮你写日程、记便签、查资料。"
        }
        db.insertMessage("assistant", greeting)
    }
}
