package com.guima.esa

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Leaderboard
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.guima.esa.cluster.ClusterWorkScheduler
import com.guima.esa.data.BillingRepository
import com.guima.esa.data.CloudSyncRepository
import com.guima.esa.data.GoogleLoginResult
import com.guima.esa.data.ProgressRepository
import com.guima.esa.data.QuestionRepository
import com.guima.esa.data.SessionClaimResult
import com.guima.esa.data.SimuladoHistoryRepository
import com.guima.esa.data.UserRepository
import com.guima.esa.reminder.ReminderScheduler
import com.guima.esa.ui.CentralScreen
import com.guima.esa.ui.CentralTabIcon
import com.guima.esa.ui.FirstAccessLoginScreen
import com.guima.esa.ui.HomeScreen
import com.guima.esa.ui.ProfileScreen
import com.guima.esa.ui.ProgressoScreen
import com.guima.esa.ui.RankScreen
import com.guima.esa.ui.SimuladoSetupScreen
import com.guima.esa.ui.components.TopBannerAd
import com.guima.esa.ui.dailyMotivationalPhrases
import com.guima.esa.ui.theme.EsaeearTheme
import com.guima.esa.ui.theme.ThemeViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

private const val PLAY_STORE_REVIEW_PROMPT_DELAY_MS = 5 * 60 * 1000L

class MainActivity : ComponentActivity() {
    private val themeViewModel: ThemeViewModel by viewModels()
    private var keepSplashVisible = true
    private var presenceHeartbeatJob: Job? = null
    private var reviewPromptJob: Job? = null
    private var hasAcceptedPrivacyState by mutableStateOf(false)
    private var isGoogleLoggedInState by mutableStateOf(false)
    private var sessionVersionState by mutableIntStateOf(0)
    private var showStartupSplashState by mutableStateOf(true)
    private var isBootstrappingState by mutableStateOf(true)
    private var loginNoticeState by mutableStateOf<String?>(null)
    private var isHandlingRemoteSession by mutableStateOf(false)
    private var showPlayStoreReviewPromptState by mutableStateOf(false)
    private var reviewPromptStartedAtMs: Long? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        splashScreen.setKeepOnScreenCondition { keepSplashVisible }

        requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        configureImmersiveMode()
        keepSplashVisible = false

        FirebaseApp.initializeApp(this)
        QuestionRepository.initialize(assets)
        ProgressRepository.initialize(this)
        SimuladoHistoryRepository.initialize(this)
        UserRepository.initialize(this)
        UserRepository.setClusterEnabled(false)
        BillingRepository.initialize(applicationContext)
        ReminderScheduler.sync(this, UserRepository.isReminderEnabled(), UserRepository.getReminderTime())
        ClusterWorkScheduler.sync(this, false)
        themeViewModel.hydrateFromStorage(UserRepository.isDarkMode())
        hasAcceptedPrivacyState = UserRepository.hasAcceptedPrivacy()
        loginNoticeState = UserRepository.consumePendingSessionNotice().ifBlank { null }

        MobileAds.initialize(this) { }

        val auth = FirebaseAuth.getInstance()
        val lastGoogleAccount = GoogleSignIn.getLastSignedInAccount(this)
        val authenticatedGoogleUid = auth.currentUser?.takeIf { !it.isAnonymous }?.uid
        if (lastGoogleAccount != null && authenticatedGoogleUid != null) {
            UserRepository.saveGoogleAccount(
                accountId = authenticatedGoogleUid,
                email = lastGoogleAccount.email,
                displayName = lastGoogleAccount.displayName,
                photoUrl = lastGoogleAccount.photoUrl?.toString()
            )
            UserRepository.saveCloudUserId(authenticatedGoogleUid)
            BillingRepository.onGoogleAccountChanged()
        }

        val restoreCloudState = {
            lifecycleScope.launch {
                if (UserRepository.isGoogleSignedIn()) {
                    restoreExistingGoogleSession(lastGoogleAccount)
                } else {
                    CloudSyncRepository.restoreProfileFromCloud()
                    isGoogleLoggedInState = false
                }
                themeViewModel.hydrateFromStorage(UserRepository.isDarkMode())
                isBootstrappingState = false
            }
        }

