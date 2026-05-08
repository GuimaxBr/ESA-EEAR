package com.guima.esa.data

import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * Objeto simples para lidar com todas as chamadas de API.
 */
object ApiService {

    // [CORREÇÃO] Usando a nova URL base e configurando timeouts
    private const val BASE_URL = "https://action-deer-dry-charter.trycloudflare.com"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    /**
     * Cria um usuário no servidor. A API deve ser idempotente.
     */
    suspend fun createUserIfNotExists(id: String, nickname: String) {
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(mapOf("id" to id, "nickname" to nickname, "avatar_url" to null))
                val request = Request.Builder()
                    .url("$BASE_URL/user")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()
                
                client.newCall(request).execute().use { /* Resposta descartada */ }
            } catch (e: Exception) {
                // Falha silenciosamente se estiver offline.
                e.printStackTrace()
            }
        }
    }

    /**
     * Envia as estatísticas de uma sessão de simulado para o servidor.
     */
    suspend fun sendStats(userId: String, correct: Int, incorrect: Int, streak: Int) {
        withContext(Dispatchers.IO) {
            try {
                val json = gson.toJson(mapOf(
                    "user_id" to userId,
                    "acertos" to correct,
                    "erros" to incorrect,
                    "acertos_sem_erro" to streak
                ))
                val request = Request.Builder()
                    .url("$BASE_URL/stats")
                    .post(json.toRequestBody(jsonMediaType))
                    .build()

                client.newCall(request).execute().use { /* Resposta descartada */ }
            } catch (e: Exception) {
                // Falha silenciosamente se estiver offline.
                e.printStackTrace()
            }
        }
    }
}
