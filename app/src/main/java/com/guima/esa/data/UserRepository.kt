package com.guima.esa.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

object UserRepository {

    private const val PREFS_NAME = "EsaEearUser"
    private const val DEFAULT_NICKNAME = "Estudante"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_NICKNAME = "nickname"
    private const val KEY_AVATAR_ID = "avatar_id"
    private const val KEY_USER_CREATED_ON_SERVER = "user_created_on_server"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_DAILY_GOAL = "daily_goal"
    private const val KEY_DAILY_STUDY_GOAL_MINUTES = "daily_study_goal_minutes"
    private const val KEY_SARGENTOMETRO_TARGET_DATE = "sargentometro_target_date"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_TIME = "reminder_time"
    private const val KEY_GOOGLE_ACCOUNT_ID = "google_account_id"
    private const val KEY_GOOGLE_EMAIL = "google_email"
    private const val KEY_GOOGLE_DISPLAY_NAME = "google_display_name"
    private const val KEY_GOOGLE_PHOTO_URL = "google_photo_url"
    private const val KEY_CLOUD_USER_ID = "cloud_user_id"
    private const val KEY_IS_PREMIUM = "is_premium"
    private const val KEY_PREMIUM_PRODUCT_ID = "premium_product_id"
    private const val KEY_PREMIUM_PURCHASE_TOKEN = "premium_purchase_token"
    private const val KEY_PREMIUM_PURCHASE_TIME = "premium_purchase_time"
    private const val KEY_RANK_POINTS = "rank_points"
    private const val KEY_PRIVACY_ACCEPTED = "privacy_accepted"
    private const val KEY_CLUSTER_ENABLED = "cluster_enabled"
    private const val KEY_CLUSTER_CONSENT_AT = "cluster_consent_at"
    private const val KEY_CLUSTER_DEVICE_ID = "cluster_device_id"
    private const val KEY_CLUSTER_BRIDGE_URL = "cluster_bridge_url"
    private const val KEY_CLUSTER_LAST_STATUS = "cluster_last_status"
    private const val KEY_CLUSTER_LAST_RUN_AT = "cluster_last_run_at"
    private const val KEY_CLUSTER_LAST_JOB_NAME = "cluster_last_job_name"
    private const val KEY_CLUSTER_LAST_PROCESSING_MS = "cluster_last_processing_ms"
    private const val KEY_CLUSTER_COMPLETED_CHUNKS = "cluster_completed_chunks"
    private const val KEY_LAST_MOTIVATION_DATE = "last_motivation_date"
    private const val KEY_LAST_MOTIVATION_INDEX = "last_motivation_index"
    private const val KEY_PRESENCE_ONLINE = "presence_online"
    private const val KEY_LAST_SEEN_AT = "last_seen_at"
    private const val KEY_ONLINE_SINCE_AT = "online_since_at"
    private const val KEY_TOTAL_STUDY_TIME_MS = "total_study_time_ms"
    private const val KEY_STUDY_SESSION_STARTED_AT = "study_session_started_at"
    private const val KEY_DAILY_STUDY_TIME_BY_DATE = "daily_study_time_by_date"
    private const val KEY_LAST_DAILY_FAILURE_NOTICE_DATE = "last_daily_failure_notice_date"
    private const val KEY_LOGIN_DEVICE_ID = "login_device_id"
    private const val KEY_ACTIVE_SESSION_ID = "active_session_id"
    private const val KEY_PENDING_SESSION_NOTICE = "pending_session_notice"
    private const val KEY_PLAY_STORE_REVIEW_PROMPT_SHOWN = "play_store_review_prompt_shown"
    private const val KEY_PLAY_STORE_REVIEW_USAGE_MS = "play_store_review_usage_ms"

    private lateinit var prefs: SharedPreferences
    private val gson = Gson()

    fun initialize(context: Context) {
        prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getUserId(): String {
        var userId = prefs.getString(KEY_USER_ID, null)
        if (userId == null) {
            userId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_USER_ID, userId).apply()
        }
        return userId
    }

    fun getCloudUserId(): String {
        return getAuthenticatedCloudUserId()
            ?: getSavedCloudUserId().takeUnless { it.isBlank() || it.contains("@") }
            ?: getUserId()
    }

