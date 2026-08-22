package com.example.epubreader.ui.player

import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ExtractorOutput
import androidx.media3.extractor.TrackOutput
import androidx.media3.extractor.ts.ElementaryStreamReader
import androidx.media3.extractor.ts.TsPayloadReader

private const val TAG = "PgsReader"

@UnstableApi
class PgsElementaryStreamReader(esInfo: TsPayloadReader.EsInfo) : ElementaryStreamReader {
    private val language: String? = esInfo.language
    private var output: TrackOutput? = null
    private var pesTimeUs: Long = C.TIME_UNSET
    private var lastValidPesTimeUs: Long = C.TIME_UNSET
    private val sampleBuffer = ParsableByteArray(65536)
    private var isSynchronized = false

    override fun seek() {
        isSynchronized = false
        pesTimeUs = C.TIME_UNSET
        lastValidPesTimeUs = C.TIME_UNSET
        sampleBuffer.reset(0)
    }

    override fun createTracks(extractorOutput: ExtractorOutput, idGenerator: TsPayloadReader.TrackIdGenerator) {
        idGenerator.generateNewId()
        output = extractorOutput.track(idGenerator.trackId, C.TRACK_TYPE_TEXT)
        val format = Format.Builder()
            .setId(idGenerator.formatId)
            .setSampleMimeType(MimeTypes.APPLICATION_PGS)
            .setLanguage(language)
            .build()
        output?.format(format)
    }

    override fun packetStarted(pesTimeUs: Long, flags: Int) {
        this.isSynchronized = true
        if (pesTimeUs != C.TIME_UNSET) {
            this.pesTimeUs = pesTimeUs
            this.lastValidPesTimeUs = pesTimeUs
        }
    }

    override fun consume(data: ParsableByteArray) {
        if (!isSynchronized) return
        val available = data.bytesLeft()
        if (available <= 0) return

        val currentPos = sampleBuffer.limit()
        sampleBuffer.ensureCapacity(currentPos + available)
        System.arraycopy(data.data, data.position, sampleBuffer.data, currentPos, available)
        sampleBuffer.setLimit(currentPos + available)
        data.skipBytes(available)

        // Check if one or more complete PGS Display Sets (ending with 0x80 END segment) are ready
        while (true) {
            val completeSize = findCompleteDisplaySetSize(sampleBuffer)
            if (completeSize > 0) {
                flushSampleOfSize(completeSize)
            } else {
                break
            }
        }
    }

    override fun packetFinished() {
        // If there's an unprocessed complete display set remaining when packet ends, flush it
        val completeSize = findCompleteDisplaySetSize(sampleBuffer)
        if (completeSize > 0) {
            flushSampleOfSize(completeSize)
        }
    }

    private fun findCompleteDisplaySetSize(buffer: ParsableByteArray): Int {
        val bytes = buffer.data
        val limit = buffer.limit()
        var pos = 0
        while (pos + 3 <= limit) {
            val segType = bytes[pos].toInt() and 0xFF
            // Valid PGS segment types: 0x14 (PDS), 0x15 (ODS), 0x16 (PCS), 0x17 (WDS), 0x80 (END)
            if (segType != 0x14 && segType != 0x15 && segType != 0x16 && segType != 0x17 && segType != 0x80) {
                // If header byte is invalid, skip 1 byte to find next valid segment sync
                pos++
                continue
            }
            if (pos + 3 > limit) return 0
            val segLen = ((bytes[pos + 1].toInt() and 0xFF) shl 8) or (bytes[pos + 2].toInt() and 0xFF)
            val segEnd = pos + 3 + segLen
            if (segEnd > limit) {
                // Incomplete segment, wait for remaining TS packets
                return 0
            }
            if (segType == 0x80) { // END segment found and complete!
                return segEnd
            }
            pos = segEnd
        }
        return 0
    }

    private fun flushSampleOfSize(size: Int) {
        if (size > 0) {
            val currentOutput = output
            val emitTimeUs = when {
                pesTimeUs != C.TIME_UNSET -> pesTimeUs
                lastValidPesTimeUs != C.TIME_UNSET -> lastValidPesTimeUs
                else -> C.TIME_UNSET
            }
            if (emitTimeUs != C.TIME_UNSET && currentOutput != null) {
                sampleBuffer.setPosition(0)
                currentOutput.sampleData(sampleBuffer, size)
                currentOutput.sampleMetadata(
                    emitTimeUs,
                    C.BUFFER_FLAG_KEY_FRAME,
                    size,
                    0,
                    null
                )
                Log.i(TAG, "Emitted PGS display set: size=$size bytes, timeUs=$emitTimeUs (${emitTimeUs / 1000}ms)")
            }
        }
        val remaining = sampleBuffer.limit() - size
        if (remaining > 0) {
            System.arraycopy(sampleBuffer.data, size, sampleBuffer.data, 0, remaining)
            sampleBuffer.setLimit(remaining)
        } else {
            sampleBuffer.reset(0)
        }
        sampleBuffer.setPosition(0)
        pesTimeUs = C.TIME_UNSET
    }
}

