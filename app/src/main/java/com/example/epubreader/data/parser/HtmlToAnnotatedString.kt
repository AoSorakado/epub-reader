package com.example.epubreader.data.parser

import android.text.Html
import android.text.Spanned
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.sp
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.text.style.ForegroundColorSpan
import android.text.style.AbsoluteSizeSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan

object HtmlToAnnotatedString {
    
    /**
     * Converts raw HTML from an EPUB chapter into a Compose AnnotatedString.
     * Uses Android's built-in Html.fromHtml as an intermediate step to handle robust HTML parsing.
     */
    fun parse(htmlContent: String): AnnotatedString {
        // Strip out head/style/script tags to prevent parsing garbage
        val cleanHtml = htmlContent
            .replace(Regex("<head>.*?</head>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<style>.*?</style>", RegexOption.DOT_MATCHES_ALL), "")
            .replace(Regex("<script>.*?</script>", RegexOption.DOT_MATCHES_ALL), "")
        
        val spanned = Html.fromHtml(cleanHtml, Html.FROM_HTML_MODE_COMPACT)
        return spannedToAnnotatedString(spanned)
    }

    private fun spannedToAnnotatedString(spanned: Spanned): AnnotatedString {
        return buildAnnotatedString {
            append(spanned.toString())

            val spans = spanned.getSpans(0, spanned.length, Any::class.java)
            for (span in spans) {
                val start = spanned.getSpanStart(span)
                val end = spanned.getSpanEnd(span)

                when (span) {
                    is StyleSpan -> {
                        when (span.style) {
                            android.graphics.Typeface.BOLD -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
                            android.graphics.Typeface.ITALIC -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
                            android.graphics.Typeface.BOLD_ITALIC -> addStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic), start, end)
                        }
                    }
                    is UnderlineSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.Underline), start, end)
                    is StrikethroughSpan -> addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, end)
                    is ForegroundColorSpan -> addStyle(SpanStyle(color = Color(span.foregroundColor)), start, end)
                    is RelativeSizeSpan -> addStyle(SpanStyle(fontSize = (span.sizeChange * 16).sp), start, end) // Assuming base 16sp
                    is AbsoluteSizeSpan -> {
                        if (span.dip) {
                            addStyle(SpanStyle(fontSize = span.size.sp), start, end)
                        } else {
                            addStyle(SpanStyle(fontSize = (span.size / 2f).sp), start, end)
                        }
                    }
                }
            }
        }
    }
}
