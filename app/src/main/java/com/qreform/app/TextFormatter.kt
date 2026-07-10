package com.qreform.app

/**
 * Parses raw, possibly messy question-paper text and converts it into a list of
 * [FormattedBlock]s that a renderer (see PdfBuilder) can lay out consistently.
 *
 * Rules implemented (as specified):
 *  1. First 2-3 lines = heading (school name / exam title) -> always CENTER aligned,
 *     regardless of how they were originally aligned/collapsed.
 *  2. The next couple of lines are "details": a line with the date -> LEFT aligned;
 *     the following line normally holds the class -> LEFT aligned, and if marks info
 *     is present on that same line, it is pushed to the RIGHT side of that same row.
 *  3. Lines like "Part-I", "Part-II", "Section-I", "Section-A" etc -> CENTER aligned.
 *  4. Standalone Roman-numeral sub-headings (I., II., III. ...) -> BOLD + BLACK,
 *     kept in normal (left) alignment -- these are distinct from Part/Section headings.
 *  5. Numbered questions -> number and question body kept in a fixed, consistent
 *     column so every question number lines up vertically.
 *  6. Any obviously "broken" / wrapped lines (no ending punctuation, continuation
 *     starting in lowercase, etc.) are merged back together so output isn't
 *     collapsed or messy.
 */
object TextFormatter {

    private val romanNumerals = listOf(
        "XX", "XIX", "XVIII", "XVII", "XVI", "XV", "XIV", "XIII", "XII", "XI", "X",
        "IX", "VIII", "VII", "VI", "V", "IV", "III", "II", "I"
    )
    private val romanPattern = romanNumerals.joinToString("|")

    private val partSectionRegex =
        Regex("(?i)^\\s*(part|section)\\s*[-:.]?\\s*([ivxlcdm]+|[a-h])\\b.*$")

    private val romanHeadingRegex =
        Regex("(?i)^\\s*\\(?($romanPattern)\\)?\\s*[.):-]\\s*(.*)$")

    private val questionRegex =
        Regex("(?i)^\\s*(?:q(?:uestion)?\\.?\\s*)?(\\d{1,3})\\s*[.):-]\\s*(.*)$")

    private val detailKeywordRegex =
        Regex("(?i)\\b(date|class|grade|roll\\s*no\\.?|marks|m\\.m\\.?|total\\s*marks|time\\s*allowed|duration|subject)\\b")

    private val marksKeywordRegex =
        Regex("(?i)(total\\s*marks|m\\.m\\.?\\s*[:\\-]?\\s*\\d*|marks\\s*[:\\-]?\\s*\\d*|marks)")

    fun format(raw: String): List<FormattedBlock> {
        val cleanedLines = cleanupLines(raw)
        if (cleanedLines.isEmpty()) return emptyList()

        val blocks = mutableListOf<FormattedBlock>()
        var i = 0

        // ---- 1. Heading block: first 2-3 lines, always centered ----
        var headingCount = 0
        while (i < cleanedLines.size && headingCount < 3) {
            val line = cleanedLines[i]
            if (isSpecialLine(line) && headingCount > 0) break
            if (detailKeywordRegex.containsMatchIn(line) && headingCount > 0) break
            blocks.add(
                FormattedBlock(
                    type = BlockType.HEADING,
                    text = line,
                    align = Align.CENTER,
                    bold = headingCount == 0,
                    sizeSp = if (headingCount == 0) 16f else 13f
                )
            )
            i++
            headingCount++
            if (headingCount >= 2 && i < cleanedLines.size &&
                detailKeywordRegex.containsMatchIn(cleanedLines[i])
            ) break
        }

        // ---- 2. Detail lines (date / class + marks) ----
        var detailLinesConsumed = 0
        while (i < cleanedLines.size && detailLinesConsumed < 2) {
            val line = cleanedLines[i]
            if (!detailKeywordRegex.containsMatchIn(line)) break

            val marksMatch = marksKeywordRegex.find(line)
            if (marksMatch != null && marksMatch.range.first > 0) {
                val leftPart = line.substring(0, marksMatch.range.first).trim(' ', ',', '-', '|')
                val rightPart = line.substring(marksMatch.range.first).trim()
                blocks.add(
                    FormattedBlock(
                        type = BlockType.DETAIL_SPLIT,
                        text = leftPart,
                        rightText = rightPart,
                        align = Align.LEFT
                    )
                )
            } else {
                blocks.add(
                    FormattedBlock(
                        type = BlockType.DETAIL_SINGLE,
                        text = line,
                        align = Align.LEFT
                    )
                )
            }
            i++
            detailLinesConsumed++
        }

        // ---- 3. Body: Part/Section headings, Roman headings, Questions, paragraphs ----
        while (i < cleanedLines.size) {
            val line = cleanedLines[i]

            when {
                partSectionRegex.matches(line) -> {
                    blocks.add(
                        FormattedBlock(
                            type = BlockType.PART_SECTION,
                            text = line.uppercase(),
                            align = Align.CENTER,
                            bold = true,
                            sizeSp = 13f
                        )
                    )
                }

                romanHeadingRegex.matches(line) -> {
                    blocks.add(
                        FormattedBlock(
                            type = BlockType.ROMAN_HEADING,
                            text = line,
                            align = Align.LEFT,
                            bold = true,
                            sizeSp = 12.5f
                        )
                    )
                }

                questionRegex.matches(line) -> {
                    val m = questionRegex.find(line)!!
                    val num = m.groupValues[1]
                    val body = m.groupValues[2].trim()
                    blocks.add(
                        FormattedBlock(
                            type = BlockType.QUESTION,
                            text = body,
                            number = "$num.",
                            align = Align.LEFT
                        )
                    )
                }

                else -> {
                    // Continuation of the previous question / heading if it looks like
                    // wrapped text, otherwise a standalone paragraph.
                    val last = blocks.lastOrNull()
                    if (last != null && last.type == BlockType.QUESTION) {
                        blocks[blocks.lastIndex] = last.copy(text = (last.text + " " + line).trim())
                    } else {
                        blocks.add(
                            FormattedBlock(
                                type = BlockType.PARAGRAPH,
                                text = line,
                                align = Align.LEFT
                            )
                        )
                    }
                }
            }
            i++
        }

        return blocks
    }

    private fun isSpecialLine(line: String): Boolean =
        partSectionRegex.matches(line) || romanHeadingRegex.matches(line) || questionRegex.matches(line)

    /**
     * Normalizes whitespace and re-joins lines that were clearly wrapped /
     * broken mid-sentence, so the output isn't "collapsed or mixed dirty looking".
     */
    private fun cleanupLines(raw: String): List<String> {
        val rough = raw.split("\n")
            .map { it.trim().replace(Regex("[ \\t]+"), " ") }
            .filter { it.isNotEmpty() }

        val merged = mutableListOf<String>()
        for (line in rough) {
            if (merged.isEmpty()) {
                merged.add(line)
                continue
            }
            val prev = merged.last()
            val prevEndsSentence = Regex("[.:;!?)\\-]$").containsMatchIn(prev)
            val prevIsSpecial = isSpecialLine(prev) || detailKeywordRegex.containsMatchIn(prev)
            val lineIsSpecial = isSpecialLine(line) || detailKeywordRegex.containsMatchIn(line)
            val lineStartsLower = line.first().isLowerCase()

            val shouldMerge = !prevEndsSentence && !prevIsSpecial && !lineIsSpecial &&
                (lineStartsLower || prev.length < 20)

            if (shouldMerge) {
                merged[merged.lastIndex] = "$prev $line"
            } else {
                merged.add(line)
            }
        }
        return merged
    }
}