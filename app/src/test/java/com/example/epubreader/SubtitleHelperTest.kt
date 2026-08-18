package com.example.epubreader

import com.example.epubreader.data.anime.SubtitleHelper
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SubtitleHelperTest {

    @Test
    fun testCurryBoxSignTransform() {
        val raw = """{\an5\fn方正正中黑_GBK\fs60\frz-15\blur0.8\shad2\c&HE0E0E4&\4c&H475C77&\pos(480,244)}黄金阳光咖喱"""
        val transform = SubtitleHelper.extractPosAndTransform(raw)

        assertNotNull(transform.posX)
        assertNotNull(transform.posY)
        assertEquals(480f / 1920f, transform.posX!!, 0.001f)
        assertEquals(244f / 1080f, transform.posY!!, 0.001f)
        assertEquals(-15f, transform.rotZ, 0.001f)
        assertEquals(0.5f, transform.anchorX, 0.001f)
        assertEquals(0.5f, transform.anchorY, 0.001f)
        assertEquals("方正正中黑_GBK", transform.fontName)
        assertFalse(transform.isVertical)
    }

    @Test
    fun testVerticalNoodleSignTransform() {
        val raw = """{\an5\fn@024-上首软糖体\fs50\blur0.8\shad2\c&H9E9E9F&\4c&H2B2A2C&\pos(818.49,719.11)}{\fax0\frx-8\fry-6\frz-90\fscx100\fscy100}赞岐乌冬面"""
        val transform = SubtitleHelper.extractPosAndTransform(raw)

        assertNotNull(transform.posX)
        assertNotNull(transform.posY)
        assertEquals(818.49f / 1920f, transform.posX!!, 0.001f)
        assertEquals(719.11f / 1080f, transform.posY!!, 0.001f)
        assertEquals(-90f, transform.rotZ, 0.001f)
        assertEquals(0.5f, transform.anchorX, 0.001f)
        assertEquals(0.5f, transform.anchorY, 0.001f)
        assertTrue(transform.isVertical)
    }

    @Test
    fun testVerticalRotationCalculation() {
        // Horizontal sign (\frz-15)
        val curryBox = SubtitleHelper.extractPosAndTransform("""{\an5\fn方正正中黑_GBK\fs60\frz-15\pos(480,244)}黄金阳光咖喱""")
        val curryRotZ = if (curryBox.isVertical) -(curryBox.rotZ + 90f) else -curryBox.rotZ
        assertEquals(15f, curryRotZ, 0.001f)

        // Vertical sign with \frz-90 (upright vertical)
        val noodle = SubtitleHelper.extractPosAndTransform("""{\an5\fn@024-上首软糖体\fs50\pos(818.49,719.11)}{\frz-90}赞岐乌冬面""")
        val noodleRotZ = if (noodle.isVertical) -(noodle.rotZ + 90f) else -noodle.rotZ
        assertEquals(0f, noodleRotZ, 0.001f)

        // Vertical sign with \frz-74 (slanted vertical)
        val cupRamen = SubtitleHelper.extractPosAndTransform("""{\an5\fn@方正少儿_GBK\fs32\pos(722.2,781.37)}{\frz-74}杯 面""")
        val cupRotZ = if (cupRamen.isVertical) -(cupRamen.rotZ + 90f) else -cupRamen.rotZ
        assertEquals(-16f, cupRotZ, 0.001f)
    }

    @Test
    fun testSummerPocketsSceneAt7m08sTopBilingual() {
        val assFile = File("..", "[Ygm] Summer Pockets [01][Ma10p_2160p][x265_flac_ass].ass")
        val altFile = File("[Ygm] Summer Pockets [01][Ma10p_2160p][x265_flac_ass].ass")
        val file = if (assFile.exists()) assFile else altFile
        if (!file.exists()) return

        val content = file.readText()
        val doc = SubtitleHelper.parseAssDocument(content)
        assertTrue(doc.events.isNotEmpty())

        // 7m08s is 428,000 ms (TV commercial scene)
        val posMs = (7 * 60 + 8) * 1000L
        val active = doc.findActiveEvents(posMs)
        assertTrue(active.isNotEmpty())

        val topTexts = active.filter { it.isTop }.map { it.primaryText }
        assertTrue(topTexts.any { it.contains("超大块的什锦天妇罗") })
        assertTrue(topTexts.any { it.contains("ど～んとでっかいかき揚げ天ぷら！") })
    }

    @Test
    fun testSummerPocketsTrackTitleTopRightCard() {
        val assFile = File("..", "[Ygm] Summer Pockets [01][Ma10p_2160p][x265_flac_ass].ass")
        val altFile = File("[Ygm] Summer Pockets [01][Ma10p_2160p][x265_flac_ass].ass")
        val file = if (assFile.exists()) assFile else altFile
        if (!file.exists()) return

        val content = file.readText()
        val doc = SubtitleHelper.parseAssDocument(content)
        assertTrue(doc.events.isNotEmpty())

        // 5m38s is 338,000 ms
        val posMs = (5 * 60 + 38) * 1000L
        val active = doc.findActiveEvents(posMs)
        assertTrue(active.isNotEmpty())

        val trackTitleEvent = active.firstOrNull { it.primaryText.contains("木陰の憩") }
        assertNotNull("Track title event should be present", trackTitleEvent)
        assertTrue("Track title event must have isTopRight = true", trackTitleEvent!!.isTopRight)
    }

    @Test
    fun testSummerPocketsSceneAt7m15s() {
        val assFile = File("..", "[Ygm] Summer Pockets [01][Ma10p_2160p][x265_flac_ass].ass")
        val altFile = File("[Ygm] Summer Pockets [01][Ma10p_2160p][x265_flac_ass].ass")
        val file = if (assFile.exists()) assFile else altFile
        if (!file.exists()) return

        val content = file.readText()
        val doc = SubtitleHelper.parseAssDocument(content)
        assertTrue(doc.events.isNotEmpty())

        // 7m15s is 435,000 ms
        val posMs = (7 * 60 + 15) * 1000L
        val active = doc.findActiveEvents(posMs)
        assertTrue(active.isNotEmpty())

        val texts = active.map { it.primaryText }
        assertTrue(texts.any { it.contains("黄金阳光咖喱") })
        assertTrue(texts.any { it.contains("中辣") })
        assertTrue(texts.any { it.contains("赞岐乌冬面") })
        assertTrue(texts.any { it.contains("杯 面") })
        assertTrue(texts.any { it.contains("来岛上的第一餐就吃这些不太好吧") })
        assertTrue(texts.any { it.contains("初めて来た島の食事が") })
    }
}
