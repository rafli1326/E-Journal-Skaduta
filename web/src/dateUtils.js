export const indonesianMonths = [
  "Januari", "Februari", "Maret", "April", "Mei", "Juni",
  "Juli", "Agustus", "September", "Oktober", "November", "Desember"
];

export const indonesianDays = [
  "Minggu", "Senin", "Selasa", "Rabu", "Kamis", "Jumat", "Sabtu"
];

export function formatDate(millis) {
  const d = new Date(millis);
  const day = d.getDate().toString().padStart(2, '0');
  const month = indonesianMonths[d.getMonth()];
  const year = d.getFullYear();
  return `${day} ${month} ${year}`;
}

export function formatShortDate(millis) {
  const d = new Date(millis);
  const day = d.getDate().toString().padStart(2, '0');
  const month = (d.getMonth() + 1).toString().padStart(2, '0');
  const year = d.getFullYear();
  return `${day}/${month}/${year}`;
}

export function getDayOfWeek(millis) {
  const d = new Date(millis);
  return indonesianDays[d.getDay()];
}

export function formatMonthYear(monthIndex, year) {
  return `${indonesianMonths[monthIndex]} ${year}`;
}

