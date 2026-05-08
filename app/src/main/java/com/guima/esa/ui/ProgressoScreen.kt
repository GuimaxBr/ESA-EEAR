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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.guima.esa.data.ProgressRepository
import com.guima.esa.data.QuestionRepository
import com.guima.esa.ui.theme.EsaInkBlue

data class SubjectProgressData(val subject: String, val correctCount: Int, val total: Int)

@Composable
fun ProgressoScreen(onBack: () -> Unit) {
    BackHandler(onBack = onBack)

    val allQuestions = QuestionRepository.getAllQuestions()
    val correctlyAnsweredIds = ProgressRepository.getCorrectlyAnsweredUniqueIds()
    val questionsBySubject = allQuestions.groupBy { it.subject }

    val progressData = questionsBySubject.map { (subject, questionsInSubject) ->
        val total = questionsInSubject.size
        val correctCount = questionsInSubject.count { it.uniqueId in correctlyAnsweredIds }

        SubjectProgressData(
            subject = subject.replaceFirstChar { it.uppercase() },
            correctCount = correctCount,
            total = total
        )
    }.sortedBy { it.subject }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        ProgressHeader(onBack = onBack)
        Spacer(modifier = Modifier.height(12.dp))

        if (progressData.isEmpty() || progressData.all { it.correctCount == 0 }) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Nenhum acerto ainda. Continue estudando!")
            }
            return
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Progresso por materia",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items(progressData) {
                ProgressItem(data = it)
            }
        }
    }
}

@Composable
private fun ProgressHeader(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Voltar para Home"
            )
        }
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "Voltar para Home",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun ProgressItem(data: SubjectProgressData) {
    val progressColor = MaterialTheme.colorScheme.primary
    val progressTrackColor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        EsaInkBlue.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    }
    val percentage = if (data.total > 0) {
        data.correctCount.toFloat() / data.total.toFloat()
    } else {
        0f
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = data.subject,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { percentage },
                    modifier = Modifier.weight(1f),
                    color = progressColor,
                    trackColor = progressTrackColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "${(percentage * 100).toInt()}% (${data.correctCount}/${data.total})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
