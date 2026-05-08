package com.guima.esa.ui

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.rememberAsyncImagePainter
import com.github.mikephil.charting.charts.RadarChart
import com.github.mikephil.charting.data.RadarData
import com.github.mikephil.charting.data.RadarDataSet
import com.github.mikephil.charting.data.RadarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.guima.esa.R
import com.guima.esa.data.BillingRepository
import com.guima.esa.data.ProgressRepository
import com.guima.esa.data.QuestionRepository
import com.guima.esa.data.SimuladoHistoryEntry
import com.guima.esa.data.UserRepository
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import com.guima.esa.reminder.ReminderScheduler
import com.guima.esa.ui.theme.ThemeViewModel
import com.guima.esa.util.AvatarAssetResolver
import com.guima.esa.util.findActivity
import com.guima.esa.util.formatPointsCompact

private val baseVersionTesters = listOf(
    "Africano",
    "Bruno Sil",
    "Elielson",
    "Emerson C",
    "EVERTON S",
    "Fabio Gue",
    "Gabriel S",
    "igor l",
    "Joao Pedro",
    "Josué",
    "Ruan Finocchio Muniz Moraes",
    "Caio Ronald da Silva",
    "Lucas Jún",
    "Luis Rodr",
    "Mario Aze",
    "Mark7888",
    "Marcos",
    "Maseeh",
    "Miguel França neto",
    "Miguel L",
    "Nailso Da",
    "Ney Soare",
    "Rebeca Di",
    "Robson Fi",
    "Ruan M.",
    "チンクリス",
    "Kauam Souza",
    "Kauan Soares Sousa"
)

// Mantemos a implementacao pronta; basta trocar para true para reexibir a area premium.
private const val SHOW_PREMIUM_SECTION = false

