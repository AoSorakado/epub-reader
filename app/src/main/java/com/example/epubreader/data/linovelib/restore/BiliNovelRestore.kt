package com.example.epubreader.data.linovelib.restore

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node

/**
 * Native deterministic paragraph de-scrambler ported from bili_novel_packer.
 * Reverses the Linear Congruential Generator (LCG) Fisher-Yates shuffle
 * injected by tw.linovelib.com's chapterlog.js without running any browser JavaScript.
 */
object BiliNovelRestore {

    data class ShuffleParams(
        val fixedLength: Int = 20,
        val seed: Long,
        val a: Long,
        val c: Long,
        val mod: Long
    )

    /**
     * Restores paragraph order directly inside Jsoup DOM Element.
     */
    fun restore(content: Element, params: ShuffleParams) {
        val childNodes = content.childNodes().toList()
        val paragraphSlots = mutableListOf<Int>()
        val paragraphs = mutableListOf<Element>()

        for (i in childNodes.indices) {
            val node = childNodes[i]
            if (isShuffleParagraph(node)) {
                paragraphSlots.add(i)
                paragraphs.add((node as Element).clone())
            }
        }

        if (paragraphs.size <= params.fixedLength) {
            return
        }

        val restoredParagraphs = restoreParagraphList(paragraphs, params)

        val newChildNodes = childNodes.toMutableList()
        for (i in paragraphSlots.indices) {
            newChildNodes[paragraphSlots[i]] = restoredParagraphs[i]
        }

        content.empty()
        for (node in newChildNodes) {
            content.appendChild(node)
        }
    }

    /**
     * Restores a list of paragraphs in memory.
     */
    fun restoreTextList(paragraphs: List<String>, params: ShuffleParams): List<String> {
        val n = paragraphs.size
        if (n <= params.fixedLength) return paragraphs

        val indices = (0 until n).toMutableList()
        val shuffled = indices.subList(params.fixedLength, n).toMutableList()

        var seed = params.seed
        for (i in shuffled.size - 1 downTo 1) {
            seed = (seed * params.a + params.c) % params.mod
            val j = ((seed.toDouble() / params.mod.toDouble()) * (i + 1)).toInt()
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp
        }

        val allIndices = indices.subList(0, params.fixedLength) + shuffled
        val restored = arrayOfNulls<String>(n)
        for (i in 0 until n) {
            restored[allIndices[i]] = paragraphs[i]
        }

        return restored.map { it ?: "" }
    }

    private fun restoreParagraphList(paragraphs: List<Element>, params: ShuffleParams): List<Element> {
        val n = paragraphs.size
        val indices = (0 until n).toMutableList()
        val shuffled = indices.subList(params.fixedLength, n).toMutableList()

        var seed = params.seed
        for (i in shuffled.size - 1 downTo 1) {
            seed = (seed * params.a + params.c) % params.mod
            val j = ((seed.toDouble() / params.mod.toDouble()) * (i + 1)).toInt()
            val tmp = shuffled[i]
            shuffled[i] = shuffled[j]
            shuffled[j] = tmp
        }

        val allIndices = indices.subList(0, params.fixedLength) + shuffled
        val restored = arrayOfNulls<Element>(n)
        for (i in 0 until n) {
            restored[allIndices[i]] = paragraphs[i]
        }

        return restored.mapNotNull { it }
    }

    private fun isShuffleParagraph(node: Node): Boolean {
        if (node !is Element) return false
        if (node.tagName().lowercase() != "p") return false
        val text = node.text().replace(Regex("\\s+"), "")
        return text.isNotEmpty()
    }
}