    fun getLoginDeviceId(): String {
        var deviceId = prefs.getString(KEY_LOGIN_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_LOGIN_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun saveActiveSessionId(sessionId: String) {
        prefs.edit().putString(KEY_ACTIVE_SESSION_ID, sessionId).apply()
    }

    fun getActiveSessionId(): String {
        return prefs.getString(KEY_ACTIVE_SESSION_ID, "") ?: ""
    }

    fun clearActiveSessionId() {
        prefs.edit().remove(KEY_ACTIVE_SESSION_ID).apply()
    }

    fun savePendingSessionNotice(message: String) {
        prefs.edit().putString(KEY_PENDING_SESSION_NOTICE, message).apply()
    }

    fun consumePendingSessionNotice(): String {
        val message = prefs.getString(KEY_PENDING_SESSION_NOTICE, "") ?: ""
        if (message.isNotBlank()) {
            prefs.edit().remove(KEY_PENDING_SESSION_NOTICE).apply()
        }
        return message
    }

    fun hasShownPlayStoreReviewPrompt(): Boolean {
        return prefs.getBoolean(KEY_PLAY_STORE_REVIEW_PROMPT_SHOWN, false)
    }

    fun markPlayStoreReviewPromptShown() {
        prefs.edit()
            .putBoolean(KEY_PLAY_STORE_REVIEW_PROMPT_SHOWN, true)
            .apply()
    }

    fun getPlayStoreReviewUsageMs(): Long {
        return prefs.getLong(KEY_PLAY_STORE_REVIEW_USAGE_MS, 0L)
    }

    fun addPlayStoreReviewUsageMs(durationMs: Long) {
        if (durationMs <= 0L) return
        prefs.edit()
            .putLong(KEY_PLAY_STORE_REVIEW_USAGE_MS, getPlayStoreReviewUsageMs() + durationMs)
            .apply()
    }

    fun getAuthenticatedCloudUserId(): String? {
        return FirebaseAuth.getInstance().currentUser
            ?.takeIf { !it.isAnonymous }
            ?.uid
            ?.takeUnless { it.isBlank() }
    }

    fun saveCloudUserId(userId: String) {
        prefs.edit().putString(KEY_CLOUD_USER_ID, userId).apply()
    }

    fun getSavedCloudUserId(): String {
        return prefs.getString(KEY_CLOUD_USER_ID, "") ?: ""
    }

    fun saveNickname(nickname: String) {
        prefs.edit().putString(KEY_NICKNAME, nickname.ifBlank { DEFAULT_NICKNAME }).apply()
    }

    fun getNickname(): String {
        return prefs.getString(KEY_NICKNAME, DEFAULT_NICKNAME) ?: DEFAULT_NICKNAME
    }

    fun saveAvatarId(avatarId: Int) {
        prefs.edit().putInt(KEY_AVATAR_ID, avatarId).apply()
    }

    fun getAvatarId(): Int {
        return prefs.getInt(KEY_AVATAR_ID, 1)
    }

    fun saveDarkMode(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DARK_MODE, enabled).apply()
    }

    fun isDarkMode(): Boolean {
        return prefs.getBoolean(KEY_DARK_MODE, false)
    }

    fun saveDailyGoal(goal: Int) {
        prefs.edit().putInt(KEY_DAILY_GOAL, goal).apply()
    }

    fun getDailyGoal(): Int {
        return prefs.getInt(KEY_DAILY_GOAL, 20)
    }

    fun saveDailyStudyGoalMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_DAILY_STUDY_GOAL_MINUTES, minutes.coerceIn(20, 240)).apply()
    }

    fun getDailyStudyGoalMinutes(): Int {
        return prefs.getInt(KEY_DAILY_STUDY_GOAL_MINUTES, 20).coerceIn(20, 240)
    }

    fun saveSargentometroTargetDate(date: String) {
        prefs.edit().putString(KEY_SARGENTOMETRO_TARGET_DATE, date).apply()
    }

    fun getSargentometroTargetDate(): String {
        return prefs.getString(KEY_SARGENTOMETRO_TARGET_DATE, "2026-09-15") ?: "2026-09-15"
    }

    fun saveReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REMINDER_ENABLED, enabled).apply()
    }

    fun isReminderEnabled(): Boolean {
        return prefs.getBoolean(KEY_REMINDER_ENABLED, false)
    }

    fun saveReminderTime(time: String) {
        prefs.edit().putString(KEY_REMINDER_TIME, time).apply()
    }

    fun getReminderTime(): String {
        return prefs.getString(KEY_REMINDER_TIME, "20:00") ?: "20:00"
    }

    fun saveGoogleAccount(
        accountId: String,
        email: String?,
        displayName: String?,
        photoUrl: String?
    ) {
        prefs.edit()
            .putString(KEY_GOOGLE_ACCOUNT_ID, accountId)
            .putString(KEY_GOOGLE_EMAIL, email.orEmpty())
            .putString(KEY_GOOGLE_DISPLAY_NAME, displayName.orEmpty())
            .putString(KEY_GOOGLE_PHOTO_URL, photoUrl.orEmpty())
            .apply()
    }

    fun updateGoogleAccountMetadata(email: String?, displayName: String?, photoUrl: String?) {
        prefs.edit()
            .putString(KEY_GOOGLE_EMAIL, email ?: getGoogleEmail())
            .putString(KEY_GOOGLE_DISPLAY_NAME, displayName ?: getGoogleDisplayName())
            .putString(KEY_GOOGLE_PHOTO_URL, photoUrl ?: getGooglePhotoUrl())
            .apply()
    }

    fun clearGoogleAccount() {
        prefs.edit()
            .remove(KEY_GOOGLE_ACCOUNT_ID)
            .remove(KEY_GOOGLE_EMAIL)
            .remove(KEY_GOOGLE_DISPLAY_NAME)
            .remove(KEY_GOOGLE_PHOTO_URL)
            .remove(KEY_CLOUD_USER_ID)
            .remove(KEY_ACTIVE_SESSION_ID)
            .remove(KEY_NICKNAME)
            .remove(KEY_AVATAR_ID)
            .remove(KEY_IS_PREMIUM)
            .remove(KEY_PREMIUM_PRODUCT_ID)
            .remove(KEY_PREMIUM_PURCHASE_TOKEN)
            .remove(KEY_PREMIUM_PURCHASE_TIME)
            .remove(KEY_RANK_POINTS)
            .remove(KEY_PRESENCE_ONLINE)
            .remove(KEY_LAST_SEEN_AT)
            .remove(KEY_ONLINE_SINCE_AT)
            .remove(KEY_TOTAL_STUDY_TIME_MS)
            .remove(KEY_STUDY_SESSION_STARTED_AT)
            .remove(KEY_DAILY_STUDY_TIME_BY_DATE)
            .remove(KEY_LAST_DAILY_FAILURE_NOTICE_DATE)
            .apply()
    }

    fun isGoogleSignedIn(): Boolean {
        return getGoogleAccountId().isNotBlank() && getAuthenticatedCloudUserId() != null
    }

    fun getGoogleAccountId(): String {
        return prefs.getString(KEY_GOOGLE_ACCOUNT_ID, "") ?: ""
    }

    fun getGoogleEmail(): String {
        return prefs.getString(KEY_GOOGLE_EMAIL, "") ?: ""
    }

    fun getGoogleDisplayName(): String {
        return prefs.getString(KEY_GOOGLE_DISPLAY_NAME, "") ?: ""
    }

    fun getGooglePhotoUrl(): String {
        return prefs.getString(KEY_GOOGLE_PHOTO_URL, "") ?: ""
    }

    fun savePremiumStatus(
        isPremium: Boolean,
        productId: String? = null,
        purchaseToken: String? = null,
        purchaseTime: Long? = null
    ) {
        prefs.edit()
            .putBoolean(KEY_IS_PREMIUM, isPremium)
            .putString(KEY_PREMIUM_PRODUCT_ID, productId.orEmpty())
            .putString(KEY_PREMIUM_PURCHASE_TOKEN, purchaseToken.orEmpty())
            .putLong(KEY_PREMIUM_PURCHASE_TIME, purchaseTime ?: 0L)
            .apply()
    }

    fun clearPremiumStatus() {
        prefs.edit()
            .putBoolean(KEY_IS_PREMIUM, false)
            .remove(KEY_PREMIUM_PRODUCT_ID)
            .remove(KEY_PREMIUM_PURCHASE_TOKEN)
            .remove(KEY_PREMIUM_PURCHASE_TIME)
            .apply()
    }

    fun isPremium(): Boolean {
        return prefs.getBoolean(KEY_IS_PREMIUM, false)
    }

    fun getPremiumProductId(): String {
        return prefs.getString(KEY_PREMIUM_PRODUCT_ID, "") ?: ""
    }

    fun getPremiumPurchaseToken(): String {
        return prefs.getString(KEY_PREMIUM_PURCHASE_TOKEN, "") ?: ""
    }

    fun getPremiumPurchaseTime(): Long {
        return prefs.getLong(KEY_PREMIUM_PURCHASE_TIME, 0L)
    }

    fun saveRankPoints(points: Int) {
        prefs.edit().putInt(KEY_RANK_POINTS, points.coerceAtLeast(0)).apply()
    }

    fun getRankPoints(): Int {
        return prefs.getInt(KEY_RANK_POINTS, 0)
    }

    fun savePresenceState(isOnline: Boolean, lastSeenAt: Long = System.currentTimeMillis()) {
        val currentOnlineSinceAt = getOnlineSinceAt()
        val resolvedOnlineSinceAt = if (isOnline) {
            currentOnlineSinceAt.takeIf { it > 0L } ?: lastSeenAt
        } else {
            currentOnlineSinceAt
        }

        prefs.edit()
            .putBoolean(KEY_PRESENCE_ONLINE, isOnline)
            .putLong(KEY_LAST_SEEN_AT, lastSeenAt)
            .putLong(KEY_ONLINE_SINCE_AT, resolvedOnlineSinceAt)
            .apply()
    }

    fun isPresenceOnline(): Boolean {
        return prefs.getBoolean(KEY_PRESENCE_ONLINE, false)
    }

    fun getLastSeenAt(): Long {
        return prefs.getLong(KEY_LAST_SEEN_AT, 0L)
    }

    fun getOnlineSinceAt(): Long {
        return prefs.getLong(KEY_ONLINE_SINCE_AT, 0L)
    }

    fun resetOnlineSinceAt(startedAt: Long = System.currentTimeMillis()) {
        prefs.edit().putLong(KEY_ONLINE_SINCE_AT, startedAt).apply()
    }

    fun startStudySession(startedAt: Long = System.currentTimeMillis()) {
        if (prefs.getLong(KEY_STUDY_SESSION_STARTED_AT, 0L) > 0L) return
        prefs.edit().putLong(KEY_STUDY_SESSION_STARTED_AT, startedAt).apply()
    }

    fun stopStudySession(endedAt: Long = System.currentTimeMillis()) {
        val startedAt = getStudySessionStartedAt()
        if (startedAt <= 0L) return

        val sessionDurationMs = (endedAt - startedAt).coerceAtLeast(0L)
        val dailyStudyTimeMap = getDailyStudyTimeByDateMap().toMutableMap()
        distributeStudyTimeAcrossDays(startedAt, endedAt).forEach { (dateKey, millis) ->
            dailyStudyTimeMap[dateKey] = dailyStudyTimeMap.getOrDefault(dateKey, 0L) + millis
        }
        prefs.edit()
            .putLong(KEY_TOTAL_STUDY_TIME_MS, getTotalStudyTimeMs() + sessionDurationMs)
            .putString(KEY_DAILY_STUDY_TIME_BY_DATE, gson.toJson(dailyStudyTimeMap))
            .remove(KEY_STUDY_SESSION_STARTED_AT)
            .apply()
    }

    fun getStudySessionStartedAt(): Long {
        return prefs.getLong(KEY_STUDY_SESSION_STARTED_AT, 0L)
    }

    fun getTotalStudyTimeMs(): Long {
        return prefs.getLong(KEY_TOTAL_STUDY_TIME_MS, 0L)
    }

    fun saveStudyTimeMs(totalStudyTimeMs: Long) {
        prefs.edit().putLong(KEY_TOTAL_STUDY_TIME_MS, totalStudyTimeMs.coerceAtLeast(0L)).apply()
    }

    fun getAccumulatedStudyTimeMs(now: Long = System.currentTimeMillis()): Long {
        val activeSessionStartedAt = getStudySessionStartedAt()
        val activeSessionDurationMs = if (activeSessionStartedAt > 0L) {
            (now - activeSessionStartedAt).coerceAtLeast(0L)
        } else {
            0L
        }
        return getTotalStudyTimeMs() + activeSessionDurationMs
    }

    fun getTodaysStudyTimeMs(now: Long = System.currentTimeMillis()): Long {
        val todayKey = buildDateKey(now)
        val storedTodayMs = getStudyTimeForDate(todayKey)
        val activeSessionStartedAt = getStudySessionStartedAt()
        val activeTodayMs = if (activeSessionStartedAt > 0L) {
            distributeStudyTimeAcrossDays(activeSessionStartedAt, now)[todayKey] ?: 0L
        } else {
            0L
        }
        return storedTodayMs + activeTodayMs
    }

    fun getCurrentStudyStreakDays(now: Long = System.currentTimeMillis()): Int {
        val dailyStudyTimeMap = getDailyStudyTimeByDateMap()
        if (dailyStudyTimeMap.isEmpty()) return 0

        val calendar = Calendar.getInstance().apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }

        var streakDays = 0
        while (true) {
            val dateKey = buildDateKey(calendar.timeInMillis)
            if ((dailyStudyTimeMap[dateKey] ?: 0L) <= 0L) {
                break
            }
            streakDays++
            calendar.add(Calendar.DAY_OF_YEAR, -1)
        }

        return streakDays
    }

    fun getStudyTimeForDate(dateKey: String): Long {
        return getDailyStudyTimeByDateMap()[dateKey] ?: 0L
    }

    fun getDateKeyForTimestamp(timestamp: Long): String {
        return buildDateKey(timestamp)
    }

    fun saveLastDailyFailureNoticeDate(dateKey: String) {
        prefs.edit().putString(KEY_LAST_DAILY_FAILURE_NOTICE_DATE, dateKey).apply()
    }

    fun getLastDailyFailureNoticeDate(): String {
        return prefs.getString(KEY_LAST_DAILY_FAILURE_NOTICE_DATE, "") ?: ""
    }

    private fun getDailyStudyTimeByDateMap(): Map<String, Long> {
        val json = prefs.getString(KEY_DAILY_STUDY_TIME_BY_DATE, "{}") ?: "{}"
        val type = object : TypeToken<MutableMap<String, Long>>() {}.type
        return gson.fromJson<MutableMap<String, Long>>(json, type) ?: emptyMap()
    }

    private fun distributeStudyTimeAcrossDays(startedAt: Long, endedAt: Long): Map<String, Long> {
        if (endedAt <= startedAt) return emptyMap()

        val result = mutableMapOf<String, Long>()
        var segmentStart = startedAt

        while (segmentStart < endedAt) {
            val calendar = Calendar.getInstance().apply { timeInMillis = segmentStart }
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)

            val nextDayStart = calendar.timeInMillis
            val segmentEnd = minOf(nextDayStart, endedAt)
            val dateKey = buildDateKey(segmentStart)
            result[dateKey] = result.getOrDefault(dateKey, 0L) + (segmentEnd - segmentStart)
            segmentStart = segmentEnd
        }

        return result
    }

    private fun buildDateKey(timestamp: Long): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(timestamp))
    }

    fun acceptPrivacy() {
        savePrivacyConsent(System.currentTimeMillis().toString())
    }

    fun savePrivacyConsent(consentedAt: String) {
        prefs.edit()
            .putBoolean(KEY_PRIVACY_ACCEPTED, true)
            .putBoolean(KEY_CLUSTER_ENABLED, false)
            .putString(KEY_CLUSTER_CONSENT_AT, "")
            .apply()
    }

    fun hasAcceptedPrivacy(): Boolean {
        return prefs.getBoolean(KEY_PRIVACY_ACCEPTED, false)
    }

    fun isClusterEnabled(): Boolean {
        return prefs.getBoolean(KEY_CLUSTER_ENABLED, false)
    }

    fun setClusterEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_CLUSTER_ENABLED, enabled).apply()
    }

    fun getClusterConsentAt(): String {
        return prefs.getString(KEY_CLUSTER_CONSENT_AT, "") ?: ""
    }

    fun getClusterDeviceId(): String {
        var deviceId = prefs.getString(KEY_CLUSTER_DEVICE_ID, null)
        if (deviceId.isNullOrBlank()) {
            deviceId = UUID.randomUUID().toString()
            prefs.edit().putString(KEY_CLUSTER_DEVICE_ID, deviceId).apply()
        }
        return deviceId
    }

    fun saveClusterBridgeUrl(url: String) {
        prefs.edit().putString(KEY_CLUSTER_BRIDGE_URL, url).apply()
    }

    fun getClusterBridgeUrl(): String {
        return prefs.getString(KEY_CLUSTER_BRIDGE_URL, "") ?: ""
    }

    fun saveClusterWorkerSnapshot(
        status: String,
        runAt: String,
        jobName: String = "",
        processingMs: Long = 0L,
        completedChunksDelta: Int = 0
    ) {
        val currentCompleted = getClusterCompletedChunks()
        prefs.edit()
            .putString(KEY_CLUSTER_LAST_STATUS, status)
            .putString(KEY_CLUSTER_LAST_RUN_AT, runAt)
            .putString(KEY_CLUSTER_LAST_JOB_NAME, jobName)
            .putLong(KEY_CLUSTER_LAST_PROCESSING_MS, processingMs)
            .putInt(KEY_CLUSTER_COMPLETED_CHUNKS, (currentCompleted + completedChunksDelta).coerceAtLeast(0))
            .apply()
    }

    fun getClusterLastStatus(): String {
        return prefs.getString(KEY_CLUSTER_LAST_STATUS, "Aguardando") ?: "Aguardando"
    }

    fun getClusterLastRunAt(): String {
        return prefs.getString(KEY_CLUSTER_LAST_RUN_AT, "") ?: ""
    }

    fun getClusterLastJobName(): String {
        return prefs.getString(KEY_CLUSTER_LAST_JOB_NAME, "") ?: ""
    }

    fun getClusterLastProcessingMs(): Long {
        return prefs.getLong(KEY_CLUSTER_LAST_PROCESSING_MS, 0L)
    }

    fun getClusterCompletedChunks(): Int {
        return prefs.getInt(KEY_CLUSTER_COMPLETED_CHUNKS, 0)
    }

    fun shouldShowDailyMotivation(todayKey: String): Boolean {
        return prefs.getString(KEY_LAST_MOTIVATION_DATE, "") != todayKey
    }

    fun getNextDailyMotivationIndex(totalPhrases: Int): Int {
        if (totalPhrases <= 0) return 0

        val lastIndex = prefs.getInt(KEY_LAST_MOTIVATION_INDEX, -1)
        return if (lastIndex in 0 until totalPhrases) {
            (lastIndex + 1) % totalPhrases
        } else {
            0
        }
    }

    fun getDailyMotivationIndex(todayKey: String, totalPhrases: Int): Int {
        if (totalPhrases <= 0) return 0

        val lastDate = prefs.getString(KEY_LAST_MOTIVATION_DATE, "") ?: ""
        val lastIndex = prefs.getInt(KEY_LAST_MOTIVATION_INDEX, -1)

        if (lastDate == todayKey && lastIndex in 0 until totalPhrases) {
            return lastIndex
        }

        val nextIndex = if (lastIndex in 0 until totalPhrases) {
            (lastIndex + 1) % totalPhrases
        } else {
            0
        }

        markDailyMotivationShown(todayKey, nextIndex)
        return nextIndex
    }

    fun markDailyMotivationShown(todayKey: String, phraseIndex: Int) {
        prefs.edit()
            .putString(KEY_LAST_MOTIVATION_DATE, todayKey)
            .putInt(KEY_LAST_MOTIVATION_INDEX, phraseIndex)
            .apply()
    }

    fun isUserCreatedOnServer(): Boolean {
        return prefs.getBoolean(KEY_USER_CREATED_ON_SERVER, false)
    }

    fun setUserCreatedOnServer() {
        prefs.edit().putBoolean(KEY_USER_CREATED_ON_SERVER, true).apply()
    }
}
