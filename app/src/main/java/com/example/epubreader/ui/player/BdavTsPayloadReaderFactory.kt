package com.example.epubreader.ui.player

import android.util.Log
import android.util.SparseArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import androidx.media3.extractor.ts.TsPayloadReader

private const val TAG = "BdavTsPRF"

/**
 * A TsPayloadReader.Factory wrapper that adds support for HDMV/BDMV-specific TS stream types
 * that the DefaultTsPayloadReaderFactory does not recognize.
 *
 * BDMV uses private stream type codes in the PMT that differ from standard DVB/ATSC:
 *   0x80 — LPCM (Linear PCM)
 *   0x81 — Dolby Digital AC-3
 *   0x82 — DTS 5.1              ← handled by DefaultFactory with FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS
 *   0x83 — Dolby TrueHD         ← remapped to 0x81 (AC-3 core)
 *   0x84 — Dolby Digital Plus
 *   0x85 — DTS-HD HRA           ← remapped to 0x82 (DTS core)
 *   0x86 — DTS-HD Master Audio  ← remapped to 0x82 (DTS core)
 *   0x90 — PGS subtitles
 */
@UnstableApi
class BdavTsPayloadReaderFactory : TsPayloadReader.Factory {

    private val delegate = DefaultTsPayloadReaderFactory(
        DefaultTsPayloadReaderFactory.FLAG_ENABLE_HDMV_DTS_AUDIO_STREAMS or
        DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
        DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS or
        DefaultTsPayloadReaderFactory.FLAG_IGNORE_SPLICE_INFO_STREAM
    )

    override fun createInitialPayloadReaders(): SparseArray<TsPayloadReader> {
        return delegate.createInitialPayloadReaders()
    }

    override fun createPayloadReader(
        streamType: Int,
        esInfo: TsPayloadReader.EsInfo
    ): TsPayloadReader? {
        // Log the actual stream types found in the BDMV PMT for debugging
        Log.i(TAG, "PMT stream type: 0x${streamType.toString(16).uppercase()}")

        // Remap BDMV-specific stream types to ExoPlayer-supported equivalents
        val remappedType = when (streamType) {
            0x83 -> {
                Log.i(TAG, "  0x83 (Dolby TrueHD) → 0x81 (AC-3 core fallback)")
                0x81 // Map TrueHD to AC-3 core reader to extract compatible audio
            }
            0x85 -> {
                Log.i(TAG, "  0x85 (DTS-HD HRA) → 0x82 (DTS core)")
                0x82  // DTS-HD HRA has DTS 5.1 core
            }
            0x86 -> {
                Log.i(TAG, "  0x86 (DTS-HD MA) → 0x82 (DTS core)")
                0x82  // DTS-HD MA has DTS 5.1 core
            }
            else -> streamType
        }

        if (streamType == 0x90) {
            Log.i(TAG, "  0x90 (PGS Subtitles) → Custom PesReader with PgsElementaryStreamReader")
            return androidx.media3.extractor.ts.PesReader(
                PgsElementaryStreamReader(esInfo)
            )
        }

        val reader = delegate.createPayloadReader(remappedType, esInfo)
        if (reader == null && remappedType != streamType) {
            Log.w(TAG, "  delegate returned null for remapped 0x${remappedType.toString(16)} (original 0x${streamType.toString(16)})")
        }
        return reader
    }
}
