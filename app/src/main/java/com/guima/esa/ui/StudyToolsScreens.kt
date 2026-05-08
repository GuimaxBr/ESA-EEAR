package com.guima.esa.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guima.esa.data.ProgressRepository
import com.guima.esa.data.Question
import com.guima.esa.data.QuestionRepository
import com.guima.esa.util.htmlToPlainText
import java.util.Locale

@Composable
fun BizurometroScreen(
    onBack: () -> Unit,
    onOpenFlashCard: (() -> Unit)? = null
) {
    BackHandler(onBack = onBack)
    val allQuestions = remember { QuestionRepository.getAllQuestions() }
    val questionMap = remember(allQuestions) { allQuestions.associateBy { it.uniqueId } }
    val bizuEntries = remember {
        ProgressRepository.getAllQuestionNotes()
            .mapNotNull { (uniqueId, note) ->
                val trimmedNote = note.trim()
                if (trimmedNote.isBlank()) {
                    null
                } else {
                    val question = questionMap[uniqueId]
                    Triple(uniqueId, question, trimmedNote)
                }
            }
            .sortedWith(
                compareBy<Triple<String, Question?, String>> {
                    it.second?.subject?.lowercase(Locale.ROOT) ?: "zzz"
                }.thenBy {
                    it.second?.uniqueId ?: it.first
                }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text("Bizurômetro", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (onOpenFlashCard != null) {
            OutlinedButton(
                onClick = onOpenFlashCard,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Abrir Flash Card")
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        if (bizuEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Você ainda não salvou bizus neste aparelho.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(bizuEntries) { (uniqueId, question, note) ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = question?.let {
                                    "${extractExamLabel(it.uniqueId)} • ${it.subject}"
                                } ?: uniqueId,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = question?.let { "Questão ${it.id}" } ?: "Questão identificada pelo código salvo",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = note,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                lineHeight = 22.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlashCardScreen(
    onBack: () -> Unit,
    onStartTraining: (SimuladoConfig) -> Unit
) {
    BackHandler(onBack = onBack)
    val allQuestions = remember { QuestionRepository.getAllQuestions() }
    var version by remember { mutableIntStateOf(0) }
    val flashCardEntries = remember(version, allQuestions) {
        val notes = ProgressRepository.getAllQuestionNotes()
        val questionsById = allQuestions.associateBy { it.uniqueId }
        ProgressRepository.getFlashCardQuestionIds()
            .mapNotNull { uniqueId ->
                questionsById[uniqueId]?.let { question ->
                    FlashCardQuestionEntry(
                        question = question,
                        note = notes[uniqueId].orEmpty()
                    )
                }
            }
            .sortedWith(
                compareBy<FlashCardQuestionEntry> { it.question.subject.lowercase(Locale.ROOT) }
                    .thenBy { it.question.uniqueId }
            )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text("Flash Card", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (flashCardEntries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Você ainda não adicionou questões ao Flash Card.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
            return@Column
        }

        Button(
            onClick = {
                onStartTraining(
                    SimuladoConfig(
                        exam = "flashcard",
                        year = "treino",
                        materias = flashCardEntries.map { it.question.subject }.distinct(),
                        tempoSegundos = null,
                        customQuestions = flashCardEntries.map { it.question },
                        displayTitle = "Flash Card"
                    )
                )
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("REFAZER QUESTÕES DO FLASH CARD", fontWeight = FontWeight.ExtraBold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(flashCardEntries) { entry ->
                var expanded by remember(entry.question.uniqueId) { mutableStateOf(false) }
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "${extractExamLabel(entry.question.uniqueId)} • ${entry.question.subject}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = entry.question.text.htmlToPlainText(),
                            maxLines = if (expanded) Int.MAX_VALUE else 4,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 21.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (entry.note.isNotBlank()) {
                            Text(
                                text = "Bizu: ${entry.note}",
                                color = MaterialTheme.colorScheme.primary,
                                lineHeight = 21.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { expanded = !expanded },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (expanded) "Ocultar" else "Rever")
                            }
                            OutlinedButton(
                                onClick = {
                                    ProgressRepository.removeQuestionFromFlashCard(entry.question.uniqueId)
                                    version += 1
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Remover")
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class FlashCardQuestionEntry(
    val question: Question,
    val note: String
)

private fun extractExamLabel(uniqueId: String): String {
    val parts = uniqueId.split("/")
    return if (parts.size >= 2) {
        "${parts[0].uppercase()} ${parts[1]}"
    } else {
        uniqueId
    }
}
