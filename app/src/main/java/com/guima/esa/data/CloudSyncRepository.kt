package com.guima.esa.data

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.guima.esa.util.AvatarAssetResolver
import java.util.UUID
import kotlinx.coroutines.tasks.await

data class CloudUserProfile(
    val nickname: String = "Estudante",
    val avatarId: Int = 1,
    val dailyGoal: Int = 20,
    val dailyStudyGoalMinutes: Int = 20,
    val sargentometroTargetDate: String = "2026-09-15",
    val reminderEnabled: Boolean = false,
    val reminderTime: String = "20:00",
    val darkMode: Boolean = false,
    val isPremium: Boolean = false,
    val premiumProductId: String = "",
    val premiumPurchaseToken: String = "",
    val premiumPurchaseTime: Long = 0L,
    val rankPoints: Int = 0,
    val googleEmail: String = "",
    val googleDisplayName: String = "",
    val googlePhotoUrl: String = "",
    val privacyAccepted: Boolean = false,
    val clusterEnabled: Boolean = false,
    val clusterConsentAt: String = "",
    val isOnline: Boolean = false,
    val lastSeenAt: Long = 0L,
    val onlineSinceAt: Long = 0L,
    val studyTimeMs: Long = 0L,
    val platform: String = ""
)

data class ActiveGoogleSession(
    val deviceId: String = "",
    val sessionId: String = "",
    val updatedAt: Long = 0L,
    val platform: String = ""
)

sealed interface SessionClaimResult {
    data class Claimed(val sessionId: String) : SessionClaimResult
    data object NeedsTakeover : SessionClaimResult
    data class Error(val message: String) : SessionClaimResult
}

object CloudSyncRepository {
    private const val USERS_COLLECTION = "users"
    private const val TAG = "FIRESTORE"
    private const val ACTIVE_SESSION_FIELD = "activeSession"
    private const val SESSION_STALE_TIMEOUT_MS = 90_000L

    private val db = FirebaseFirestore.getInstance()
    private val rankRepository = RankRepository()
    private var sessionListener: ListenerRegistration? = null

    private data class ResolvedCloudState(
        val nickname: String,
        val avatarId: Int,
        val rankPoints: Int,
        val isPremium: Boolean,
        val premiumProductId: String,
        val premiumPurchaseToken: String,
        val premiumPurchaseTime: Long
    )

