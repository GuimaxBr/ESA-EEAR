package com.guima.esa.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.ReportProblem
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.guima.esa.data.ApiService
import com.guima.esa.data.CloudSyncRepository
import com.guima.esa.data.ProgressRepository
import com.guima.esa.data.QuestionDifficultySnapshot
import com.guima.esa.data.Question
import com.guima.esa.data.QuestionRepository
import com.guima.esa.data.RankRepository
import com.guima.esa.data.SimuladoHistoryEntry
import com.guima.esa.data.SimuladoHistoryRepository
import com.guima.esa.data.UserRepository
import com.guima.esa.util.fromHtml
import com.guima.esa.util.htmlToPlainText
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class SimuladoConfig(
    val exam: String,
    val year: String,
    val materias: List<String>,
    val tempoSegundos: Long?,
    val customQuestions: List<Question> = emptyList(),
    val displayTitle: String? = null
)

private data class SubjectPerformance(
    val subject: String,
    val correctCount: Int,
    val totalQuestions: Int
) {
    val incorrectCount: Int = (totalQuestions - correctCount).coerceAtLeast(0)
    val accuracy: Float = if (totalQuestions == 0) 0f else correctCount.toFloat() / totalQuestions.toFloat()
}

private data class SimuladoResultSummary(
    val totalQuestions: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val finalGrade: Float,
    val accuracy: Float,
    val bestStreak: Int,
    val rankingPointsGained: Int,
    val subjectResults: List<SubjectPerformance>
)

private data class DifficultyQuestionCandidate(
    val question: Question,
    val snapshot: QuestionDifficultySnapshot
)

private data class DifficultySubjectSummary(
    val subject: String,
    val totalAttempts: Int,
    val correctCount: Int,
    val incorrectCount: Int,
    val activeQuestionsCount: Int,
    val recoveredQuestionsCount: Int,
    val score: Float
) {
    val accuracy: Float = if (totalAttempts == 0) 0f else correctCount.toFloat() / totalAttempts.toFloat()
}

private data class DifficultyTrainingPlan(
    val hardestSubject: DifficultySubjectSummary?,
    val subjectSummaries: List<DifficultySubjectSummary>,
    val selectedQuestions: List<Question>,
    val activePoolSize: Int,
    val recoveredQuestionsCount: Int
)

private val ttsPtBrLocale: Locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()
private val ttsPtLocale: Locale = Locale.Builder().setLanguage("pt").build()

@Composable
fun SimuladoSetupScreen(
    onActiveExamChange: (Boolean) -> Unit = {}
) {
    var selectedExam by remember { mutableStateOf<String?>(null) }
    var selectedYear by remember { mutableStateOf<String?>(null) }
    var runningConfig by remember { mutableStateOf<SimuladoConfig?>(null) }
    var showDifficultyScreen by remember { mutableStateOf(false) }

    DisposableEffect(Unit) {
        onDispose { onActiveExamChange(false) }
    }

    LaunchedEffect(runningConfig) {
        if (runningConfig == null) {
            onActiveExamChange(false)
        }
    }

    when {
        runningConfig != null -> ExecucaoSimuladoScreen(
            config = runningConfig!!,
            onFinish = { runningConfig = null },
            onActiveExamChange = onActiveExamChange
        )

        selectedExam != null && selectedYear != null -> ConfigSimuladoScreen(
            exam = selectedExam!!,
            year = selectedYear!!,
            onBack = { selectedYear = null },
            onStart = { runningConfig = it }
        )

        selectedExam != null -> YearSelectionScreen(
            exam = selectedExam!!,
            onYearSelected = { selectedYear = it },
            onBack = { selectedExam = null }
        )

        showDifficultyScreen -> DifficultyTrainingScreen(
            onBack = { showDifficultyScreen = false },
            onStart = {
                runningConfig = it
                showDifficultyScreen = false
            }
        )

        else -> ExamSelectionScreen(
            onExamSelected = { selectedExam = it },
            onDifficultySelected = { showDifficultyScreen = true }
        )
    }
}

@Composable
private fun AssetImage(
    assetBasePath: String,
    imageName: String,
    modifier: Modifier = Modifier,
    expandOnClick: Boolean = true
) {
    val imageModel = remember(assetBasePath, imageName) { "file:///android_asset/$assetBasePath/$imageName" }
    var isExpanded by remember(imageModel) { mutableStateOf(false) }

    AsyncImage(
        model = imageModel,
        contentDescription = null,
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (expandOnClick) {
                    Modifier.clickable { isExpanded = true }
                } else {
                    Modifier
                }
            ),
        contentScale = ContentScale.Fit
    )

    if (expandOnClick && isExpanded) {
        ExpandedAssetImage(
            imageModel = imageModel,
            onDismiss = { isExpanded = false }
        )
    }
}

