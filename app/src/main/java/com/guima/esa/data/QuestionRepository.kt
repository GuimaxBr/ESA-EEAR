package com.guima.esa.data

import android.content.res.AssetManager
import com.google.gson.Gson

object QuestionRepository {

    private lateinit var assetManager: AssetManager
    private val gson = Gson()

    fun initialize(assets: AssetManager) {
        assetManager = assets
    }

    private fun listOrEmpty(path: String): List<String> {
        return try {
            assetManager.list(path)?.toList() ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getExams(): List<String> = listOrEmpty("simulados")

    fun getYearsForExam(exam: String): List<String> = listOrEmpty("simulados/$exam")

    fun getSubjectsForYear(exam: String, year: String): List<String> = listOrEmpty("simulados/$exam/$year")

    private fun getQuestionFiles(exam: String, year: String, subject: String): List<String> {
        return listOrEmpty("simulados/$exam/$year/$subject")
            .filter { it.endsWith(".json", ignoreCase = true) }
            .sortedWith(compareBy({ extractQuestionOrder(it) }, { it }))
    }

    private fun extractQuestionOrder(fileName: String): Int {
        return fileName.substringAfterLast('_').substringBeforeLast('.').toIntOrNull() ?: Int.MAX_VALUE
    }

    fun getQuestions(exam: String, year: String, subjects: List<String>): List<Question> {
        val questions = mutableListOf<Question>()

        for (subject in subjects) {
            getQuestionFiles(exam, year, subject).forEach { fileName ->
                val basePath = "simulados/$exam/$year/$subject"
                val path = "$basePath/$fileName"
                try {
                    val jsonString = assetManager.open(path).bufferedReader().use { it.readText() }
                    val question: Question = gson.fromJson(jsonString, Question::class.java)

                    question.subject = subject
                    question.uniqueId = "$exam/$year/$subject/${question.id}"
                    question.assetBasePath = basePath
                    questions.add(question)
                } catch (e: Exception) {
                    // Ignora arquivos JSON com erro ou ausentes
                }
            }
        }

        return questions
    }

    fun getAllQuestions(): List<Question> {
        val allQuestions = mutableListOf<Question>()
        getExams().forEach { exam ->
            getYearsForExam(exam).forEach { year ->
                getSubjectsForYear(exam, year).forEach { subject ->
                    allQuestions.addAll(getQuestions(exam, year, listOf(subject)))
                }
            }
        }
        return allQuestions
    }
}
