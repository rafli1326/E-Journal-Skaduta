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

  // Header Title
  doc.setFont("helvetica", "bold");
  doc.setFontSize(11);
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

  const tableBody = Object.entries(grouped).map(([dateStr, data], index) => {
    const descText = data.tasks.length === 1
      ? data.tasks[0].description
      : data.tasks.map((t, idx) => `${idx + 1}. ${t.description}`).join("\n\n");

    const evidenceText = data.tasks.map((t, idx) => {
      const prefix = data.tasks.length > 1 ? `${idx + 1}. ` : "";
      const text = t.evidenceText ? `${prefix}${t.evidenceText}` : "";
      const hasPhoto = t.evidenceImage ? `${prefix}[Foto Terlampir]` : "";
      return [text, hasPhoto].filter(Boolean).join(" ");
    }).filter(Boolean).join("\n\n") || "-";

    return [
      index + 1,
      `${data.dayOfWeek}\n${formatShortDate(data.dateMillis)}`,
      descText,
      evidenceText
    ];
  });

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
    }
  });

  // Signatures Block
  let finalY = doc.lastAutoTable.finalY + 20;
  if (finalY + 140 > doc.internal.pageSize.getHeight() - 40) {
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

  // Save / Download PDF
  const safeMonth = formatMonthYear(monthIndex, year).replace(/\s+/g, "_");
  doc.save(`EJournal_${safeMonth}.pdf`);
}

