import { jsPDF } from "jspdf";
import autoTable from "jspdf-autotable";
import { formatMonthYear, formatShortDate, formatDate } from "./dateUtils";
import { isSafeImageDataUrl } from "./db";

let robotoRegularBinary = null;
let robotoBoldBinary = null;

async function loadRobotoFonts(doc) {
  try {
    if (!robotoRegularBinary || !robotoBoldBinary) {
      const [regRes, boldRes] = await Promise.all([
        fetch('./fonts/Roboto-Regular.ttf'),
        fetch('./fonts/Roboto-Bold.ttf')
      ]);
      if (!regRes.ok || !boldRes.ok) throw new Error('Failed to fetch Roboto fonts');
      const [regBuf, boldBuf] = await Promise.all([
        regRes.arrayBuffer(),
        boldRes.arrayBuffer()
      ]);

      const toBinary = (buf) => {
        let binary = '';
        const bytes = new Uint8Array(buf);
        const len = bytes.byteLength;
        for (let i = 0; i < len; i++) {
          binary += String.fromCharCode(bytes[i]);
        }
        return binary;
      };

      robotoRegularBinary = toBinary(regBuf);
      robotoBoldBinary = toBinary(boldBuf);
    }

    doc.addFileToVFS('Roboto-Regular.ttf', robotoRegularBinary);
    doc.addFont('Roboto-Regular.ttf', 'Roboto', 'normal');
    doc.addFileToVFS('Roboto-Bold.ttf', robotoBoldBinary);
    doc.addFont('Roboto-Bold.ttf', 'Roboto', 'bold');
    return 'Roboto';
  } catch (err) {
    console.warn('Gagal memuat font Roboto custom, beralih ke helvetica:', err);
    return 'helvetica';
  }
}

function calculateCollageSlots(images, innerX, innerY, innerW) {
  const n = images.length;
  const slots = [];
  if (n === 1) {
    const boxW = 280;
    const boxH = 190;
    const boxX = innerX + (innerW - boxW) / 2;
    slots.push({ image: images[0], x: boxX, y: innerY, w: boxW, h: boxH });
    return { slots, totalH: boxH };
  } else if (n === 2) {
    const gap = 10;
    const boxW = (innerW - gap) / 2;
    const boxH = 160;
    slots.push({ image: images[0], x: innerX, y: innerY, w: boxW, h: boxH });
    slots.push({ image: images[1], x: innerX + boxW + gap, y: innerY, w: boxW, h: boxH });
    return { slots, totalH: boxH };
  } else if (n === 3) {
    const gap = 8;
    const boxW = (innerW - 2 * gap) / 3;
    const boxH = 120;
    for (let i = 0; i < 3; i++) {
      slots.push({ image: images[i], x: innerX + i * (boxW + gap), y: innerY, w: boxW, h: boxH });
    }
    return { slots, totalH: boxH };
  } else if (n === 4) {
    const gapX = 10;
    const gapY = 8;
    const boxW = (innerW - gapX) / 2;
    const boxH = 135;
    for (let i = 0; i < 4; i++) {
      const col = i % 2;
      const row = Math.floor(i / 2);
      slots.push({
        image: images[i],
        x: innerX + col * (boxW + gapX),
        y: innerY + row * (boxH + gapY),
        w: boxW,
        h: boxH
      });
    }
    return { slots, totalH: boxH * 2 + gapY };
  } else {
    const gapX = 8;
    const gapY = 8;
    const boxW = (innerW - 2 * gapX) / 3;
    const boxH = 110;
    const rows = Math.ceil(n / 3);
    for (let i = 0; i < n; i++) {
      const col = i % 3;
      const row = Math.floor(i / 3);
      slots.push({
        image: images[i],
        x: innerX + col * (boxW + gapX),
        y: innerY + row * (boxH + gapY),
        w: boxW,
        h: boxH
      });
    }
    return { slots, totalH: rows * boxH + (rows - 1) * gapY };
  }
}

