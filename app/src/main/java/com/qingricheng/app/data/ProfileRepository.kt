package com.qingricheng.app.data

import java.util.UUID

class ProfileRepository(private val db: AppDb) {
    fun list(): List<ProfileItem> = db.listProfiles()

    fun get(id: String): ProfileItem? = db.profile(id)

    /** Add or replace by label (case-insensitive). */
    fun set(label: String, value: String): ProfileItem {
        val cleanLabel = label.trim()
        val cleanValue = value.trim()
        val now = System.currentTimeMillis()
        val existing = list().firstOrNull { it.label.equals(cleanLabel, ignoreCase = true) }
        val item = existing?.copy(
            label = cleanLabel,
            value = cleanValue,
            updatedAt = now
        ) ?: ProfileItem(
            id = "profile_" + UUID.randomUUID().toString().take(12),
            label = cleanLabel,
            value = cleanValue,
            createdAt = now,
            updatedAt = now
        )
        db.upsertProfile(item)
        return item
    }

    fun delete(id: String) = db.deleteProfile(id)

    fun deleteByLabel(label: String): Boolean {
        val item = list().firstOrNull { it.label.equals(label.trim(), ignoreCase = true) } ?: return false
        db.deleteProfile(item.id)
        return true
    }
}
