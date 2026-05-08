package com.guima.esa.util

import java.util.Locale
import kotlin.math.abs

fun formatPointsCompact(points: Int): String {
    val pointsLong = points.toLong()
    val absolutePoints = abs(pointsLong)

    if (absolutePoints < 1_000L) {
        return pointsLong.toString()
    }

    if (absolutePoints >= 999_500_000L) {
        return formatWithSuffix(pointsLong, 1_000_000_000L, "B")
    }

    if (absolutePoints >= 999_500L) {
        return formatWithSuffix(pointsLong, 1_000_000L, "M")
    }

    return formatWithSuffix(pointsLong, 1_000L, "K")
}

private fun formatWithSuffix(value: Long, divisor: Long, suffix: String): String {
    val scaledValue = value.toDouble() / divisor.toDouble()
    val pattern = if (abs(scaledValue) >= 10) "%.0f" else "%.1f"
    val formattedValue = String.format(Locale.US, pattern, scaledValue)
        .removeSuffix(".0")

    return "$formattedValue$suffix"
}
