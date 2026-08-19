package com.example.epubreader

import com.example.epubreader.data.anime.AnimeFilenameParser
import org.junit.Assert.*
import org.junit.Test

class AnimeM2tsImportTest {

    @Test
    fun testYourNameBluRayFolderCleaning() {
        val folderName = "Your.Name.2016.JAPANESE.2160p.BluRay.HEVC.DTS-HD.MA.5.1-TASTED"
        val cleanTitle = AnimeFilenameParser.cleanAnimeFolderName(folderName)
        assertEquals("Your Name", cleanTitle)
    }

    @Test
    fun testYourNameM2tsMainEpisodeParsing() {
        val parentFolder = "Your.Name.2016.JAPANESE.2160p.BluRay.HEVC.DTS-HD.MA.5.1-TASTED"
        val parsed = AnimeFilenameParser.parseEpisodeFilename(
            filename = "00000.m2ts",
            parentFolderName = parentFolder
        )

        assertEquals("Your Name", parsed.animeTitle)
        assertEquals("正片", parsed.cleanTitle)
        assertEquals("01", parsed.episodeNumber)
        assertEquals(1, parsed.episodeIndex)
        assertEquals("2160P", parsed.resolution)
        assertEquals("HEVC", parsed.videoCodec)
        assertEquals("DTS-HD MA", parsed.audioCodec)
        assertEquals("TASTED", parsed.releaseGroup)
    }

    @Test
    fun testIgnoreExtraClips() {
        assertTrue(AnimeFilenameParser.isIgnoredExtraFile("Menu.m2ts"))
        assertTrue(AnimeFilenameParser.isIgnoredExtraFile("00001_Menu.m2ts"))
        assertTrue(AnimeFilenameParser.isIgnoredExtraFile("[NCOP] Opening.mkv"))
        assertTrue(AnimeFilenameParser.isIgnoredExtraFile("Trailer.mp4"))
        assertFalse(AnimeFilenameParser.isIgnoredExtraFile("00000.m2ts"))
        assertFalse(AnimeFilenameParser.isIgnoredExtraFile("00001.m2ts"))
    }
}