@Composable
fun ProfileScreen(
    sessionVersion: Int,
    themeViewModel: ThemeViewModel = viewModel(),
    onSignedOut: () -> Unit
) {
    val profileViewModel: ProfileViewModel = viewModel(key = "profile-$sessionVersion")
    val uiState by profileViewModel.uiState.collectAsState()
    var showAvatarPicker by remember { mutableStateOf(false) }
    var showHistoryScreen by remember { mutableStateOf(false) }
    var showBizurometroScreen by remember { mutableStateOf(false) }
    var showAboutScreen by remember { mutableStateOf(false) }
    var openLegalDocument by remember { mutableStateOf<LegalDocumentType?>(null) }
    val context = LocalContext.current
    val activity = context.findActivity()
    val googleSignInClient = remember(context) {
        GoogleSignIn.getClient(
            context,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(context.getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        )
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            profileViewModel.onReminderToggled(true, context)
        }
    }
    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            if (account != null) {
                profileViewModel.onGoogleAccountSignedIn(account)
            }
        } catch (_: ApiException) {
        }
    }

    LaunchedEffect(Unit) {
        profileViewModel.loadAvailableAvatars(context)
        profileViewModel.refreshLocalProfileState()
        BillingRepository.onGoogleAccountChanged()
    }

    LaunchedEffect(uiState.isGoogleSignedIn) {
        themeViewModel.hydrateFromStorage(UserRepository.isDarkMode())
    }

    if (showHistoryScreen) {
        SimuladoHistoryScreen(
            history = uiState.simuladoHistory,
            onBack = { showHistoryScreen = false }
        )
        return
    }

    if (showBizurometroScreen) {
        BizurometroScreen(onBack = { showBizurometroScreen = false })
        return
    }

    if (openLegalDocument != null) {
        LoginLegalDocumentScreen(
            documentType = openLegalDocument!!,
            onBack = { openLegalDocument = null }
        )
        return
    }

    if (showAboutScreen) {
        AboutAppScreen(
            onBack = { showAboutScreen = false }
        )
        return
    }

    if (showAvatarPicker) {
        Dialog(onDismissRequest = { showAvatarPicker = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Escolha seu avatar", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Avatares 1 a 6 são exclusivos do Premium.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(16.dp))
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.availableAvatars) { id ->
                            val canUseAvatar = AvatarAssetResolver.canUseAvatar(id, uiState.premiumActive)
                            Box(
                                modifier = Modifier
                                    .size(80.dp)
                                    .border(
                                        width = if (uiState.avatarId == id) 3.dp else 0.dp,
                                        color = if (uiState.avatarId == id) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent
                                    )
                                    .clickable(enabled = canUseAvatar) {
                                        profileViewModel.onAvatarSelected(id)
                                        showAvatarPicker = false
                                    }
                            ) {
                                AvatarImage(avatarId = id, size = 80.dp)
                                if (!canUseAvatar) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.55f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                imageVector = Icons.Default.Lock,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary
                                            )
                                            Text(
                                                text = "Premium",
                                                color = MaterialTheme.colorScheme.onPrimary,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ProfileImage(avatarId = uiState.avatarId) { showAvatarPicker = true }
        Spacer(Modifier.height(16.dp))
        EditableNickname(nickname = uiState.nickname, onDone = { profileViewModel.onNicknameChange(it) })

        Spacer(Modifier.height(24.dp))

        AccountAccessCard(
            isGoogleSignedIn = uiState.isGoogleSignedIn,
            displayName = uiState.googleDisplayName,
            email = uiState.googleEmail,
            rankingPoints = uiState.rankingPoints,
            onSignIn = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
            onSignOut = {
                googleSignInClient.signOut().addOnCompleteListener {
                    profileViewModel.onGoogleSignOut()
                    onSignedOut()
                }
            }
        )

        Spacer(Modifier.height(16.dp))

        if (SHOW_PREMIUM_SECTION) {
            PremiumCard(
                premiumActive = uiState.premiumActive,
                priceLabel = uiState.premiumPrice,
                billingReady = uiState.billingReady,
                onBuy = {
                    activity?.let { BillingRepository.launchPremiumPurchase(it) }
                },
                onRestore = { BillingRepository.restorePurchasesForCurrentAccount() }
            )

            Spacer(Modifier.height(24.dp))
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                DailyGoalSetter(goal = uiState.dailyGoal, onGoalChange = { profileViewModel.onDailyGoalChange(it) })
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                DailyStudyGoalSetter(
                    minutes = uiState.dailyStudyGoalMinutes,
                    onMinutesChange = { profileViewModel.onDailyStudyGoalMinutesChange(it) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                SargentometroDateSetting(
                    selectedDate = uiState.sargentometroTargetDate,
                    onDateSelected = { year, month, day ->
                        profileViewModel.onSargentometroDateChanged(year, month, day)
                    }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ReminderSwitch(
                    enabled = uiState.reminderEnabled,
                    time = uiState.reminderTime,
                    onToggle = { enabled ->
                        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            val hasPermission = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) == PackageManager.PERMISSION_GRANTED

                            if (hasPermission) {
                                profileViewModel.onReminderToggled(true, context)
                            } else {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        } else {
                            profileViewModel.onReminderToggled(enabled, context)
                        }
                    },
                    onTimeSelected = { h, m -> profileViewModel.onReminderTimeChanged(h, m, context) }
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                ThemeSwitcher(isDarkTheme = themeViewModel.isDarkTheme, onToggle = { themeViewModel.setDarkMode(it) })
            }
        }

        Spacer(Modifier.height(16.dp))

        PrivacyStatusCard(
            privacyAccepted = uiState.privacyAccepted,
            onOpenDocument = { openLegalDocument = it }
        )

        Spacer(Modifier.height(16.dp))

        SimuladoHistoryShortcutCard(
            historyCount = uiState.simuladoHistory.size,
            latestGrade = uiState.simuladoHistory.firstOrNull()?.finalGrade,
            onOpenHistory = { showHistoryScreen = true }
        )

        Spacer(Modifier.height(16.dp))

        BizurometroShortcutCard(
            notesCount = ProgressRepository.getAllQuestionNotes().size,
            onOpenBizurometro = { showBizurometroScreen = true }
        )

        Spacer(Modifier.height(24.dp))

        if (uiState.stats.isNotEmpty()) {
            Text("Desempenho por matéria", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            StatsRadarChart(stats = uiState.stats)
        }

        Spacer(Modifier.height(24.dp))

        OutlinedButton(
            onClick = { showAboutScreen = true },
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text("Sobre o app")
        }
    }
}

@Composable
private fun AccountAccessCard(
    isGoogleSignedIn: Boolean,
    displayName: String,
    email: String,
    rankingPoints: Int,
    onSignIn: () -> Unit,
    onSignOut: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Conta Google", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (isGoogleSignedIn) {
                Text(text = displayName.ifBlank { "Conta conectada" }, fontWeight = FontWeight.SemiBold)
                if (email.isNotBlank()) {
                    Text(text = email, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ranking salvo: ${formatPointsCompact(rankingPoints)} pts")
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onSignOut) {
                    Text("Sair da conta")
                }
            } else {
                Text("Entre com Google para sincronizar ranking e compras premium.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onSignIn) {
                    Text("Entrar com Google")
                }
            }
        }
    }
}

@Composable
private fun PremiumCard(
    premiumActive: Boolean,
    priceLabel: String,
    billingReady: Boolean,
    onBuy: () -> Unit,
    onRestore: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("🚀 PLANO PREMIUM CHEGANDO!", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            if (premiumActive) {
                Text("Premium ativo nesta conta.", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            } else {
                Text("Sem anúncio")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onBuy, enabled = billingReady) {
                    Text("Comprar premium")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(onClick = onRestore, enabled = billingReady) {
                Text("Restaurar compra")
            }
        }
    }
}

@Composable
private fun PrivacyStatusCard(
    privacyAccepted: Boolean,
    onOpenDocument: (LegalDocumentType) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Política de Privacidade e Termos de Uso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                if (privacyAccepted) {
                    "Consentimento salvo para o uso dos recursos do app."
                } else {
                    "Consentimento ainda não salvo."
                }
            )
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = { onOpenDocument(LegalDocumentType.TERMS) }) {
                    Text("Termos de Uso")
                }
                Text(
                    text = "|",
                    color = MaterialTheme.colorScheme.outline
                )
                TextButton(onClick = { onOpenDocument(LegalDocumentType.PRIVACY) }) {
                    Text("Política de Privacidade")
                }
            }
        }
    }
}

