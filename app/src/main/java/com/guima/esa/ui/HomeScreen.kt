package com.guima.esa.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.guima.esa.AppDestinations
import com.guima.esa.ui.theme.EsaInkBlue

private data class HomeShortcutItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun HomeScreen(
    onNavigate: (AppDestinations) -> Unit,
    dailyMotivationPhrase: String?,
    onActiveExamChange: (Boolean) -> Unit = {},
    homeViewModel: HomeViewModel = viewModel()
) {
    val uiState by homeViewModel.uiState.collectAsState()
    var showBizurometroScreen by remember { mutableStateOf(false) }
    var showFlashCardScreen by remember { mutableStateOf(false) }
    var runningFlashCardConfig by remember { mutableStateOf<SimuladoConfig?>(null) }

    DisposableEffect(Unit) {
        homeViewModel.refreshData()
        onDispose { }
    }

    when {
        runningFlashCardConfig != null -> {
            ExecucaoSimuladoScreen(
                config = runningFlashCardConfig!!,
                onFinish = { runningFlashCardConfig = null },
                onActiveExamChange = onActiveExamChange
            )
            return
        }

        showBizurometroScreen -> {
            BizurometroScreen(
                onBack = { showBizurometroScreen = false },
                onOpenFlashCard = {
                    showBizurometroScreen = false
                    showFlashCardScreen = true
                }
            )
            return
        }

        showFlashCardScreen -> {
            FlashCardScreen(
                onBack = { showFlashCardScreen = false },
                onStartTraining = { runningFlashCardConfig = it }
            )
            return
        }
    }

    val menuItems = remember(onNavigate) {
        listOf(
            HomeShortcutItem("Provas", Icons.Default.Quiz) { onNavigate(AppDestinations.SIMULADO) },
            HomeShortcutItem("Progresso", checkNotNull(AppDestinations.PROGRESSO.icon)) { onNavigate(AppDestinations.PROGRESSO) },
            HomeShortcutItem("Ranking", checkNotNull(AppDestinations.RANK.icon)) { onNavigate(AppDestinations.RANK) },
            HomeShortcutItem("Perfil", checkNotNull(AppDestinations.PROFILE.icon)) { onNavigate(AppDestinations.PROFILE) }
        )
    }
    val studyTools = remember {
        listOf(
            HomeShortcutItem("Flash Card", Icons.Default.Quiz) { showFlashCardScreen = true },
            HomeShortcutItem("Bizurômetro", Icons.Default.Edit) { showBizurometroScreen = true }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        DailyProgressCard(
            metaDiaria = uiState.dailyGoal,
            progressoHoje = uiState.todaysCorrectAnswers,
            metaEstudoMinutos = uiState.dailyStudyGoalMinutes,
            estudoHojeMs = uiState.todaysStudyTimeMs
        )

        Spacer(modifier = Modifier.height(24.dp))

        PersonalStats(
            total = uiState.totalCorrectAnswers,
            sequence = uiState.bestCorrectAnswerStreak,
            today = uiState.todaysCorrectAnswers
        )

        Spacer(modifier = Modifier.height(24.dp))

        dailyMotivationPhrase?.let { phrase ->
            DailyMotivationCard(phrase = phrase)
            Spacer(modifier = Modifier.height(24.dp))
        }

        Text(
            text = "BANCO DE QUESTÕES",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))
        HomeShortcutGrid(menuItems)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "FERRAMENTAS DE ESTUDO",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(16.dp))
        HomeShortcutGrid(studyTools)
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun HomeShortcutGrid(items: List<HomeShortcutItem>) {
    items.chunked(2).forEach { rowItems ->
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            rowItems.forEach { item ->
                HomeActionCard(
                    title = item.title,
                    icon = item.icon,
                    modifier = Modifier.weight(1f),
                    onClick = item.onClick
                )
            }
            if (rowItems.size == 1) {
                Spacer(modifier = Modifier.weight(1f))
            }
        }
        Spacer(modifier = Modifier.height(12.dp))
    }
}

@Composable
private fun DailyProgressCard(
    metaDiaria: Int,
    progressoHoje: Int,
    metaEstudoMinutos: Int,
    estudoHojeMs: Long
) {
    val progressColor = MaterialTheme.colorScheme.primary
    val progressTrackColor = if (MaterialTheme.colorScheme.background.luminance() > 0.5f) {
        EsaInkBlue.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.26f)
    }

    val progressoQuestoes = if (metaDiaria > 0) {
        progressoHoje.toFloat() / metaDiaria.toFloat()
    } else {
        0f
    }
    val estudoHojeMinutos = (estudoHojeMs / 60000L).toInt()
    val progressoEstudo = if (metaEstudoMinutos > 0) {
        estudoHojeMinutos.toFloat() / metaEstudoMinutos.toFloat()
    } else {
        0f
    }
    val metaQuestoesConcluida = progressoHoje >= metaDiaria
    val metaEstudoConcluida = estudoHojeMinutos >= metaEstudoMinutos

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row {
                GoalStatusText(
                    text = "Meta diária",
                    completed = metaQuestoesConcluida
                )
                Spacer(modifier = Modifier.weight(1f))
                GoalStatusText(
                    text = "$metaDiaria questões",
                    completed = metaQuestoesConcluida,
                    emphasize = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Progresso hoje", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progressoQuestoes.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                    color = progressColor,
                    trackColor = progressTrackColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "$progressoHoje / $metaDiaria",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row {
                GoalStatusText(
                    text = "Requisito de tempo",
                    completed = metaEstudoConcluida
                )
                Spacer(modifier = Modifier.weight(1f))
                GoalStatusText(
                    text = "$metaEstudoMinutos min",
                    completed = metaEstudoConcluida,
                    emphasize = true
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text("Tempo de estudo hoje", fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { progressoEstudo.coerceIn(0f, 1f) },
                    modifier = Modifier.weight(1f),
                    color = progressColor,
                    trackColor = progressTrackColor
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "${formatStudyMinutes(estudoHojeMinutos)} / ${metaEstudoMinutos} min",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun GoalStatusText(
    text: String,
    completed: Boolean,
    emphasize: Boolean = false
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Bold,
            textDecoration = if (completed) TextDecoration.LineThrough else null,
            color = if (completed) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
        )
        if (completed) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.Done,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

private fun formatStudyMinutes(totalMinutes: Int): String {
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        "${hours}h ${minutes}m"
    } else {
        "${minutes}m"
    }
}

@Composable
private fun PersonalStats(total: Int, sequence: Int, today: Int) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        StatItem(icon = Icons.Default.CheckCircle, value = total.toString(), label = "Total de acertos")
        StatItem(icon = Icons.Default.LocalFireDepartment, value = sequence.toString(), label = "Melhor sequência")
        StatItem(icon = Icons.Default.DateRange, value = today.toString(), label = "Acertos hoje")
    }
}

@Composable
private fun RowScope.StatItem(icon: ImageVector, value: String, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.weight(1f)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        Text(label, fontSize = 12.sp, textAlign = TextAlign.Center)
    }
}

@Composable
private fun HomeActionCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(130.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        }
    }
}
