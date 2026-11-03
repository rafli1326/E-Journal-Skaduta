import { openDB } from 'idb';

const DB_NAME = 'ejournal_skaduta_db';
const DB_VERSION = 1;

export async function getDb() {
  return openDB(DB_NAME, DB_VERSION, {
    upgrade(db) {
      if (!db.objectStoreNames.contains('entries')) {
        const entryStore = db.createObjectStore('entries', { keyPath: 'id', autoIncrement: true });
        entryStore.createIndex('dateMillis', 'dateMillis');
      }
      if (!db.objectStoreNames.contains('profile')) {
        db.createObjectStore('profile', { keyPath: 'id' });
      }
    },
  });
}

export const defaultProfile = {
  id: 1,
  fullName: "",
  employeeId: "",
  position: "Staf Tata Usaha",
  schoolName: "SMK Negeri 2 Tanjung",
  department: "Tata Usaha",
  signatureCity: "Tabalong",
  subbagName: "Erliana Indarti, S.Pd., M.M.",
  subbagId: "198012172006042010",
  subbagPosition: "Kepala Sub Bagian Tata Usaha",
  headmasterName: "Bainah, S.Pd., M.A.Pd.",
  headmasterId: "196812061994122003",
  headmasterPosition: "Kepala Sekolah",
  reminderEnabled: true,
  reminderHour: 16,
  reminderMinute: 30
};

export async function getProfile() {
  const db = await getDb();
  const profile = await db.get('profile', 1);
  if (!profile) {
    await db.put('profile', defaultProfile);
    return defaultProfile;
  }
  return profile;
}

export async function saveProfile(profile) {
  const db = await getDb();
  await db.put('profile', { ...profile, id: 1 });
}

export async function getAllEntries() {
  const db = await getDb();
  const entries = await db.getAll('entries');
  return entries.sort((a, b) => b.dateMillis - a.dateMillis || b.id - a.id);
}

export async function getEntriesByMonth(monthIndex, year) {
  const all = await getAllEntries();
  return all.filter(e => {
    const d = new Date(e.dateMillis);
    return d.getMonth() === monthIndex && d.getFullYear() === year;
  }).sort((a, b) => a.dateMillis - b.dateMillis || a.id - b.id);
}

export async function saveEntry(entry) {
  const db = await getDb();
  if (entry.id) {
    await db.put('entries', entry);
    return entry.id;
  } else {
    return await db.add('entries', entry);
  }
}

export async function deleteEntry(id) {
  const db = await getDb();
  await db.delete('entries', id);
}

