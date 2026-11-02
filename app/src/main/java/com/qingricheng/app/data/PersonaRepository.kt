package com.qingricheng.app.data

import android.content.Context

class PersonaRepository(
    private val context: Context,
    private val db: AppDb,
    private val settings: SettingsStore
) {
    fun seedExampleIfNeeded() {
        val existing = db.listPersonas()
        if (existing.isNotEmpty()) {
            if (settings.activePersonaIds.isEmpty()) {
                settings.activePersonaIds = listOf(existing.first().id)
            }
            return
        }
        val example = runCatching { exampleJson() }.getOrNull() ?: return
        val card = runCatching { PersonaParser.parseMany(example).first() }.getOrNull() ?: return
        db.upsertPersona(card.copy(builtin = true))
        settings.activePersonaIds = listOf(card.id)
        settings.seededExample = true
    }

    fun list(): List<PersonaCard> = db.listPersonas()

    fun activeIds(): List<String> = settings.activePersonaIds

    fun activeCards(): List<PersonaCard> {
        val cards = db.listPersonas().associateBy { it.id }
        return settings.activePersonaIds.mapNotNull { cards[it] }
    }

    fun setActive(ids: List<String>) {
        val available = db.listPersonas().map { it.id }.toSet()
        settings.activePersonaIds = ids.filter { it in available }.distinct()
    }

    fun toggleActive(id: String, checked: Boolean) {
        val ids = settings.activePersonaIds.toMutableList()
        if (checked) {
            if (!ids.contains(id)) ids += id
        } else {
            ids.remove(id)
        }
        setActive(ids)
    }

    fun importJson(text: String): List<PersonaCard> {
        val cards = PersonaParser.parseMany(text)
        cards.forEach { db.upsertPersona(it) }
        if (settings.activePersonaIds.isEmpty() && cards.isNotEmpty()) {
            settings.activePersonaIds = listOf(cards.first().id)
        }
        return cards
    }

    fun delete(id: String) {
        db.deletePersona(id)
        settings.activePersonaIds = settings.activePersonaIds.filter { it != id }
    }

    fun exampleJson(): String = context.assets.open("personas/example.persona.json")
        .bufferedReader(Charsets.UTF_8)
        .use { it.readText() }
}
