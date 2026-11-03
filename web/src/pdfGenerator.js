import { jsPDF } from "jspdf";
import autoTable from "jspdf-autotable";
import { formatMonthYear, formatShortDate, formatDate } from "./dateUtils";

export function generateReportPdf(entries, profile, monthIndex, year) {
  const doc = new jsPDF({
    orientation: "portrait",
    unit: "pt",
    format: "a4"
  });

  const periodTitle = formatMonthYear(monthIndex, year);
  const pageWidth = doc.internal.pageSize.getWidth();
  const pageHeight = doc.internal.pageSize.getHeight();

  // Header Title
  doc.setFont("helvetica", "bold");
  doc.setFontSize(11);
  doc.setTextColor(0, 0, 0);
  doc.text("LAPORAN JURNAL KEGIATAN KERJA HARIAN PEGAWAI", pageWidth / 2, 45, { align: "center" });

  doc.setFontSize(9);
  doc.setFont("helvetica", "normal");
  doc.text(`Periode: ${periodTitle}`, pageWidth / 2, 60, { align: "center" });

  // Employee Info
  const leftX = 36;
  const midX = 300;
  let infoY = 85;

  doc.setFontSize(8.5);
  doc.setFont("helvetica", "bold");
  doc.text("Nama Pegawai", leftX, infoY);
  doc.setFont("helvetica", "normal");
  doc.text(`: ${profile?.fullName || "-"}`, leftX + 75, infoY);

  doc.setFont("helvetica", "bold");
  doc.text("Jabatan", midX, infoY);
  doc.setFont("helvetica", "normal");
  doc.text(`: ${profile?.position || "Staf Tata Usaha"}`, midX + 65, infoY);

  infoY += 13;
  doc.setFont("helvetica", "bold");
  doc.text("NIP / ID", leftX, infoY);
  doc.setFont("helvetica", "normal");
  doc.text(`: ${profile?.employeeId || "-"}`, leftX + 75, infoY);

  doc.setFont("helvetica", "bold");
  doc.text("Unit Kerja", midX, infoY);
  doc.setFont("helvetica", "normal");
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

  // Prepare table data and photo list
  const photoAttachments = [];
  const rowsMeta = [];

  const tableBody = Object.entries(grouped).map(([dateStr, data], index) => {
    const isMulti = data.tasks.length > 1;

    // Col 2: Uraian Pekerjaan
    const descText = isMulti
      ? data.tasks.map((t, idx) => `${idx + 1}. ${t.description}`).join("\n\n")
      : (data.tasks[0]?.description || "-");

    // Col 3: Bukti Dukung (Format teks rapi + hitung kebutuhan thumbnail)
    const cellThumbnails = [];
    const evidenceLines = data.tasks.map((t, idx) => {
      const prefix = isMulti ? `${idx + 1}. ` : "";
      const text = (t.evidenceText || "").trim();
      const parts = [];

      if (text) parts.push(text);
      if (t.evidenceImage) {
        // Simpan untuk lampiran foto di akhir PDF
        photoAttachments.push({
          dayOfWeek: data.dayOfWeek,
          formattedDate: dateStr,
          description: t.description,
          evidenceText: text,
          image: t.evidenceImage
        });

        // Simpan untuk thumbnail dalam sel tabel
        cellThumbnails.push({
          taskIdx: idx,
          image: t.evidenceImage,
          hasText: !!text
        });
      } else if (!text) {
        parts.push("-");
      }

      // Berikan ruang spasi jika ada thumbnail gambar di dalam baris ini
      const spacingForThumb = t.evidenceImage ? "\n\n\n\n" : "";
      return `${prefix}${parts.join(" ")}${spacingForThumb}`;
    });

    const evidenceText = evidenceLines.join("\n\n") || "-";

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

  // Render Table
  autoTable(doc, {
    startY: 115,
    margin: { left: 36, right: 36 },
    head: [["No", "Hari / Tanggal", "Uraian Pekerjaan / Kegiatan", "Bukti Dukung"]],
    body: tableBody,
    theme: "grid",
    headStyles: {
      fillColor: [232, 234, 246],
      textColor: [0, 0, 0],
      fontStyle: "bold",
      fontSize: 8.5,
      halign: "left"
    },
    styles: {
      fontSize: 8,
      cellPadding: 5,
      textColor: [0, 0, 0],
      valign: "top"
    },
    columnStyles: {
      0: { cellWidth: 28, halign: "center" },
      1: { cellWidth: 85 },
      2: { cellWidth: 260 },
      3: { cellWidth: 150 }
    },
    didDrawCell: (hookData) => {
      // Gambar thumbnail foto langsung di kolom Bukti Dukung
      if (hookData.section === "body" && hookData.column.index === 3) {
        const rowInfo = rowsMeta[hookData.row.index];
        if (rowInfo && rowInfo.thumbnails && rowInfo.thumbnails.length > 0) {
          rowInfo.thumbnails.forEach((thumb, tIdx) => {
            const thumbY = hookData.cell.y + (thumb.hasText ? 16 : 6) + (tIdx * 46);
            const thumbX = hookData.cell.x + (rowInfo.tasksCount > 1 ? 16 : 6);
            try {
              // Frame border kecil
              doc.setDrawColor(210, 215, 222);
              doc.rect(thumbX - 1, thumbY - 1, 38, 38);
              // Gambar thumbnail foto
              doc.addImage(thumb.image, "JPEG", thumbX, thumbY, 36, 36);
            } catch (err) {
              console.warn("Gagal render thumbnail PDF:", err);
            }
          });
        }
      }
    }
  });

  // Signatures Block
  let finalY = doc.lastAutoTable.finalY + 20;
  if (finalY + 140 > pageHeight - 40) {
    doc.addPage();
    finalY = 50;
  }

  const city = profile?.signatureCity || "Tabalong";
  const todayStr = formatDate(Date.now());
  const rightX = pageWidth - 190;

  doc.setFontSize(8);
  doc.setFont("helvetica", "normal");
  doc.text(`${city}, ${todayStr}`, rightX, finalY);

  finalY += 12;
  doc.text("Memeriksa / Mengetahui,", leftX, finalY);
  doc.text("Pegawai Yang Bersangkutan,", rightX, finalY);

  finalY += 10;
  doc.text(profile?.subbagPosition || "Kepala Sub Bagian Tata Usaha", leftX, finalY);
  doc.text(profile?.position || "Staf Tata Usaha", rightX, finalY);

  finalY += 45;
  doc.setFont("helvetica", "bold");
  doc.text(profile?.subbagName || "( ................................... )", leftX, finalY);
  doc.text(profile?.fullName || "( ................................... )", rightX, finalY);

  finalY += 10;
  doc.setFont("helvetica", "normal");
  doc.text(`NIP. ${profile?.subbagId || "-"}`, leftX, finalY);
  doc.text(`NIP. ${profile?.employeeId || "-"}`, rightX, finalY);

  // Kepala Sekolah (Center)
  finalY += 20;
  doc.text("Mengesahkan,", pageWidth / 2, finalY, { align: "center" });
  finalY += 10;
  doc.text(profile?.headmasterPosition || "Kepala Sekolah", pageWidth / 2, finalY, { align: "center" });

  finalY += 45;
  doc.setFont("helvetica", "bold");
  doc.text(profile?.headmasterName || "( ................................... )", pageWidth / 2, finalY, { align: "center" });
  finalY += 10;
  doc.setFont("helvetica", "normal");
  doc.text(`NIP. ${profile?.headmasterId || "-"}`, pageWidth / 2, finalY, { align: "center" });

  // ========================================================
  // BAGIAN LAMPIRAN BUKTI DOKUMENTASI FOTO (Jika Ada Foto)
  // ========================================================
  if (photoAttachments.length > 0) {
    doc.addPage();
    let curY = 45;

    // Header Halaman Lampiran
    doc.setFont("helvetica", "bold");
    doc.setFontSize(11);
    doc.setTextColor(0, 0, 0);
    doc.text("LAMPIRAN BUKTI DOKUMENTASI FOTO KEGIATAN", pageWidth / 2, curY, { align: "center" });
    curY += 15;
    doc.setFontSize(9);
    doc.setFont("helvetica", "normal");
    doc.text(`Periode: ${periodTitle}`, pageWidth / 2, curY, { align: "center" });
    curY += 13;
    doc.setFontSize(8.5);
    doc.text(`${profile?.schoolName || "SMK Negeri 2 Tanjung"} • Pegawai: ${profile?.fullName || "-"}`, pageWidth / 2, curY, { align: "center" });
    curY += 10;
    doc.setDrawColor(200, 200, 200);
    doc.line(36, curY, pageWidth - 36, curY);
    curY += 16;

    // Render Kartu Foto (Maks 2 kartu per halaman A4 agar foto besar dan tajam)
    photoAttachments.forEach((photo, pIdx) => {
      const cardHeight = 310;
      if (curY + cardHeight > pageHeight - 40) {
        doc.addPage();
        curY = 50;
      }

      const cardX = 36;
      const cardW = pageWidth - 72;

      // Card Border & Background
      doc.setDrawColor(215, 220, 228);
      doc.setFillColor(255, 255, 255);
      doc.roundedRect(cardX, curY, cardW, cardHeight, 6, 6, "FD");

      // Badge Header Kartu
      doc.setFillColor(241, 245, 249);
      doc.rect(cardX + 1, curY + 1, cardW - 2, 22, "F");
      doc.setFont("helvetica", "bold");
      doc.setFontSize(8.5);
      doc.setTextColor(15, 23, 42);
      doc.text(`Dokumentasi #${pIdx + 1} • ${photo.dayOfWeek}, ${photo.formattedDate}`, cardX + 10, curY + 15);

      // Foto Bukti
      const imgW = 280;
      const imgH = 190;
      const imgX = cardX + (cardW - imgW) / 2;
      const imgY = curY + 30;

      try {
        doc.setDrawColor(226, 232, 240);
        doc.rect(imgX - 1, imgY - 1, imgW + 2, imgH + 2);
        doc.addImage(photo.image, "JPEG", imgX, imgY, imgW, imgH);
      } catch (err) {
        console.warn("Gagal render foto lampiran:", err);
      }

      // Keterangan di bawah foto
      let textY = imgY + imgH + 16;
      doc.setFont("helvetica", "bold");
      doc.setFontSize(8);
      doc.setTextColor(71, 85, 105);
      doc.text("Kegiatan:", cardX + 16, textY);
      doc.setFont("helvetica", "normal");
      doc.setTextColor(15, 23, 42);
      const splitDesc = doc.splitTextToSize(photo.description || "-", cardW - 100);
      doc.text(splitDesc, cardX + 65, textY);

      if (photo.evidenceText) {
        textY += (splitDesc.length * 10) + 4;
        doc.setFont("helvetica", "bold");
        doc.setTextColor(71, 85, 105);
        doc.text("Bukti Teks:", cardX + 16, textY);
        doc.setFont("helvetica", "normal");
        doc.setTextColor(15, 23, 42);
        doc.text(photo.evidenceText, cardX + 65, textY);
      }

      curY += cardHeight + 16;
    });
  }

  // Nomor Halaman Seragam di Seluruh Halaman (Footer)
  const totalPages = doc.internal.getNumberOfPages();
  for (let i = 1; i <= totalPages; i++) {
    doc.setPage(i);
    doc.setFont("helvetica", "normal");
    doc.setFontSize(7.5);
    doc.setTextColor(120, 120, 120);
    doc.text(`Hal. ${i} dari ${totalPages}`, pageWidth - 36, pageHeight - 16, { align: "right" });
  }

  // Save / Download PDF
  const safeMonth = formatMonthYear(monthIndex, year).replace(/\s+/g, "_");
  doc.save(`EJournal_${safeMonth}.pdf`);
}
