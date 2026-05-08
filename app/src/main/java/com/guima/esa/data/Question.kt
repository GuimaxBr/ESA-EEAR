package com.guima.esa.data

/**
 * Representa uma unica questao.
 */
data class Question(
    val id: Int,
    var uniqueId: String = "",
    val text: String,
    val questionImage: String? = null,
    val options: List<String>,
    val optionImages: List<String?> = emptyList(),
    val correctOption: Int,
    var subject: String,
    val explanation: String,
    val explanationImage: String? = null,
    val year: Int = 2006,
    var assetBasePath: String = ""
)
