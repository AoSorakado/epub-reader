package com.example.epubreader.ui.player

import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.C
import androidx.media3.common.text.Cue
import androidx.media3.common.util.ParsableByteArray
import androidx.media3.common.util.UnstableApi
import androidx.media3.extractor.text.SimpleSubtitleDecoder
import androidx.media3.extractor.text.Subtitle
import java.util.Arrays

private const val TAG = "CustomPgsDecoder"

private const val SECTION_TYPE_PALETTE = 0x14
private const val SECTION_TYPE_OBJECT = 0x15
private const val SECTION_TYPE_COMPOSITION = 0x16
private const val SECTION_TYPE_WINDOW = 0x17
private const val SECTION_TYPE_END = 0x80

@UnstableApi
class CustomPgsDecoder : SimpleSubtitleDecoder("CustomPgsDecoder") {

    private val buffer = ParsableByteArray()
    private val palette = IntArray(256)
    private var planeWidth = 1920
    private var planeHeight = 1080

    private data class CompositionObject(
        val objectId: Int,
        val x: Int,
        val y: Int
    )

    private class ObjectData(
        val objectId: Int,
        var width: Int,
        var height: Int,
        var rleData: ByteArray
    )

    private val compositionObjects = mutableListOf<CompositionObject>()
    private val objectsMap = mutableMapOf<Int, ObjectData>()

    override fun decode(data: ByteArray, length: Int, reset: Boolean): Subtitle {
        if (reset) {
            Log.i(TAG, "decode: reset=true")
            compositionObjects.clear()
            objectsMap.clear()
            palette.fill(0)
        }

        buffer.reset(data, length)
        val cues = mutableListOf<Cue>()

        while (buffer.bytesLeft() >= 3) {
            val sectionType = buffer.readUnsignedByte()
            val sectionLength = buffer.readUnsignedShort()

            if (buffer.bytesLeft() < sectionLength) {
                Log.w(TAG, "Incomplete section 0x${Integer.toHexString(sectionType)}: bytesLeft=${buffer.bytesLeft()}, len=$sectionLength")
                break
            }

            val nextSectionPosition = buffer.position + sectionLength

            when (sectionType) {
                SECTION_TYPE_COMPOSITION -> {
                    compositionObjects.clear()
                    if (sectionLength >= 11) {
                        planeWidth = buffer.readUnsignedShort()
                        planeHeight = buffer.readUnsignedShort()
                        buffer.skipBytes(1) // frame_rate
                        buffer.skipBytes(2) // composition_number
                        val compositionState = buffer.readUnsignedByte()
                        if (compositionState == 0x80) { // Epoch start
                            objectsMap.clear()
                            palette.fill(0)
                        }
                        buffer.skipBytes(1) // palette_update_flag
                        buffer.skipBytes(1) // palette_id
                        val objectCount = buffer.readUnsignedByte()
                        Log.i(TAG, "PCS: plane=${planeWidth}x${planeHeight}, state=0x${Integer.toHexString(compositionState)}, objects=$objectCount")

                        for (i in 0 until objectCount) {
                            if (buffer.bytesLeft() >= 8) {
                                val objectId = buffer.readUnsignedShort()
                                buffer.skipBytes(1) // window_id
                                val croppedFlag = buffer.readUnsignedByte()
                                val x = buffer.readUnsignedShort()
                                val y = buffer.readUnsignedShort()
                                if ((croppedFlag and 0x80) != 0 && buffer.bytesLeft() >= 8) {
                                    buffer.skipBytes(8) // crop_x, crop_y, crop_w, crop_h
                                }
                                compositionObjects.add(CompositionObject(objectId, x, y))
                                Log.i(TAG, "  CompObj #$i: id=$objectId, pos=($x, $y)")
                            }
                        }
                    }
                }

                SECTION_TYPE_PALETTE -> {
                    if (sectionLength >= 2) {
                        val paletteId = buffer.readUnsignedByte()
                        val paletteVersion = buffer.readUnsignedByte()
                        val entryCount = (sectionLength - 2) / 5
                        Log.i(TAG, "PDS: id=$paletteId, ver=$paletteVersion, entries=$entryCount")
                        for (i in 0 until entryCount) {
                            val entryId = buffer.readUnsignedByte()
                            val y = buffer.readUnsignedByte()
                            val cr = buffer.readUnsignedByte() - 128
                            val cb = buffer.readUnsignedByte() - 128
                            val a = buffer.readUnsignedByte()

                            val r = (1.164383f * (y - 16).coerceAtLeast(0) + 1.792742f * cr).toInt().coerceIn(0, 255)
                            val g = (1.164383f * (y - 16).coerceAtLeast(0) - 0.213249f * cb - 0.532909f * cr).toInt().coerceIn(0, 255)
                            val b = (1.164383f * (y - 16).coerceAtLeast(0) + 2.112402f * cb).toInt().coerceIn(0, 255)

                            palette[entryId] = (a shl 24) or (r shl 16) or (g shl 8) or b
                        }
                    }
                }

                SECTION_TYPE_OBJECT -> {
                    if (sectionLength >= 4) {
                        val objectId = buffer.readUnsignedShort()
                        val objectVersion = buffer.readUnsignedByte()
                        val sequenceFlag = buffer.readUnsignedByte()
                        val isFirst = (sequenceFlag and 0x80) != 0

                        if (isFirst && sectionLength >= 11) {
                            val rleLength = buffer.readUnsignedInt24()
                            val width = buffer.readUnsignedShort()
                            val height = buffer.readUnsignedShort()
                            val dataLen = sectionLength - 11
                            val rleBytes = ByteArray(dataLen)
                            if (dataLen > 0 && buffer.bytesLeft() >= dataLen) {
                                buffer.readBytes(rleBytes, 0, dataLen)
                            }
                            objectsMap[objectId] = ObjectData(objectId, width, height, rleBytes)
                            Log.i(TAG, "ODS (First): id=$objectId, ${width}x${height}, rleLen=$rleLength, chunk=$dataLen")
                        } else {
                            val dataLen = sectionLength - 4
                            val rleBytes = ByteArray(dataLen)
                            if (dataLen > 0 && buffer.bytesLeft() >= dataLen) {
                                buffer.readBytes(rleBytes, 0, dataLen)
                            }
                            val existing = objectsMap[objectId]
                            if (existing != null) {
                                val combined = ByteArray(existing.rleData.size + rleBytes.size)
                                System.arraycopy(existing.rleData, 0, combined, 0, existing.rleData.size)
                                System.arraycopy(rleBytes, 0, combined, existing.rleData.size, rleBytes.size)
                                existing.rleData = combined
                            }
                            Log.i(TAG, "ODS (Cont): id=$objectId, chunk=$dataLen")
                        }
                    }
                }

                SECTION_TYPE_END -> {
                    if (compositionObjects.isNotEmpty() && planeWidth > 0 && planeHeight > 0) {
                        for (comp in compositionObjects) {
                            val obj = objectsMap[comp.objectId]
                            if (obj != null && obj.width > 0 && obj.height > 0) {
                                val bitmap = decodeRleBitmap(obj.rleData, obj.width, obj.height, palette)
                                if (bitmap != null) {
                                    val cue = Cue.Builder()
                                        .setBitmap(bitmap)
                                        .setPosition(comp.x.toFloat() / planeWidth.toFloat())
                                        .setPositionAnchor(Cue.ANCHOR_TYPE_START)
                                        .setLine(comp.y.toFloat() / planeHeight.toFloat(), Cue.LINE_TYPE_FRACTION)
                                        .setLineAnchor(Cue.ANCHOR_TYPE_START)
                                        .setSize(obj.width.toFloat() / planeWidth.toFloat())
                                        .setBitmapHeight(obj.height.toFloat() / planeHeight.toFloat())
                                        .build()
                                    cues.add(cue)
                                    Log.i(TAG, "Created Cue for obj #${comp.objectId}: pos=(${comp.x}, ${comp.y}), size=${obj.width}x${obj.height}")
                                } else {
                                    Log.w(TAG, "Failed to decode bitmap for obj #${comp.objectId}")
                                }
                            } else {
                                Log.w(TAG, "Obj #${comp.objectId} not found in objectsMap (keys=${objectsMap.keys})")
                            }
                        }
                    } else {
                        Log.i(TAG, "END: empty composition (clearing subtitles)")
                    }
                }
            }

            buffer.position = nextSectionPosition
        }

        Log.i(TAG, "decode finished: produced ${cues.size} cues")
        return PgsSubtitle(cues)
    }

