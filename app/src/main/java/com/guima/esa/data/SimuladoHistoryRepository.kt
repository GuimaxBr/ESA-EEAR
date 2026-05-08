package com.guima.esa.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class SimuladoHistoryEntry(
    val id: Long = System.currentTimeMillis(),
    val exam: String = "",
    val year: String = "",
    val title: String = "",
    val finalGrade: Float = 0f,
    val correctCount: Int = 0,
    val totalQuestions: Int = 0,
    val completedAt: String = "",
    val durationSeconds: Long = 0L
)

object SimuladoHistoryRepository {
    private const val PREFS_NAME = "SimuladoHistory"
    private const val KEY_HISTORY = "history"
    private const val MAX_HISTORY_ITEMS = 200

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveResult(entry: SimuladoHistoryEntry) {
        val updatedHistory = (listOf(entry) + getHistory())
            .distinctBy { it.id }
            .take(MAX_HISTORY_ITEMS)
        prefs.edit().putString(KEY_HISTORY, gson.toJson(updatedHistory)).apply()
    }

    fun getHistory(): List<SimuladoHistoryEntry> {
        val rawJson = prefs.getString(KEY_HISTORY, "[]") ?: "[]"
        val type = object : TypeToken<List<SimuladoHistoryEntry>>() {}.type
        return runCatching { gson.fromJson<List<SimuladoHistoryEntry>>(rawJson, type) }
            .getOrDefault(emptyList())
            .sortedByDescending { it.id }
    }
}
