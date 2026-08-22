package com.example.epubreader.ui.player

import android.net.Uri
import android.util.Log
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * Zero-allocation high-performance BDAV / M2TS DataSource.
 * Auto-detects packet format on open():
 *   - 192-byte BDAV packets → strips 4-byte TP timestamp headers, presents clean 188-byte TS
 *   - 188-byte standard TS packets → transparent passthrough (no modification)
 *
 * This prevents stream corruption when remuxed .m2ts files use standard 188-byte packets.
 */
class BdavDataSource(private val upstream: DataSource) : DataSource {

    companion object {
        private const val TAG = "BdavDS"
        private const val TS_SYNC_BYTE = 0x47.toByte()
        private const val PROBE_SIZE = 192 * 8  // Read enough for reliable detection
    }

    private var current188Position: Long = 0L
    private var isOpened = false

    /** true = BDAV 192-byte packets (strip 4-byte header); false = standard 188-byte TS (passthrough) */
    private var isBdav192 = true

    // 64KB buffer (multiple of 192: 341 * 192 = 65472 bytes)
    private val rawBuffer = ByteArray(65472)
    private var rawBufferOffset = 0
    private var rawBufferLimit = 0

    private val packet188 = ByteArray(188)
    private var packet188Pos = 188

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(spec: DataSpec): Long {
        this.isOpened = true
        this.rawBufferOffset = 0
        this.rawBufferLimit = 0
        this.packet188Pos = 188

        // --- Probe phase: detect 192 vs 188 byte packet format ---
        isBdav192 = probePacketFormat(spec)
        Log.i(TAG, "Detected packet format: ${if (isBdav192) "BDAV 192-byte (stripping TP headers)" else "Standard 188-byte TS (passthrough)"}")

        // Close and reopen upstream for actual playback from the requested position
        upstream.close()

        if (isBdav192) {
            // BDAV 192-byte mode: map 188-byte logical position to 192-byte physical position
            this.current188Position = spec.position
            val packetIndex = spec.position / 188L
            val offsetIn188 = (spec.position % 188L).toInt()
            val upstreamPos = packetIndex * 192L

            val upstreamSpec = spec.buildUpon()
                .setPosition(upstreamPos)
                .setLength(C.LENGTH_UNSET.toLong())
                .build()

            val upstreamOpenedLength = upstream.open(upstreamSpec)

            if (offsetIn188 > 0) {
                skipBytes(offsetIn188)
            }

            return if (upstreamOpenedLength != C.LENGTH_UNSET.toLong()) {
                upstreamOpenedLength * 188L / 192L
            } else {
                C.LENGTH_UNSET.toLong()
            }
        } else {
            // Standard 188-byte TS: transparent passthrough, no position mapping
            this.current188Position = spec.position
            return upstream.open(spec)
        }
    }

    /**
     * Probes the first bytes of the stream to determine if this is a 192-byte BDAV
     * or standard 188-byte TS stream.
     *
     * Detection logic:
     * - Check for 0x47 sync bytes at intervals of 192 (with 4-byte offset) → BDAV
     * - Check for 0x47 sync bytes at intervals of 188 → standard TS
     */
    private fun probePacketFormat(spec: DataSpec): Boolean {
        val probeSpec = spec.buildUpon()
            .setPosition(0L)
            .setLength(PROBE_SIZE.toLong())
            .build()

        try {
            upstream.open(probeSpec)
        } catch (e: IOException) {
            Log.w(TAG, "Probe open failed, defaulting to BDAV 192", e)
            return true
        }

        val probeBuffer = ByteArray(PROBE_SIZE)
        var totalRead = 0
        try {
            while (totalRead < PROBE_SIZE) {
                val read = upstream.read(probeBuffer, totalRead, PROBE_SIZE - totalRead)
                if (read == C.RESULT_END_OF_INPUT) break
                totalRead += read
            }
        } catch (e: IOException) {
            Log.w(TAG, "Probe read failed after $totalRead bytes, defaulting to BDAV 192", e)
            return true
        }

        if (totalRead < 192 * 3) {
            Log.w(TAG, "Probe too short ($totalRead bytes), defaulting to BDAV 192")
            return true
        }

        // Count sync byte hits for 192-byte BDAV pattern (sync at offset 4, 196, 388, ...)
        val hits192 = countSyncHits(probeBuffer, totalRead, packetSize = 192, syncOffset = 4)
        // Count sync byte hits for 188-byte standard TS pattern (sync at offset 0, 188, 376, ...)
        val hits188 = countSyncHits(probeBuffer, totalRead, packetSize = 188, syncOffset = 0)

        Log.i(TAG, "Probe results: 192-byte sync hits=$hits192, 188-byte sync hits=$hits188 (from $totalRead bytes)")

        // Require at least 3 consecutive sync hits for confident detection
        return when {
            hits192 >= 3 && hits192 > hits188 -> true   // BDAV 192
            hits188 >= 3 -> false                         // Standard 188
            hits192 >= 1 -> true                          // Weak BDAV signal, assume BDAV
            else -> true                                  // Fallback to BDAV
        }
    }

