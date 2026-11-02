package com.qingricheng.app.data

import android.content.Context
import org.json.JSONArray

class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("qingricheng_settings", Context.MODE_PRIVATE)

    var llmBaseUrl: String
        get() = prefs.getString("llm_base_url", DEFAULT_LLM_BASE) ?: DEFAULT_LLM_BASE
        set(value) = prefs.edit().putString("llm_base_url", value.trim()).apply()

    var llmModel: String
        get() = prefs.getString("llm_model", DEFAULT_LLM_MODEL) ?: DEFAULT_LLM_MODEL
        set(value) = prefs.edit().putString("llm_model", value.trim()).apply()

    var caldavUrl: String
        get() = prefs.getString("caldav_url", "") ?: ""
        set(value) = prefs.edit().putString("caldav_url", value.trim()).apply()

    var caldavUser: String
        get() = prefs.getString("caldav_user", "") ?: ""
        set(value) = prefs.edit().putString("caldav_user", value.trim()).apply()

    var caldavTimezone: String
        get() = prefs.getString("caldav_timezone", DEFAULT_TIMEZONE) ?: DEFAULT_TIMEZONE
        set(value) = prefs.edit().putString("caldav_timezone", value.trim()).apply()

    var searchProvider: String
        get() = prefs.getString("search_provider", "bing") ?: "bing"
        set(value) = prefs.edit().putString("search_provider", value).apply()

    var reminderLeadMinutes: Int
        get() = prefs.getInt("reminder_lead_minutes", 15).coerceIn(0, 1440)
        set(value) = prefs.edit().putInt("reminder_lead_minutes", value.coerceIn(0, 1440)).apply()

    var activePersonaIds: List<String>
        get() {
            val raw = prefs.getString("active_personas", "") ?: ""
            if (raw.isBlank()) return emptyList()
            return try {
                val arr = JSONArray(raw)
                (0 until arr.length()).map { arr.optString(it) }.filter { it.isNotBlank() }
            } catch (_: Exception) {
                emptyList()
            }
        }
        set(value) {
            val arr = JSONArray()
            value.forEach { arr.put(it) }
            prefs.edit().putString("active_personas", arr.toString()).apply()
        }

    var seededExample: Boolean
        get() = prefs.getBoolean("seeded_example", false)
        set(value) = prefs.edit().putBoolean("seeded_example", value).apply()

    companion object {
        const val DEFAULT_LLM_BASE = "https://api.deepseek.com/v1"
        const val DEFAULT_LLM_MODEL = "deepseek-flash"
        const val DEFAULT_TIMEZONE = "Asia/Shanghai"
    }
}
