package com.guima.esa.util

import android.content.Context

object AvatarAssetResolver {
    private val premiumAvatarIds = (1..6).toSet()
    private const val freeFallbackAvatarId = 8
    private val avatarRegex = Regex("""^avatar_(\d+)\.([a-z0-9]+)$""", RegexOption.IGNORE_CASE)
    private val supportedExtensions = setOf("png", "jpg", "jpeg", "webp")

    @Volatile
    private var cachedAvatarFiles: List<String>? = null

    fun getAvailableAvatarIds(context: Context): List<Int> {
        return getAvatarFiles(context)
            .mapNotNull { fileName ->
                val match = avatarRegex.matchEntire(fileName) ?: return@mapNotNull null
                val extension = match.groupValues[2].lowercase()
                if (extension !in supportedExtensions) {
                    return@mapNotNull null
                }
                match.groupValues[1].toIntOrNull()
            }
            .distinct()
            .sorted()
    }

    fun isPremiumAvatar(avatarId: Int): Boolean = avatarId in premiumAvatarIds

    fun canUseAvatar(avatarId: Int, isPremium: Boolean): Boolean {
        return isPremium || !isPremiumAvatar(avatarId)
    }

    fun sanitizeAvatarId(avatarId: Int, isPremium: Boolean): Int {
        return if (canUseAvatar(avatarId, isPremium)) avatarId else freeFallbackAvatarId
    }

    fun getAvatarAssetPath(context: Context, avatarId: Int): String {
        val fileName = findAvatarFileName(context, avatarId)
            ?: findAvatarFileName(context, freeFallbackAvatarId)
            ?: findAvatarFileName(context, 1)
            ?: return ""
        return "file:///android_asset/avatars/$fileName"
    }

    private fun findAvatarFileName(context: Context, avatarId: Int): String? {
        return getAvatarFiles(context).firstOrNull { fileName ->
            val match = avatarRegex.matchEntire(fileName) ?: return@firstOrNull false
            val extension = match.groupValues[2].lowercase()
            extension in supportedExtensions && match.groupValues[1].toIntOrNull() == avatarId
        }
    }

    private fun getAvatarFiles(context: Context): List<String> {
        cachedAvatarFiles?.let { return it }

        val files = try {
            context.assets.list("avatars")?.toList().orEmpty()
        } catch (_: Exception) {
            emptyList()
        }

        cachedAvatarFiles = files
        return files
    }
}