    /**
     * Counts consecutive 0x47 sync byte hits starting from [syncOffset] with [packetSize] stride.
     */
    private fun countSyncHits(buffer: ByteArray, length: Int, packetSize: Int, syncOffset: Int): Int {
        var hits = 0
        var pos = syncOffset
        while (pos < length) {
            if (buffer[pos] == TS_SYNC_BYTE) {
                hits++
            } else {
                break  // Stop on first miss for consecutive count
            }
            pos += packetSize
        }
        return hits
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        if (!isBdav192) {
            // Standard 188-byte TS: direct passthrough, no stripping
            return upstream.read(buffer, offset, length)
        }

        // BDAV 192-byte mode: strip 4-byte TP headers
        var bytesWritten = 0
        var destOffset = offset
        var remaining = length

        while (remaining > 0) {
            // Drain remaining bytes from current 188-byte packet
            if (packet188Pos < 188) {
                val available = 188 - packet188Pos
                val toCopy = minOf(remaining, available)
                System.arraycopy(packet188, packet188Pos, buffer, destOffset, toCopy)
                packet188Pos += toCopy
                destOffset += toCopy
                bytesWritten += toCopy
                current188Position += toCopy
                remaining -= toCopy
                continue
            }

            // Ensure we have at least 192 bytes in rawBuffer
            if (rawBufferLimit - rawBufferOffset < 192) {
                if (!refillRawBuffer()) {
                    return if (bytesWritten > 0) bytesWritten else C.RESULT_END_OF_INPUT
                }
            }

            // Skip 4-byte TP header and copy 188-byte TS payload
            rawBufferOffset += 4
            System.arraycopy(rawBuffer, rawBufferOffset, packet188, 0, 188)
            rawBufferOffset += 188
            packet188Pos = 0
        }

        return bytesWritten
    }

    private fun skipBytes(count: Int) {
        val temp = ByteArray(minOf(count, 4096))
        var totalSkipped = 0
        while (totalSkipped < count) {
            val toRead = minOf(count - totalSkipped, temp.size)
            val read = read(temp, 0, toRead)
            if (read == C.RESULT_END_OF_INPUT) break
            totalSkipped += read
        }
    }

    @Throws(IOException::class)
    private fun refillRawBuffer(): Boolean {
        // Move any leftover bytes (< 192) to the beginning
        val leftover = rawBufferLimit - rawBufferOffset
        if (leftover > 0 && rawBufferOffset > 0) {
            System.arraycopy(rawBuffer, rawBufferOffset, rawBuffer, 0, leftover)
        }
        rawBufferOffset = 0
        rawBufferLimit = leftover

        // Fill remaining space from upstream
        while (rawBufferLimit < rawBuffer.size) {
            val bytesRead = upstream.read(rawBuffer, rawBufferLimit, rawBuffer.size - rawBufferLimit)
            if (bytesRead == C.RESULT_END_OF_INPUT) {
                break
            }
            rawBufferLimit += bytesRead
            if (rawBufferLimit >= 192) {
                return true
            }
        }

        return rawBufferLimit >= 192
    }

    @Throws(IOException::class)
    override fun close() {
        isOpened = false
        upstream.close()
    }

    class Factory(private val upstreamFactory: DataSource.Factory) : DataSource.Factory {
        override fun createDataSource(): DataSource {
            return BdavDataSource(upstreamFactory.createDataSource())
        }
    }
}