    private fun decodeRleBitmap(rleData: ByteArray, width: Int, height: Int, colors: IntArray): Bitmap? {
        return try {
            val pixels = IntArray(width * height)
            var pixelIndex = 0
            val input = ParsableByteArray(rleData)

            while (input.bytesLeft() > 0 && pixelIndex < pixels.size) {
                val firstByte = input.readUnsignedByte()
                if (firstByte != 0) {
                    val color = if (firstByte in colors.indices) colors[firstByte] else 0
                    pixels[pixelIndex++] = color
                } else {
                    if (input.bytesLeft() <= 0) break
                    val switchByte = input.readUnsignedByte()
                    if (switchByte == 0) {
                        // End of line marker
                        val col = pixelIndex % width
                        if (col != 0) {
                            pixelIndex += (width - col)
                        }
                    } else {
                        val runLength: Int
                        val colorIndex: Int
                        when (switchByte and 0xC0) {
                            0x00 -> {
                                // 1-byte, transparent run
                                runLength = switchByte and 0x3F
                                colorIndex = 0
                            }
                            0x40 -> {
                                // 2-byte, transparent run
                                val next = if (input.bytesLeft() > 0) input.readUnsignedByte() else 0
                                runLength = ((switchByte and 0x3F) shl 8) or next
                                colorIndex = 0
                            }
                            0x80 -> {
                                // 2-byte, color run
                                runLength = switchByte and 0x3F
                                colorIndex = if (input.bytesLeft() > 0) input.readUnsignedByte() else 0
                            }
                            0xC0 -> {
                                // 3-byte, color run
                                val len2 = if (input.bytesLeft() > 0) input.readUnsignedByte() else 0
                                runLength = ((switchByte and 0x3F) shl 8) or len2
                                colorIndex = if (input.bytesLeft() > 0) input.readUnsignedByte() else 0
                            }
                            else -> {
                                runLength = 0
                                colorIndex = 0
                            }
                        }
                        if (runLength > 0) {
                            val color = if (colorIndex in colors.indices) colors[colorIndex] else 0
                            val end = minOf(pixelIndex + runLength, pixels.size)
                            Arrays.fill(pixels, pixelIndex, end, color)
                            pixelIndex = end
                        }
                    }
                }
            }

            Bitmap.createBitmap(pixels, width, height, Bitmap.Config.ARGB_8888)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to decode PGS RLE bitmap", e)
            null
        }
    }

    private class PgsSubtitle(private val cues: List<Cue>) : Subtitle {
        override fun getEventTime(index: Int): Long = 0L
        override fun getEventTimeCount(): Int = 1
        override fun getCues(timeUs: Long): List<Cue> = cues
        override fun getNextEventTimeIndex(timeUs: Long): Int = C.INDEX_UNSET
    }
}


