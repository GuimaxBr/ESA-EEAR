package com.guima.esa.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = EsaBlueLight,
    onPrimary = EsaNight,
    secondary = EsaSky,
    tertiary = EsaSky,
    primaryContainer = EsaSurfaceVariantDark,
    onPrimaryContainer = EsaOnDark,
    secondaryContainer = EsaSurfaceVariantDark,
    onSecondaryContainer = EsaOnDark,
    tertiaryContainer = EsaSurfaceDark,
    onTertiaryContainer = EsaOnDark,
    background = EsaNight,
    surface = EsaSurfaceDark,
    surfaceVariant = EsaSurfaceVariantDark,
    outline = EsaBlueLight.copy(alpha = 0.32f),
    surfaceTint = EsaBlueLight,
    onSurface = EsaOnDark,
    onSurfaceVariant = EsaBlueLight.copy(alpha = 0.84f)
)

private val LightColorScheme = lightColorScheme(
    primary = EsaInkBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = EsaInkBlue,
    tertiary = EsaBlueDark,
    primaryContainer = EsaInkBlueLight,
    onPrimaryContainer = EsaBlueDark,
    secondaryContainer = EsaSurfaceVariantLight,
    onSecondaryContainer = EsaBlueDark,
    tertiaryContainer = EsaSurfaceVariantLight,
    onTertiaryContainer = EsaBlueDark,
    background = EsaSurfaceLight,
    surface = androidx.compose.ui.graphics.Color.White,
    surfaceVariant = EsaSurfaceVariantLight,
    outline = EsaBlue.copy(alpha = 0.28f),
    surfaceTint = EsaInkBlue,
    onSurface = EsaNight,
    onSurfaceVariant = EsaBlueDark.copy(alpha = 0.74f)
)

@Composable
fun EsaeearTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
