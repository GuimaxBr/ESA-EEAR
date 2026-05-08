package com.guima.esa.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

private const val LEFT_BADGE_ASSET_NAME = "esquerda"
private const val RIGHT_BADGE_ASSET_NAME = "direita"

@Composable
fun LeftSargentometroAssetBadge(modifier: Modifier = Modifier) {
    AssetBadge(
        modifier = modifier,
        assetBaseName = LEFT_BADGE_ASSET_NAME,
        contentDescription = "Distintivo esquerdo"
    )
}

@Composable
fun RightSargentometroAssetBadge(modifier: Modifier = Modifier) {
    AssetBadge(
        modifier = modifier,
        assetBaseName = RIGHT_BADGE_ASSET_NAME,
        contentDescription = "Distintivo direito"
    )
}

@Composable
private fun AssetBadge(
    modifier: Modifier,
    assetBaseName: String,
    contentDescription: String
) {
    val bitmap = rememberAvatarAssetBitmap(assetBaseName)

    Box(
        modifier = modifier.wrapContentHeight(),
        contentAlignment = Alignment.Center
    ) {
        if (bitmap != null) {
            AvatarAssetImage(
                assetBaseName = assetBaseName,
                contentDescription = contentDescription,
                modifier = Modifier
                    .width(72.dp)
                    .height(72.dp)
            )
        } else {
            Spacer(
                modifier = Modifier
                    .width(72.dp)
                    .height(72.dp)
            )
        }
    }
}
