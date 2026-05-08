package com.guima.esa.cluster

import android.content.Context
import android.os.Build
import android.util.Base64
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.guima.esa.data.ClusterConfigRepository
import com.guima.esa.data.UserRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okio.ByteString.Companion.toByteString
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPOutputStream

data class ClusterCycleResult(
    val status: String,
    val jobName: String = "",
    val processingMs: Long = 0L,
    val completedChunksDelta: Int = 0,
    val suggestedDelaySeconds: Long = 60L
)

private data class RegisterDeviceRequest(
    val deviceId: String,
    val deviceName: String,
    val estimatedLoadPercent: Int
)

private data class PollRequest(
    val deviceId: String
)

private data class WorkerResultRequest(
    val deviceId: String,
    val jobId: String,
    val chunkId: String,
    val status: String,
    val resultBase64: String? = null,
    val outputBytes: Int? = null,
    val processingMs: Long? = null,
    val sha256: String? = null,
    val errorMessage: String? = null,
    val estimatedLoadPercent: Int = 0
)

private data class RegisterResponse(
    val ok: Boolean = false
)

private data class PollResponse(
    val assignment: ClusterAssignment? = null
)

private data class ClusterAssignment(
    val jobId: String,
    val fileName: String,
    val chunkId: String,
    val index: Int,
    val inputBase64: String,
    val inputBytes: Int,
    @SerializedName("targetLoadPercent")
    val targetLoadPercent: Int
)

object ClusterWorkerRepository {
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    suspend fun runSingleCycle(context: Context): ClusterCycleResult = withContext(Dispatchers.IO) {
        if (!UserRepository.isClusterEnabled() || !UserRepository.hasAcceptedPrivacy()) {
            return@withContext ClusterCycleResult(
                status = "Cluster desativado",
                suggestedDelaySeconds = 300
            )
        }

        val config = ClusterConfigRepository.getEffectiveConfig(context)
        val baseUrl = config.publicBaseUrl.trim().trimEnd('/')
        if (baseUrl.isBlank()) {
            return@withContext ClusterCycleResult(
                status = "Bridge sem URL",
                suggestedDelaySeconds = 300
            )
        }

        val deviceId = UserRepository.getClusterDeviceId()
        val deviceName = buildDeviceName()
        val loadPercent = config.workerMaxLoadPercent.coerceIn(1, 50)

        try {
            postJson(
                "$baseUrl/api/worker/register",
                RegisterDeviceRequest(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    estimatedLoadPercent = 0
                ),
                RegisterResponse::class.java
            )
        } catch (error: Exception) {
            return@withContext ClusterCycleResult(
                status = "Bridge indisponivel",
                suggestedDelaySeconds = 120
            )
        }

        val pollResponse = postJson(
            "$baseUrl/api/worker/poll",
            PollRequest(deviceId = deviceId),
            PollResponse::class.java
        )

        val assignment = pollResponse.assignment ?: return@withContext ClusterCycleResult(
            status = "Aguardando tarefa",
            suggestedDelaySeconds = 45
        )

        val startedAt = System.currentTimeMillis()
        return@withContext try {
            val inputBytes = Base64.decode(assignment.inputBase64, Base64.DEFAULT)
            val compressed = gzip(inputBytes)
            val processingMs = System.currentTimeMillis() - startedAt
            val sha256 = sha256Hex(inputBytes)

            postJson<Unit>(
                "$baseUrl/api/worker/result",
                WorkerResultRequest(
                    deviceId = deviceId,
                    jobId = assignment.jobId,
                    chunkId = assignment.chunkId,
                    status = "completed",
                    resultBase64 = Base64.encodeToString(compressed, Base64.NO_WRAP),
                    outputBytes = compressed.size,
                    processingMs = processingMs,
                    sha256 = sha256,
                    estimatedLoadPercent = loadPercent
                ),
                Unit::class.java
            )

            ClusterCycleResult(
                status = "Chunk concluido",
                jobName = assignment.fileName,
                processingMs = processingMs,
                completedChunksDelta = 1,
                suggestedDelaySeconds = 10
            )
        } catch (error: Exception) {
            postJson<Unit>(
                "$baseUrl/api/worker/result",
                WorkerResultRequest(
                    deviceId = deviceId,
                    jobId = assignment.jobId,
                    chunkId = assignment.chunkId,
                    status = "failed",
                    errorMessage = error.message ?: "Falha no worker Android.",
                    estimatedLoadPercent = 0
                ),
                Unit::class.java
            )
            ClusterCycleResult(
                status = "Falha no chunk",
                jobName = assignment.fileName,
                suggestedDelaySeconds = 60
            )
        }
    }

    private fun buildDeviceName(): String {
        val brand = Build.MANUFACTURER.orEmpty().trim()
        val model = Build.MODEL.orEmpty().trim()
        val userSuffix = UserRepository.getUserId().take(4)
        return listOf(brand, model, userSuffix)
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .take(32)
            .ifBlank { "Android Worker" }
    }

    private fun gzip(bytes: ByteArray): ByteArray {
        val outputStream = ByteArrayOutputStream()
        GZIPOutputStream(outputStream).use { it.write(bytes) }
        return outputStream.toByteArray()
    }

    private fun sha256Hex(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(bytes)
        return digest.toByteString().hex()
    }

    private fun <T> postJson(url: String, body: Any, responseType: Class<T>): T {
        val requestBody = gson.toJson(body).toRequestBody(jsonMediaType)
        val request = Request.Builder()
            .url(url)
            .post(requestBody)
            .build()
        httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("Cluster HTTP ${response.code}")
            }
            val rawBody = response.body?.string().orEmpty()
            if (responseType == Unit::class.java) {
                @Suppress("UNCHECKED_CAST")
                return Unit as T
            }
            return gson.fromJson(rawBody, responseType)
        }
    }

    fun persistCycleSnapshot(result: ClusterCycleResult) {
        UserRepository.saveClusterWorkerSnapshot(
            status = result.status,
            runAt = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault()).format(Date()),
            jobName = result.jobName,
            processingMs = result.processingMs,
            completedChunksDelta = result.completedChunksDelta
        )
    }
}
