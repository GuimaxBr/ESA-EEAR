package com.guima.esa.ui

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.guima.esa.data.ApiService
import com.guima.esa.data.BillingRepository
import com.guima.esa.data.CloudSyncRepository
import com.guima.esa.data.ProgressRepository
import com.guima.esa.data.QuestionRepository
import com.guima.esa.data.RankRepository
import com.guima.esa.data.SimuladoHistoryEntry
import com.guima.esa.data.SimuladoHistoryRepository
import com.guima.esa.data.UserRepository
import com.guima.esa.reminder.ReminderScheduler
import com.guima.esa.util.AvatarAssetResolver
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProfileUiState(
    val nickname: String = "Estudante",
    val avatarId: Int = 1,
    val availableAvatars: List<Int> = emptyList(),
    val dailyGoal: Int = 20,
    val dailyStudyGoalMinutes: Int = 20,
    val sargentometroTargetDate: String = "2026-09-15",
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "20:00",
    val stats: Map<String, Float> = emptyMap(),
    val isGoogleSignedIn: Boolean = false,
    val googleDisplayName: String = "",
    val googleEmail: String = "",
    val premiumActive: Boolean = false,
    val premiumPrice: String = "R$ 5,00",
    val billingReady: Boolean = false,
    val rankingPoints: Int = 0,
    val privacyAccepted: Boolean = false,
    val simuladoHistory: List<SimuladoHistoryEntry> = emptyList()
)

