package com.example.epubreader.data.anime

import android.net.Uri
import android.util.Log
import okhttp3.Credentials
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit

object MkvChapterParser {

    private const val TAG = "MkvChapterParser"

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    /**
     * Attempts to fetch and parse chapters from an MKV URL or URI.
     * Uses HTTP Range request to only download the first ~384 KB of the file.
     */
    suspend fun parseChaptersFromMediaUrl(
        mediaUrl: String,
        webdavAuth: Pair<String, String>? = null
    ): List<SubtitleHelper.PlayerChapter> {
        return try {
            val uri = Uri.parse(mediaUrl)
            val scheme = uri.scheme?.lowercase()

            val bytes = if (scheme == "http" || scheme == "https") {
                val reqBuilder = Request.Builder()
                    .url(mediaUrl)
                    .header("Range", "bytes=0-1048576") // Fetch first 1 MB

                if (webdavAuth != null) {
                    reqBuilder.header("Authorization", Credentials.basic(webdavAuth.first, webdavAuth.second))
                }

                val resp = httpClient.newCall(reqBuilder.build()).execute()
                if (resp.isSuccessful || resp.code == 206) {
                    resp.body?.bytes()
                } else null
            } else {
                // Local file
                try {
                    val filePath = if (mediaUrl.startsWith("file://")) mediaUrl.substring(7) else mediaUrl
                    val file = java.io.File(filePath)
                    if (file.exists()) {
                        file.inputStream().use { input ->
                            val buf = ByteArray(1024 * 1024)
                            val read = input.read(buf)
                            if (read > 0) buf.copyOf(read) else null
                        }
                    } else null
                } catch (e: Exception) {
                    null
                }
            }

            if (bytes != null && bytes.isNotEmpty()) {
                parseMkvChapters(bytes)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error fetching MKV chapters from URL: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parses EBML elements from MKV header bytes searching for Chapters element (0x1043A770).
     * Accurately skips SeekID references in SeekHead.
     */
    fun parseMkvChapters(data: ByteArray): List<SubtitleHelper.PlayerChapter> {
        val chapters = mutableListOf<SubtitleHelper.PlayerChapter>()
        try {
            val target = byteArrayOf(0x10, 0x43, 0xA7.toByte(), 0x70)
            var searchIdx = 0
            val maxSearch = data.size - 8

            while (searchIdx <= maxSearch) {
                var match = true
                for (j in 0 until 4) {
                    if (data[searchIdx + j] != target[j]) {
                        match = false
                        break
                    }
                }

                if (match) {
                    val chOffset = searchIdx
                    // Check if this occurrence is a SeekID inside a SeekHead (preceded by 0x53 0xAB)
                    val isSeekId = (chOffset >= 2 && data[chOffset - 2] == 0x53.toByte() && data[chOffset - 1] == 0xAB.toByte()) ||
                            (chOffset >= 3 && data[chOffset - 3] == 0x53.toByte() && data[chOffset - 2] == 0xAB.toByte())

                    if (!isSeekId) {
                        val buffer = ByteBuffer.wrap(data)
                        buffer.position(chOffset + 4)
                        val chSize = readVInt(buffer)
                        if (chSize != null && chSize > 0) {
                            val chEnd = (buffer.position() + chSize).toInt().coerceAtMost(data.size)
                            while (buffer.position() < chEnd && buffer.remaining() > 4) {
                                val id = readElementId(buffer) ?: break
                                val size = readVInt(buffer) ?: break

                                if (id == 0x45B9L) { // EditionEntry
                                    val endPos = (buffer.position() + size).toInt().coerceAtMost(chEnd)
                                    parseEditionEntry(buffer, endPos, chapters)
                                } else {
                                    val newPos = (buffer.position() + size).toInt()
                                    if (newPos in 0..chEnd) {
                                        buffer.position(newPos)
                                    } else break
                                }
                            }
                        }
                    }
                    searchIdx += 4
                } else {
                    searchIdx++
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "Error parsing MKV EBML chapters: ${e.message}")
        }
        return chapters.distinctBy { it.startMs }.sortedBy { it.startMs }
    }

    private fun parseEditionEntry(
        buffer: ByteBuffer,
        endPos: Int,
        outChapters: MutableList<SubtitleHelper.PlayerChapter>
    ) {
        while (buffer.position() < endPos && buffer.remaining() > 2) {
            val id = readElementId(buffer) ?: break
            val size = readVInt(buffer) ?: break

            if (id == 0xB6L) { // ChapterAtom
                val atomEnd = (buffer.position() + size).toInt().coerceAtMost(endPos)
                parseChapterAtom(buffer, atomEnd, outChapters)
            } else {
                val newPos = (buffer.position() + size).toInt()
                if (newPos in 0..endPos) {
                    buffer.position(newPos)
                } else break
            }
        }
    }

    private fun parseChapterAtom(
        buffer: ByteBuffer,
        endPos: Int,
        outChapters: MutableList<SubtitleHelper.PlayerChapter>
    ) {
        var startMs = 0L
        var endMs = 0L
        var title = ""

        while (buffer.position() < endPos && buffer.remaining() > 2) {
            val id = readElementId(buffer) ?: break
            val size = readVInt(buffer) ?: break

            when (id) {
                0x91L -> { // ChapterTimeStart (uint in nanoseconds)
                    val nano = readUInt(buffer, size.toInt())
                    startMs = nano / 1_000_000L
                }
                0x92L -> { // ChapterTimeEnd
                    val nano = readUInt(buffer, size.toInt())
                    endMs = nano / 1_000_000L
                }
                0x80L -> { // ChapterDisplay
                    val dispEnd = (buffer.position() + size).toInt().coerceAtMost(endPos)
                    val dispTitle = parseChapterDisplay(buffer, dispEnd)
                    if (dispTitle.isNotBlank()) title = dispTitle
                }
                else -> {
                    val newPos = (buffer.position() + size).toInt()
                    if (newPos in 0..endPos) {
                        buffer.position(newPos)
                    } else break
                }
            }
        }

        if (title.isBlank()) {
            title = "章节 #${outChapters.size + 1}"
        }
        outChapters.add(SubtitleHelper.PlayerChapter(title = title, startMs = startMs, endMs = endMs))
    }

    private fun parseChapterDisplay(buffer: ByteBuffer, endPos: Int): String {
        var title = ""
        while (buffer.position() < endPos && buffer.remaining() > 2) {
            val id = readElementId(buffer) ?: break
            val size = readVInt(buffer) ?: break

            if (id == 0x85L) { // ChapString
                val strBytes = ByteArray(size.toInt().coerceIn(0, buffer.remaining()))
                buffer.get(strBytes)
                title = String(strBytes, StandardCharsets.UTF_8).trim()
            } else {
                val newPos = (buffer.position() + size).toInt()
                if (newPos in 0..endPos) {
                    buffer.position(newPos)
                } else break
            }
        }
        return title
    }

    private fun readElementId(buffer: ByteBuffer): Long? {
        if (buffer.remaining() < 1) return null
        val b0 = buffer.get().toInt() and 0xFF
        var mask = 0x80
        var len = 1
        while (len <= 4 && (b0 and mask) == 0) {
            mask = mask shr 1
            len++
        }
        if (len > 4 || buffer.remaining() < len - 1) return null

        var id = b0.toLong()
        for (i in 1 until len) {
            id = (id shl 8) or ((buffer.get().toInt() and 0xFF).toLong())
        }
        return id
    }

    private fun readVInt(buffer: ByteBuffer): Long? {
        if (buffer.remaining() < 1) return null
        val b0 = buffer.get().toInt() and 0xFF
        var mask = 0x80
        var len = 1
        while (len <= 8 && (b0 and mask) == 0) {
            mask = mask shr 1
            len++
        }
        if (len > 8 || buffer.remaining() < len - 1) return null

        var v = (b0 and (mask - 1)).toLong()
        for (i in 1 until len) {
            v = (v shl 8) or ((buffer.get().toInt() and 0xFF).toLong())
        }
        return v
    }

    private fun readUInt(buffer: ByteBuffer, size: Int): Long {
        var v = 0L
        val count = size.coerceIn(0, buffer.remaining())
        for (i in 0 until count) {
            v = (v shl 8) or ((buffer.get().toInt() and 0xFF).toLong())
        }
        return v
    }
}