@Composable
private fun ExpandedAssetImage(imageModel: String, onDismiss: () -> Unit) {
    val minScale = 1f
    val maxScale = 5f
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var imageSize by remember { mutableStateOf(IntSize.Zero) }

    fun clampOffsets(nextScale: Float, proposedX: Float, proposedY: Float): Pair<Float, Float> {
        if (containerSize == IntSize.Zero || imageSize == IntSize.Zero || nextScale <= minScale) {
            return 0f to 0f
        }

        val maxOffsetX = ((imageSize.width * nextScale) - containerSize.width).coerceAtLeast(0f) / 2f
        val maxOffsetY = ((imageSize.height * nextScale) - containerSize.height).coerceAtLeast(0f) / 2f

        return proposedX.coerceIn(-maxOffsetX, maxOffsetX) to proposedY.coerceIn(-maxOffsetY, maxOffsetY)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .onGloballyPositioned { containerSize = it.size }
                .pointerInput(onDismiss) {
                    detectTapGestures(onTap = { onDismiss() })
                },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .onGloballyPositioned { imageSize = it.size }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
                    .pointerInput(imageModel) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val updatedScale = (scale * zoom).coerceIn(minScale, maxScale)
                            if (updatedScale <= minScale) {
                                scale = minScale
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                val zoomProgress = ((updatedScale - minScale) / (maxScale - minScale)).coerceIn(0f, 1f)
                                val panSensitivity = 1.2f + (zoomProgress * 1.4f)
                                val proposedX = offsetX + (pan.x * updatedScale * panSensitivity)
                                val proposedY = offsetY + (pan.y * updatedScale * panSensitivity)
                                val (clampedX, clampedY) = clampOffsets(updatedScale, proposedX, proposedY)

                                scale = updatedScale
                                offsetX = clampedX
                                offsetY = clampedY
                            }
                        }
                    },
                contentScale = ContentScale.Fit
            )

            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp),
                shape = RoundedCornerShape(999.dp),
                color = Color.Black.copy(alpha = 0.6f)
            ) {
                Text(
                    text = "Toque novamente para minimizar. Use dois dedos para dar zoom.",
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
fun ExecucaoSimuladoScreen(
    config: SimuladoConfig,
    onFinish: () -> Unit,
    onActiveExamChange: (Boolean) -> Unit
) {
    val context = LocalContext.current
    val questions = remember(config) {
        if (config.customQuestions.isNotEmpty()) {
            config.customQuestions
        } else {
            QuestionRepository.getQuestions(config.exam, config.year, config.materias)
        }
    }
    val rankRepository = remember { RankRepository() }
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    var currentIdx by remember { mutableIntStateOf(0) }
    var activeQuestionIdx by remember { mutableIntStateOf(0) }
    var elapsedSeconds by remember { mutableStateOf(0L) }
    var pendingSelection by remember { mutableStateOf<Int?>(null) }
    var showExitDialog by remember { mutableStateOf(false) }
    var isSavingResult by remember { mutableStateOf(false) }
    var finalSummary by remember { mutableStateOf<SimuladoResultSummary?>(null) }
    val sessionAnswers = remember { mutableStateListOf<Pair<String, Boolean>>() }
    val answeredOptions = remember { mutableStateMapOf<Int, Int>() }
    val eliminatedOptionsByQuestion = remember { mutableStateMapOf<Int, Set<Int>>() }
    var textToSpeech by remember { mutableStateOf<TextToSpeech?>(null) }
    var isTtsReady by remember { mutableStateOf(false) }
    var speakingQuestionId by remember { mutableStateOf<String?>(null) }
    var showFeedbackDialog by remember { mutableStateOf(false) }
    var showQuestionNotesDialog by remember { mutableStateOf(false) }

    LaunchedEffect(finalSummary) {
        onActiveExamChange(finalSummary == null)
    }

    DisposableEffect(context) {
        var initializedTts: TextToSpeech? = null
        val tts = TextToSpeech(context) { status ->
            val activeTts = initializedTts ?: return@TextToSpeech
            if (status == TextToSpeech.SUCCESS) {
                val ptBrResult = activeTts.setLanguage(ttsPtBrLocale)
                if (ptBrResult == TextToSpeech.LANG_MISSING_DATA || ptBrResult == TextToSpeech.LANG_NOT_SUPPORTED) {
                    activeTts.setLanguage(ttsPtLocale)
                }
                activeTts.setSpeechRate(1.0f)
                activeTts.setPitch(1.0f)
                activeTts.setOnUtteranceProgressListener(
                    object : UtteranceProgressListener() {
                        override fun onStart(utteranceId: String?) {
                            mainHandler.post { speakingQuestionId = utteranceId }
                        }

                        override fun onDone(utteranceId: String?) {
                            mainHandler.post {
                                if (speakingQuestionId == utteranceId) {
                                    speakingQuestionId = null
                                }
                            }
                        }

                        @Deprecated("Deprecated in Java")
                        override fun onError(utteranceId: String?) {
                            mainHandler.post {
                                if (speakingQuestionId == utteranceId) {
                                    speakingQuestionId = null
                                }
                            }
                        }
                    }
                )
                textToSpeech = activeTts
                isTtsReady = true
            } else {
                isTtsReady = false
                speakingQuestionId = null
            }
        }
        initializedTts = tts

        onDispose {
            tts.stop()
            tts.shutdown()
            textToSpeech = null
            isTtsReady = false
            speakingQuestionId = null
        }
    }

    if (finalSummary != null) {
        SimuladoResultScreen(summary = finalSummary!!, onFinish = onFinish)
        return
    }

    BackHandler { showExitDialog = true }
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Sair da prova?") },
            text = { Text("Se você sair agora, seu progresso nesta prova será perdido.") },
            confirmButton = { TextButton(onClick = onFinish) { Text("Sair") } },
            dismissButton = { TextButton(onClick = { showExitDialog = false }) { Text("Cancelar") } }
        )
    }

    if (config.tempoSegundos != null && !answeredOptions.containsKey(activeQuestionIdx) && !isSavingResult) {
        LaunchedEffect(elapsedSeconds) {
            delay(1000L)
            elapsedSeconds++
        }
    }

    if (questions.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Nenhuma questão encontrada para os filtros selecionados.")
                Button(onClick = onFinish, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Voltar")
                }
            }
        }
        return
    }

    if (isSavingResult) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator()
                Spacer(Modifier.height(16.dp))
                Text("Calculando sua nota final...", fontWeight = FontWeight.Bold)
            }
        }
        return
    }

    val q = questions[currentIdx]
    val isReviewingPreviousQuestion = currentIdx != activeQuestionIdx
    val answeredOption = answeredOptions[currentIdx]
    val showResult = answeredOption != null
    val selectedOption = answeredOption ?: pendingSelection
    val eliminatedOptions = eliminatedOptionsByQuestion[currentIdx].orEmpty()
    val questionSpeechText = remember(q.uniqueId, q.text) { q.text.htmlToPlainText() }
    val canSpeakQuestion = questionSpeechText.isNotBlank() && isTtsReady
    val questionScrollState = rememberScrollState()
    var feedbackDraft by remember(q.uniqueId) { mutableStateOf("") }
    var questionNoteDraft by remember(q.uniqueId) { mutableStateOf(ProgressRepository.getQuestionNote(q.uniqueId)) }
    val questionNoteStats = remember(q.uniqueId, answeredOptions.size) { ProgressRepository.getAnswerStats(q.uniqueId) }
    val currentSessionQuestionResult = sessionAnswers.lastOrNull { it.first == q.uniqueId }?.second
    val questionNoteStatsCorrect = questionNoteStats.correctCount + if (currentSessionQuestionResult == true) 1 else 0
    val questionNoteStatsIncorrect = questionNoteStats.incorrectCount + if (currentSessionQuestionResult == false) 1 else 0
    val hasQuestionNote = questionNoteDraft.isNotBlank()
    var isOnFlashCard by remember(q.uniqueId) { mutableStateOf(ProgressRepository.isQuestionOnFlashCard(q.uniqueId)) }
    var floatingButtonOffsetX by remember { mutableStateOf(0f) }
    var floatingButtonOffsetY by remember { mutableStateOf(0f) }
    val floatingButtonMotion = rememberInfiniteTransition(label = "notesButtonFloat")
    val floatingButtonLift by floatingButtonMotion.animateFloat(
        initialValue = 0f,
        targetValue = -10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "notesButtonLift"
    )
    val floatingButtonSwing by floatingButtonMotion.animateFloat(
        initialValue = -2f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "notesButtonSwing"
    )

    LaunchedEffect(q.uniqueId) {
        textToSpeech?.stop()
        speakingQuestionId = null
        questionScrollState.scrollTo(0)
        questionNoteDraft = ProgressRepository.getQuestionNote(q.uniqueId)
        isOnFlashCard = ProgressRepository.isQuestionOnFlashCard(q.uniqueId)
    }

    if (showFeedbackDialog) {
        AlertDialog(
            onDismissRequest = { showFeedbackDialog = false },
            title = { Text("Enviar feedback da questao") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${config.exam.uppercase()} ${config.year} • ${q.subject} • Questao ${currentIdx + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = feedbackDraft,
                        onValueChange = { feedbackDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 4,
                        maxLines = 8,
                        label = { Text("Relato do usuario") },
                        placeholder = { Text("Descreva o erro, duvida ou sugestao dessa questao.") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val emailOpened = openQuestionFeedbackEmail(
                            context = context,
                            config = config,
                            question = q,
                            questionIndex = currentIdx,
                            totalQuestions = questions.size,
                            selectedOption = selectedOption,
                            feedbackMessage = feedbackDraft
                        )
                        if (emailOpened) {
                            showFeedbackDialog = false
                            feedbackDraft = ""
                        }
                    },
                    enabled = feedbackDraft.isNotBlank()
                ) {
                    Text("Abrir e-mail")
                }
            },
            dismissButton = {
                TextButton(onClick = { showFeedbackDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showQuestionNotesDialog) {
        AlertDialog(
            onDismissRequest = { showQuestionNotesDialog = false },
            title = { Text("Bizurômetro") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "${config.exam.uppercase()} ${config.year} • ${q.subject} • Questão ${currentIdx + 1}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = questionNoteDraft,
                        onValueChange = { questionNoteDraft = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 5,
                        maxLines = 10,
                        label = { Text("Seu bizu") },
                        placeholder = { Text("Escreva aqui seus bizus, macetes ou a resposta que você quer lembrar.") }
                    )
                    Text(
                        text = "Acertos: ${questionNoteStats.correctCount} • Erros: ${questionNoteStats.incorrectCount}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TextButton(
                        onClick = {
                            if (isOnFlashCard) {
                                ProgressRepository.removeQuestionFromFlashCard(q.uniqueId)
                            } else {
                                ProgressRepository.addQuestionToFlashCard(q.uniqueId)
                            }
                            isOnFlashCard = ProgressRepository.isQuestionOnFlashCard(q.uniqueId)
                        }
                    ) {
                        Text(if (isOnFlashCard) "Remover do Flash Card" else "Adicionar ao Flash Card")
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        ProgressRepository.saveQuestionNote(q.uniqueId, questionNoteDraft)
                        questionNoteDraft = ProgressRepository.getQuestionNote(q.uniqueId)
                        showQuestionNotesDialog = false
                    }
                ) {
                    Text("Salvar")
                }
            },
            dismissButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.End
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = {
                                questionNoteDraft = ""
                                ProgressRepository.saveQuestionNote(q.uniqueId, "")
                            }
                        ) {
                            Text("Limpar")
                        }
                        TextButton(onClick = { showQuestionNotesDialog = false }) {
                            Text("Fechar")
                        }
                    }
                }
            }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Questão ${currentIdx + 1}/${questions.size}", fontWeight = FontWeight.Bold)
            if (config.tempoSegundos != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Timer, null, modifier = Modifier.size(16.dp))
                    Text(
                        " ${formatTime(elapsedSeconds)}",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        if (currentIdx > 0 || isReviewingPreviousQuestion) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentIdx > 0) {
                    Button(
                        onClick = { currentIdx-- },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("ANTERIOR", fontWeight = FontWeight.Bold)
                    }
                }

                if (isReviewingPreviousQuestion) {
                    Button(
                        onClick = { currentIdx = activeQuestionIdx },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("VOLTAR À ATUAL", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        LinearProgressIndicator(
            progress = { (currentIdx + 1).toFloat() / questions.size.toFloat() },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .verticalScroll(questionScrollState)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SuggestionChip(onClick = {}, label = { Text(q.subject) })
                    AssistChip(
                        onClick = {
                            if (speakingQuestionId == q.uniqueId) {
                                textToSpeech?.stop()
                                speakingQuestionId = null
                            } else if (questionSpeechText.isNotBlank()) {
                                textToSpeech?.stop()
                                textToSpeech?.speak(
                                    questionSpeechText,
                                    TextToSpeech.QUEUE_FLUSH,
                                    null,
                                    q.uniqueId
                                )
                                speakingQuestionId = q.uniqueId
                            }
                        },
                        enabled = canSpeakQuestion,
                        label = {
                            Text(if (speakingQuestionId == q.uniqueId) "Parar áudio" else "Ouça esta questão")
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (speakingQuestionId == q.uniqueId) {
                                    Icons.AutoMirrored.Filled.VolumeOff
                                } else {
                                    Icons.AutoMirrored.Filled.VolumeUp
                                },
                                contentDescription = null
                            )
                        }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = Color(0x26FF5F52),
                        tonalElevation = 0.dp,
                        modifier = Modifier.clickable { showFeedbackDialog = true }
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ReportProblem,
                                contentDescription = "Enviar feedback da questao",
                                tint = Color(0xFFFF6B5D)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = q.text.fromHtml(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        lineHeight = 30.sp
                    ),
                    color = MaterialTheme.colorScheme.onSurface
                )

                q.questionImage?.takeIf { it.isNotBlank() }?.let {
                    Spacer(Modifier.height(12.dp))
                    AssetImage(q.assetBasePath, it, Modifier.heightIn(max = 260.dp))
                }

                Spacer(Modifier.height(24.dp))

                q.options.forEachIndexed { index, option ->
                    val isCorrect = index == q.correctOption
                    val isSelected = selectedOption == index
                    val isEliminated = index in eliminatedOptions
                    val optionImage = q.optionImages.getOrNull(index)
                    val color = when {
                        showResult && isCorrect -> Color(0xFF2E7D32)
                        showResult && isSelected && !isCorrect -> Color(0xFFC62828)
                        isSelected -> MaterialTheme.colorScheme.primary
                        isEliminated -> MaterialTheme.colorScheme.outline.copy(alpha = 0.55f)
                        else -> MaterialTheme.colorScheme.outline
                    }

                    OutlinedCard(
                        onClick = {
                            if (!showResult && !isReviewingPreviousQuestion && !isEliminated) {
                                pendingSelection = index
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = when {
                                isSelected || (showResult && isCorrect) -> color.copy(alpha = 0.10f)
                                isEliminated -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.18f)
                                else -> Color.Transparent
                            }
                        ),
                        border = CardDefaults.outlinedCardBorder().copy(brush = SolidColor(color))
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 16.dp, top = 16.dp, end = 58.dp, bottom = 16.dp)
                                    .graphicsLayer {
                                        alpha = if (isEliminated) 0.42f else 1f
                                    }
                            ) {
                                Text(
                                    text = "${'A' + index}) $option".fromHtml(),
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 17.sp,
                                        lineHeight = 26.sp
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                optionImage?.takeIf { it.isNotBlank() }?.let {
                                    Spacer(Modifier.height(12.dp))
                                    AssetImage(
                                        assetBasePath = q.assetBasePath,
                                        imageName = it,
                                        modifier = Modifier.heightIn(max = 220.dp),
                                        expandOnClick = false
                                    )
                                }
                            }

                            if (isEliminated) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp)
                                        .height(2.dp)
                                        .background(Color(0xD8AAB4C8), RoundedCornerShape(999.dp))
                                )
                            }

                            if (!showResult && !isReviewingPreviousQuestion) {
                                IconButton(
                                    onClick = {
                                        val currentEliminated = eliminatedOptionsByQuestion[currentIdx].orEmpty().toMutableSet()
                                        if (index in currentEliminated) {
                                            currentEliminated.remove(index)
                                        } else {
                                            currentEliminated.add(index)
                                            if (pendingSelection == index) {
                                                pendingSelection = null
                                            }
                                        }

                                        if (currentEliminated.isEmpty()) {
                                            eliminatedOptionsByQuestion.remove(currentIdx)
                                        } else {
                                            eliminatedOptionsByQuestion[currentIdx] = currentEliminated.toSet()
                                        }
                                    },
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .offset(x = (-6).dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ContentCut,
                                        contentDescription = if (isEliminated) {
                                            "Remover marcacao de eliminacao"
                                        } else {
                                            "Marcar alternativa como eliminada"
                                        },
                                        tint = if (isEliminated) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        }
                                    )
                                }
                            }
                        }
                    }
                }

                if (showResult && (q.explanation.isNotBlank() || !q.explanationImage.isNullOrBlank())) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Resolução:",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (q.explanation.isNotBlank()) {
                        Text(
                            text = q.explanation.fromHtml(),
                            style = MaterialTheme.typography.bodyLarge.copy(
                                fontSize = 16.sp,
                                lineHeight = 25.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    q.explanationImage?.takeIf { it.isNotBlank() }?.let {
                        Spacer(Modifier.height(12.dp))
                        AssetImage(q.assetBasePath, it, Modifier.heightIn(max = 260.dp))
                    }
                }
            }
        }

        Button(
            onClick = {
                if (isReviewingPreviousQuestion) {
                    currentIdx = activeQuestionIdx
                } else if (!showResult) {
                    val confirmedOption = selectedOption ?: return@Button
                    answeredOptions[currentIdx] = confirmedOption
                    val wasCorrect = confirmedOption == q.correctOption
                    if (sessionAnswers.none { it.first == q.uniqueId }) {
                        sessionAnswers.add(q.uniqueId to wasCorrect)
                    }
                } else if (currentIdx < questions.size - 1) {
                    activeQuestionIdx++
                    currentIdx++
                    pendingSelection = null
                } else {
                    coroutineScope.launch {
                        isSavingResult = true
                        val correctCount = sessionAnswers.count { it.second }
                        var currentStreak = 0
                        var maxStreak = 0

                        sessionAnswers.forEach { (_, wasCorrect) ->
                            if (wasCorrect) {
                                currentStreak++
                            } else {
                                if (currentStreak > maxStreak) maxStreak = currentStreak
                                currentStreak = 0
                            }
                        }

                        if (currentStreak > maxStreak) maxStreak = currentStreak

                        val summary = buildSimuladoSummary(questions, sessionAnswers.toList(), maxStreak)
                        sessionAnswers.forEach { (uniqueId, wasCorrect) ->
                            ProgressRepository.recordAnswer(uniqueId, wasCorrect)
                        }
                        SimuladoHistoryRepository.saveResult(
                            SimuladoHistoryEntry(
                                exam = config.exam,
                                year = config.year,
                                title = config.displayTitle ?: "${config.exam.uppercase()} ${config.year}",
                                finalGrade = summary.finalGrade,
                                correctCount = summary.correctCount,
                                totalQuestions = summary.totalQuestions,
                                completedAt = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date()),
                                durationSeconds = elapsedSeconds
                            )
                        )
                        finalSummary = summary
                        isSavingResult = false

                        launch {
                            val userId = UserRepository.getUserId()
                            val nickname = UserRepository.getNickname()
                            val incorrectCount = sessionAnswers.size - correctCount

                            ApiService.createUserIfNotExists(userId, nickname)
                            ApiService.sendStats(userId, correctCount, incorrectCount, maxStreak)
                            rankRepository.updateRankingData(
                                UserRepository.getCloudUserId(),
                                nickname,
                                correctCount,
                                UserRepository.getAvatarId(),
                                UserRepository.isPremium()
                            )
                            CloudSyncRepository.safeSyncCurrentUser()
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            enabled = isReviewingPreviousQuestion || showResult || selectedOption != null
        ) {
            Text(
                if (isReviewingPreviousQuestion) {
                    "VOLTAR À ATUAL"
                } else if (!showResult) {
                    "VERIFICAR"
                } else if (currentIdx < questions.size - 1) {
                    "PRÓXIMA"
                } else {
                    "FINALIZAR"
                },
                fontWeight = FontWeight.Bold
            )
        }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 86.dp)
                .offset {
                    IntOffset(
                        floatingButtonOffsetX.roundToInt(),
                        floatingButtonOffsetY.roundToInt()
                    )
                }
                .graphicsLayer {
                    translationY = floatingButtonLift
                    rotationZ = floatingButtonSwing
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        floatingButtonOffsetX += dragAmount.x
                        floatingButtonOffsetY += dragAmount.y
                    }
                }
        ) {
            FloatingActionButton(
                onClick = { showQuestionNotesDialog = true },
                modifier = Modifier.size(64.dp),
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    modifier = Modifier.size(26.dp),
                    contentDescription = "Abrir Bizurômetro da questão"
                )
            }

            if (hasQuestionNote) {
                Badge(modifier = Modifier.align(Alignment.TopEnd)) {
                    Text("1")
                }
            }
        }
    }
}