class ProfileViewModel : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()
    private val rankRepository = RankRepository()

    init {
        loadProfileData()
        loadStats()
        observeBillingState()
    }

    private fun observeBillingState() {
        viewModelScope.launch {
            BillingRepository.uiState.collect { billingState ->
                _uiState.update {
                    it.copy(
                        premiumActive = billingState.isPremium,
                        premiumPrice = billingState.priceLabel,
                        billingReady = billingState.isReady
                    )
                }
            }
        }
    }

    private fun loadProfileData() {
        _uiState.update {
            it.copy(
                nickname = UserRepository.getNickname(),
                avatarId = UserRepository.getAvatarId(),
                dailyGoal = UserRepository.getDailyGoal(),
                dailyStudyGoalMinutes = UserRepository.getDailyStudyGoalMinutes(),
                sargentometroTargetDate = UserRepository.getSargentometroTargetDate(),
                reminderEnabled = UserRepository.isReminderEnabled(),
                reminderTime = UserRepository.getReminderTime(),
                isGoogleSignedIn = UserRepository.isGoogleSignedIn(),
                googleDisplayName = UserRepository.getGoogleDisplayName(),
                googleEmail = UserRepository.getGoogleEmail(),
                premiumActive = UserRepository.isPremium(),
                rankingPoints = UserRepository.getRankPoints(),
                privacyAccepted = UserRepository.hasAcceptedPrivacy(),
                simuladoHistory = SimuladoHistoryRepository.getHistory()
            )
        }
    }

    fun refreshLocalProfileState() {
        loadProfileData()
    }

    fun loadAvailableAvatars(context: Context) {
        viewModelScope.launch {
            val avatars = AvatarAssetResolver.getAvailableAvatarIds(context)
            val premiumActive = UserRepository.isPremium()
            val sanitizedAvatarId = AvatarAssetResolver.sanitizeAvatarId(
                avatarId = UserRepository.getAvatarId(),
                isPremium = premiumActive
            )

            if (sanitizedAvatarId != UserRepository.getAvatarId()) {
                UserRepository.saveAvatarId(sanitizedAvatarId)
                rankRepository.updateRankingData(
                    userId = UserRepository.getCloudUserId(),
                    nickname = UserRepository.getNickname(),
                    pointsToAdd = 0,
                    avatarId = sanitizedAvatarId,
                    isPremium = premiumActive
                )
                CloudSyncRepository.safeSyncCurrentUser()
            }

            _uiState.update {
                it.copy(
                    avatarId = sanitizedAvatarId,
                    availableAvatars = avatars,
                    premiumActive = premiumActive
                )
            }
        }
    }

    fun loadStats() {
        viewModelScope.launch {
            val allQuestions = QuestionRepository.getAllQuestions()
            val correctlyAnsweredIds = ProgressRepository.getCorrectlyAnsweredUniqueIds()

            val statsMap = allQuestions.groupBy { it.subject }.mapValues { (_, questions) ->
                val total = questions.size
                if (total == 0) {
                    0f
                } else {
                    val correct = questions.count { it.uniqueId in correctlyAnsweredIds }
                    (correct.toFloat() / total.toFloat()) * 100
                }
            }
            _uiState.update { it.copy(stats = statsMap) }
        }
    }

    fun onGoogleAccountSignedIn(account: GoogleSignInAccount) {
        viewModelScope.launch {
            val cloudUserId = signInFirebaseWithGoogle(account)
            if (cloudUserId == null) {
                Log.e("AUTH", "Perfil: Google selecionado, mas Firebase Auth nao autenticou.")
                return@launch
            }

            UserRepository.saveGoogleAccount(
                accountId = cloudUserId,
                email = account.email?.lowercase(),
                displayName = account.displayName,
                photoUrl = account.photoUrl?.toString()
            )
            UserRepository.saveCloudUserId(cloudUserId)
            CloudSyncRepository.onGoogleAccountSignedIn()
            BillingRepository.onGoogleAccountChanged()
            loadProfileData()
            loadStats()
        }
    }

    private suspend fun signInFirebaseWithGoogle(account: GoogleSignInAccount): String? {
        val idToken = account.idToken ?: return null
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            FirebaseAuth.getInstance().signInWithCredential(credential).await().user?.uid
        } catch (e: Exception) {
            Log.e("AUTH", "Perfil: falha ao autenticar Google no Firebase.", e)
            null
        }
    }

    fun onGoogleSignOut() {
        viewModelScope.launch {
            CloudSyncRepository.safeReleaseCurrentSession()
            FirebaseAuth.getInstance().signOut()
            UserRepository.clearGoogleAccount()
            BillingRepository.onGoogleAccountChanged()
            loadProfileData()
        }
    }

    fun onNicknameChange(newName: String) {
        _uiState.update { it.copy(nickname = newName) }
        UserRepository.saveNickname(newName)

        viewModelScope.launch {
            rankRepository.updateRankingData(
                userId = UserRepository.getCloudUserId(),
                nickname = newName,
                pointsToAdd = 0,
                avatarId = UserRepository.getAvatarId(),
                isPremium = UserRepository.isPremium()
            )
            CloudSyncRepository.safeSyncCurrentUser()
            ApiService.createUserIfNotExists(UserRepository.getUserId(), newName)
            loadProfileData()
        }
    }

    fun onDailyGoalChange(newGoal: Int) {
        val finalGoal = newGoal.coerceIn(1, 100)
        _uiState.update { it.copy(dailyGoal = finalGoal) }
        UserRepository.saveDailyGoal(finalGoal)
        viewModelScope.launch {
            CloudSyncRepository.safeSyncCurrentUser()
        }
    }

    fun onDailyStudyGoalMinutesChange(newMinutes: Int) {
        val finalMinutes = newMinutes.coerceIn(20, 240)
        _uiState.update { it.copy(dailyStudyGoalMinutes = finalMinutes) }
        UserRepository.saveDailyStudyGoalMinutes(finalMinutes)
        viewModelScope.launch {
            CloudSyncRepository.safeSyncCurrentUser()
        }
    }

    fun onSargentometroDateChanged(year: Int, month: Int, day: Int) {
        val dateString = String.format("%04d-%02d-%02d", year, month + 1, day)
        _uiState.update { it.copy(sargentometroTargetDate = dateString) }
        UserRepository.saveSargentometroTargetDate(dateString)
        viewModelScope.launch {
            CloudSyncRepository.safeSyncCurrentUser()
        }
    }

    fun onAvatarSelected(avatarId: Int) {
        if (!AvatarAssetResolver.canUseAvatar(avatarId, UserRepository.isPremium())) {
            return
        }

        _uiState.update { it.copy(avatarId = avatarId) }
        UserRepository.saveAvatarId(avatarId)

        viewModelScope.launch {
            rankRepository.updateRankingData(
                userId = UserRepository.getCloudUserId(),
                nickname = UserRepository.getNickname(),
                pointsToAdd = 0,
                avatarId = avatarId,
                isPremium = UserRepository.isPremium()
            )
            CloudSyncRepository.safeSyncCurrentUser()
            loadProfileData()
        }
    }

    fun onReminderToggled(enabled: Boolean, context: Context) {
        _uiState.update { it.copy(reminderEnabled = enabled) }
        UserRepository.saveReminderEnabled(enabled)
        ReminderScheduler.sync(context, enabled, _uiState.value.reminderTime)
        viewModelScope.launch {
            CloudSyncRepository.safeSyncCurrentUser()
        }
    }

    fun onReminderTimeChanged(hour: Int, minute: Int, context: Context) {
        val timeString = String.format("%02d:%02d", hour, minute)
        _uiState.update { it.copy(reminderTime = timeString) }
        UserRepository.saveReminderTime(timeString)
        if (_uiState.value.reminderEnabled) {
            ReminderScheduler.schedule(context, hour, minute)
        }
        viewModelScope.launch {
            CloudSyncRepository.safeSyncCurrentUser()
        }
    }

    fun onResetProgress() {
        viewModelScope.launch {
            ProgressRepository.clearProgress()
            loadStats()
        }
    }
}
