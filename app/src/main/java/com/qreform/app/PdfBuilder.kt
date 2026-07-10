package com.qreform.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.File
import java.io.FileOutputStream

/**
 * Renders a list of [FormattedBlock]s produced by [TextFormatter] onto an
 * A4-sized PDF, honoring alignment, bold/black headings, the logo placed to
 * the left of the heading, the left/right split for class+marks, and a fixed
 * hanging-indent column so every question number lines up consistently.
 */
object PdfBuilder {

    private const val PAGE_WIDTH = 595   // A4 at 72dpi
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 40f
    private const val QUESTION_NUMBER_COL_WIDTH = 34f
    private const val LOGO_SIZE = 60f
    private const val LINE_SPACING = 6f

    fun build(blocks: List<FormattedBlock>, logo: Bitmap?, outFile: File) {
        val document = PdfDocument()
        val contentWidth = (PAGE_WIDTH - MARGIN * 2)

        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, 1).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas
        var y = MARGIN
        var pageNumber = 1
        var logoDrawnOnThisPage = false

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(neededHeight: Float) {
            if (y + neededHeight > PAGE_HEIGHT - MARGIN) {
                newPage()
            }
        }

        fun buildLayout(text: String, width: Int, sizeSp: Float, bold: Boolean, align: Layout.Alignment): StaticLayout {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
            paint.textSize = sizeSp * 1.15f
            paint.color = Color.BLACK
            paint.isFakeBoldText = bold
            return StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(align)
                .setLineSpacing(2f, 1f)
                .setIncludePad(false)
                .build()
        }

        for (block in blocks) {
            when (block.type) {

                BlockType.HEADING -> {
                    // Reserve left space for the logo on the very first heading line of the doc.
                    val leftReserve = if (!logoDrawnOnThisPage && logo != null) (LOGO_SIZE + 12f) else 0f
                    val usableWidth = (contentWidth - leftReserve).toInt()
                    val layout = buildLayout(block.text, usableWidth, block.sizeSp, block.bold, Layout.Alignment.ALIGN_CENTER)
                    ensureSpace(layout.height.toFloat() + LINE_SPACING)

                    if (!logoDrawnOnThisPage && logo != null) {
                        canvas.drawBitmap(
                            Bitmap.createScaledBitmap(logo, LOGO_SIZE.toInt(), LOGO_SIZE.toInt(), true),
                            MARGIN, y, null
                        )
                        logoDrawnOnThisPage = true
                    }

                    canvas.save()
                    canvas.translate(MARGIN + leftReserve, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += maxOf(layout.height.toFloat(), if (leftReserve > 0) LOGO_SIZE else 0f) + LINE_SPACING
                }

                BlockType.DETAIL_SINGLE -> {
                    val layout = buildLayout(block.text, contentWidth.toInt(), block.sizeSp, false, Layout.Alignment.ALIGN_NORMAL)
                    ensureSpace(layout.height.toFloat() + LINE_SPACING)
                    canvas.save()
                    canvas.translate(MARGIN, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += layout.height + LINE_SPACING
                }

                BlockType.DETAIL_SPLIT -> {
                    val halfWidth = (contentWidth / 2).toInt()
                    val leftLayout = buildLayout(block.text, halfWidth, block.sizeSp, false, Layout.Alignment.ALIGN_NORMAL)
                    val rightLayout = buildLayout(block.rightText ?: "", halfWidth, block.sizeSp, false, Layout.Alignment.ALIGN_OPPOSITE)
                    val rowHeight = maxOf(leftLayout.height, rightLayout.height).toFloat()
                    ensureSpace(rowHeight + LINE_SPACING)

                    canvas.save()
                    canvas.translate(MARGIN, y)
                    leftLayout.draw(canvas)
                    canvas.restore()

                    canvas.save()
                    canvas.translate(MARGIN + halfWidth, y)
                    rightLayout.draw(canvas)
                    canvas.restore()

                    y += rowHeight + LINE_SPACING
                }

                BlockType.PART_SECTION -> {
                    y += LINE_SPACING // small breathing room above section headers
                    val layout = buildLayout(block.text, contentWidth.toInt(), block.sizeSp, true, Layout.Alignment.ALIGN_CENTER)
                    ensureSpace(layout.height.toFloat() + LINE_SPACING * 2)
                    canvas.save()
                    canvas.translate(MARGIN, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += layout.height + LINE_SPACING * 2
                }

                BlockType.ROMAN_HEADING -> {
                    val layout = buildLayout(block.text, contentWidth.toInt(), block.sizeSp, true, Layout.Alignment.ALIGN_NORMAL)
                    ensureSpace(layout.height.toFloat() + LINE_SPACING)
                    canvas.save()
                    canvas.translate(MARGIN, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += layout.height + LINE_SPACING
                }

                BlockType.QUESTION -> {
                    val bodyWidth = (contentWidth - QUESTION_NUMBER_COL_WIDTH).toInt()
                    val numberLayout = buildLayout(block.number ?: "", QUESTION_NUMBER_COL_WIDTH.toInt(), block.sizeSp, false, Layout.Alignment.ALIGN_NORMAL)
                    val bodyLayout = buildLayout(block.text, bodyWidth, block.sizeSp, false, Layout.Alignment.ALIGN_NORMAL)
                    val rowHeight = maxOf(numberLayout.height, bodyLayout.height).toFloat()
                    ensureSpace(rowHeight + LINE_SPACING)

                    canvas.save()
                    canvas.translate(MARGIN, y)
                    numberLayout.draw(canvas)
                    canvas.restore()

                    canvas.save()
                    canvas.translate(MARGIN + QUESTION_NUMBER_COL_WIDTH, y)
                    bodyLayout.draw(canvas)
                    canvas.restore()

                    y += rowHeight + LINE_SPACING
                }

                BlockType.PARAGRAPH -> {
                    val layout = buildLayout(block.text, contentWidth.toInt(), block.sizeSp, false, Layout.Alignment.ALIGN_NORMAL)
                    ensureSpace(layout.height.toFloat() + LINE_SPACING)
                    canvas.save()
                    canvas.translate(MARGIN, y)
                    layout.draw(canvas)
                    canvas.restore()
                    y += layout.height + LINE_SPACING
                }
            }
        }

        document.finishPage(page)

        FileOutputStream(outFile).use { out ->
            document.writeTo(out)
        }
        document.close()
    }
}