package com.qingricheng.app.data

class MemoryRepository(private val db: AppDb) {
    fun list(limit: Int = 80): List<MemoryItem> = db.listMemories(limit)
    fun remember(text: String, source: String = "chat"): MemoryItem = db.insertMemory(text, source)
    fun search(query: String): List<MemoryItem> = db.searchMemories(query)
}
