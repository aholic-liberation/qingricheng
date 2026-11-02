package com.qingricheng.app.data

class NotesRepository(private val db: AppDb) {
    fun list(): List<StickyItem> = db.listStickies()
    fun upsert(sticky: StickyItem) = db.upsertSticky(sticky)
    fun delete(id: String) = db.deleteSticky(id)
}