export async function generateReportPdf(entries, profile, monthIndex, year, options = {}) {
  const { includePhotoAppendix = true } = options;

  const doc = new jsPDF({
    orientation: "portrait",
    unit: "pt",
    format: "a4"
  });

  const fontName = await loadRobotoFonts(doc);

  const periodTitle = formatMonthYear(monthIndex, year);
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();

  // Header Title
  doc.setFont(fontName, "bold");
  doc.setFontSize(13);
  doc.setTextColor(0, 0, 0);
  doc.text("LAPORAN JURNAL KEGIATAN KERJA HARIAN PEGAWAI", pageWidth / 2, 45, { align: "center" });

  doc.setFontSize(11);
  doc.setFont(fontName, "bold");
  doc.setTextColor(80, 80, 80);
  doc.text(`Periode: ${periodTitle}`, pageWidth / 2, 62, { align: "center" });

  // 1. Jarak header dengan identitas (+1 enter, infoY = 104)
  const leftX = 36;
  const midX = 300;
  let infoY = 104;

  doc.setFontSize(11);
  doc.setFont(fontName, "bold");
  doc.setTextColor(0, 0, 0);
  doc.text("Nama Pegawai", leftX, infoY);
  doc.setFont(fontName, "normal");
  doc.text(`: ${profile?.fullName || "-"}`, leftX + 85, infoY);

  doc.setFont(fontName, "bold");
  doc.text("Jabatan", midX, infoY);
  doc.setFont(fontName, "normal");
  doc.text(`: ${profile?.position || "Staf Tata Usaha"}`, midX + 65, infoY);

  infoY += 15;
  doc.setFont(fontName, "bold");
  doc.text("NIP / ID", leftX, infoY);
  doc.setFont(fontName, "normal");
  doc.text(`: ${profile?.employeeId || "-"}`, leftX + 85, infoY);

  doc.setFont(fontName, "bold");
  doc.text("Unit Kerja", midX, infoY);
  doc.setFont(fontName, "normal");
  doc.text(`: ${profile?.schoolName || "SMK Negeri 2 Tanjung"}`, midX + 65, infoY);

  // Group entries by date
  const grouped = {};
  entries.forEach(e => {
    if (!grouped[e.formattedDate]) {
      grouped[e.formattedDate] = {
        dayOfWeek: e.dayOfWeek,
        dateMillis: e.dateMillis,
        tasks: []
      };
    }
    grouped[e.formattedDate].tasks.push(e);
  });

  // Ukuran thumbnail selebar kolom Bukti Dukung (140 - 14 = 126 pt)
  const THUMB_WIDTH = 126;
  const THUMB_HEIGHT = 84;

  // Prepare table data and activity photo list for collage
  const activityAttachments = [];
  const rowsMeta = [];

  const tableBody = Object.entries(grouped).map(([dateStr, data], index) => {
    const isMulti = data.tasks.length > 1;

    // Spacing 1.0 (baris baru tanpa enter ganda berlebih)
    const descText = isMulti
      ? data.tasks.map((t, idx) => `${idx + 1}. ${t.description}`).join("\n")
      : (data.tasks[0]?.description || "-");

    // Col 3: Bukti Dukung (Format teks rapi + thumbnail foto selebar kolom)
    const cellThumbnails = [];
    data.tasks.forEach((t) => {
      const rawImages = (t.evidenceImages && Array.isArray(t.evidenceImages) && t.evidenceImages.length > 0)
        ? t.evidenceImages
        : (t.evidenceImage ? [t.evidenceImage] : []);
      const taskImages = rawImages.filter(isSafeImageDataUrl);

      if (taskImages.length > 0) {
        activityAttachments.push({
          dayOfWeek: data.dayOfWeek,
          formattedDate: dateStr,
          description: t.description,
          evidenceText: (t.evidenceText || "").trim(),
          images: taskImages
        });
      }
    });

    const evidenceLines = data.tasks.map((t, idx) => {
      const prefix = isMulti ? `${idx + 1}. ` : "";
      const text = (t.evidenceText || "").trim();
      const parts = [];

      if (text) parts.push(text);

      const rawImages = (t.evidenceImages && Array.isArray(t.evidenceImages) && t.evidenceImages.length > 0)
        ? t.evidenceImages
        : (t.evidenceImage ? [t.evidenceImage] : []);
      const taskImages = rawImages.filter(isSafeImageDataUrl);

      if (taskImages.length > 0) {
        // Di tabel laporan utama, jika ada lebih dari satu foto, hanya dimunculkan 1 foto thumbnail
        const thumbImages = taskImages.slice(0, 1);
        thumbImages.forEach((img, pIdx) => {
          cellThumbnails.push({
            taskIdx: idx,
            photoIdx: pIdx,
            image: img,
            hasText: !!text
          });
        });
      } else if (!text) {
        parts.push("-");
      }

      // Spasi untuk thumbnail selebar kolom (hanya 1 foto thumbnail di laporan utama)
      const spacingForThumb = taskImages.length > 0 ? "\n\n\n\n\n\n" : "";
      return `${prefix}${parts.join(" ")}${spacingForThumb}`;
    });

    const evidenceText = evidenceLines.join("\n") || "-";

    rowsMeta.push({
      thumbnails: cellThumbnails,
      tasksCount: data.tasks.length
    });

    return [
      index + 1,
      `${data.dayOfWeek}\n${formatShortDate(data.dateMillis)}`,
      descText,
      evidenceText
    ];
  });

  // Jarak identitas dengan tabel (+1 enter, startY: 145)
  // Ukuran font tabel 11 pt, Head table bold & rata tengah
  autoTable(doc, {
    startY: 145,
    margin: { left: 36, right: 36 },
    head: [["No", "Hari / Tanggal", "Uraian Pekerjaan / Kegiatan", "Bukti Dukung"]],
    body: tableBody,
    theme: "grid",
    headStyles: {
      font: fontName,
      fillColor: [232, 234, 246],
      textColor: [0, 0, 0],
      fontStyle: "bold",
      fontSize: 11,
      halign: "center",
      valign: "middle"
    },
    styles: {
      font: fontName,
      fontSize: 11,
      cellPadding: 4,
      textColor: [0, 0, 0],
      valign: "top"
    },
    columnStyles: {
      0: { cellWidth: 28, halign: "center" },
      1: { cellWidth: 90 },
      2: { cellWidth: 265 },
      3: { cellWidth: 140 }
    },
    didDrawCell: (hookData) => {
      // Gambar thumbnail foto selebar kolom di kolom Bukti Dukung
      if (hookData.section === "body" && hookData.column.index === 3) {
        const rowInfo = rowsMeta[hookData.row.index];
        if (rowInfo && rowInfo.thumbnails && rowInfo.thumbnails.length > 0) {
          let thumbY = hookData.cell.y + (rowInfo.thumbnails[0].hasText ? 16 : 5);
          rowInfo.thumbnails.forEach((thumb) => {
            const thumbX = hookData.cell.x + 7;
            try {
              // Frame border
              doc.setDrawColor(207, 216, 220);
              doc.rect(thumbX - 1, thumbY - 1, THUMB_WIDTH + 2, THUMB_HEIGHT + 2);
              // Gambar foto selebar kolom
              doc.addImage(thumb.image, "JPEG", thumbX, thumbY, THUMB_WIDTH, THUMB_HEIGHT);
            } catch (err) {
              console.warn("Gagal render thumbnail PDF:", err);
            }
            thumbY += THUMB_HEIGHT + 6;
          });
        }
      }
    }
  });

  // Signatures Block: Spasi tanda tangan (+2 enter, 75 pt)
  let finalY = doc.lastAutoTable.finalY + 20;
  if (finalY + 240 > pageHeight - 40) {
    doc.addPage();
    finalY = 50;
  }

  const city = profile?.signatureCity || "Tabalong";
  const todayStr = formatDate(Date.now());
  const rightX = pageWidth - 190;

  doc.setFontSize(11);
  doc.setFont(fontName, "normal");
  doc.text(`${city}, ${todayStr}`, rightX, finalY);

  finalY += 16;
  doc.text("Memeriksa / Mengetahui,", leftX, finalY);
  doc.text("Pegawai Yang Bersangkutan,", rightX, finalY);

  finalY += 15;
  doc.text(profile?.subbagPosition || "Kepala Sub Bagian Tata Usaha", leftX, finalY);
  doc.text(profile?.position || "Staf Tata Usaha", rightX, finalY);

  // Spasi tanda tangan +2 enter
  finalY += 75;
  doc.setFont(fontName, "bold");
  doc.text(profile?.subbagName || "( ................................... )", leftX, finalY);
  doc.text(profile?.fullName || "( ................................... )", rightX, finalY);

  finalY += 15;
  doc.setFont(fontName, "normal");
  doc.text(`NIP. ${profile?.subbagId || "-"}`, leftX, finalY);
  doc.text(`NIP. ${profile?.employeeId || "-"}`, rightX, finalY);

  // Kepala Sekolah (Center)
  finalY += 24;
  doc.text("Mengesahkan,", pageWidth / 2, finalY, { align: "center" });
  finalY += 15;
  doc.text(profile?.headmasterPosition || "Kepala Sekolah", pageWidth / 2, finalY, { align: "center" });

  // Spasi tanda tangan Kepala Sekolah +2 enter
  finalY += 75;
  doc.setFont(fontName, "bold");
  doc.text(profile?.headmasterName || "( ................................... )", pageWidth / 2, finalY, { align: "center" });
  finalY += 15;
  doc.setFont(fontName, "normal");
  doc.text(`NIP. ${profile?.headmasterId || "-"}`, pageWidth / 2, finalY, { align: "center" });

  // ========================================================
  // BAGIAN LAMPIRAN BUKTI DOKUMENTASI FOTO (KOLASE PER KEGIATAN)
  // ========================================================
  if (includePhotoAppendix && activityAttachments.length > 0) {
    doc.addPage();
    let curY = 45;

    // Header Halaman Lampiran (hanya dicetak 1 kali di lembar pertama)
    doc.setFont(fontName, "bold");
    doc.setFontSize(13);
    doc.setTextColor(0, 0, 0);
    doc.text("LAMPIRAN BUKTI DOKUMENTASI FOTO KEGIATAN", pageWidth / 2, curY, { align: "center" });
    curY += 16;
    doc.setFontSize(11);
    doc.setFont(fontName, "bold");
    doc.setTextColor(80, 80, 80);
    doc.text(`Periode: ${periodTitle}`, pageWidth / 2, curY, { align: "center" });
    curY += 14;
    doc.setFontSize(11);
    doc.setFont(fontName, "normal");
    doc.setTextColor(71, 85, 105);
    doc.text(`${profile?.schoolName || "SMK Negeri 2 Tanjung"} • Pegawai: ${profile?.fullName || "-"}`, pageWidth / 2, curY, { align: "center" });
    curY += 10;
    doc.setDrawColor(200, 200, 200);
    doc.line(36, curY, pageWidth - 36, curY);
    curY += 16;

    const cardX = 36;
    const cardW = pageWidth - 72;
    const innerPadding = 12;
    const innerW = cardW - (2 * innerPadding);

    activityAttachments.forEach((act, actIdx) => {
      const splitDesc = doc.splitTextToSize(act.description || "-", cardW - 95);
      const descHeight = splitDesc.length * 13;
      const evHeight = act.evidenceText ? 18 : 0;

      const { totalH: photoAreaHeight } = calculateCollageSlots(act.images, cardX + innerPadding, 0, innerW);
      const totalCardHeight = 24 + 8 + photoAreaHeight + 12 + descHeight + evHeight + 14;

      // Pindah halaman jika kartu melebihi batas bawah (tanpa mengulang header)
      if (curY + totalCardHeight > pageHeight - 40) {
        doc.addPage();
        curY = 50;
      }

      // Card Border & Background
      doc.setDrawColor(215, 220, 228);
      doc.setFillColor(255, 255, 255);
      doc.roundedRect(cardX, curY, cardW, totalCardHeight, 6, 6, "FD");

      // Header Badge
      doc.setFillColor(241, 245, 249);
      doc.rect(cardX + 1, curY + 1, cardW - 2, 24, "F");
      doc.setFont(fontName, "bold");
      doc.setFontSize(11);
      doc.setTextColor(15, 23, 42);
      doc.text(`Dokumentasi #${actIdx + 1} • ${act.dayOfWeek}, ${act.formattedDate}`, cardX + 10, curY + 16);

      // Badge Count di kanan
      doc.setFontSize(10);
      doc.setTextColor(100, 116, 139);
      doc.text(`${act.images.length} Foto Terlampir`, cardX + cardW - 12, curY + 16, { align: "right" });

      // Render Kolase Foto
      const photoStartY = curY + 32;
      const { slots } = calculateCollageSlots(act.images, cardX + innerPadding, photoStartY, innerW);

      slots.forEach((slot) => {
        try {
          doc.setDrawColor(226, 232, 240);
          doc.rect(slot.x - 1, slot.y - 1, slot.w + 2, slot.h + 2);
          doc.addImage(slot.image, "JPEG", slot.x, slot.y, slot.w, slot.h);
        } catch (err) {
          console.warn("Gagal render foto kolase:", err);
        }
      });

      // Keterangan di bawah foto (HANYA DITULIS 1 KALI PER KEGIATAN)
      let textY = photoStartY + photoAreaHeight + 14;
      doc.setFont(fontName, "bold");
      doc.setFontSize(11);
      doc.setTextColor(71, 85, 105);
      doc.text("Kegiatan:", cardX + 16, textY);
      doc.setFont(fontName, "normal");
      doc.setTextColor(15, 23, 42);
      doc.text(splitDesc, cardX + 75, textY);

      if (act.evidenceText) {
        textY += descHeight + 4;
        doc.setFont(fontName, "bold");
        doc.setTextColor(71, 85, 105);
        doc.text("Bukti Teks:", cardX + 16, textY);
        doc.setFont(fontName, "normal");
        doc.setTextColor(15, 23, 42);
        doc.text(act.evidenceText, cardX + 75, textY);
      }

      curY += totalCardHeight + 14;
    });
  }

  // Nomor Halaman Seragam di Seluruh Halaman (Footer)
  const totalPages = doc.internal.getNumberOfPages();
  for (let i = 1; i <= totalPages; i++) {
    doc.setPage(i);
    doc.setFont(fontName, "normal");
    doc.setFontSize(9.5);
    doc.setTextColor(120, 120, 120);
    doc.text(`Hal. ${i} dari ${totalPages}`, pageWidth - 36, pageHeight - 16, { align: "right" });
  }

  // Unduh Berkas PDF (sanitasi nama file)
  const safeFileName = periodTitle.replace(/[^a-zA-Z0-9_-]/g, "_");
  doc.save(`EJournal_${safeFileName}.pdf`);
}