private fun buildSimuladoSummary(
    questions: List<Question>,
    answers: List<Pair<String, Boolean>>,
    bestStreak: Int
): SimuladoResultSummary {
    val answerMap = answers.toMap()
    val total = questions.size.coerceAtLeast(1)
    val correct = answers.count { it.second }
    val accuracy = correct.toFloat() / total.toFloat()
    val subjectResults = questions
        .groupBy { it.subject }
        .map { (subject, subjectQuestions) ->
            SubjectPerformance(
                subject = subject,
                correctCount = subjectQuestions.count { answerMap[it.uniqueId] == true },
                totalQuestions = subjectQuestions.size
            )
        }
        .sortedWith(compareByDescending<SubjectPerformance> { it.accuracy }.thenByDescending { it.correctCount })

    return SimuladoResultSummary(
        totalQuestions = total,
        correctCount = correct,
        incorrectCount = (total - correct).coerceAtLeast(0),
        finalGrade = (accuracy * 10f).coerceIn(0f, 10f),
        accuracy = accuracy,
        bestStreak = bestStreak,
        rankingPointsGained = correct,
        subjectResults = subjectResults
    )
}

@Composable
private fun SimuladoResultScreen(summary: SimuladoResultSummary, onFinish: () -> Unit) {
    BackHandler(onBack = onFinish)
    val animatedGrade by animateFloatAsState(
        targetValue = summary.finalGrade,
        animationSpec = tween(1800, easing = FastOutSlowInEasing),
        label = "grade"
    )
    val animatedAccuracy by animateFloatAsState(
        targetValue = summary.accuracy.coerceIn(0f, 1f),
        animationSpec = tween(1600, easing = FastOutSlowInEasing),
        label = "acc"
    )
    val animatedCorrect by animateIntAsState(summary.correctCount, tween(1400), label = "correct")
    val animatedIncorrect by animateIntAsState(summary.incorrectCount, tween(1400), label = "incorrect")
    val animatedPoints by animateIntAsState(summary.rankingPointsGained, tween(1500), label = "points")
    val animatedStreak by animateIntAsState(summary.bestStreak, tween(1500), label = "streak")

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background,
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        )
                    )
                )
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text("Resultado final", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(18.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        )
                        .padding(24.dp)
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Nota final",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.88f),
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            formatGrade(animatedGrade),
                            color = MaterialTheme.colorScheme.onPrimary,
                            style = MaterialTheme.typography.displayLarge,
                            fontWeight = FontWeight.Black
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Fórmula: (${animatedCorrect}/${summary.totalQuestions}) x 10",
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f),
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(Modifier.height(14.dp))
                        LinearProgressIndicator(
                            progress = { animatedAccuracy },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(12.dp),
                            color = Color.White,
                            trackColor = Color.White.copy(alpha = 0.22f)
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "${(animatedAccuracy * 100).toInt()}% de aproveitamento",
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ResultStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Acertos",
                    value = animatedCorrect.toString(),
                    subtitle = "Questões certas",
                    accent = Color(0xFF1B8E3E)
                ) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color(0xFF1B8E3E))
                }
                ResultStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Erros",
                    value = animatedIncorrect.toString(),
                    subtitle = "Questões erradas",
                    accent = Color(0xFFC62828)
                ) {
                    Icon(Icons.Default.Close, null, tint = Color(0xFFC62828))
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ResultStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Ranking",
                    value = "+$animatedPoints",
                    subtitle = "Pontos ganhos",
                    accent = MaterialTheme.colorScheme.primary
                ) {
                    Text("XP", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Black)
                }
                ResultStatCard(
                    modifier = Modifier.weight(1f),
                    title = "Sequência",
                    value = animatedStreak.toString(),
                    subtitle = "Melhor embalo",
                    accent = MaterialTheme.colorScheme.tertiary
                ) {
                    Text("ST", color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Black)
                }
            }

            Spacer(Modifier.height(18.dp))

            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp)) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Desempenho por matéria", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(16.dp))
                    summary.subjectResults.forEach {
                        SubjectPerformanceCard(it)
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }

            Spacer(Modifier.height(18.dp))

            Button(
                onClick = onFinish,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text("Voltar para as provas", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ResultStatCard(
    modifier: Modifier,
    title: String,
    value: String,
    subtitle: String,
    accent: Color,
    icon: @Composable () -> Unit
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                icon()
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .background(accent.copy(alpha = 0.12f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(title, color = accent, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SubjectPerformanceCard(subjectResult: SubjectPerformance) {
    val animatedAccuracy by animateFloatAsState(
        targetValue = subjectResult.accuracy.coerceIn(0f, 1f),
        animationSpec = tween(1400, easing = FastOutSlowInEasing),
        label = "subject-${subjectResult.subject}"
    )
    val animatedCorrect by animateIntAsState(
        targetValue = subjectResult.correctCount,
        animationSpec = tween(1400),
        label = "subject-correct-${subjectResult.subject}"
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(subjectResult.subject, fontWeight = FontWeight.Bold)
                Text(
                    "${(animatedAccuracy * 100).toInt()}%",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { animatedAccuracy },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "$animatedCorrect de ${subjectResult.totalQuestions} acertos | ${subjectResult.incorrectCount} erros",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatGrade(value: Float): String =
    String.format(Locale.US, "%.1f", value).replace('.', ',')

fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return "%02d:%02d".format(minutes, remainingSeconds)
}

private fun openQuestionFeedbackEmail(
    context: Context,
    config: SimuladoConfig,
    question: Question,
    questionIndex: Int,
    totalQuestions: Int,
    selectedOption: Int?,
    feedbackMessage: String
): Boolean {
    val timestamp = Date()
    val locale = Locale.Builder().setLanguage("pt").setRegion("BR").build()
    val readableTimestamp = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", locale).format(timestamp)
    val userEmail = UserRepository.getGoogleEmail().ifBlank { "nao informado" }
    val nickname = UserRepository.getNickname()
    val selectedLabel = selectedOption?.let { "${'A' + it}" } ?: "nenhuma"
    val subject = "Feedback ${config.exam.uppercase()} ${config.year} - ${question.subject} - questao ${questionIndex + 1}"
    val body = buildString {
        appendLine("Prova: ${config.exam.uppercase()} ${config.year}")
        appendLine("Materia: ${question.subject}")
        appendLine("Questao: ${questionIndex + 1}/$totalQuestions")
        appendLine("ID: ${question.uniqueId}")
        appendLine("Grupo: ${config.exam.uppercase()} ${config.year} / ${question.subject}")
        appendLine("Data e hora: $readableTimestamp")
        appendLine("Usuario (apelido): $nickname")
        appendLine("Usuario (gmail): $userEmail")
        appendLine("Alternativa marcada: $selectedLabel")
        appendLine()
        appendLine("Relato do usuario:")
        appendLine(feedbackMessage.trim())
        appendLine()
        appendLine("Enunciado da questao:")
        appendLine(question.text.htmlToPlainText())
    }

    val mailtoUri = Uri.parse(
        "mailto:${Uri.encode("guimaxguima@gmail.com")}" +
            "?subject=${Uri.encode(subject)}" +
            "&body=${Uri.encode(body)}"
    )

    val intent = Intent(Intent.ACTION_SENDTO).apply {
        data = mailtoUri
        putExtra(Intent.EXTRA_SUBJECT, subject)
        putExtra(Intent.EXTRA_TEXT, body)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    return try {
        if (intent.resolveActivity(context.packageManager) == null) {
            Toast.makeText(context, "Nenhum app de e-mail encontrado.", Toast.LENGTH_LONG).show()
            false
        } else {
            context.startActivity(intent)
            true
        }
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "Nenhum app de e-mail encontrado.", Toast.LENGTH_LONG).show()
        false
    }
}

private fun buildDifficultyTrainingPlan(
    allQuestions: List<Question>,
    now: Long = System.currentTimeMillis()
): DifficultyTrainingPlan {
    val questionById = allQuestions.associateBy { it.uniqueId }
    val mappedCandidates = ProgressRepository.getQuestionDifficultySnapshots(now)
        .mapNotNull { snapshot ->
            questionById[snapshot.uniqueId]?.let { question ->
                DifficultyQuestionCandidate(question = question, snapshot = snapshot)
            }
        }

    val subjectSummaries = mappedCandidates
        .groupBy { it.question.subject }
        .map { (subject, candidates) ->
            val totalAttempts = candidates.sumOf { it.snapshot.totalAttempts }
            val correctCount = candidates.sumOf { it.snapshot.correctCount }
            val incorrectCount = candidates.sumOf { it.snapshot.incorrectCount }
            val activeQuestionsCount = candidates.count { it.snapshot.isActiveDifficulty }
            val recoveredQuestionsCount = candidates.count { it.snapshot.isRecoveredRecently }
            val score = candidates.sumOf { it.snapshot.difficultyScore.toDouble() }.toFloat()

            DifficultySubjectSummary(
                subject = subject,
                totalAttempts = totalAttempts,
                correctCount = correctCount,
                incorrectCount = incorrectCount,
                activeQuestionsCount = activeQuestionsCount,
                recoveredQuestionsCount = recoveredQuestionsCount,
                score = score
            )
        }
        .sortedWith(
            compareByDescending<DifficultySubjectSummary> { it.score }
                .thenByDescending { it.incorrectCount }
                .thenBy { it.subject.lowercase(Locale.ROOT) }
        )

    val activeCandidates = mappedCandidates
        .filter { it.snapshot.isActiveDifficulty }
        .sortedWith(
            compareByDescending<DifficultyQuestionCandidate> { it.snapshot.difficultyScore }
                .thenByDescending { it.snapshot.incorrectCount }
                .thenByDescending { it.snapshot.lastAnsweredAt }
        )

    return DifficultyTrainingPlan(
        hardestSubject = subjectSummaries.firstOrNull(),
        subjectSummaries = subjectSummaries,
        selectedQuestions = selectMixedDifficultyQuestions(activeCandidates, 10),
        activePoolSize = activeCandidates.size,
        recoveredQuestionsCount = mappedCandidates.count { it.snapshot.isRecoveredRecently }
    )
}

private fun selectMixedDifficultyQuestions(
    candidates: List<DifficultyQuestionCandidate>,
    limit: Int
): List<Question> {
    if (candidates.isEmpty() || limit <= 0) return emptyList()

    val subjectOrder = candidates
        .groupBy { it.question.subject }
        .entries
        .sortedByDescending { entry -> entry.value.sumOf { it.snapshot.difficultyScore.toDouble() } }
        .map { it.key }

    val groupedCandidates = candidates
        .groupBy { it.question.subject }
        .mapValues { (_, subjectCandidates) -> subjectCandidates.toMutableList() }
        .toMutableMap()

    val selectedQuestions = mutableListOf<Question>()
    var foundQuestionInRound = true

    while (selectedQuestions.size < limit && foundQuestionInRound) {
        foundQuestionInRound = false
        subjectOrder.forEach { subject ->
            val subjectQuestions = groupedCandidates[subject] ?: return@forEach
            val nextCandidate = subjectQuestions.removeFirstOrNull() ?: return@forEach
            if (selectedQuestions.none { it.uniqueId == nextCandidate.question.uniqueId }) {
                selectedQuestions += nextCandidate.question
                foundQuestionInRound = true
            }
            if (selectedQuestions.size >= limit) return@forEach
        }
    }

    return selectedQuestions
}

@Composable
private fun DifficultyTrainingScreen(
    onBack: () -> Unit,
    onStart: (SimuladoConfig) -> Unit
) {
    val allQuestions = remember { QuestionRepository.getAllQuestions() }
    val trainingPlan = remember { buildDifficultyTrainingPlan(allQuestions) }
    val hardestSubject = trainingPlan.hardestSubject
    val selectedQuestions = trainingPlan.selectedQuestions

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
            }
            Text("Minhas dificuldades", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = hardestSubject?.let { "Sua matéria mais sensível agora é ${it.subject}." }
                        ?: "Ainda não há dados suficientes para mapear suas dificuldades.",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (hardestSubject != null) {
                        "O treino abaixo junta as questões em que você mais tropeçou, mistura matérias para evitar repetição mecânica e segura de fora o que você já está começando a superar."
                    } else {
                        "Resolva algumas provas primeiro. Assim o app aprende onde você mais erra e monta um treino realmente inteligente."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        if (hardestSubject != null) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text("Relatório atual", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Matéria crítica: ${hardestSubject.subject}",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Acertos: ${hardestSubject.correctCount} • Erros: ${hardestSubject.incorrectCount} • Aproveitamento: ${(hardestSubject.accuracy * 100).toInt()}%",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        "Questões ativas nessa matéria: ${hardestSubject.activeQuestionsCount}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        if (trainingPlan.subjectSummaries.isNotEmpty()) {
            Text("Matérias com mais dificuldade", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(10.dp))
            trainingPlan.subjectSummaries.take(4).forEach { subjectSummary ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(subjectSummary.subject, fontWeight = FontWeight.Bold)
                            Text(
                                "${(subjectSummary.accuracy * 100).toInt()}%",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { subjectSummary.accuracy.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Erros: ${subjectSummary.incorrectCount} • Acertos: ${subjectSummary.correctCount} • Questões em revisão: ${subjectSummary.activeQuestionsCount}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.28f)
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Treino inteligente", fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    if (selectedQuestions.isNotEmpty()) {
                        "Este modo separou ${selectedQuestions.size} questões difíceis e misturou matérias para você não cair em repetição previsível."
                    } else {
                        "No momento não há questões difíceis ativas para repetir."
                    },
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    "Pool ativo: ${trainingPlan.activePoolSize} questão(ões) | Questões já em recuperação: ${trainingPlan.recoveredQuestionsCount}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        if (selectedQuestions.isNotEmpty()) {
            Button(
                onClick = {
                    onStart(
                        SimuladoConfig(
                            exam = "dificuldades",
                            year = "treino",
                            materias = selectedQuestions.map { it.subject }.distinct(),
                            tempoSegundos = null,
                            customQuestions = selectedQuestions,
                            displayTitle = "Minhas dificuldades"
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(18.dp)
            ) {
                Text(
                    "TREINAR ${selectedQuestions.size} QUESTÕES DIFÍCEIS",
                    fontWeight = FontWeight.ExtraBold
                )
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "Você está superando suas dificuldades.",
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Não encontramos novas questões problemáticas ativas agora. Continue estudando: quando surgirem novas dificuldades reais, elas aparecem aqui.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 22.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ExamSelectionScreen(
    onExamSelected: (String) -> Unit,
    onDifficultySelected: () -> Unit
) {
    val exams = remember { QuestionRepository.getExams() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text("Selecione a prova", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onDifficultySelected() },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    ListItem(
                        headlineContent = { Text("MINHAS DIFICULDADES", fontWeight = FontWeight.ExtraBold) },
                        supportingContent = {
                            Text(
                                "Treino adaptativo com as questões que você mais erra.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingContent = { Text("Treinar >", color = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
            items(exams) { exam ->
                val isComingSoon = exam.equals("eear", ignoreCase = true)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isComingSoon) {
                                Modifier
                            } else {
                                Modifier.clickable { onExamSelected(exam) }
                            }
                        ),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isComingSoon) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                        }
                    )
                ) {
                    ListItem(
                        headlineContent = { Text(exam.uppercase(), fontWeight = FontWeight.Bold) },
                        supportingContent = {
                            if (isComingSoon) {
                                Text(
                                    "Coming Soon",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        },
                        trailingContent = {
                            if (isComingSoon) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Lock,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Bloqueado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            } else {
                                Text("Selecionar >", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun YearSelectionScreen(exam: String, onYearSelected: (String) -> Unit, onBack: () -> Unit) {
    val years = remember(exam) { QuestionRepository.getYearsForExam(exam) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
            }
            Text("Selecione o ano", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(years) { year ->
                Card(modifier = Modifier.fillMaxWidth().clickable { onYearSelected(year) }) {
                    ListItem(
                        headlineContent = { Text("Prova $year", fontWeight = FontWeight.Bold) },
                        trailingContent = { Text("Selecionar >", color = MaterialTheme.colorScheme.primary) }
                    )
                }
            }
        }
    }
}

@Composable
fun ConfigSimuladoScreen(exam: String, year: String, onBack: () -> Unit, onStart: (SimuladoConfig) -> Unit) {
    val materiasList = remember(exam, year) { QuestionRepository.getSubjectsForYear(exam, year) }
    val selectedMaterias = remember { mutableStateListOf<String>().apply { addAll(materiasList) } }
    var useTime by remember { mutableStateOf(true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar")
            }
            Text("Configurar prova $year", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Prova: ${exam.uppercase()} $year", fontWeight = FontWeight.ExtraBold)
                Text("Duração sugerida: 4 horas", style = MaterialTheme.typography.bodyMedium)
            }
        }

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Deseja cronometrar o tempo?", fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Switch(checked = useTime, onCheckedChange = { useTime = it })
            }
        }

        Text("Matérias:", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        materiasList.forEach { materia ->
            val isSelected = selectedMaterias.contains(materia)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (isSelected) {
                            selectedMaterias.remove(materia)
                        } else {
                            selectedMaterias.add(materia)
                        }
                    }
                    .padding(vertical = 2.dp)
            ) {
                Checkbox(checked = isSelected, onCheckedChange = null)
                Text(materia)
            }
        }

        Button(
            onClick = {
                val tempo = if (useTime) selectedMaterias.size * 3600L else null
                onStart(SimuladoConfig(exam, year, selectedMaterias.toList(), tempo))
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp),
            enabled = selectedMaterias.isNotEmpty()
        ) {
            Text("INICIAR AGORA", fontWeight = FontWeight.Bold)
        }
    }
}
