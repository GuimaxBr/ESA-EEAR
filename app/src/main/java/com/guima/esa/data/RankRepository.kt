package com.guima.esa.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

data class RankingUser(
    val userId: String = "",
    val nickname: String = "",
    val pontos: Int = 0,
    val avatarId: Int = 0,
    val isPremium: Boolean = false,
    val isOnline: Boolean? = null,
    val lastSeenAt: Long = 0L,
    val onlineSinceAt: Long = 0L,
    val studyTimeMs: Long = 0L,
    val platform: String = ""
)

class RankRepository {
    private val db = FirebaseFirestore.getInstance()

    fun getTop100Flow(): Flow<List<RankingUser>> = callbackFlow {
        val query = db.collection("ranking")
            .orderBy("pontos", Query.Direction.DESCENDING)
            .limit(100)

        val subscription = query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.e("FIRESTORE", "Erro ao ler ranking", error)
                trySend(emptyList())
                return@addSnapshotListener
            }

            if (snapshot != null) {
                val users = snapshot.documents.map { doc ->
                    (doc.toObject(RankingUser::class.java) ?: RankingUser()).copy(userId = doc.id)
                }
                trySend(users)
            }
        }

        awaitClose {
            subscription.remove()
        }
    }

    suspend fun getUserRanking(userId: String): RankingUser? {
        return try {
            db.collection("ranking").document(userId).get().await()
                .toObject(RankingUser::class.java)
                ?.copy(userId = userId)
        } catch (e: Exception) {
            Log.e("FIRESTORE", "Erro ao carregar ranking do usuario", e)
            null
        }
    }

    suspend fun setRankingData(
        userId: String,
        nickname: String,
        totalPoints: Int,
        avatarId: Int,
        isPremium: Boolean
    ): Boolean {
        if (userId == UserRepository.getCloudUserId()) {
            UserRepository.saveRankPoints(totalPoints)
        }

        return try {
            val data = mapOf(
                "userId" to userId,
                "nickname" to nickname,
                "pontos" to totalPoints,
                "avatarId" to avatarId,
                "isPremium" to isPremium,
                "googleEmail" to UserRepository.getGoogleEmail(),
                "isOnline" to UserRepository.isPresenceOnline(),
                "lastSeenAt" to UserRepository.getLastSeenAt(),
                "onlineSinceAt" to UserRepository.getOnlineSinceAt(),
                "studyTimeMs" to UserRepository.getAccumulatedStudyTimeMs(),
                "platform" to "android"
            )

            db.collection("ranking").document(userId).set(data, SetOptions.merge()).await()
            true
        } catch (e: Exception) {
            Log.e("FIRESTORE", "Erro ao salvar ranking do usuario", e)
            false
        }
    }

    suspend fun updateRankingData(
        userId: String,
        nickname: String,
        pointsToAdd: Int,
        avatarId: Int,
        isPremium: Boolean
    ) {
        val userRef = db.collection("ranking").document(userId)
        val isCurrentUser = userId == UserRepository.getCloudUserId()
        val previousLocalPoints = UserRepository.getRankPoints()
        val localTotalPoints = if (isCurrentUser) {
            (previousLocalPoints + pointsToAdd).coerceAtLeast(0)
        } else {
            previousLocalPoints
        }

        if (isCurrentUser) {
            UserRepository.saveRankPoints(localTotalPoints)
        }

        try {
            db.runTransaction { transaction ->
                val snapshot = transaction.get(userRef)
                val currentPoints = snapshot.getLong("pontos")?.toInt() ?: 0
                val basePoints = if (isCurrentUser) {
                    maxOf(currentPoints, previousLocalPoints)
                } else {
                    currentPoints
                }
                val newTotalPoints = basePoints + pointsToAdd

                val data = mutableMapOf<String, Any>(
                    "userId" to userId,
                    "nickname" to nickname,
                    "pontos" to newTotalPoints,
                    "avatarId" to avatarId,
                    "isPremium" to isPremium,
                    "googleEmail" to UserRepository.getGoogleEmail(),
                    "isOnline" to UserRepository.isPresenceOnline(),
                    "lastSeenAt" to UserRepository.getLastSeenAt(),
                    "onlineSinceAt" to UserRepository.getOnlineSinceAt(),
                    "studyTimeMs" to UserRepository.getAccumulatedStudyTimeMs(),
                    "platform" to "android"
                )

                transaction.set(userRef, data, SetOptions.merge())
                newTotalPoints
            }.await()?.let { newTotalPoints ->
                if (isCurrentUser) {
                    UserRepository.saveRankPoints(newTotalPoints as Int)
                }
            }
        } catch (e: Exception) {
            Log.e("FIRESTORE", "Erro ao atualizar ranking do usuario", e)
        }
    }

    suspend fun updatePresenceState(userId: String, isOnline: Boolean, lastSeenAt: Long) {
        try {
            val data = mapOf(
                "userId" to userId,
                "nickname" to UserRepository.getNickname(),
                "pontos" to UserRepository.getRankPoints(),
                "avatarId" to UserRepository.getAvatarId(),
                "isPremium" to UserRepository.isPremium(),
                "googleEmail" to UserRepository.getGoogleEmail(),
                "isOnline" to isOnline,
                "lastSeenAt" to lastSeenAt,
                "onlineSinceAt" to UserRepository.getOnlineSinceAt(),
                "studyTimeMs" to UserRepository.getAccumulatedStudyTimeMs(lastSeenAt),
                "platform" to "android"
            )

            db.collection("ranking").document(userId).set(data, SetOptions.merge()).await()
        } catch (e: Exception) {
            Log.w("FIRESTORE", "Nao foi possivel atualizar a presenca do ranking.", e)
        }
    }
}