@Composable
private fun SimuladoHistoryShortcutCard(
    historyCount: Int,
    latestGrade: Float?,
    onOpenHistory: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Histórico de provas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenHistory) {
                Text("Abrir histórico")
            }
        }
    }
}

@Composable
private fun BizurometroShortcutCard(
    notesCount: Int,
    onOpenBizurometro: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Bizurômetro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (notesCount > 0) {
                    "$notesCount bizu(s) salvo(s) para revisar."
                } else {
                    "Seus bizus das questões vão aparecer aqui."
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenBizurometro) {
                Text("Abrir Bizurômetro")
            }
        }
    }
}

@Composable
private fun SimuladoHistoryScreen(
    history: List<SimuladoHistoryEntry>,
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text("Histórico de provas", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(8.dp))
        if (history.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Você ainda não concluiu provas neste aparelho.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(history) { entry ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = entry.title.ifBlank { "${entry.exam.uppercase()} ${entry.year}" },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Nota final: ${formatProfileGrade(entry.finalGrade)} | acertos: ${entry.correctCount}/${entry.totalQuestions}",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Concluido em ${entry.completedAt}",
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            if (entry.durationSeconds > 0) {
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Tempo decorrido: ${formatTime(entry.durationSeconds)}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BizurometroScreen(
    onBack: () -> Unit
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
                compareBy<Triple<String, com.guima.esa.data.Question?, String>> {
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
private fun AboutAppScreen(
    onBack: () -> Unit
) {
    BackHandler(onBack = onBack)
    val highlightedCommunity = remember {
        baseVersionTesters
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
            }
            Text("Sobre", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(
                    text = "Questoes ESA/EEAR",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.ExtraBold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Plataforma de estudo criada para deixar a preparação mais direta, organizada e competitiva para quem busca ESA e EEAR.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text("Grupo Rotina Papiro", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(14.dp))
                DeveloperRow(name = "Sipriano")
                Spacer(modifier = Modifier.height(10.dp))
                DeveloperRow(name = "Estevão")
                Spacer(modifier = Modifier.height(10.dp))
                DeveloperRow(name = "Guima")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        val testersAccent = animatedRgbAccent()

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
            ),
            border = BorderStroke(1.6.dp, testersAccent.copy(alpha = 0.95f))
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    testersAccent.copy(alpha = 0.35f),
                                    testersAccent,
                                    testersAccent.copy(alpha = 0.35f)
                                )
                            )
                        )
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Testadores da versão 1.0",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = testersAccent
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A todos que dedicaram tempo para testar a versão base do app, registrar falhas, validar ajustes e acompanhar a evolução do projeto: nosso muito obrigado. Esta versão ficou mais estável, mais clara e mais confiável por causa da ajuda de vocês.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(14.dp))

                highlightedCommunity.forEach { nickname ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Text(
                            text = nickname,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeveloperRow(name: String) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Text(
            text = name,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            fontWeight = FontWeight.SemiBold
        )
    }
}

private fun formatProfileGrade(value: Float): String {
    return String.format(Locale.US, "%.1f", value).replace('.', ',')
}

private fun extractExamLabel(uniqueId: String): String {
    val parts = uniqueId.split("/")
    return if (parts.size >= 2) {
        "${parts[0].uppercase()} ${parts[1]}"
    } else {
        uniqueId
    }
}

@Composable
fun AvatarImage(avatarId: Int, size: androidx.compose.ui.unit.Dp) {
    val context = LocalContext.current
    Image(
        painter = rememberAsyncImagePainter(AvatarAssetResolver.getAvatarAssetPath(context, avatarId)),
        contentDescription = null,
        modifier = Modifier.size(size),
        contentScale = ContentScale.Crop
    )
}

@Composable
fun ThemeSwitcher(isDarkTheme: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (isDarkTheme) Icons.Default.Brightness4 else Icons.Default.Brightness7,
            contentDescription = "Theme icon",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(text = "Modo escuro", fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.weight(1f))
        Switch(checked = isDarkTheme, onCheckedChange = onToggle)
    }
}

@Composable
fun ProfileImage(avatarId: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .size(120.dp)
            .clickable { onClick() }
            .border(2.dp, MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = rememberAsyncImagePainter(AvatarAssetResolver.getAvatarAssetPath(context, avatarId)),
            contentDescription = "Foto de perfil",
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .padding(4.dp)
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun EditableNickname(nickname: String, onDone: (String) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    val maxChars = 9
    var text by remember(nickname) {
        mutableStateOf(if (nickname.length > maxChars) nickname.take(maxChars) else nickname)
    }

    if (isEditing) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                if (it.length <= maxChars) {
                    text = it
                }
            },
            label = { Text("Apelido") },
            singleLine = true,
            supportingText = {
                Text(
                    text = "${text.length} / $maxChars",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.End,
                    color = if (text.length >= maxChars) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            trailingIcon = {
                IconButton(onClick = {
                    onDone(text)
                    isEditing = false
                }) {
                    Icon(Icons.Default.Done, contentDescription = "Salvar", tint = MaterialTheme.colorScheme.primary)
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
    } else {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { isEditing = true }
        ) {
            Text(
                text = nickname,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.Default.Edit,
                contentDescription = "Editar apelido",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DailyGoalSetter(goal: Int, onGoalChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Checklist, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text("Meta diaria", fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { onGoalChange(goal - 1) }) { Icon(Icons.Default.Remove, "Diminuir") }
        Text("$goal", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        IconButton(onClick = { onGoalChange(goal + 1) }) { Icon(Icons.Default.Add, "Aumentar") }
    }
}

@Composable
fun DailyStudyGoalSetter(minutes: Int, onMinutesChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Timer, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text("Meta de estudo", fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { onMinutesChange(minutes - 5) }) { Icon(Icons.Default.Remove, "Diminuir") }
        Text("${minutes} min", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        IconButton(onClick = { onMinutesChange(minutes + 5) }) { Icon(Icons.Default.Add, "Aumentar") }
    }
}

@Composable
fun ReminderSwitch(enabled: Boolean, time: String, onToggle: (Boolean) -> Unit, onTimeSelected: (Int, Int) -> Unit) {
    val context = LocalContext.current
    val (initialHour, initialMinute) = remember(time) { ReminderScheduler.parseTime(time) }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Notifications, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Text("Lembrete diario", fontWeight = FontWeight.Medium)
        Spacer(Modifier.weight(1f))
        if (enabled) {
            Text(
                text = time,
                modifier = Modifier.clickable {
                    TimePickerDialog(
                        context,
                        { _, hour, minute -> onTimeSelected(hour, minute) },
                        initialHour,
                        initialMinute,
                        true
                    ).show()
                },
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked = enabled, onCheckedChange = onToggle)
    }
}

@Composable
fun SargentometroDateSetting(
    selectedDate: String,
    onDateSelected: (Int, Int, Int) -> Unit
) {
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("dd/MM/yyyy") }
    val targetDate = remember(selectedDate) {
        runCatching { LocalDate.parse(selectedDate) }.getOrElse { LocalDate.of(2026, 9, 15) }
    }
    val daysRemaining = remember(targetDate) {
        ChronoUnit.DAYS.between(LocalDate.now(), targetDate).coerceAtLeast(0L)
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.DateRange, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(16.dp))
        Column {
            Text("Data do Sargentômetro", fontWeight = FontWeight.Medium)
            Text(
                text = "$daysRemaining dias restantes",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = targetDate.format(formatter),
            modifier = Modifier.clickable {
                DatePickerDialog(
                    context,
                    { _, year, month, dayOfMonth ->
                        onDateSelected(year, month, dayOfMonth)
                    },
                    targetDate.year,
                    targetDate.monthValue - 1,
                    targetDate.dayOfMonth
                ).show()
            },
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun StatsRadarChart(stats: Map<String, Float>) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val isLight = MaterialTheme.colorScheme.isLight
    val textColor = if (isLight) android.graphics.Color.BLACK else android.graphics.Color.WHITE

    AndroidView(
        factory = { context -> RadarChart(context) },
        modifier = Modifier.fillMaxWidth().height(300.dp),
        update = { chart ->
            val entries = stats.values.map { RadarEntry(it) }
            val labels = stats.keys.toList()

            val dataSet = RadarDataSet(entries, "Desempenho").apply {
                color = primaryColor.toArgb()
                fillColor = primaryColor.copy(alpha = 0.2f).toArgb()
                setDrawFilled(true)
                setDrawValues(false)
            }

            chart.data = RadarData(dataSet)
            chart.xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(labels)
                this.textColor = textColor
            }
            chart.yAxis.apply {
                axisMinimum = 0f
                axisMaximum = 100f
                setDrawLabels(false)
                setDrawAxisLine(false)
            }
            chart.description.isEnabled = false
            chart.legend.isEnabled = false
            chart.invalidate()
        }
    )
}

@Composable
private fun animatedRgbAccent(): Color {
    val transition = rememberInfiniteTransition(label = "testersRgbAccent")
    val accent by transition.animateColor(
        initialValue = Color(0xFFFF4D4D),
        targetValue = Color(0xFFFF4D4D),
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 4800
                Color(0xFFFF4D4D) at 0 using LinearEasing
                Color(0xFFFFC14D) at 800 using LinearEasing
                Color(0xFF8BFF4D) at 1600 using LinearEasing
                Color(0xFF4DFFF3) at 2400 using LinearEasing
                Color(0xFF4D8DFF) at 3200 using LinearEasing
                Color(0xFFD04DFF) at 4000 using LinearEasing
                Color(0xFFFF4D4D) at 4800 using LinearEasing
            }
        ),
        label = "testersRgbColor"
    )
    return accent
}

val androidx.compose.material3.ColorScheme.isLight get() = ColorUtils.calculateLuminance(this.background.toArgb()) > 0.5f
