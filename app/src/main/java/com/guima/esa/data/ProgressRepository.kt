package com.guima.esa.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.util.Calendar
import java.util.Date

/**
 * Estrutura que representa um único evento de resposta.
 */
data class AnswerEvent(
    val uniqueId: String,
    val timestamp: Long,
    val wasCorrect: Boolean
)

data class QuestionAnswerStats(
    val correctCount: Int,
    val incorrectCount: Int
)

data class QuestionDifficultySnapshot(
    val uniqueId: String,
    val totalAttempts: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val lastAnsweredAt: Long,
    val recentCorrectRate: Float,
    val recentCorrectStreak: Int,
    val isRecoveredRecently: Boolean,
    val isActiveDifficulty: Boolean,
    val difficultyScore: Float
)

/**
 * Repositório para gerenciar e calcular todas as métricas de progresso baseadas em ACERTOS.
 */
object ProgressRepository {

    private const val PREFS_NAME = "EsaEearProgress"
    private const val ANSWER_EVENTS_KEY = "answerEvents"
    private const val QUESTION_NOTES_KEY = "questionNotes"
    private const val FLASH_CARD_IDS_KEY = "flashCardIds"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    private var answerEvents = mutableListOf<AnswerEvent>()
    private var questionNotes = mutableMapOf<String, String>()
    private var flashCardQuestionIds = mutableSetOf<String>()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        loadAnswerEvents()
    }

    private fun loadAnswerEvents() {
        val json = prefs.getString(ANSWER_EVENTS_KEY, "[]")
        val type = object : TypeToken<MutableList<AnswerEvent>>() {}.type
        answerEvents = gson.fromJson(json, type)
        loadQuestionNotes()
        loadFlashCardQuestionIds()
    }

    private fun loadQuestionNotes() {
        val json = prefs.getString(QUESTION_NOTES_KEY, "{}")
        val type = object : TypeToken<MutableMap<String, String>>() {}.type
        questionNotes = gson.fromJson<MutableMap<String, String>>(json, type) ?: mutableMapOf()
    }

    private fun loadFlashCardQuestionIds() {
        val json = prefs.getString(FLASH_CARD_IDS_KEY, "[]")
        val type = object : TypeToken<MutableSet<String>>() {}.type
        flashCardQuestionIds = gson.fromJson<MutableSet<String>>(json, type) ?: mutableSetOf()
    }

    fun recordAnswer(uniqueId: String, wasCorrect: Boolean) {
        answerEvents.add(AnswerEvent(uniqueId, System.currentTimeMillis(), wasCorrect))
        save()
    }

    private fun save() {
        val json = gson.toJson(answerEvents)
        prefs.edit().putString(ANSWER_EVENTS_KEY, json).apply()
    }

    private fun saveQuestionNotes() {
        val json = gson.toJson(questionNotes)
        prefs.edit().putString(QUESTION_NOTES_KEY, json).apply()
    }

    private fun saveFlashCardQuestionIds() {
        val json = gson.toJson(flashCardQuestionIds)
        prefs.edit().putString(FLASH_CARD_IDS_KEY, json).apply()
    }

    // --- FUNÇÕES DE CÁLCULO ---

    fun getTotalCorrectAnswers(): Int {
        return answerEvents.count { it.wasCorrect }
    }

    fun getBestCorrectAnswerStreak(): Int {
        var bestStreak = 0
        var currentStreak = 0
        answerEvents.sortedBy { it.timestamp }.forEach { event ->
            if (event.wasCorrect) {
                currentStreak++
            } else {
                if (currentStreak > bestStreak) {
                    bestStreak = currentStreak
                }
                currentStreak = 0
            }
        }
        return maxOf(bestStreak, currentStreak)
    }

    fun getTodaysCorrectAnswers(): Int {
        val today = Calendar.getInstance()
        return answerEvents.count { event ->
            val eventDate = Calendar.getInstance().apply { timeInMillis = event.timestamp }
            event.wasCorrect &&
            eventDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            eventDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        }
    }

    fun getTodaysAnswerAttempts(): Int {
        val today = Calendar.getInstance()
        return answerEvents.count { event ->
            val eventDate = Calendar.getInstance().apply { timeInMillis = event.timestamp }
            eventDate.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
                eventDate.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
        }
    }

    fun getTotalAnswerAttempts(): Int {
        return answerEvents.size
    }

    fun getOverallAccuracyPercent(): Int {
        val totalAttempts = getTotalAnswerAttempts()
        if (totalAttempts == 0) return 0
        return ((getTotalCorrectAnswers().toFloat() / totalAttempts.toFloat()) * 100f).toInt()
    }

    fun getCorrectAnswersForDate(targetDate: Date): Int {
        val targetCalendar = Calendar.getInstance().apply { time = targetDate }
        return answerEvents.count { event ->
            val eventDate = Calendar.getInstance().apply { timeInMillis = event.timestamp }
            event.wasCorrect &&
                eventDate.get(Calendar.YEAR) == targetCalendar.get(Calendar.YEAR) &&
                eventDate.get(Calendar.DAY_OF_YEAR) == targetCalendar.get(Calendar.DAY_OF_YEAR)
        }
    }

    /**
     * Retorna um conjunto com as IDs únicas de todas as questões acertadas pelo menos uma vez.
     * Essencial para a tela de progresso por matéria.
     */
    fun getCorrectlyAnsweredUniqueIds(): Set<String> {
        return answerEvents.filter { it.wasCorrect }.map { it.uniqueId }.toSet()
    }

    fun getAnswerStats(uniqueId: String): QuestionAnswerStats {
        val correctCount = answerEvents.count { it.uniqueId == uniqueId && it.wasCorrect }
        val incorrectCount = answerEvents.count { it.uniqueId == uniqueId && !it.wasCorrect }
        return QuestionAnswerStats(correctCount = correctCount, incorrectCount = incorrectCount)
    }

    fun getQuestionDifficultySnapshots(now: Long = System.currentTimeMillis()): List<QuestionDifficultySnapshot> {
        return answerEvents
            .groupBy { it.uniqueId }
            .mapNotNull { (uniqueId, eventsForQuestion) ->
                val sortedEvents = eventsForQuestion.sortedBy { it.timestamp }
                val totalAttempts = sortedEvents.size
                val correctCount = sortedEvents.count { it.wasCorrect }
                val incorrectCount = totalAttempts - correctCount

                if (totalAttempts == 0 || incorrectCount == 0) {
                    return@mapNotNull null
                }

                val recentEvents = sortedEvents.takeLast(4)
                val recentCorrectCount = recentEvents.count { it.wasCorrect }
                val recentCorrectRate = if (recentEvents.isEmpty()) {
                    0f
                } else {
                    recentCorrectCount.toFloat() / recentEvents.size.toFloat()
                }
                val recentIncorrectCount = recentEvents.count { !it.wasCorrect }
                val recentCorrectStreak = sortedEvents
                    .asReversed()
                    .takeWhile { it.wasCorrect }
                    .count()
                val totalAccuracy = correctCount.toFloat() / totalAttempts.toFloat()
                val lastEvent = sortedEvents.last()
                val millisSinceLastAnswer = (now - lastEvent.timestamp).coerceAtLeast(0L)
                val recencyBoost = when {
                    millisSinceLastAnswer <= 12L * 60L * 60L * 1000L -> 2.2f
                    millisSinceLastAnswer <= 3L * 24L * 60L * 60L * 1000L -> 1.4f
                    millisSinceLastAnswer <= 10L * 24L * 60L * 60L * 1000L -> 0.7f
                    else -> 0f
                }

                val isRecoveredRecently =
                    recentEvents.size >= 3 &&
                    recentCorrectRate >= 0.7f &&
                    recentCorrectStreak >= 2 &&
                    lastEvent.wasCorrect

                val rawDifficultyScore =
                    (incorrectCount * 3.1f) +
                    (recentIncorrectCount * 2.0f) +
                    ((1f - totalAccuracy) * 4.5f) +
                    (if (lastEvent.wasCorrect) 0f else 2.8f) +
                    recencyBoost -
                    (recentCorrectStreak * 1.35f)

                val difficultyScore = rawDifficultyScore.coerceAtLeast(0f)
                val isActiveDifficulty = !isRecoveredRecently && difficultyScore > 0f

                QuestionDifficultySnapshot(
                    uniqueId = uniqueId,
                    totalAttempts = totalAttempts,
                    correctCount = correctCount,
                    incorrectCount = incorrectCount,
                    lastAnsweredAt = lastEvent.timestamp,
                    recentCorrectRate = recentCorrectRate,
                    recentCorrectStreak = recentCorrectStreak,
                    isRecoveredRecently = isRecoveredRecently,
                    isActiveDifficulty = isActiveDifficulty,
                    difficultyScore = difficultyScore
                )
            }
            .sortedWith(
                compareByDescending<QuestionDifficultySnapshot> { it.difficultyScore }
                    .thenByDescending { it.incorrectCount }
                    .thenByDescending { it.lastAnsweredAt }
            )
    }

    fun getQuestionNote(uniqueId: String): String {
        return questionNotes[uniqueId].orEmpty()
    }

    fun getAllQuestionNotes(): Map<String, String> {
        return questionNotes.toMap()
    }

    fun getFlashCardQuestionIds(): Set<String> {
        return flashCardQuestionIds.toSet()
    }

    fun isQuestionOnFlashCard(uniqueId: String): Boolean {
        return flashCardQuestionIds.contains(uniqueId)
    }

    fun addQuestionToFlashCard(uniqueId: String) {
        flashCardQuestionIds.add(uniqueId)
        saveFlashCardQuestionIds()
    }

    fun removeQuestionFromFlashCard(uniqueId: String) {
        flashCardQuestionIds.remove(uniqueId)
        saveFlashCardQuestionIds()
    }

    fun saveQuestionNote(uniqueId: String, note: String) {
        val trimmed = note.trim()
        if (trimmed.isBlank()) {
            questionNotes.remove(uniqueId)
        } else {
            questionNotes[uniqueId] = note
        }
        saveQuestionNotes()
    }
    
    fun clearProgress() {
        answerEvents.clear()
        save()
    }
}
