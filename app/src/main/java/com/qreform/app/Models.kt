package com.qreform.app

enum class Align { LEFT, CENTER, RIGHT }

enum class BlockType {
    HEADING,        // school name / exam title lines -> centered
    DETAIL_SINGLE,  // e.g. a lone date line -> left aligned
    DETAIL_SPLIT,   // class (left) + marks (right) on the same line
    PART_SECTION,   // Part-I, Section-A, etc -> centered, bold
    ROMAN_HEADING,  // standalone roman numeral sub-heading -> bold black, normal (left) alignment
    QUESTION,       // a numbered question -> number and body aligned in a fixed column
    PARAGRAPH       // anything else -> left aligned normal text
}

data class FormattedBlock(
    val type: BlockType,
    val text: String,
    val rightText: String? = null,   // used by DETAIL_SPLIT (marks side)
    val number: String? = null,      // used by QUESTION (e.g. "1.")
    val align: Align = Align.LEFT,
    val bold: Boolean = false,
    val sizeSp: Float = 12f
)