    suspend fun restoreProfileFromCloud() {
        val userId = UserRepository.getAuthenticatedCloudUserId()
        if (userId == null) {
            Log.d(TAG, "Restauracao em nuvem ignorada: usuario ainda nao autenticou com Google no Firebase.")
            return
        }

        try {
            val snapshot = db.collection(USERS_COLLECTION).document(userId).get().await()
            val cloudProfile = snapshot.toObject(CloudUserProfile::class.java)

            if (cloudProfile != null) {
                applyCloudProfile(cloudProfile)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nao foi possivel restaurar o perfil em nuvem; seguindo com dados locais.", e)
        }

        refreshRankingCache()
        safeSyncCurrentUser()
    }

    suspend fun onGoogleAccountSignedIn() {
        mergeGoogleLoginProfile()
    }

    suspend fun mergeGoogleLoginProfile() {
        val userId = UserRepository.getAuthenticatedCloudUserId()
        if (userId == null) {
            Log.w(TAG, "Merge em nuvem ignorado: usuario ainda nao autenticou com Google no Firebase.")
            return
        }

        var nickname = UserRepository.getNickname()
        var avatarId = UserRepository.getAvatarId()
        var bestPoints = UserRepository.getRankPoints()
        var premiumActive = UserRepository.isPremium()
        var premiumProductId = UserRepository.getPremiumProductId()
        var premiumPurchaseToken = UserRepository.getPremiumPurchaseToken()
        var premiumPurchaseTime = UserRepository.getPremiumPurchaseTime()
        var bestStudyTimeMs = UserRepository.getTotalStudyTimeMs()

        try {
            val profileSnapshot = db.collection(USERS_COLLECTION).document(userId).get().await()
            val cloudProfile = profileSnapshot.toObject(CloudUserProfile::class.java)
            if (cloudProfile != null) {
                if (bestPoints <= 0 && cloudProfile.rankPoints > 0) {
                    bestPoints = cloudProfile.rankPoints
                } else {
                    bestPoints = maxOf(bestPoints, cloudProfile.rankPoints)
                }
                if (nickname.isBlank() || nickname == "Estudante") {
                    nickname = cloudProfile.nickname.ifBlank { nickname }
                }
                if (avatarId <= 0 || avatarId == 1) {
                    avatarId = cloudProfile.avatarId
                }
                if (!premiumActive && cloudProfile.isPremium) {
                    premiumActive = true
                    premiumProductId = cloudProfile.premiumProductId
                    premiumPurchaseToken = cloudProfile.premiumPurchaseToken
                    premiumPurchaseTime = cloudProfile.premiumPurchaseTime
                }
                bestStudyTimeMs = maxOf(bestStudyTimeMs, cloudProfile.studyTimeMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nao foi possivel ler perfil em nuvem no login Google; subindo dados locais.", e)
        }

        try {
            val cloudRanking = rankRepository.getUserRanking(userId)
            if (cloudRanking != null) {
                bestPoints = maxOf(bestPoints, cloudRanking.pontos)
                if (nickname.isBlank() || nickname == "Estudante") {
                    nickname = cloudRanking.nickname.ifBlank { nickname }
                }
                if (avatarId <= 0 || avatarId == 1) {
                    avatarId = cloudRanking.avatarId
                }
                bestStudyTimeMs = maxOf(bestStudyTimeMs, cloudRanking.studyTimeMs)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nao foi possivel ler ranking em nuvem no login Google; subindo dados locais.", e)
        }

        if (nickname.isBlank() || nickname == "Estudante") {
            nickname = UserRepository.getGoogleDisplayName().take(9).ifBlank { nickname }
        }

        UserRepository.savePremiumStatus(
            isPremium = premiumActive,
            productId = premiumProductId,
            purchaseToken = premiumPurchaseToken,
            purchaseTime = premiumPurchaseTime
        )
        avatarId = AvatarAssetResolver.sanitizeAvatarId(avatarId, premiumActive)
        UserRepository.saveNickname(nickname)
        UserRepository.saveAvatarId(avatarId)
        UserRepository.saveRankPoints(bestPoints)
        UserRepository.saveStudyTimeMs(bestStudyTimeMs)

        safeSyncCurrentUser()
    }

    suspend fun syncCurrentUser() {
        val userId = UserRepository.getAuthenticatedCloudUserId()
        if (userId == null) {
            Log.w(TAG, "Sincronizacao ignorada: login Google ainda nao autenticou no Firebase Auth.")
            return
        }

        val resolvedState = resolveCloudStateForWrite(userId)
        val sanitizedAvatarId = AvatarAssetResolver.sanitizeAvatarId(
            avatarId = resolvedState.avatarId,
            isPremium = resolvedState.isPremium
        )

        UserRepository.saveNickname(resolvedState.nickname)
        UserRepository.saveAvatarId(sanitizedAvatarId)
        UserRepository.saveRankPoints(resolvedState.rankPoints)
        UserRepository.savePremiumStatus(
            isPremium = resolvedState.isPremium,
            productId = resolvedState.premiumProductId,
            purchaseToken = resolvedState.premiumPurchaseToken,
            purchaseTime = resolvedState.premiumPurchaseTime
        )

        val currentProfile = CloudUserProfile(
            nickname = resolvedState.nickname,
            avatarId = sanitizedAvatarId,
            dailyGoal = UserRepository.getDailyGoal(),
            dailyStudyGoalMinutes = UserRepository.getDailyStudyGoalMinutes(),
            sargentometroTargetDate = UserRepository.getSargentometroTargetDate(),
            reminderEnabled = UserRepository.isReminderEnabled(),
            reminderTime = UserRepository.getReminderTime(),
            darkMode = UserRepository.isDarkMode(),
            isPremium = resolvedState.isPremium,
            premiumProductId = resolvedState.premiumProductId,
            premiumPurchaseToken = resolvedState.premiumPurchaseToken,
            premiumPurchaseTime = resolvedState.premiumPurchaseTime,
            rankPoints = resolvedState.rankPoints,
            googleEmail = UserRepository.getGoogleEmail(),
            googleDisplayName = UserRepository.getGoogleDisplayName(),
            googlePhotoUrl = UserRepository.getGooglePhotoUrl(),
            privacyAccepted = UserRepository.hasAcceptedPrivacy(),
            clusterEnabled = false,
            clusterConsentAt = "",
            isOnline = UserRepository.isPresenceOnline(),
            lastSeenAt = UserRepository.getLastSeenAt(),
            onlineSinceAt = UserRepository.getOnlineSinceAt(),
            studyTimeMs = UserRepository.getAccumulatedStudyTimeMs(),
            platform = "android"
        )

        Log.d(TAG, "Sincronizando perfil Google userId=$userId email=${UserRepository.getGoogleEmail()} pontos=${UserRepository.getRankPoints()} avatar=${UserRepository.getAvatarId()}")
        db.collection(USERS_COLLECTION).document(userId).set(currentProfile, SetOptions.merge()).await()
        val rankingSaved = rankRepository.setRankingData(
            userId = userId,
            nickname = resolvedState.nickname,
            totalPoints = resolvedState.rankPoints,
            avatarId = sanitizedAvatarId,
            isPremium = resolvedState.isPremium
        )
        if (!rankingSaved) {
            Log.w(TAG, "Perfil salvo em users, mas ranking nao foi salvo para userId=$userId.")
        }
    }

    suspend fun refreshRankingCache() {
        val userId = UserRepository.getAuthenticatedCloudUserId()
        if (userId == null) {
            Log.d(TAG, "Cache do ranking em nuvem ignorado: usuario ainda nao autenticou com Google no Firebase.")
            return
        }

        try {
            val ranking = rankRepository.getUserRanking(userId)
            if (ranking != null) {
                UserRepository.saveRankPoints(maxOf(UserRepository.getRankPoints(), ranking.pontos))
                UserRepository.saveStudyTimeMs(maxOf(UserRepository.getTotalStudyTimeMs(), ranking.studyTimeMs))
                if (ranking.nickname.isNotBlank()) {
                    UserRepository.saveNickname(ranking.nickname)
                }
                if (ranking.avatarId > 0) {
                    UserRepository.saveAvatarId(
                        AvatarAssetResolver.sanitizeAvatarId(
                            avatarId = ranking.avatarId,
                            isPremium = ranking.isPremium
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Nao foi possivel restaurar o ranking em nuvem; seguindo com cache local.", e)
        }
    }

    suspend fun safeSyncCurrentUser() {
        try {
            syncCurrentUser()
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao sincronizar perfil; dados locais foram preservados.", e)
        }
    }

    suspend fun claimCurrentGoogleSession(forceTakeover: Boolean): SessionClaimResult {
        val userId = UserRepository.getAuthenticatedCloudUserId()
            ?: return SessionClaimResult.Error("Nao foi possivel validar sua sessao Google agora. Tente novamente.")
        val now = System.currentTimeMillis()
        val deviceId = UserRepository.getLoginDeviceId()

        return try {
            val document = db.collection(USERS_COLLECTION).document(userId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(document)
                val remoteSession = readActiveSession(snapshot)
                val remoteIsOnline = snapshot.getBoolean("isOnline") == true
                val remoteIsRecent = remoteSession.sessionId.isNotBlank() &&
                    (now - remoteSession.updatedAt) <= SESSION_STALE_TIMEOUT_MS
                val anotherDeviceOwnsSession = remoteIsOnline &&
                    remoteIsRecent &&
                    remoteSession.deviceId.isNotBlank() &&
                    remoteSession.deviceId != deviceId

                if (anotherDeviceOwnsSession && !forceTakeover) {
                    SessionClaimResult.NeedsTakeover
                } else {
                    val sessionId = UUID.randomUUID().toString()
                    transaction.set(
                        document,
                        mapOf(
                            ACTIVE_SESSION_FIELD to mapOf(
                                "deviceId" to deviceId,
                                "sessionId" to sessionId,
                            "updatedAt" to now,
                            "platform" to "android"
                        ),
                        "isOnline" to true,
                        "lastSeenAt" to now,
                        "onlineSinceAt" to now,
                        "studyTimeMs" to UserRepository.getAccumulatedStudyTimeMs(now),
                        "platform" to "android"
                    ),
                    SetOptions.merge()
                )
                SessionClaimResult.Claimed(sessionId)
                }
            }.await().also { result ->
                if (result is SessionClaimResult.Claimed) {
                    UserRepository.saveActiveSessionId(result.sessionId)
                    UserRepository.resetOnlineSinceAt(now)
                    UserRepository.savePresenceState(isOnline = true, lastSeenAt = now)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao assumir sessao Google.", e)
            SessionClaimResult.Error("Nao foi possivel validar sua sessao agora. Tente novamente.")
        }
    }

    fun startSessionMonitor(onSessionTakenOver: (String) -> Unit) {
        stopSessionMonitor()

        val userId = UserRepository.getAuthenticatedCloudUserId() ?: return
        sessionListener = db.collection(USERS_COLLECTION).document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Erro ao observar a sessao ativa.", error)
                    return@addSnapshotListener
                }

                val localSessionId = UserRepository.getActiveSessionId()
                if (snapshot == null || localSessionId.isBlank()) {
                    return@addSnapshotListener
                }

                val remoteSession = readActiveSession(snapshot)
                if (remoteSession.sessionId.isNotBlank() && remoteSession.sessionId != localSessionId) {
                    onSessionTakenOver(
                        "Este app esta sendo usado em outro dispositivo. Somente uma sessao por Gmail e permitida."
                    )
                }
            }
    }

    fun stopSessionMonitor() {
        sessionListener?.remove()
        sessionListener = null
    }

    suspend fun isCurrentSessionOwner(): Boolean {
        val userId = UserRepository.getAuthenticatedCloudUserId() ?: return false
        val localSessionId = UserRepository.getActiveSessionId()
        if (localSessionId.isBlank()) {
            return false
        }

        return try {
            val snapshot = db.collection(USERS_COLLECTION).document(userId).get().await()
            readActiveSession(snapshot).sessionId == localSessionId
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao verificar proprietario da sessao atual.", e)
            false
        }
    }

    suspend fun updatePresence(isOnline: Boolean): Boolean {
        val userId = UserRepository.getAuthenticatedCloudUserId() ?: return false
        val localSessionId = UserRepository.getActiveSessionId()
        if (localSessionId.isBlank()) {
            return false
        }

        val now = System.currentTimeMillis()
        val deviceId = UserRepository.getLoginDeviceId()
        if (isOnline && !UserRepository.isPresenceOnline()) {
            UserRepository.resetOnlineSinceAt(now)
        }
        UserRepository.savePresenceState(isOnline = isOnline, lastSeenAt = now)

        return try {
            val document = db.collection(USERS_COLLECTION).document(userId)
            db.runTransaction { transaction ->
                val snapshot = transaction.get(document)
                val remoteSession = readActiveSession(snapshot)
                if (remoteSession.sessionId.isNotBlank() && remoteSession.sessionId != localSessionId) {
                    false
                } else {
                    val data = mapOf(
                        ACTIVE_SESSION_FIELD to mapOf(
                            "deviceId" to deviceId,
                            "sessionId" to localSessionId,
                        "updatedAt" to now,
                        "platform" to "android"
                    ),
                    "isOnline" to isOnline,
                    "lastSeenAt" to now,
                    "onlineSinceAt" to UserRepository.getOnlineSinceAt(),
                    "studyTimeMs" to UserRepository.getAccumulatedStudyTimeMs(now),
                    "platform" to "android"
                )
                transaction.set(document, data, SetOptions.merge())
                    true
                }
            }.await().also { updated ->
                if (updated) {
                    rankRepository.updatePresenceState(userId = userId, isOnline = isOnline, lastSeenAt = now)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao atualizar presenca em nuvem.", e)
            false
        }
    }

    suspend fun safeUpdatePresence(isOnline: Boolean): Boolean {
        return try {
            updatePresence(isOnline)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao sincronizar presenca; seguindo com cache local.", e)
            false
        }
    }

    suspend fun safeReleaseCurrentSession() {
        try {
            updatePresence(false)
        } catch (e: Exception) {
            Log.w(TAG, "Erro ao liberar sessao atual.", e)
        } finally {
            stopSessionMonitor()
            UserRepository.clearActiveSessionId()
        }
    }

    private fun applyCloudProfile(profile: CloudUserProfile) {
        val sanitizedAvatarId = AvatarAssetResolver.sanitizeAvatarId(
            avatarId = profile.avatarId,
            isPremium = profile.isPremium
        )
        UserRepository.saveNickname(profile.nickname)
        UserRepository.saveAvatarId(sanitizedAvatarId)
        UserRepository.saveDailyGoal(profile.dailyGoal)
        UserRepository.saveDailyStudyGoalMinutes(profile.dailyStudyGoalMinutes)
        UserRepository.saveSargentometroTargetDate(profile.sargentometroTargetDate)
        UserRepository.saveReminderEnabled(profile.reminderEnabled)
        UserRepository.saveReminderTime(profile.reminderTime)
        UserRepository.saveDarkMode(profile.darkMode)
        UserRepository.saveRankPoints(maxOf(UserRepository.getRankPoints(), profile.rankPoints))
        UserRepository.saveStudyTimeMs(maxOf(UserRepository.getTotalStudyTimeMs(), profile.studyTimeMs))
        UserRepository.savePremiumStatus(
            isPremium = profile.isPremium,
            productId = profile.premiumProductId,
            purchaseToken = profile.premiumPurchaseToken,
            purchaseTime = profile.premiumPurchaseTime
        )

        if (UserRepository.isGoogleSignedIn()) {
            UserRepository.updateGoogleAccountMetadata(
                email = profile.googleEmail.ifBlank { null },
                displayName = profile.googleDisplayName.ifBlank { null },
                photoUrl = profile.googlePhotoUrl.ifBlank { null }
            )
        }

        if (profile.privacyAccepted) {
            UserRepository.savePrivacyConsent("")
        }
    }

    private fun readActiveSession(snapshot: DocumentSnapshot): ActiveGoogleSession {
        val raw = snapshot.get(ACTIVE_SESSION_FIELD) as? Map<*, *> ?: emptyMap<String, Any>()
        return ActiveGoogleSession(
            deviceId = raw["deviceId"] as? String ?: "",
            sessionId = raw["sessionId"] as? String ?: "",
            updatedAt = (raw["updatedAt"] as? Number)?.toLong() ?: 0L,
            platform = raw["platform"] as? String ?: ""
        )
    }

    private suspend fun resolveCloudStateForWrite(userId: String): ResolvedCloudState {
        val localNickname = UserRepository.getNickname()
        val localAvatarId = UserRepository.getAvatarId()
        val localRankPoints = UserRepository.getRankPoints()
        var resolvedPremium = UserRepository.isPremium()
        var resolvedPremiumProductId = UserRepository.getPremiumProductId()
        var resolvedPremiumPurchaseToken = UserRepository.getPremiumPurchaseToken()
        var resolvedPremiumPurchaseTime = UserRepository.getPremiumPurchaseTime()

        val cloudProfile = try {
            db.collection(USERS_COLLECTION).document(userId).get().await().toObject(CloudUserProfile::class.java)
        } catch (e: Exception) {
            Log.w(TAG, "Nao foi possivel ler o perfil remoto antes de sincronizar.", e)
            null
        }

        val cloudRanking = try {
            rankRepository.getUserRanking(userId)
        } catch (e: Exception) {
            Log.w(TAG, "Nao foi possivel ler o ranking remoto antes de sincronizar.", e)
            null
        }

        if (!resolvedPremium && cloudProfile?.isPremium == true) {
            resolvedPremium = true
            resolvedPremiumProductId = cloudProfile.premiumProductId
            resolvedPremiumPurchaseToken = cloudProfile.premiumPurchaseToken
            resolvedPremiumPurchaseTime = cloudProfile.premiumPurchaseTime
        }

        val resolvedPoints = maxOf(localRankPoints, cloudProfile?.rankPoints ?: 0, cloudRanking?.pontos ?: 0)
        val resolvedNickname = choosePreferredNickname(
            localNickname,
            cloudProfile?.nickname,
            cloudRanking?.nickname,
            UserRepository.getGoogleDisplayName().take(9)
        )
        val resolvedAvatar = choosePreferredAvatarId(
            localAvatarId = localAvatarId,
            localPremium = resolvedPremium,
            cloudAvatarId = cloudProfile?.avatarId,
            cloudRankingAvatarId = cloudRanking?.avatarId
        )

        return ResolvedCloudState(
            nickname = resolvedNickname,
            avatarId = resolvedAvatar,
            rankPoints = resolvedPoints,
            isPremium = resolvedPremium,
            premiumProductId = resolvedPremiumProductId,
            premiumPurchaseToken = resolvedPremiumPurchaseToken,
            premiumPurchaseTime = resolvedPremiumPurchaseTime
        )
    }

    private fun choosePreferredNickname(vararg candidates: String?): String {
        return candidates.firstOrNull { candidate ->
            !candidate.isNullOrBlank() && candidate != "Estudante"
        } ?: "Estudante"
    }

    private fun choosePreferredAvatarId(
        localAvatarId: Int,
        localPremium: Boolean,
        cloudAvatarId: Int?,
        cloudRankingAvatarId: Int?
    ): Int {
        if (localAvatarId > 0 && AvatarAssetResolver.canUseAvatar(localAvatarId, localPremium) && localAvatarId != 1) {
            return localAvatarId
        }

        val remoteAvatar = listOfNotNull(cloudAvatarId, cloudRankingAvatarId)
            .firstOrNull { avatarId -> avatarId > 0 && AvatarAssetResolver.canUseAvatar(avatarId, localPremium) }

        return remoteAvatar ?: localAvatarId.coerceAtLeast(1)
    }
}
