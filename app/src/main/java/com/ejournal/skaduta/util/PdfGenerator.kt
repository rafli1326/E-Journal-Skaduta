package com.ejournal.skaduta.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.ejournal.skaduta.data.local.entity.JournalEntry
import com.ejournal.skaduta.data.local.entity.UserProfile
import java.io.File
import java.io.FileOutputStream

object PdfGenerator {

    // A4 dimensions at 72 DPI (Points)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private const val MARGIN_LEFT = 36f
    private const val MARGIN_RIGHT = 559f
    private const val MARGIN_TOP = 40f
    private const val MARGIN_BOTTOM = 806f
    private const val CONTENT_WIDTH = MARGIN_RIGHT - MARGIN_LEFT // 523f

    // Column widths
    private const val COL_NO_W = 28f
    private const val COL_DATE_W = 90f
    private const val COL_DESC_W = 265f
    private const val COL_EVIDENCE_W = 140f

    data class DayReportRow(
        val dayOfWeek: String,
        val formattedDate: String,
        val dateMillis: Long,
        val tasks: List<JournalEntry>
    )

    fun generateMonthlyReport(
        context: Context,
        entries: List<JournalEntry>,
        profile: UserProfile?,
        monthIndex: Int,
        year: Int
    ): File {
        val document = PdfDocument()

        val periodTitle = DateUtils.formatMonthYear(monthIndex, year)
        val reportsDir = File(context.filesDir, "generated_reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val safeMonth = DateUtils.indonesianMonths.getOrElse(monthIndex) { "Bulan" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(reportsDir, "EJournal_${safeMonth}_${year}.pdf")

        try {
            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            // Paints
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 8.5f
                isAntiAlias = true
            }

            val boldTextPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 8.5f
                typeface = Typeface.DEFAULT_BOLD
                isAntiAlias = true
            }

            val borderPaint = Paint().apply {
                color = Color.parseColor("#424242")
                style = Paint.Style.STROKE
                strokeWidth = 0.75f
            }

            val headerBgPaint = Paint().apply {
                color = Color.parseColor("#E8EAF6")
                style = Paint.Style.FILL
            }

            // Draw Document Title & Employee Info Box (Tanpa Kop Surat Duka/Sekolah)
            var currentY = drawDocumentHeaderAndInfo(canvas, textPaint, boldTextPaint, profile, periodTitle, MARGIN_TOP)

            // Draw Table Header
            currentY = drawTableHeader(canvas, headerBgPaint, borderPaint, boldTextPaint, currentY)

            // Group entries by date (1 row per date, with numbering inside if multiple activities)
            val dayRows = entries.sortedBy { it.dateMillis }
                .groupBy { it.formattedDate }
                .map { (dateStr, list) ->
                    DayReportRow(
                        dayOfWeek = list.first().dayOfWeek,
                        formattedDate = dateStr,
                        dateMillis = list.first().dateMillis,
                        tasks = list
                    )
                }

            var dayNumber = 1
            for (row in dayRows) {
                val rowHeight = calculateGroupedRowHeight(row, textPaint)

                // Check if row fits on current page
                if (currentY + rowHeight > MARGIN_BOTTOM) {
                    drawPageNumber(canvas, textPaint, pageNumber)
                    document.finishPage(page)

                    pageNumber++
                    pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas
                    currentY = MARGIN_TOP

                    // Redraw table header on new page
                    currentY = drawTableHeader(canvas, headerBgPaint, borderPaint, boldTextPaint, currentY)
                }

                drawGroupedTableRow(context, canvas, borderPaint, textPaint, row, dayNumber, currentY, rowHeight)
                currentY += rowHeight
                dayNumber++
            }

            // Check space for 3-party Signature Block (needs ~160pt)
            if (currentY + 165f > MARGIN_BOTTOM) {
                drawPageNumber(canvas, textPaint, pageNumber)
                document.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                currentY = MARGIN_TOP + 15f
            } else {
                currentY += 15f
            }

            // Draw 3-Party Signature Block
            drawThreePartySignatures(canvas, textPaint, boldTextPaint, profile, currentY)
            drawPageNumber(canvas, textPaint, pageNumber)

            document.finishPage(page)

            // ========================================================
            // BAGIAN LAMPIRAN BUKTI DOKUMENTASI FOTO (Jika Ada Foto)
            // ========================================================
            val photoAttachments = entries.filter { !it.evidenceImagePath.isNullOrBlank() }
            if (photoAttachments.isNotEmpty()) {
                pageNumber = drawPhotoAttachmentPages(
                    context = context,
                    document = document,
                    photoAttachments = photoAttachments,
                    profile = profile,
                    periodTitle = periodTitle,
                    startPageNumber = pageNumber + 1
                )
            }

            // Save to File
            FileOutputStream(outputFile).use { out ->
                document.writeTo(out)
            }
        } finally {
            document.close()
        }

