package com.guima.esa.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.Normalizer

private const val GUIDE_HERO_ASSET_DIR = "avatars"
private const val GUIDE_HERO_ASSET_NAME = "imagem sem fundo 1 esa"

@Composable
fun GuideHeroCardStyled(
    bodyText: Color,
    mutedText: Color
) {
    val guideHeroBitmap = rememberGuideHeroBitmap()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp)) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF6F2ED)
            ) {
                Text(
                    text = "“Sargento! Elo fundamental\nentre o comando e a tropa!”",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Black,
                        lineHeight = 30.sp,
                        color = Color(0xFF191919)
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = Color(0xFFF7F3EC),
                border = BorderStroke(1.dp, Color(0xFFE1D8C9))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (guideHeroBitmap != null) {
                        Image(
                            bitmap = guideHeroBitmap,
                            contentDescription = "Entrada da ESA",
                            modifier = Modifier.fillMaxWidth(),
                            contentScale = ContentScale.Fit
                        )
                    } else {
                        Text(
                            text = "Imagem da ESA",
                            color = mutedText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Olá, futuro sargento!",
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 17.sp,
                    color = bodyText
                )
            )
        }
    }
}

@Composable
private fun rememberGuideHeroBitmap(): ImageBitmap? {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            val assetFileName = context.assets.list(GUIDE_HERO_ASSET_DIR)
                ?.firstOrNull { fileName ->
                    normalizeGuideHeroAssetName(fileName.substringBeforeLast(".")) == GUIDE_HERO_ASSET_NAME
                }
                ?: return@runCatching null

            context.assets.open("$GUIDE_HERO_ASSET_DIR/$assetFileName").use { inputStream ->
                BitmapFactory.decodeStream(inputStream)?.asImageBitmap()
            }
        }.getOrNull()
    }
}

private fun normalizeGuideHeroAssetName(value: String): String {
    val normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
    return normalized
        .replace("\\p{InCombiningDiacriticalMarks}+".toRegex(), "")
        .lowercase()
}
