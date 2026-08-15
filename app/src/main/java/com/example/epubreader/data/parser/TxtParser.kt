package com.example.epubreader.data.parser

import java.io.File
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets

object TxtParser {

    private val CHAPTER_REGEX = Regex(
        "(?m)^[ \\t\\u3000]*(第[0-9一二三四五六七八九十百千万0-9]+[章卷节回部篇集话]|Chapter\\s+\\d+|楔子|序章|尾声|番外|后记|前言|简介|引子|终章|特别篇)[^\\r\\n]{0,35}$"
    )

    fun parse(file: File): EpubBook {
        val bytes = file.readBytes()
        val charset = detectCharset(bytes)
        val text = String(bytes, charset)

        val bookTitle = file.nameWithoutExtension.ifBlank { "未知文本文档" }
        val chapters = splitIntoChapters(text, bookTitle)
        val toc = chapters.map { EpubTocItem(title = it.title, href = it.href) }

        return EpubBook(
            title = bookTitle,
            author = "纯文本小说",
            coverImage = null,
            chapters = chapters,
            images = emptyMap(),
            toc = toc
        )
    }

    private fun detectCharset(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return StandardCharsets.UTF_8
        }
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) {
            return StandardCharsets.UTF_16BE
        }
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) {
            return StandardCharsets.UTF_16LE
        }

        // Test UTF-8 decoding
        try {
            val decoder = StandardCharsets.UTF_8.newDecoder()
            decoder.decode(java.nio.ByteBuffer.wrap(bytes))
            return StandardCharsets.UTF_8
        } catch (e: Exception) {
            // Not valid UTF-8, fall back to GBK / GB18030 for Chinese txt files
        }

        return try {
            Charset.forName("GB18030")
        } catch (e: Exception) {
            try {
                Charset.forName("GBK")
            } catch (e2: Exception) {
                StandardCharsets.UTF_8
            }
        }
    }

    private fun splitIntoChapters(fullText: String, defaultTitle: String): List<EpubChapter> {
        val matches = CHAPTER_REGEX.findAll(fullText).toList()

        if (matches.isEmpty()) {
            // Fallback: chunk by character count (approx 4000 characters on paragraph boundary)
            return chunkTextByLength(fullText)
        }

        val chapters = mutableListOf<EpubChapter>()

        // 1. Text before the first chapter header
        val firstMatch = matches.first()
        if (firstMatch.range.first > 0) {
            val introText = fullText.substring(0, firstMatch.range.first).trim()
            if (introText.isNotBlank()) {
                chapters.add(
                    EpubChapter(
                        title = "序章 / 前言",
                        href = "ch_0.html",
                        content = formatToHtml(introText)
                    )
                )
            }
        }

        // 2. Chapters extracted by regex
        for (i in matches.indices) {
            val currentMatch = matches[i]
            val chapterTitle = currentMatch.value.trim()

            val startIndex = currentMatch.range.last + 1
            val endIndex = if (i + 1 < matches.size) {
                matches[i + 1].range.first
            } else {
                fullText.length
            }

            val bodyText = if (startIndex < endIndex) {
                fullText.substring(startIndex, endIndex).trim()
            } else {
                ""
            }

            chapters.add(
                EpubChapter(
                    title = chapterTitle,
                    href = "ch_${chapters.size + 1}.html",
                    content = formatToHtml(bodyText)
                )
            )
        }

        return chapters.ifEmpty {
            listOf(
                EpubChapter(
                    title = defaultTitle,
                    href = "ch_1.html",
                    content = formatToHtml(fullText)
                )
            )
        }
    }

    private fun chunkTextByLength(fullText: String, targetChunkSize: Int = 4000): List<EpubChapter> {
        val chapters = mutableListOf<EpubChapter>()
        val lines = fullText.lines()
        var currentChunk = StringBuilder()
        var chunkIndex = 1

        for (line in lines) {
            currentChunk.append(line).append("\n")
            if (currentChunk.length >= targetChunkSize) {
                chapters.add(
                    EpubChapter(
                        title = "第 $chunkIndex 部分",
                        href = "ch_$chunkIndex.html",
                        content = formatToHtml(currentChunk.toString().trim())
                    )
                )
                currentChunk = StringBuilder()
                chunkIndex++
            }
        }

        if (currentChunk.isNotBlank()) {
            chapters.add(
                EpubChapter(
                    title = "第 $chunkIndex 部分",
                    href = "ch_$chunkIndex.html",
                    content = formatToHtml(currentChunk.toString().trim())
                )
            )
        }

        return chapters
    }

    private fun formatToHtml(rawText: String): String {
        val sb = StringBuilder()
        rawText.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank()) {
                val escaped = trimmed
                    .replace("&", "&amp;")
                    .replace("<", "&lt;")
                    .replace(">", "&gt;")
                sb.append("<p>").append(escaped).append("</p>\n")
            }
        }
        return sb.toString()
    }
}