        return outputFile
    }

    private fun drawDocumentHeaderAndInfo(
        canvas: Canvas,
        textPaint: TextPaint,
        boldTextPaint: TextPaint,
        profile: UserProfile?,
        periodTitle: String,
        startY: Float
    ): Float {
        var y = startY

        val reportTitlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val subtitlePaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 10f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        // Document Title
        canvas.drawText("LAPORAN JURNAL KEGIATAN KERJA HARIAN PEGAWAI", PAGE_WIDTH / 2f, y + 10f, reportTitlePaint)
        y += 24f
        canvas.drawText("Periode: $periodTitle", PAGE_WIDTH / 2f, y, subtitlePaint)
        y += 20f

        // Employee Info Box (Clean & Formal)
        val leftX = MARGIN_LEFT
        val midX = MARGIN_LEFT + 265f

        val name = profile?.fullName?.ifBlank { "-" } ?: "-"
        val nip = profile?.employeeId?.ifBlank { "-" } ?: "-"
        val pos = profile?.position?.ifBlank { "Staf Tata Usaha" } ?: "Staf Tata Usaha"
        val school = profile?.schoolName?.ifBlank { "SMK Negeri 2 Tanjung" } ?: "SMK Negeri 2 Tanjung"

        canvas.drawText("Nama Pegawai", leftX, y, boldTextPaint)
        canvas.drawText(": $name", leftX + 75f, y, textPaint)

        canvas.drawText("Jabatan", midX, y, boldTextPaint)
        canvas.drawText(": $pos", midX + 65f, y, textPaint)
        y += 13f

        canvas.drawText("NIP / ID", leftX, y, boldTextPaint)
        canvas.drawText(": $nip", leftX + 75f, y, textPaint)

        canvas.drawText("Unit Kerja", midX, y, boldTextPaint)
        canvas.drawText(": $school", midX + 65f, y, textPaint)
        y += 18f

        return y
    }

    private fun drawTableHeader(
        canvas: Canvas,
        bgPaint: Paint,
        borderPaint: Paint,
        headerPaint: TextPaint,
        startY: Float
    ): Float {
        val h = 20f
        val x0 = MARGIN_LEFT
        val x1 = x0 + COL_NO_W
        val x2 = x1 + COL_DATE_W
        val x3 = x2 + COL_DESC_W
        val x4 = MARGIN_RIGHT

        canvas.drawRect(x0, startY, x4, startY + h, bgPaint)
        canvas.drawRect(x0, startY, x4, startY + h, borderPaint)

        canvas.drawLine(x1, startY, x1, startY + h, borderPaint)
        canvas.drawLine(x2, startY, x2, startY + h, borderPaint)
        canvas.drawLine(x3, startY, x3, startY + h, borderPaint)

        val textY = startY + 13f
        canvas.drawText("No", x0 + 7f, textY, headerPaint)
        canvas.drawText("Hari / Tanggal", x1 + 6f, textY, headerPaint)
        canvas.drawText("Uraian Pekerjaan / Kegiatan", x2 + 6f, textY, headerPaint)
        canvas.drawText("Bukti Dukung", x3 + 6f, textY, headerPaint)

        return startY + h
    }

    private fun formatGroupedDescription(tasks: List<JournalEntry>): String {
        return if (tasks.size == 1) {
            tasks[0].description
        } else {
            tasks.mapIndexed { idx, t -> "${idx + 1}. ${t.description}" }.joinToString("\n\n")
        }
    }

    private fun formatGroupedEvidenceText(tasks: List<JournalEntry>): String {
        val textList = mutableListOf<String>()
        tasks.forEachIndexed { idx, t ->
            if (t.evidenceType == JournalEntry.EVIDENCE_TYPE_TEXT && !t.evidenceText.isNullOrBlank()) {
                val prefix = if (tasks.size > 1) "${idx + 1}. " else ""
                textList.add("$prefix${t.evidenceText}")
            }
        }
        return textList.joinToString("\n\n")
    }

    private fun calculateGroupedRowHeight(row: DayReportRow, textPaint: TextPaint): Float {
        val descString = formatGroupedDescription(row.tasks)
        val descLayout = createStaticLayout(descString, textPaint, (COL_DESC_W - 12).toInt())
        val descHeight = descLayout.height.toFloat()

        // Calculate evidence height accurately for combined text and photos
        var evidenceHeight = 0f
        row.tasks.forEach { task ->
            var itemEvHeight = 0f
            if (!task.evidenceText.isNullOrBlank()) {
                val evLayout = createStaticLayout(task.evidenceText, textPaint, (COL_EVIDENCE_W - 14).toInt())
                itemEvHeight += evLayout.height.toFloat() + 3f
            }
            if (!task.evidenceImagePath.isNullOrBlank()) {
                itemEvHeight += 44f
            }
            if (itemEvHeight == 0f) {
                itemEvHeight = 14f
            }
            evidenceHeight += itemEvHeight + 4f
        }

        val contentMax = maxOf(descHeight, evidenceHeight, 20f)
        return contentMax + 14f // padding top and bottom
    }

    private fun drawGroupedTableRow(
        context: Context,
        canvas: Canvas,
        borderPaint: Paint,
        textPaint: TextPaint,
        row: DayReportRow,
        number: Int,
        startY: Float,
        rowHeight: Float
    ) {
        val x0 = MARGIN_LEFT
        val x1 = x0 + COL_NO_W
        val x2 = x1 + COL_DATE_W
        val x3 = x2 + COL_DESC_W
        val x4 = MARGIN_RIGHT
        val endY = startY + rowHeight

        // Row borders
        canvas.drawRect(x0, startY, x4, endY, borderPaint)
        canvas.drawLine(x1, startY, x1, endY, borderPaint)
        canvas.drawLine(x2, startY, x2, endY, borderPaint)
        canvas.drawLine(x3, startY, x3, endY, borderPaint)

        // Col 1: Number
        canvas.drawText(number.toString(), x0 + 8f, startY + 14f, textPaint)

        // Col 2: Hari / Tanggal
        canvas.drawText(row.dayOfWeek, x1 + 5f, startY + 14f, textPaint)
        val shortDate = DateUtils.formatShortDate(row.dateMillis)
        canvas.drawText(shortDate, x1 + 5f, startY + 26f, textPaint)

        // Col 3: Description (Semua kegiatan hari ini dalam 1 baris ber-nomor)
        val fullDesc = formatGroupedDescription(row.tasks)
        canvas.save()
        canvas.translate(x2 + 5f, startY + 5f)
        val descLayout = createStaticLayout(fullDesc, textPaint, (COL_DESC_W - 10).toInt())
        descLayout.draw(canvas)
        canvas.restore()

        // Col 4: Bukti Dukung (Kombinasi teks keterangan dan foto thumbnail)
        var currentEvY = startY + 5f
        row.tasks.forEachIndexed { idx, task ->
            val numPrefix = if (row.tasks.size > 1) "${idx + 1}. " else ""

            val hasText = !task.evidenceText.isNullOrBlank()
            val hasImage = !task.evidenceImagePath.isNullOrBlank()

            if (hasText) {
                val textWithNum = "$numPrefix${task.evidenceText}"
                canvas.save()
                canvas.translate(x3 + 5f, currentEvY)
                val evLayout = createStaticLayout(textWithNum, textPaint, (COL_EVIDENCE_W - 10).toInt())
                evLayout.draw(canvas)
                canvas.restore()
                currentEvY += evLayout.height.toFloat() + 3f
            }

            if (hasImage) {
                val imgFile = File(task.evidenceImagePath!!)
                if (imgFile.exists() && FileUtils.isSafeEvidenceFile(context, imgFile)) {
                    if (!hasText && row.tasks.size > 1) {
                        canvas.drawText(numPrefix, x3 + 5f, currentEvY + 10f, textPaint)
                    }
                    val bitmap = decodeSampledBitmap(imgFile.absolutePath, 70, 70)
                    if (bitmap != null) {
                        try {
                            val imgX = if (!hasText && row.tasks.size > 1) x3 + 16f else x3 + 5f
                            val destRect = Rect(imgX.toInt(), currentEvY.toInt(), (imgX + 40f).toInt(), (currentEvY + 40f).toInt())
                            canvas.drawBitmap(bitmap, null, destRect, null)
                            currentEvY += 44f
                        } finally {
                            bitmap.recycle()
                        }
                    } else {
                        canvas.drawText("${if (!hasText) numPrefix else ""}[Foto]", x3 + 5f, currentEvY + 10f, textPaint)
                        currentEvY += 14f
                    }
                } else {
                    canvas.drawText("${if (!hasText) numPrefix else ""}[Foto Terlampir]", x3 + 5f, currentEvY + 10f, textPaint)
                    currentEvY += 14f
                }
            }

            if (!hasText && !hasImage) {
                canvas.drawText("${numPrefix}-", x3 + 5f, currentEvY + 10f, textPaint)
                currentEvY += 14f
            }

            currentEvY += 4f // gap between task evidence items
        }
    }

    private fun drawThreePartySignatures(
        canvas: Canvas,
        textPaint: TextPaint,
        boldPaint: TextPaint,
        profile: UserProfile?,
        startY: Float
    ) {
        val city = profile?.signatureCity?.ifBlank { "Tabalong" } ?: "Tabalong"
        val currentDate = DateUtils.formatDate(System.currentTimeMillis())

        val leftX = MARGIN_LEFT + 15f
        val rightX = MARGIN_RIGHT - 180f
        val centerX = PAGE_WIDTH / 2f

        var y = startY

        // Line 1: Location & Date on right side
        canvas.drawText("$city, $currentDate", rightX, y, textPaint)
        y += 12f

        // Row 1 Signatories: Kasubbag TU (Left) & Staf TU (Right)
        canvas.drawText("Memeriksa / Mengetahui,", leftX, y, textPaint)
        canvas.drawText("Pegawai Yang Bersangkutan,", rightX, y, textPaint)
        y += 11f

        val subbagPos = profile?.subbagPosition?.ifBlank { "Kepala Sub Bagian Tata Usaha" } ?: "Kepala Sub Bagian Tata Usaha"
        canvas.drawText(subbagPos, leftX, y, textPaint)

        val empPos = profile?.position?.ifBlank { "Staf Tata Usaha" } ?: "Staf Tata Usaha"
        canvas.drawText(empPos, rightX, y, textPaint)
        y += 46f // Space for signature

        val subbagName = profile?.subbagName?.ifBlank { "( ................................... )" } ?: "( ................................... )"
        val empName = profile?.fullName?.ifBlank { "( ................................... )" } ?: "( ................................... )"

        canvas.drawText(subbagName, leftX, y, boldPaint)
        canvas.drawText(empName, rightX, y, boldPaint)
        y += 11f

        val subbagId = if (!profile?.subbagId.isNullOrBlank()) "NIP. ${profile.subbagId}" else "NIP. -"
        val empId = if (!profile?.employeeId.isNullOrBlank()) "NIP. ${profile.employeeId}" else "NIP. -"

        canvas.drawText(subbagId, leftX, y, textPaint)
        canvas.drawText(empId, rightX, y, textPaint)
        y += 18f

        // Row 2 Signatory: Kepala Sekolah (Center Bottom)
        val centerTextPaint = TextPaint(textPaint).apply { textAlign = Paint.Align.CENTER }
        val centerBoldPaint = TextPaint(boldPaint).apply { textAlign = Paint.Align.CENTER }

        canvas.drawText("Mengesahkan,", centerX, y, centerTextPaint)
        y += 11f
        val headmasterPos = profile?.headmasterPosition?.ifBlank { "Kepala Sekolah" } ?: "Kepala Sekolah"
        canvas.drawText(headmasterPos, centerX, y, centerTextPaint)
        y += 46f // Space for signature & school stamp

        val headmasterName = profile?.headmasterName?.ifBlank { "( ................................... )" } ?: "( ................................... )"
        canvas.drawText(headmasterName, centerX, y, centerBoldPaint)
        y += 11f

        val headmasterId = if (!profile?.headmasterId.isNullOrBlank()) "NIP. ${profile.headmasterId}" else "NIP. -"
        canvas.drawText(headmasterId, centerX, y, centerTextPaint)
    }

    private fun drawPageNumber(canvas: Canvas, textPaint: TextPaint, pageNumber: Int) {
        val paint = TextPaint(textPaint).apply {
            color = Color.GRAY
            textSize = 8f
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Hal. $pageNumber", MARGIN_RIGHT, PAGE_HEIGHT - 16f, paint)
    }

    @Suppress("DEPRECATION")
    private fun createStaticLayout(text: String, paint: TextPaint, width: Int): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(width, 10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.15f)
            .setIncludePad(false)
            .build()
    }

    private fun drawPhotoAttachmentPages(
        context: Context,
        document: PdfDocument,
        photoAttachments: List<JournalEntry>,
        profile: UserProfile?,
        periodTitle: String,
        startPageNumber: Int
    ): Int {
        var pageNumber = startPageNumber
        val cardHeight = 315f
        val cardW = CONTENT_WIDTH
        val cardX = MARGIN_LEFT

        val cardBgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }
        val cardBorderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val badgeBgPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }
        val badgeTextPaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 8.5f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val labelPaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 8f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
        }
        val textPaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 8f
            isAntiAlias = true
        }
        val reportTitlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = Typeface.DEFAULT_BOLD
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val subtitlePaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 9f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val smallInfoPaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 8.5f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        val dividerPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }
        val photoFramePaint = Paint().apply {
            color = Color.parseColor("#E2E8F0")
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }

        var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        fun drawAttachmentHeader(c: Canvas): Float {
            var y = 45f
            c.drawText("LAMPIRAN BUKTI DOKUMENTASI FOTO KEGIATAN", PAGE_WIDTH / 2f, y, reportTitlePaint)
            y += 15f
            c.drawText("Periode: $periodTitle", PAGE_WIDTH / 2f, y, subtitlePaint)
            y += 13f
            val school = profile?.schoolName?.ifBlank { "SMK Negeri 2 Tanjung" } ?: "SMK Negeri 2 Tanjung"
            val empName = profile?.fullName?.ifBlank { "-" } ?: "-"
            c.drawText("$school • Pegawai: $empName", PAGE_WIDTH / 2f, y, smallInfoPaint)
            y += 10f
            c.drawLine(MARGIN_LEFT, y, MARGIN_RIGHT, y, dividerPaint)
            return y + 16f
        }

        var curY = drawAttachmentHeader(canvas)

        photoAttachments.forEachIndexed { idx, photo ->
            if (curY + cardHeight > MARGIN_BOTTOM) {
                drawPageNumber(canvas, textPaint, pageNumber)
                document.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                curY = drawAttachmentHeader(canvas)
            }

            // Card border & background
            val cardRect = RectF(cardX, curY, cardX + cardW, curY + cardHeight)
            canvas.drawRoundRect(cardRect, 6f, 6f, cardBgPaint)
            canvas.drawRoundRect(cardRect, 6f, 6f, cardBorderPaint)

            // Header badge inside card
            val badgePath = Path().apply {
                addRoundRect(
                    RectF(cardX, curY, cardX + cardW, curY + 22f),
                    floatArrayOf(6f, 6f, 6f, 6f, 0f, 0f, 0f, 0f),
                    Path.Direction.CW
                )
            }
            canvas.drawPath(badgePath, badgeBgPaint)
            canvas.drawLine(cardX, curY + 22f, cardX + cardW, curY + 22f, cardBorderPaint)
            canvas.drawText("Dokumentasi #${idx + 1} • ${photo.dayOfWeek}, ${photo.formattedDate}", cardX + 10f, curY + 15f, badgeTextPaint)

            // Photo
            val imgBoxW = 280f
            val imgBoxH = 190f
            val imgX = cardX + (cardW - imgBoxW) / 2f
            val imgY = curY + 29f

            val imgFile = File(photo.evidenceImagePath!!)
            if (imgFile.exists() && FileUtils.isSafeEvidenceFile(context, imgFile)) {
                val bitmap = decodeSampledBitmap(imgFile.absolutePath, 700, 500)
                if (bitmap != null) {
                    try {
                        val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                        val targetRatio = imgBoxW / imgBoxH
                        val finalW: Float
                        val finalH: Float
                        if (srcRatio > targetRatio) {
                            finalW = imgBoxW
                            finalH = imgBoxW / srcRatio
                        } else {
                            finalH = imgBoxH
                            finalW = imgBoxH * srcRatio
                        }
                        val drawX = imgX + (imgBoxW - finalW) / 2f
                        val drawY = imgY + (imgBoxH - finalH) / 2f
                        canvas.drawRect(drawX - 1f, drawY - 1f, drawX + finalW + 1f, drawY + finalH + 1f, photoFramePaint)
                        val destRect = Rect(drawX.toInt(), drawY.toInt(), (drawX + finalW).toInt(), (drawY + finalH).toInt())
                        canvas.drawBitmap(bitmap, null, destRect, null)
                    } finally {
                        bitmap.recycle()
                    }
                }
            }

            // Captions below photo
            var textY = imgY + imgBoxH + 16f
            canvas.drawText("Kegiatan:", cardX + 16f, textY, labelPaint)
            val descLayout = createStaticLayout(photo.description, textPaint, (cardW - 90f).toInt())
            canvas.save()
            canvas.translate(cardX + 65f, textY - 8f)
            descLayout.draw(canvas)
            canvas.restore()

            if (!photo.evidenceText.isNullOrBlank()) {
                textY += descLayout.height.toFloat() + 4f
                canvas.drawText("Bukti Teks:", cardX + 16f, textY, labelPaint)
                canvas.drawText(photo.evidenceText, cardX + 65f, textY, textPaint)
            }

            curY += cardHeight + 15f
        }

        drawPageNumber(canvas, textPaint, pageNumber)
        document.finishPage(page)
        return pageNumber
    }

    private fun decodeSampledBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, options)
        options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
        options.inJustDecodeBounds = false
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(options: BitmapFactory.Options, reqWidth: Int, reqHeight: Int): Int {
        val height = options.outHeight
        val width = options.outWidth
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
