package com.guima.esa.util

import android.os.Build
import android.text.Html
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextDecoration

fun String.fromHtml(): AnnotatedString {
    val sanitizedText = sanitizeMathMojibake(this)
    val spanned = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(sanitizedText, Html.FROM_HTML_MODE_LEGACY)
    } else {
        @Suppress("DEPRECATION")
        Html.fromHtml(sanitizedText)
    }

    return buildAnnotatedString {
        append(spanned.toString())

        spanned.getSpans(0, spanned.length, Any::class.java).forEach { span ->
            val start = spanned.getSpanStart(span)
            val end = spanned.getSpanEnd(span)

            when (span) {
                is android.text.style.StyleSpan -> when (span.style) {
                    android.graphics.Typeface.BOLD -> {
                        addStyle(SpanStyle(fontWeight = FontWeight.ExtraBold), start, end)
                    }
                    android.graphics.Typeface.ITALIC -> {
                        addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                    }
                    android.graphics.Typeface.BOLD_ITALIC -> {
                        addStyle(
                            SpanStyle(
                                fontWeight = FontWeight.ExtraBold,
                                fontStyle = FontStyle.Italic
                            ),
                            start,
                            end
                        )
                    }
                }

                is android.text.style.UnderlineSpan -> {
                    addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                }

                is android.text.style.SuperscriptSpan -> {
                    addStyle(SpanStyle(baselineShift = BaselineShift.Superscript), start, end)
                }
            }
        }
    }
}

fun String.htmlToPlainText(): String {
    val sanitizedText = sanitizeMathMojibake(this)
    val plainText = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        Html.fromHtml(sanitizedText, Html.FROM_HTML_MODE_LEGACY).toString()
    } else {
        @Suppress("DEPRECATION")
        Html.fromHtml(sanitizedText).toString()
    }

    return plainText
        .replace(Regex("\\s+"), " ")
        .trim()
}

private fun sanitizeMathMojibake(text: String): String {
    return text
        .replace("Â¹", "¹")
        .replace("Â²", "²")
        .replace("Â³", "³")
        .replace("�0", "⁰")
        .replace("�1", "¹")
        .replace("�2", "²")
        .replace("�3", "³")
        .replace("�4", "⁴")
        .replace("�5", "⁵")
        .replace("�6", "⁶")
        .replace("�7", "⁷")
        .replace("�8", "⁸")
        .replace("�9", "⁹")
        .replace(Regex("[�Â]\\s*\\n\\s*([0-9])")) { matchResult ->
            superscriptDigit(matchResult.groupValues[1])
        }
        .replace(Regex("[�Â]\\s*([0-9])")) { matchResult ->
            superscriptDigit(matchResult.groupValues[1])
        }
}

private fun superscriptDigit(value: String): String {
    return when (value) {
        "0" -> "⁰"
        "1" -> "¹"
        "2" -> "²"
        "3" -> "³"
        "4" -> "⁴"
        "5" -> "⁵"
        "6" -> "⁶"
        "7" -> "⁷"
        "8" -> "⁸"
        "9" -> "⁹"
        else -> value
    }
}
