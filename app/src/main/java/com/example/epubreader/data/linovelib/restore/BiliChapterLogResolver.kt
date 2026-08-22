package com.example.epubreader.data.linovelib.restore

import java.util.regex.Pattern

/**
 * Resolver that extracts LCG shuffle parameters from chapterlog.js or provides fallback defaults.
 */
class BiliChapterLogResolver {

    data class Template(
        val fixedLength: Int = 20,
        val seedMultiplier: Long = 126L,
        val seedOffset: Long = 232L,
        val a: Long = 9302L,
        val c: Long = 49397L,
        val mod: Long = 233280L
    ) {
        fun toShuffleParams(chapterId: Long): BiliNovelRestore.ShuffleParams {
            val seed = chapterId * seedMultiplier + seedOffset
            return BiliNovelRestore.ShuffleParams(
                fixedLength = fixedLength,
                seed = seed,
                a = a,
                c = c,
                mod = mod
            )
        }
    }

    private var cachedTemplate: Template = Template()

    /**
     * Resolves ShuffleParams for a specific chapter ID.
     */
    fun resolve(chapterId: Long, chapterLogJs: String? = null): BiliNovelRestore.ShuffleParams {
        if (!chapterLogJs.isNullOrBlank()) {
            parseTemplate(chapterLogJs)?.let {
                cachedTemplate = it
            }
        }
        return cachedTemplate.toShuffleParams(chapterId)
    }

    /**
     * Parses the obfuscated LCG parameters from chapterlog.js code.
     */
    fun parseTemplate(js: String): Template? {
        try {
            // Pattern for seed: Number(...) , (multiplier) ) , (offset) ) ,
            val seedPattern = Pattern.compile(
                """Number\s*\(\s*[^)]+\s*\)\s*,\s*([^,)]+?)\s*\)\s*,\s*([^,)]+?)\s*\)\s*,"""
            )
            val seedMatcher = seedPattern.matcher(js)
            var mult: Long? = null
            var offset: Long? = null
            if (seedMatcher.find()) {
                mult = evalArithmetic(seedMatcher.group(1))
                offset = evalArithmetic(seedMatcher.group(2))
            }

            // Pattern for LCG: (_$) = ... \(\1 , (a) \) , (c) \) , (mod) \) ;
            val lcgPattern = Pattern.compile(
                "([_\\\$a-zA-Z0-9]+)\\s*=\\s*[^;]*?\\(\\s*\\\\1\\s*,\\s*([^,)]+?)\\s*\\)\\s*,\\s*([^,)]+?)\\s*\\)\\s*,\\s*([^;)]+?)\\s*\\)\\s*;"
            )
            val lcgMatcher = lcgPattern.matcher(js)
            var lcgA: Long? = null
            var lcgC: Long? = null
            var lcgMod: Long? = null
            if (lcgMatcher.find()) {
                lcgA = evalArithmetic(lcgMatcher.group(2))
                lcgC = evalArithmetic(lcgMatcher.group(3))
                lcgMod = evalArithmetic(lcgMatcher.group(4))
            }

            if (mult != null && offset != null && lcgA != null && lcgC != null && lcgMod != null &&
                mult > 0 && offset >= 0 && lcgA > 0 && lcgC >= 0 && lcgMod > 0) {
                return Template(
                    fixedLength = 20,
                    seedMultiplier = mult,
                    seedOffset = offset,
                    a = lcgA,
                    c = lcgC,
                    mod = lcgMod
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

    /**
     * Evaluates simple JavaScript integer arithmetic expressions (e.g. "0x17d6+0xa0b+0xe7*-0x25").
     */
    private fun evalArithmetic(expr: String?): Long? {
        if (expr.isNullOrBlank()) return null
        return try {
            val clean = expr.replace(Regex("\\s+"), "")
            // Tokenize hex/dec numbers and operators +, -, *
            val tokens = mutableListOf<String>()
            val m = Pattern.compile("0x[0-9a-fA-F]+|-?[0-9]+|[+\\-*]").matcher(clean)
            while (m.find()) {
                tokens.add(m.group())
            }

            if (tokens.isEmpty()) return null

            // First pass: perform multiplication
            val addTokens = mutableListOf<String>()
            var i = 0
            while (i < tokens.size) {
                val t = tokens[i]
                if (t == "*" && addTokens.isNotEmpty() && i + 1 < tokens.size) {
                    val prev = parseNumber(addTokens.removeAt(addTokens.size - 1))
                    val next = parseNumber(tokens[i + 1])
                    addTokens.add((prev * next).toString())
                    i += 2
                } else {
                    addTokens.add(t)
                    i++
                }
            }

            // Second pass: addition and subtraction
            var result = parseNumber(addTokens[0])
            var opIndex = 1
            while (opIndex < addTokens.size - 1) {
                val op = addTokens[opIndex]
                val nextVal = parseNumber(addTokens[opIndex + 1])
                if (op == "+") {
                    result += nextVal
                } else if (op == "-") {
                    result -= nextVal
                }
                opIndex += 2
            }
            result
        } catch (e: Exception) {
            null
        }
    }

    private fun parseNumber(token: String): Long {
        val s = token.trim()
        val isNeg = s.startsWith("-")
        val numStr = if (isNeg) s.substring(1) else s
        val v = if (numStr.startsWith("0x", ignoreCase = true)) {
            numStr.substring(2).toLong(16)
        } else {
            numStr.toLong(10)
        }
        return if (isNeg) -v else v
    }
}
