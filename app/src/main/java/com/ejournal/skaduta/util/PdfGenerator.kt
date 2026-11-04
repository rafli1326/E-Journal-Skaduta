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

    // Column widths (Total = 523f)
    private const val COL_NO_W = 28f
    private const val COL_DATE_W = 90f
    private const val COL_DESC_W = 265f
    private const val COL_EVIDENCE_W = 140f

    // Thumbnail selebar kolom di dalam sel tabel Bukti Dukung (140 - 14 = 126f)
    private const val THUMB_WIDTH = 126f
    private const val THUMB_HEIGHT = 84f

    data class DayReportRow(
        val dayOfWeek: String,
        val formattedDate: String,
        val dateMillis: Long,
        val tasks: List<JournalEntry>
    )

    data class ActivityPhotoAttachment(
        val dayOfWeek: String,
        val formattedDate: String,
        val description: String,
        val evidenceText: String?,
        val imagePaths: List<String>
    )

    private data class PhotoSlot(
        val path: String,
        val bounds: RectF
    )

    private fun getRobotoTypeface(context: Context, isBold: Boolean): Typeface {
        return try {
            val assetPath = if (isBold) "fonts/Roboto-Bold.ttf" else "fonts/Roboto-Regular.ttf"
            Typeface.createFromAsset(context.assets, assetPath)
        } catch (_: Exception) {
            Typeface.create("sans-serif", if (isBold) Typeface.BOLD else Typeface.NORMAL)
        }
    }

    fun generateMonthlyReport(
        context: Context,
        entries: List<JournalEntry>,
        profile: UserProfile?,
        monthIndex: Int,
        year: Int,
        includePhotoAppendix: Boolean = true
    ): File {
        val document = PdfDocument()

        val periodTitle = DateUtils.formatMonthYear(monthIndex, year)
        val reportsDir = File(context.filesDir, "generated_reports")
        if (!reportsDir.exists()) reportsDir.mkdirs()

        val safeMonth = DateUtils.indonesianMonths.getOrElse(monthIndex) { "Bulan" }
            .replace(Regex("[^a-zA-Z0-9_-]"), "_")
        val outputFile = File(reportsDir, "EJournal_${safeMonth}_${year}.pdf")

        try {
            val robotoNormal = getRobotoTypeface(context, false)
            val robotoBold = getRobotoTypeface(context, true)

            var pageNumber = 1
            var pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            // Paints: Isi Tabel menggunakan ukuran 11 pt
            val textPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 11f
                typeface = robotoNormal
                isAntiAlias = true
            }

            val boldTextPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 11f
                typeface = robotoBold
                isAntiAlias = true
            }

            // Head table bold, ukuran 11 pt, dan rata tengah
            val centerHeaderPaint = TextPaint().apply {
                color = Color.BLACK
                textSize = 11f
                typeface = robotoBold
                textAlign = Paint.Align.CENTER
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

            // Draw Document Title & Employee Info Box (dengan 1 enter pemisah)
            var currentY = drawDocumentHeaderAndInfo(canvas, robotoNormal, robotoBold, profile, periodTitle, MARGIN_TOP)

            // Draw Table Header (Bold & Center-aligned)
            currentY = drawTableHeader(canvas, headerBgPaint, borderPaint, centerHeaderPaint, currentY)

            // Group entries by date
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
                    currentY = drawTableHeader(canvas, headerBgPaint, borderPaint, centerHeaderPaint, currentY)
                }

                drawGroupedTableRow(context, canvas, borderPaint, textPaint, row, dayNumber, currentY, rowHeight)
                currentY += rowHeight
                dayNumber++
            }

            // Check space for 3-party Signature Block (needs ~240pt karena spasi ttd diberi +2 enter)
            if (currentY + 240f > MARGIN_BOTTOM) {
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
            drawThreePartySignatures(canvas, robotoNormal, robotoBold, profile, currentY)
            drawPageNumber(canvas, textPaint, pageNumber)

            document.finishPage(page)

            // ========================================================
            // BAGIAN LAMPIRAN BUKTI DOKUMENTASI FOTO (KOLASE PER KEGIATAN)
            // ========================================================
            if (includePhotoAppendix) {
                val activityAttachments = mutableListOf<ActivityPhotoAttachment>()
                entries.sortedBy { it.dateMillis }.forEach { entry ->
                    val paths = entry.getEvidenceImagePaths()
                    if (paths.isNotEmpty()) {
                        activityAttachments.add(
                            ActivityPhotoAttachment(
                                dayOfWeek = entry.dayOfWeek,
                                formattedDate = entry.formattedDate,
                                description = entry.description,
                                evidenceText = entry.evidenceText,
                                imagePaths = paths
                            )
                        )
                    }
                }

                if (activityAttachments.isNotEmpty()) {
                    pageNumber = drawPhotoAttachmentPages(
                        context = context,
                        document = document,
                        activityAttachments = activityAttachments,
                        profile = profile,
                        periodTitle = periodTitle,
                        startPageNumber = pageNumber + 1,
                        robotoNormal = robotoNormal,
                        robotoBold = robotoBold
                    )
                }
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
        robotoNormal: Typeface,
        robotoBold: Typeface,
        profile: UserProfile?,
        periodTitle: String,
        startY: Float
    ): Float {
        var y = startY

        val reportTitlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = robotoBold
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val subtitlePaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 11f
            typeface = robotoBold
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val infoLabelPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = robotoBold
            isAntiAlias = true
        }

        val infoValPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = robotoNormal
            isAntiAlias = true
        }

        // Document Title
        canvas.drawText("LAPORAN JURNAL KEGIATAN KERJA HARIAN PEGAWAI", PAGE_WIDTH / 2f, y + 10f, reportTitlePaint)
        y += 24f
        canvas.drawText("Periode: $periodTitle", PAGE_WIDTH / 2f, y, subtitlePaint)

        // Jarak header dengan identitas diberi +1 enter (~36f)
        y += 36f

        // Employee Info Box (Clean & Formal)
        val leftX = MARGIN_LEFT
        val midX = MARGIN_LEFT + 265f

        val name = profile?.fullName?.ifBlank { "-" } ?: "-"
        val nip = profile?.employeeId?.ifBlank { "-" } ?: "-"
        val pos = profile?.position?.ifBlank { "Staf Tata Usaha" } ?: "Staf Tata Usaha"
        val school = profile?.schoolName?.ifBlank { "SMK Negeri 2 Tanjung" } ?: "SMK Negeri 2 Tanjung"

        canvas.drawText("Nama Pegawai", leftX, y, infoLabelPaint)
        canvas.drawText(": $name", leftX + 85f, y, infoValPaint)

        canvas.drawText("Jabatan", midX, y, infoLabelPaint)
        canvas.drawText(": $pos", midX + 65f, y, infoValPaint)
        y += 15f

        canvas.drawText("NIP / ID", leftX, y, infoLabelPaint)
        canvas.drawText(": $nip", leftX + 85f, y, infoValPaint)

        canvas.drawText("Unit Kerja", midX, y, infoLabelPaint)
        canvas.drawText(": $school", midX + 65f, y, infoValPaint)

        // Jarak identitas dengan tabel diberi +1 enter (~34f)
        y += 34f

        return y
    }

    private fun drawTableHeader(
        canvas: Canvas,
        bgPaint: Paint,
        borderPaint: Paint,
        headerPaint: TextPaint,
        startY: Float
    ): Float {
        val h = 24f
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

        val textY = startY + 16f
        val cx0 = x0 + COL_NO_W / 2f
        val cx1 = x1 + COL_DATE_W / 2f
        val cx2 = x2 + COL_DESC_W / 2f
        val cx3 = x3 + COL_EVIDENCE_W / 2f

        // Center aligned headers dengan ukuran 11 pt bold
        canvas.drawText("No", cx0, textY, headerPaint)
        canvas.drawText("Hari / Tanggal", cx1, textY, headerPaint)
        canvas.drawText("Uraian Pekerjaan / Kegiatan", cx2, textY, headerPaint)
        canvas.drawText("Bukti Dukung", cx3, textY, headerPaint)

        return startY + h
    }

    private fun formatGroupedDescription(tasks: List<JournalEntry>): String {
        return if (tasks.size == 1) {
            tasks[0].description
        } else {
            // Spacing 1.0 (baris baru tanpa enter ganda berlebih)
            tasks.mapIndexed { idx, t -> "${idx + 1}. ${t.description}" }.joinToString("\n")
        }
    }

    private fun calculateGroupedRowHeight(row: DayReportRow, textPaint: TextPaint): Float {
        val descString = formatGroupedDescription(row.tasks)
        val descLayout = createStaticLayout(descString, textPaint, (COL_DESC_W - 14).toInt())
        val descHeight = descLayout.height.toFloat()

        var evidenceHeight = 0f
        row.tasks.forEach { task ->
            var itemEvHeight = 0f
            if (!task.evidenceText.isNullOrBlank()) {
                val evLayout = createStaticLayout(task.evidenceText, textPaint, (COL_EVIDENCE_W - 14).toInt())
                itemEvHeight += evLayout.height.toFloat() + 3f
            }
            val imgPaths = task.getEvidenceImagePaths()
            if (imgPaths.isNotEmpty()) {
                // Di tabel laporan utama, jika ada lebih dari satu foto, hanya dimunculkan 1 foto thumbnail
                itemEvHeight += THUMB_HEIGHT + 6f
            }
            if (itemEvHeight == 0f) {
                itemEvHeight = 14f
            }
            evidenceHeight += itemEvHeight + 3f
        }

        val contentMax = maxOf(descHeight, evidenceHeight, 22f)
        return contentMax + 10f // Spacing 1.0 yang pas dan rapi
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

        // Col 1: Number (Centered)
        val centerNumPaint = TextPaint(textPaint).apply { textAlign = Paint.Align.CENTER }
        canvas.drawText(number.toString(), x0 + COL_NO_W / 2f, startY + 15f, centerNumPaint)

        // Col 2: Hari / Tanggal
        canvas.drawText(row.dayOfWeek, x1 + 6f, startY + 15f, textPaint)
        val shortDate = DateUtils.formatShortDate(row.dateMillis)
        canvas.drawText(shortDate, x1 + 6f, startY + 29f, textPaint)

        // Col 3: Description (Spacing 1.0)
        val fullDesc = formatGroupedDescription(row.tasks)
        canvas.save()
        canvas.translate(x2 + 6f, startY + 5f)
        val descLayout = createStaticLayout(fullDesc, textPaint, (COL_DESC_W - 12).toInt())
        descLayout.draw(canvas)
        canvas.restore()

        // Col 4: Bukti Dukung (Teks dan/atau Thumbnail Foto Selebar Kolom)
        var currentEvY = startY + 5f
        row.tasks.forEachIndexed { idx, task ->
            val numPrefix = if (row.tasks.size > 1) "${idx + 1}. " else ""
            val hasText = !task.evidenceText.isNullOrBlank()
            val imgPaths = task.getEvidenceImagePaths()

            if (hasText) {
                val textWithNum = "$numPrefix${task.evidenceText}"
                canvas.save()
                canvas.translate(x3 + 6f, currentEvY)
                val evLayout = createStaticLayout(textWithNum, textPaint, (COL_EVIDENCE_W - 12).toInt())
                evLayout.draw(canvas)
                canvas.restore()
                currentEvY += evLayout.height.toFloat() + 3f
            }

            if (imgPaths.isNotEmpty()) {
                val thumbX = x3 + (COL_EVIDENCE_W - THUMB_WIDTH) / 2f
                val framePaint = Paint().apply {
                    color = Color.parseColor("#CFD8DC")
                    style = Paint.Style.STROKE
                    strokeWidth = 0.75f
                }

                // Di tabel laporan utama, jika ada lebih dari 1 foto, hanya 1 foto thumbnail yang dimunculkan
                val firstPath = imgPaths.first()
                val imgFile = File(firstPath)
                if (imgFile.exists() && FileUtils.isSafeEvidenceFile(context, imgFile)) {
                    val bitmap = decodeSampledBitmap(imgFile.absolutePath, 300, 200)
                    if (bitmap != null) {
                        try {
                            val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                            val boxRatio = THUMB_WIDTH / THUMB_HEIGHT
                            val finalW: Float
                            val finalH: Float
                            if (srcRatio > boxRatio) {
                                finalW = THUMB_WIDTH
                                finalH = THUMB_WIDTH / srcRatio
                            } else {
                                finalH = THUMB_HEIGHT
                                finalW = THUMB_HEIGHT * srcRatio
                            }
                            val drawX = thumbX + (THUMB_WIDTH - finalW) / 2f
                            val drawY = currentEvY + (THUMB_HEIGHT - finalH) / 2f

                            canvas.drawRect(drawX - 1f, drawY - 1f, drawX + finalW + 1f, drawY + finalH + 1f, framePaint)
                            val destRect = Rect(drawX.toInt(), drawY.toInt(), (drawX + finalW).toInt(), (drawY + finalH).toInt())
                            canvas.drawBitmap(bitmap, null, destRect, null)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
                currentEvY += THUMB_HEIGHT + 6f
            }

            if (!hasText && imgPaths.isEmpty()) {
                canvas.drawText("${numPrefix}-", x3 + 6f, currentEvY + 11f, textPaint)
                currentEvY += 15f
            }

            currentEvY += 2f
        }
    }

    private fun drawThreePartySignatures(
        canvas: Canvas,
        robotoNormal: Typeface,
        robotoBold: Typeface,
        profile: UserProfile?,
        startY: Float
    ) {
        val textPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = robotoNormal
            isAntiAlias = true
        }
        val boldPaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 11f
            typeface = robotoBold
            isAntiAlias = true
        }
        val centerTextPaint = TextPaint(textPaint).apply { textAlign = Paint.Align.CENTER }
        val centerBoldPaint = TextPaint(boldPaint).apply { textAlign = Paint.Align.CENTER }

        val city = profile?.signatureCity?.ifBlank { "Tabalong" } ?: "Tabalong"
        val currentDate = DateUtils.formatDate(System.currentTimeMillis())

        val leftX = MARGIN_LEFT + 15f
        val rightX = MARGIN_RIGHT - 180f
        val centerX = PAGE_WIDTH / 2f

        var y = startY

        // Penetapan Tanggal
        canvas.drawText("$city, $currentDate", rightX, y, textPaint)
        y += 16f

        // Jabatan Penandatangan 1 & 2
        canvas.drawText("Memeriksa / Mengetahui,", leftX, y, textPaint)
        canvas.drawText("Pegawai Yang Bersangkutan,", rightX, y, textPaint)
        y += 15f

        val subbagPos = profile?.subbagPosition?.ifBlank { "Kepala Sub Bagian Tata Usaha" } ?: "Kepala Sub Bagian Tata Usaha"
        val empPos = profile?.position?.ifBlank { "Staf Tata Usaha" } ?: "Staf Tata Usaha"
        canvas.drawText(subbagPos, leftX, y, textPaint)
        canvas.drawText(empPos, rightX, y, textPaint)

        // Ruang Tanda Tangan: Diberi +2 enter (75f)
        y += 75f

        // Nama Pejabat 1 & 2
        val subbagName = profile?.subbagName?.ifBlank { "( ................................... )" } ?: "( ................................... )"
        val empName = profile?.fullName?.ifBlank { "( ................................... )" } ?: "( ................................... )"
        canvas.drawText(subbagName, leftX, y, boldPaint)
        canvas.drawText(empName, rightX, y, boldPaint)
        y += 15f

        // NIP Pejabat 1 & 2
        val subbagNip = profile?.subbagId?.ifBlank { "-" } ?: "-"
        val empNip = profile?.employeeId?.ifBlank { "-" } ?: "-"
        canvas.drawText("NIP. $subbagNip", leftX, y, textPaint)
        canvas.drawText("NIP. $empNip", rightX, y, textPaint)

        // Penandatangan 3: Kepala Sekolah (Tengah Bawah)
        y += 24f
        canvas.drawText("Mengesahkan,", centerX, y, centerTextPaint)
        y += 15f

        val headmasterPos = profile?.headmasterPosition?.ifBlank { "Kepala Sekolah" } ?: "Kepala Sekolah"
        canvas.drawText(headmasterPos, centerX, y, centerTextPaint)

        // Ruang Tanda Tangan Kepala Sekolah: Diberi +2 enter (75f)
        y += 75f

        val headmasterName = profile?.headmasterName?.ifBlank { "( ................................... )" } ?: "( ................................... )"
        canvas.drawText(headmasterName, centerX, y, centerBoldPaint)
        y += 15f

        val headmasterNip = profile?.headmasterId?.ifBlank { "-" } ?: "-"
        canvas.drawText("NIP. $headmasterNip", centerX, y, centerTextPaint)
    }

    private fun drawPageNumber(canvas: Canvas, textPaint: TextPaint, pageNumber: Int) {
        val pageTextPaint = TextPaint(textPaint).apply {
            textSize = 9.5f
            color = Color.DKGRAY
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText("Hal. $pageNumber", MARGIN_RIGHT, PAGE_HEIGHT - 20f, pageTextPaint)
    }

    // Spacing 1.0 (multiplier = 1.0f, add = 0f)
    private fun createStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        width: Int
    ): StaticLayout {
        return StaticLayout.Builder.obtain(text, 0, text.length, paint, maxOf(width, 10))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, 1.0f)
            .setIncludePad(false)
            .build()
    }

    private fun calculateCollageLayout(
        paths: List<String>,
        innerX: Float,
        innerY: Float,
        innerW: Float
    ): Pair<List<PhotoSlot>, Float> {
        val n = paths.size
        val slots = mutableListOf<PhotoSlot>()

        when (n) {
            1 -> {
                val boxW = 280f
                val boxH = 190f
                val boxX = innerX + (innerW - boxW) / 2f
                slots.add(PhotoSlot(paths[0], RectF(boxX, innerY, boxX + boxW, innerY + boxH)))
                return Pair(slots, boxH)
            }
            2 -> {
                val gap = 10f
                val boxW = (innerW - gap) / 2f
                val boxH = 160f
                slots.add(PhotoSlot(paths[0], RectF(innerX, innerY, innerX + boxW, innerY + boxH)))
                slots.add(PhotoSlot(paths[1], RectF(innerX + boxW + gap, innerY, innerX + 2 * boxW + gap, innerY + boxH)))
                return Pair(slots, boxH)
            }
            3 -> {
                // 3 Foto sejajar rapi 1 baris
                val gap = 8f
                val boxW = (innerW - 2 * gap) / 3f
                val boxH = 120f
                for (i in 0 until 3) {
                    val sX = innerX + i * (boxW + gap)
                    slots.add(PhotoSlot(paths[i], RectF(sX, innerY, sX + boxW, innerY + boxH)))
                }
                return Pair(slots, boxH)
            }
            4 -> {
                // 4 Foto: Grid 2x2
                val gapX = 10f
                val gapY = 8f
                val boxW = (innerW - gapX) / 2f
                val boxH = 135f
                for (i in 0 until 4) {
                    val col = i % 2
                    val row = i / 2
                    val sX = innerX + col * (boxW + gapX)
                    val sY = innerY + row * (boxH + gapY)
                    slots.add(PhotoSlot(paths[i], RectF(sX, sY, sX + boxW, sY + boxH)))
                }
                val totalH = boxH * 2 + gapY
                return Pair(slots, totalH)
            }
            else -> {
                // 5 foto atau lebih: Grid 3 kolom
                val gapX = 8f
                val gapY = 8f
                val boxW = (innerW - 2 * gapX) / 3f
                val boxH = 110f
                val rows = (n + 2) / 3
                for (i in 0 until n) {
                    val col = i % 3
                    val row = i / 3
                    val sX = innerX + col * (boxW + gapX)
                    val sY = innerY + row * (boxH + gapY)
                    slots.add(PhotoSlot(paths[i], RectF(sX, sY, sX + boxW, sY + boxH)))
                }
                val totalH = rows * boxH + (rows - 1) * gapY
                return Pair(slots, totalH)
            }
        }
    }

    private fun drawPhotoAttachmentPages(
        context: Context,
        document: PdfDocument,
        activityAttachments: List<ActivityPhotoAttachment>,
        profile: UserProfile?,
        periodTitle: String,
        startPageNumber: Int,
        robotoNormal: Typeface,
        robotoBold: Typeface
    ): Int {
        var pageNumber = startPageNumber
        val cardW = CONTENT_WIDTH
        val cardX = MARGIN_LEFT
        val innerPadding = 12f
        val innerW = cardW - (2 * innerPadding)

        val reportTitlePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = 13f
            typeface = robotoBold
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val subtitlePaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = 11f
            typeface = robotoBold
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val smallInfoPaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 11f
            typeface = robotoNormal
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }

        val dividerPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            strokeWidth = 0.75f
        }

        val cardBorderPaint = Paint().apply {
            color = Color.parseColor("#CBD5E1")
            style = Paint.Style.STROKE
            strokeWidth = 0.75f
        }

        val cardBgPaint = Paint().apply {
            color = Color.WHITE
            style = Paint.Style.FILL
        }

        val badgeBgPaint = Paint().apply {
            color = Color.parseColor("#F1F5F9")
            style = Paint.Style.FILL
        }

        val badgeTextPaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            typeface = robotoBold
            isAntiAlias = true
        }

        val badgeCountPaint = TextPaint().apply {
            color = Color.parseColor("#64748B")
            textSize = 10f
            typeface = robotoBold
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val labelPaint = TextPaint().apply {
            color = Color.parseColor("#475569")
            textSize = 11f
            typeface = robotoBold
            isAntiAlias = true
        }

        val textPaint = TextPaint().apply {
            color = Color.parseColor("#0F172A")
            textSize = 11f
            typeface = robotoNormal
            isAntiAlias = true
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
            y += 16f
            c.drawText("Periode: $periodTitle", PAGE_WIDTH / 2f, y, subtitlePaint)
            y += 14f
            val school = profile?.schoolName?.ifBlank { "SMK Negeri 2 Tanjung" } ?: "SMK Negeri 2 Tanjung"
            val empName = profile?.fullName?.ifBlank { "-" } ?: "-"
            c.drawText("$school • Pegawai: $empName", PAGE_WIDTH / 2f, y, smallInfoPaint)
            y += 10f
            c.drawLine(MARGIN_LEFT, y, MARGIN_RIGHT, y, dividerPaint)
            return y + 16f
        }

        // Header hanya dicetak 1 kali di halaman pertama lampiran foto
        var curY = drawAttachmentHeader(canvas)

        activityAttachments.forEachIndexed { idx, activity ->
            // Hitung tinggi deskripsi teks
            val descLayout = createStaticLayout(activity.description, textPaint, (cardW - 95f).toInt())
            val descHeight = descLayout.height.toFloat()
            val evHeight = if (!activity.evidenceText.isNullOrBlank()) 18f else 0f

            // Hitung tinggi area kolase foto
            val (_, photoAreaHeight) = calculateCollageLayout(
                paths = activity.imagePaths,
                innerX = cardX + innerPadding,
                innerY = 0f,
                innerW = innerW
            )

            // Total tinggi 1 kartu kegiatan lengkap (Header + Kolase + Keterangan)
            val totalCardHeight = 24f + 8f + photoAreaHeight + 12f + descHeight + evHeight + 14f

            // Jika kartu melebihi batas bawah halaman, pindah ke halaman baru tanpa mengulang header
            if (curY + totalCardHeight > MARGIN_BOTTOM) {
                drawPageNumber(canvas, textPaint, pageNumber)
                document.finishPage(page)

                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                curY = MARGIN_TOP + 10f
            }

            // Gambar Card border & background
            val cardRect = RectF(cardX, curY, cardX + cardW, curY + totalCardHeight)
            canvas.drawRoundRect(cardRect, 6f, 6f, cardBgPaint)
            canvas.drawRoundRect(cardRect, 6f, 6f, cardBorderPaint)

            // Header badge inside card
            val badgePath = Path().apply {
                addRoundRect(
                    RectF(cardX, curY, cardX + cardW, curY + 24f),
                    floatArrayOf(6f, 6f, 6f, 6f, 0f, 0f, 0f, 0f),
                    Path.Direction.CW
                )
            }
            canvas.drawPath(badgePath, badgeBgPaint)
            canvas.drawLine(cardX, curY + 24f, cardX + cardW, curY + 24f, cardBorderPaint)

            val badgeTitle = "Dokumentasi #${idx + 1} • ${activity.dayOfWeek}, ${activity.formattedDate}"
            canvas.drawText(badgeTitle, cardX + 10f, curY + 16f, badgeTextPaint)

            // Indikator jumlah foto di kanan badge
            val countText = "${activity.imagePaths.size} Foto Terlampir"
            canvas.drawText(countText, cardX + cardW - 12f, curY + 16f, badgeCountPaint)

            // Render Kolase Foto
            val photoStartY = curY + 32f
            val (slots, _) = calculateCollageLayout(
                paths = activity.imagePaths,
                innerX = cardX + innerPadding,
                innerY = photoStartY,
                innerW = innerW
            )

            slots.forEach { slot ->
                val imgFile = File(slot.path)
                if (imgFile.exists() && FileUtils.isSafeEvidenceFile(context, imgFile)) {
                    val bitmap = decodeSampledBitmap(imgFile.absolutePath, 600, 450)
                    if (bitmap != null) {
                        try {
                            val boxW = slot.bounds.width()
                            val boxH = slot.bounds.height()
                            val srcRatio = bitmap.width.toFloat() / bitmap.height.toFloat()
                            val targetRatio = boxW / boxH
                            val finalW: Float
                            val finalH: Float
                            if (srcRatio > targetRatio) {
                                finalW = boxW
                                finalH = boxW / srcRatio
                            } else {
                                finalH = boxH
                                finalW = boxH * srcRatio
                            }
                            val drawX = slot.bounds.left + (boxW - finalW) / 2f
                            val drawY = slot.bounds.top + (boxH - finalH) / 2f

                            canvas.drawRect(drawX - 1f, drawY - 1f, drawX + finalW + 1f, drawY + finalH + 1f, photoFramePaint)
                            val destRect = Rect(drawX.toInt(), drawY.toInt(), (drawX + finalW).toInt(), (drawY + finalH).toInt())
                            canvas.drawBitmap(bitmap, null, destRect, null)
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }

            // Keterangan di bawah kolase foto (HANYA DITULIS 1 KALI)
            var textY = photoStartY + photoAreaHeight + 14f
            canvas.drawText("Kegiatan:", cardX + 16f, textY, labelPaint)
            canvas.save()
            canvas.translate(cardX + 75f, textY - 10f)
            descLayout.draw(canvas)
            canvas.restore()

            if (!activity.evidenceText.isNullOrBlank()) {
                textY += descHeight + 4f
                canvas.drawText("Bukti Teks:", cardX + 16f, textY, labelPaint)
                canvas.drawText(activity.evidenceText, cardX + 75f, textY, textPaint)
            }

            curY += totalCardHeight + 14f
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