        if (auth.currentUser == null) {
            auth.signInAnonymously()
                .addOnSuccessListener {
                    Log.d("AUTH", "Usuario autenticado com UID: ${it.user?.uid}")
                    restoreCloudState()
                }
                .addOnFailureListener {
                    Log.e("AUTH", "Falha no login anonimo", it)
                    isBootstrappingState = false
                }
        } else {
            restoreCloudState()
        }

        enableEdgeToEdge()
        setContent {
            EsaeearTheme(darkTheme = themeViewModel.isDarkTheme) {
                LaunchedEffect(Unit) {
                    delay(1500)
                    showStartupSplashState = false
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    if (isGoogleLoggedInState) {
                        EsaeearApp(
                            themeViewModel = themeViewModel,
                            sessionVersion = sessionVersionState,
                            onLoggedOut = {
                                CloudSyncRepository.stopSessionMonitor()
                                UserRepository.stopStudySession()
                                stopPresenceHeartbeat(markOffline = false)
                                stopPlayStoreReviewPromptTimer()
                                showPlayStoreReviewPromptState = false
                                loginNoticeState = null
                                isGoogleLoggedInState = false
                                sessionVersionState += 1
                            }
                        )
                    } else {
                        FirstAccessLoginScreen(
                            privacyAccepted = hasAcceptedPrivacyState,
                            onPrivacyAccepted = {
                                UserRepository.acceptPrivacy()
                                hasAcceptedPrivacyState = true
                            },
                            onLoggedIn = {
                                isGoogleLoggedInState = true
                                loginNoticeState = null
                                sessionVersionState += 1
                                themeViewModel.hydrateFromStorage(UserRepository.isDarkMode())
                                startPresenceHeartbeat()
                                startSessionMonitor()
                                lifecycleScope.launch {
                                    CloudSyncRepository.safeSyncCurrentUser()
                                }
                                startPlayStoreReviewPromptTimer()
                            },
                            noticeMessage = loginNoticeState,
                            onNoticeDismissed = { loginNoticeState = null },
                            performLogin = { account, forceTakeover ->
                                loginWithGoogleAccount(account, forceTakeover)
                            }
                        )
                    }

                    if (showStartupSplashState || isBootstrappingState) {
                        StartupSplashOverlay()
                    }

                    if (isGoogleLoggedInState && showPlayStoreReviewPromptState) {
                        PlayStoreReviewBanner(
                            onReview = {
                                showPlayStoreReviewPromptState = false
                                openPlayStoreReviewPage()
                            },
                            onDismiss = {
                                showPlayStoreReviewPromptState = false
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            configureImmersiveMode()
        }
    }

    override fun onResume() {
        super.onResume()
        startPresenceHeartbeat()
        refreshCurrentGoogleRankingState()
        startPlayStoreReviewPromptTimer()
    }

    override fun onPause() {
        UserRepository.stopStudySession()
        stopPresenceHeartbeat(markOffline = true)
        stopPlayStoreReviewPromptTimer()
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) {
            UserRepository.stopStudySession()
        }
        stopPresenceHeartbeat(markOffline = isFinishing)
        stopPlayStoreReviewPromptTimer()
        super.onDestroy()
    }

    private fun configureImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    private fun startPresenceHeartbeat() {
        if (!isGoogleLoggedInState || !UserRepository.isGoogleSignedIn() || UserRepository.getActiveSessionId().isBlank()) {
            return
        }

        UserRepository.startStudySession()
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = lifecycleScope.launch {
            if (!CloudSyncRepository.isCurrentSessionOwner()) {
                handleRemoteSessionTakeover(
                    "Este app esta sendo usado em outro dispositivo. Somente uma sessao por Gmail e permitida."
                )
                return@launch
            }

            if (!CloudSyncRepository.safeUpdatePresence(true)) {
                handleRemoteSessionTakeover(
                    "Este app esta sendo usado em outro dispositivo. Somente uma sessao por Gmail e permitida."
                )
                return@launch
            }

            while (true) {
                delay(30_000)
                if (!CloudSyncRepository.safeUpdatePresence(true)) {
                    handleRemoteSessionTakeover(
                        "Este app esta sendo usado em outro dispositivo. Somente uma sessao por Gmail e permitida."
                    )
                    break
                }
            }
        }
    }

    private fun stopPresenceHeartbeat(markOffline: Boolean) {
        presenceHeartbeatJob?.cancel()
        presenceHeartbeatJob = null

        if (markOffline && UserRepository.getActiveSessionId().isNotBlank()) {
            lifecycleScope.launch {
                CloudSyncRepository.safeUpdatePresence(false)
            }
        }
    }

    private fun startSessionMonitor() {
        CloudSyncRepository.startSessionMonitor { message ->
            lifecycleScope.launch {
                handleRemoteSessionTakeover(message)
            }
        }
    }

    private fun refreshCurrentGoogleRankingState() {
        if (!isGoogleLoggedInState || !UserRepository.isGoogleSignedIn()) {
            return
        }

        lifecycleScope.launch {
            CloudSyncRepository.safeSyncCurrentUser()
        }
    }

    private fun startPlayStoreReviewPromptTimer() {
        if (!isGoogleLoggedInState || UserRepository.hasShownPlayStoreReviewPrompt()) {
            return
        }
        if (showPlayStoreReviewPromptState) {
            return
        }

        reviewPromptStartedAtMs = System.currentTimeMillis()
        reviewPromptJob?.cancel()

        val remainingMs = PLAY_STORE_REVIEW_PROMPT_DELAY_MS - UserRepository.getPlayStoreReviewUsageMs()
        if (remainingMs <= 0L) {
            showPlayStoreReviewPrompt()
            return
        }

        reviewPromptJob = lifecycleScope.launch {
            delay(remainingMs)
            showPlayStoreReviewPrompt()
        }
    }

    private fun stopPlayStoreReviewPromptTimer() {
        reviewPromptJob?.cancel()
        reviewPromptJob = null

        val startedAt = reviewPromptStartedAtMs ?: return
        reviewPromptStartedAtMs = null
        if (!UserRepository.hasShownPlayStoreReviewPrompt()) {
            UserRepository.addPlayStoreReviewUsageMs(
                (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
            )
        }
    }

    private fun showPlayStoreReviewPrompt() {
        if (UserRepository.hasShownPlayStoreReviewPrompt()) {
            return
        }
        reviewPromptJob?.cancel()
        reviewPromptJob = null
        reviewPromptStartedAtMs = null
        UserRepository.markPlayStoreReviewPromptShown()
        showPlayStoreReviewPromptState = true
    }

    private fun openPlayStoreReviewPage() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=$packageName")
        ).apply {
            setPackage("com.android.vending")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        try {
            startActivity(marketIntent)
        } catch (_: ActivityNotFoundException) {
            startActivity(webIntent)
        }
    }

    private suspend fun restoreExistingGoogleSession(account: GoogleSignInAccount?) {
        when (val claimResult = CloudSyncRepository.claimCurrentGoogleSession(forceTakeover = false)) {
            is SessionClaimResult.Claimed -> {
                val firebaseUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                UserRepository.saveGoogleAccount(
                    accountId = firebaseUid,
                    email = account?.email,
                    displayName = account?.displayName,
                    photoUrl = account?.photoUrl?.toString()
                )
                UserRepository.saveCloudUserId(firebaseUid)
                CloudSyncRepository.mergeGoogleLoginProfile()
                BillingRepository.onGoogleAccountChanged()
                isGoogleLoggedInState = true
                startSessionMonitor()
                startPresenceHeartbeat()
                startPlayStoreReviewPromptTimer()
                CloudSyncRepository.safeSyncCurrentUser()
            }
            SessionClaimResult.NeedsTakeover -> {
                performLocalGoogleLogout(noticeMessage = "Sua conta Google ja esta sendo usada em outro dispositivo. Entre novamente e toque em Assumir sessao para continuar aqui.")
            }
            is SessionClaimResult.Error -> {
                performLocalGoogleLogout(noticeMessage = claimResult.message)
            }
        }
    }

    private suspend fun handleRemoteSessionTakeover(message: String) {
        if (isHandlingRemoteSession) {
            return
        }

        isHandlingRemoteSession = true
        try {
            UserRepository.savePendingSessionNotice(message)
            performLocalGoogleLogout(noticeMessage = UserRepository.consumePendingSessionNotice())
        } finally {
            isHandlingRemoteSession = false
        }
    }

    private suspend fun performLocalGoogleLogout(noticeMessage: String? = null) {
        CloudSyncRepository.safeReleaseCurrentSession()
        UserRepository.stopStudySession()
        stopPresenceHeartbeat(markOffline = false)
        FirebaseAuth.getInstance().signOut()
        GoogleSignIn.getClient(
            this,
            GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestIdToken(getString(R.string.default_web_client_id))
                .requestEmail()
                .build()
        ).signOut()
        UserRepository.clearGoogleAccount()
        BillingRepository.onGoogleAccountChanged()
        loginNoticeState = noticeMessage
        isGoogleLoggedInState = false
        sessionVersionState += 1
    }
}

@Composable
private fun StartupSplashOverlay() {
    val transition = rememberInfiniteTransition(label = "startupScan")
    val logoScale by transition.animateFloat(
        initialValue = 0.985f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "logoScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        Color(0xFF13345C),
                        Color(0xFF081729),
                        Color(0xFF050D19)
                    ),
                    radius = 1500f
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.74f)
                .aspectRatio(1f)
                .sizeIn(maxWidth = 340.dp, maxHeight = 340.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val glowRadius = size.minDimension * 0.54f * logoScale
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0x44D6A11F),
                            Color(0x18D6A11F),
                            Color.Transparent
                        ),
                        center = center,
                        radius = glowRadius
                    ),
                    radius = glowRadius,
                    center = center
                )
            }

            Image(
                painter = painterResource(id = R.drawable.splash_logo),
                contentDescription = null,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = logoScale
                        scaleY = logoScale
                },
                contentScale = ContentScale.Fit
            )
        }
    }
}

