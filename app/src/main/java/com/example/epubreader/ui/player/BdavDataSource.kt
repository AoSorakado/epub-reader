package com.example.epubreader.ui.player

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

/**
 * A DataSource wrapper that transparently strips the 4-byte TP timestamp header
 * from 192-byte Blu-ray BDAV (M2TS) packets, presenting a clean 188-byte MPEG-TS stream
 * to ExoPlayer's native TsExtractor.
 */
class BdavDataSource(private val upstream: DataSource) : DataSource {

    private var currentPosition: Long = 0L
    private var isOpened = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(spec: DataSpec): Long {
        this.currentPosition = spec.position
        this.isOpened = true

        // Map downstream 188-byte position to upstream 192-byte position
        val packetIndex = spec.position / 188L
        val packetOffset = spec.position % 188L
        val upstreamPos = packetIndex * 192L + 4L + packetOffset

        val upstreamLength = if (spec.length != C.LENGTH_UNSET.toLong()) {
            val endPacketIndex = (spec.position + spec.length) / 188L
            (endPacketIndex - packetIndex + 1) * 192L
        } else {
            C.LENGTH_UNSET.toLong()
        }

        val upstreamSpec = spec.buildUpon()
            .setPosition(upstreamPos)
            .setLength(upstreamLength)
            .build()

        val upstreamOpenedLength = upstream.open(upstreamSpec)
        return if (upstreamOpenedLength != C.LENGTH_UNSET.toLong()) {
            upstreamOpenedLength * 188L / 192L
        } else {
            C.LENGTH_UNSET.toLong()
        }
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0

        var bytesReadTotal = 0
        var currentOffset = offset
        var remaining = length

        while (remaining > 0) {
            val packetOffset = (currentPosition % 188L).toInt()
            val bytesAvailableInPacket = 188 - packetOffset
            val bytesToReadThisPacket = minOf(remaining, bytesAvailableInPacket)

            val readBytes = upstream.read(buffer, currentOffset, bytesToReadThisPacket)
            if (readBytes == C.RESULT_END_OF_INPUT) {
                return if (bytesReadTotal > 0) bytesReadTotal else C.RESULT_END_OF_INPUT
            }

            currentPosition += readBytes
            bytesReadTotal += readBytes
            currentOffset += readBytes
            remaining -= readBytes

            // If we just finished reading the 188-byte payload of this packet,
            // skip the 4-byte TP header of the next packet in the upstream!
            if ((currentPosition % 188L) == 0L) {
                var skipped = 0
                val skipBuffer = ByteArray(4)
                while (skipped < 4) {
                    val s = upstream.read(skipBuffer, skipped, 4 - skipped)
                    if (s == C.RESULT_END_OF_INPUT) break
                    skipped += s
                }
            }
        }

        return bytesReadTotal
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
