package com.guima.esa.ui

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import java.text.Normalizer

private const val AVATAR_ASSET_DIR = "avatars"

@Composable
fun AvatarAssetImage(
    assetBaseName: String,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop
) {
    val bitmap = rememberAvatarAssetBitmap(assetBaseName)

    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale
        )
    }
}

@Composable
fun rememberAvatarAssetBitmap(assetBaseName: String): ImageBitmap? {
    val context = LocalContext.current
    return remember(context, assetBaseName) {
        runCatching {
            val assetPath = resolveAvatarAssetPath(context, assetBaseName) ?: return@runCatching null
            context.assets.open(assetPath).use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        }.getOrNull()
    }
}

private fun resolveAvatarAssetPath(
    context: Context,
    assetBaseName: String
): String? {
    val fileName = context.assets.list(AVATAR_ASSET_DIR)
        ?.firstOrNull { assetFileName ->
            normalizeAvatarAssetName(assetFileName.substringBeforeLast(".")) == normalizeAvatarAssetName(assetBaseName)
        }
        ?: return null

    return "$AVATAR_ASSET_DIR/$fileName"
}

private fun normalizeAvatarAssetName(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
    return normalized
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()
}