private suspend fun loginWithGoogleAccount(
    account: GoogleSignInAccount,
    forceTakeover: Boolean
): GoogleLoginResult {
    val idToken = account.idToken
    if (idToken.isNullOrBlank()) {
        Log.e("AUTH", "Google Sign-In retornou sem idToken; verifique o cliente Web OAuth do Firebase.")
        return GoogleLoginResult.Error(
            "Login Google sem token do Firebase. Baixe o google-services.json com o cliente Web OAuth e tente novamente."
        )
    }

    val firebaseUser = try {
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        FirebaseAuth.getInstance().signInWithCredential(credential).await().user
    } catch (e: Exception) {
        Log.e("AUTH", "Google selecionado, mas Firebase Auth por token falhou.", e)
        null
    } ?: return GoogleLoginResult.Error(
        "Nao foi possivel autenticar no Firebase. Confira o cliente Web OAuth e as SHA-1 cadastradas."
    )

    return when (val claimResult = CloudSyncRepository.claimCurrentGoogleSession(forceTakeover = forceTakeover)) {
        is SessionClaimResult.Claimed -> {
            UserRepository.saveGoogleAccount(
                accountId = firebaseUser.uid,
                email = account.email,
                displayName = account.displayName,
                photoUrl = account.photoUrl?.toString()
            )
            UserRepository.saveCloudUserId(firebaseUser.uid)
            try {
                CloudSyncRepository.mergeGoogleLoginProfile()
                BillingRepository.onGoogleAccountChanged()
                GoogleLoginResult.Success
            } catch (e: Exception) {
                Log.e("AUTH", "Falha ao restaurar perfil apos assumir a sessao.", e)
                CloudSyncRepository.safeReleaseCurrentSession()
                FirebaseAuth.getInstance().signOut()
                UserRepository.clearGoogleAccount()
                BillingRepository.onGoogleAccountChanged()
                GoogleLoginResult.Error("Nao foi possivel concluir a entrada. Tente novamente.")
            }
        }
        SessionClaimResult.NeedsTakeover -> {
            CloudSyncRepository.safeReleaseCurrentSession()
            FirebaseAuth.getInstance().signOut()
            GoogleLoginResult.RequiresTakeover
        }
        is SessionClaimResult.Error -> {
            CloudSyncRepository.safeReleaseCurrentSession()
            FirebaseAuth.getInstance().signOut()
            GoogleLoginResult.Error(claimResult.message)
        }
    }
}

