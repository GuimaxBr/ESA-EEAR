package com.guima.esa.data

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

data class ClusterPublicConfig(
    val publicBaseUrl: String = "",
    val workerMaxLoadPercent: Int = 0,
    val updatedAt: String = ""
)

object ClusterConfigRepository {
    private const val TAG = "CLUSTER_CONFIG"
    private const val DEFAULT_CLUSTER_URL = "https://usually-innovations-validity-latinas.trycloudflare.com"
    private val db = FirebaseFirestore.getInstance()

    suspend fun getPublicConfig(): ClusterPublicConfig {
        val config = try {
            db.collection("cluster").document("public").get().await()
                .toObject(ClusterPublicConfig::class.java)
                ?: ClusterPublicConfig()
        } catch (e: Exception) {
            Log.w(TAG, "Nao foi possivel carregar a configuracao publica do cluster.", e)
            ClusterPublicConfig()
        }
        if (config.publicBaseUrl.isNotBlank()) {
            UserRepository.saveClusterBridgeUrl(config.publicBaseUrl)
        }
        return config
    }

    suspend fun getEffectiveConfig(context: Context): ClusterPublicConfig {
        val remote = getPublicConfig()
        if (remote.publicBaseUrl.isNotBlank()) {
            return remote
        }
        val cachedUrl = UserRepository.getClusterBridgeUrl()
        val fallbackUrl = cachedUrl.ifBlank { DEFAULT_CLUSTER_URL }
        if (fallbackUrl.isNotBlank()) {
            UserRepository.saveClusterBridgeUrl(fallbackUrl)
        }
        return ClusterPublicConfig(
            publicBaseUrl = fallbackUrl,
            workerMaxLoadPercent = 50,
            updatedAt = ""
        )
    }
}