@Composable
fun EsaeearApp(
    themeViewModel: ThemeViewModel,
    sessionVersion: Int,
    onLoggedOut: () -> Unit
) {
    val todayKey = remember {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val bottomBarDestinations = remember {
        listOf(
            AppDestinations.HOME,
            AppDestinations.SIMULADO,
            AppDestinations.CENTRAL,
            AppDestinations.RANK,
            AppDestinations.PROFILE
        )
    }
    var currentDestination by rememberSaveable(sessionVersion) { mutableStateOf(AppDestinations.HOME) }
    var hasActiveExam by rememberSaveable(sessionVersion) { mutableStateOf(false) }
    var pendingDestination by remember { mutableStateOf<AppDestinations?>(null) }
    var showExitExamDialog by remember { mutableStateOf(false) }
    var dailyMotivationPhrase by rememberSaveable(sessionVersion, todayKey) { mutableStateOf<String?>(null) }
    val billingUiState by BillingRepository.uiState.collectAsState()

    fun navigateTo(destination: AppDestinations) {
        if (destination == currentDestination) return

        if (hasActiveExam) {
            pendingDestination = destination
            showExitExamDialog = true
        } else {
            currentDestination = destination
        }
    }

    LaunchedEffect(sessionVersion, todayKey) {
        dailyMotivationPhrase = if (dailyMotivationalPhrases.isNotEmpty()) {
            val phraseIndex = UserRepository.getDailyMotivationIndex(
                todayKey = todayKey,
                totalPhrases = dailyMotivationalPhrases.size
            )
            dailyMotivationalPhrases[phraseIndex]
        } else {
            null
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            bottomBarDestinations.forEach {
                item(
                    icon = {
                        if (it == AppDestinations.CENTRAL) {
                            CentralTabIcon(
                                modifier = Modifier.sizeIn(maxWidth = 42.dp, maxHeight = 42.dp),
                                contentDescription = it.label
                            )
                        } else {
                            Icon(
                                imageVector = checkNotNull(it.icon),
                                contentDescription = it.label
                            )
                        }
                    },
                    label = {
                        if (it != AppDestinations.CENTRAL) {
                            Text(it.label)
                        }
                    },
                    selected = it == currentDestination,
                    onClick = { navigateTo(it) }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (!billingUiState.isPremium) {
                    TopBannerAd(modifier = Modifier.fillMaxWidth())
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (currentDestination) {
                        AppDestinations.HOME -> HomeScreen(
                            onNavigate = { navigateTo(it) },
                            dailyMotivationPhrase = dailyMotivationPhrase,
                            onActiveExamChange = { hasActiveExam = it }
                        )
                        AppDestinations.SIMULADO -> SimuladoSetupScreen(
                            onActiveExamChange = { hasActiveExam = it }
                        )
                        AppDestinations.CENTRAL -> CentralScreen()
                        AppDestinations.RANK -> RankScreen()
                        AppDestinations.PROGRESSO -> ProgressoScreen(
                            onBack = { currentDestination = AppDestinations.HOME }
                        )
                        AppDestinations.PROFILE -> ProfileScreen(
                            sessionVersion = sessionVersion,
                            themeViewModel = themeViewModel,
                            onSignedOut = onLoggedOut
                        )
                    }
                }
            }
        }

        if (showExitExamDialog) {
            AlertDialog(
                onDismissRequest = {
                    showExitExamDialog = false
                    pendingDestination = null
                },
                title = { Text("Sair da prova?") },
                text = { Text("Se você sair agora, seu progresso nesta prova será perdido. Deseja continuar?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            currentDestination = pendingDestination ?: currentDestination
                            hasActiveExam = false
                            pendingDestination = null
                            showExitExamDialog = false
                        }
                    ) {
                        Text("Sair")
                    }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            showExitExamDialog = false
                            pendingDestination = null
                        }
                    ) {
                        Text("Cancelar")
                    }
                }
            )
        }
    }
}

enum class AppDestinations(val label: String, val icon: ImageVector?) {
    HOME("Home", Icons.Default.Home),
    SIMULADO("Provas", Icons.Default.Quiz),
    CENTRAL("Central", null),
    PROGRESSO("Progresso", Icons.AutoMirrored.Filled.TrendingUp),
    RANK("Ranking", Icons.Default.Leaderboard),
    PROFILE("Perfil", Icons.Default.AccountBox),
}

@Composable
private fun LegacyPlayStoreReviewBanner(
    onReview: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(2.dp, Color(0xFFD6A11F))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Está gostando do app?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Avalie com estrelas na Play Store. Isso ajuda muito o app a crescer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Agora não")
                    }
                    Button(onClick = onReview) {
                        Text("Avaliar")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayStoreReviewBanner(
    onReview: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            border = BorderStroke(2.dp, Color(0xFFD6A11F))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Está gostando do app?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Avalie com estrelas na Play Store. Isso ajuda muito o app a crescer.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        border = BorderStroke(1.5.dp, Color(0xFFD6A11F))
                    ) {
                        Text("Nunca")
                    }
                    Button(onClick = onReview) {
                        Text("Avaliar")
                    }
                }
            }
        }
    }
}

