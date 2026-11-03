import { openDB } from 'idb';

const DB_NAME = 'ejournal_skaduta_v2_db';
const OLD_DB_NAME = 'ejournal_skaduta_db';
const DB_VERSION = 1;
const LS_ENTRIES_KEY = 'skaduta_entries';
const LS_PROFILE_KEY = 'skaduta_profile';

let dbPromise = null;

function withTimeout(promise, ms = 1500) {
  return Promise.race([
    promise,
    new Promise((_, reject) => setTimeout(() => reject(new Error('DB timeout')), ms))
  ]);
}

function getDb() {
  if (!dbPromise) {
    dbPromise = withTimeout(
      openDB(DB_NAME, DB_VERSION, {
        upgrade(db) {
          if (!db.objectStoreNames.contains('entries')) {
            const entryStore = db.createObjectStore('entries', { keyPath: 'id' });
            entryStore.createIndex('dateMillis', 'dateMillis');
          }
          if (!db.objectStoreNames.contains('profile')) {
            db.createObjectStore('profile', { keyPath: 'id' });
          }
        },
      }),
      1500
    ).catch(err => {
      console.warn('IndexedDB unavailable or timed out, using localStorage:', err);
      dbPromise = null;
      return null;
    });
  }
  return dbPromise;
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

// LocalStorage helpers
function getLocalEntries() {
  try {
    const raw = localStorage.getItem(LS_ENTRIES_KEY);
    if (!raw) return [];
    const parsed = JSON.parse(raw);
    return Array.isArray(parsed) ? parsed : [];
  } catch {
    return [];
  }
}

function setLocalEntries(arr) {
  try {
    localStorage.setItem(LS_ENTRIES_KEY, JSON.stringify(arr));
  } catch (e) {
    // Jika kuota penuh karena foto base64 besar, simpan versi ringan di localStorage
    try {
      const lightArr = arr.map(item => ({
        ...item,
        evidenceImage: item.evidenceImage && item.evidenceImage.length > 50000 ? null : item.evidenceImage
      }));
      localStorage.setItem(LS_ENTRIES_KEY, JSON.stringify(lightArr));
    } catch (err) {
      console.warn('localStorage quota exceeded:', err);
    }
  }
}

// Migrasi satu kali dari DB v1 jika ada data lama
let migratedOldDb = false;
async function migrateOldDbIfNeeded() {
  if (migratedOldDb) return;
  migratedOldDb = true;
  try {
    const oldDb = await withTimeout(openDB(OLD_DB_NAME, 1), 1000);
    if (oldDb && oldDb.objectStoreNames.contains('entries')) {
      const oldEntries = await withTimeout(oldDb.getAll('entries'), 1000);
      if (Array.isArray(oldEntries) && oldEntries.length > 0) {
        const local = getLocalEntries();
        const map = new Map();
        local.forEach(e => { if (e && e.id) map.set(Number(e.id), e); });
        oldEntries.forEach((e, idx) => {
          const id = e.id ? Number(e.id) : (Date.now() + idx);
          if (!map.has(id)) map.set(id, { ...e, id });
        });
        setLocalEntries(Array.from(map.values()));
      }
    }
  } catch {
    // Abaikan jika DB lama tidak ada
  }
}

export async function getProfile() {
  let localProfile = null;
  try {
    const p = localStorage.getItem(LS_PROFILE_KEY);
    if (p) localProfile = JSON.parse(p);
  } catch {}

  try {
    const db = await getDb();
    if (db) {
      const profile = await withTimeout(db.get('profile', 1), 1000);
      if (profile) {
        try { localStorage.setItem(LS_PROFILE_KEY, JSON.stringify(profile)); } catch {}
        return profile;
      }
      const initial = localProfile || defaultProfile;
      await withTimeout(db.put('profile', initial), 1000);
      return initial;
    }
  } catch (e) {
    console.warn('DB getProfile error:', e);
  }

  if (localProfile) return localProfile;
  try {
    localStorage.setItem(LS_PROFILE_KEY, JSON.stringify(defaultProfile));
  } catch {}
  return defaultProfile;
}

export async function saveProfile(profile) {
  const data = { ...defaultProfile, ...profile, id: 1 };
  try {
    localStorage.setItem(LS_PROFILE_KEY, JSON.stringify(data));
  } catch (e) {
    console.warn('localStorage saveProfile error:', e);
  }
  try {
    const db = await getDb();
    if (db) {
      await withTimeout(db.put('profile', data), 1500);
    }
  } catch (e) {
    console.warn('DB saveProfile error:', e);
  }
  return data;
}

export async function getAllEntries() {
  await migrateOldDbIfNeeded();

  const localList = getLocalEntries();
  let dbList = [];

  try {
    const db = await getDb();
    if (db) {
      const res = await withTimeout(db.getAll('entries'), 1500);
      if (Array.isArray(res)) {
        dbList = res;
      }
    }
  } catch (e) {
    console.warn('DB getAllEntries error:', e);
  }

  // Gabungkan data dari IndexedDB dan localStorage berdasarkan ID agar tidak ada data yang hilang
  const mergedMap = new Map();
  for (const item of localList) {
    if (item && item.id != null) {
      mergedMap.set(Number(item.id), { ...item, id: Number(item.id) });
    }
  }
  for (const item of dbList) {
    if (item && item.id != null) {
      const numId = Number(item.id);
      const existing = mergedMap.get(numId);
      // Utamakan versi yang memiliki evidenceImage lengkap atau updatedAt terbaru
      if (!existing || (item.evidenceImage && !existing.evidenceImage) || (item.updatedAt || 0) >= (existing.updatedAt || 0)) {
        mergedMap.set(numId, { ...item, id: numId });
      }
    }
  }

  const merged = Array.from(mergedMap.values()).sort(
    (a, b) => (b.dateMillis || 0) - (a.dateMillis || 0) || (b.id || 0) - (a.id || 0)
  );

  // Sinkronkan kembali ke localStorage
  if (merged.length > 0) {
    setLocalEntries(merged);
  }

  return merged;
}

export async function getEntriesByMonth(monthIndex, year) {
  const all = await getAllEntries();
  return all
    .filter(e => {
      const d = new Date(e.dateMillis);
      return d.getMonth() === monthIndex && d.getFullYear() === year;
    })
    .sort((a, b) => (a.dateMillis || 0) - (b.dateMillis || 0) || (a.id || 0) - (b.id || 0));
}

let idCounter = 0;
function generateUniqueId() {
  idCounter = (idCounter + 1) % 1000;
  return Date.now() * 1000 + idCounter;
}

export async function saveEntry(entry) {
  const numericId = entry.id != null && !isNaN(Number(entry.id)) && Number(entry.id) > 0
    ? Number(entry.id)
    : generateUniqueId();

  const toSave = {
    ...entry,
    id: numericId,
    updatedAt: Date.now()
  };

  // 1. Simpan langsung ke localStorage secara sinkron (pasti berhasil dalam <1ms)
  try {
    const list = getLocalEntries();
    const idx = list.findIndex(item => Number(item.id) === numericId);
    if (idx >= 0) {
      list[idx] = toSave;
    } else {
      list.push(toSave);
    }
    setLocalEntries(list);
  } catch (e) {
    console.warn('localStorage saveEntry error:', e);
  }

  // 2. Simpan ke IndexedDB
  try {
    const db = await getDb();
    if (db) {
      await withTimeout(db.put('entries', toSave), 1500);
    }
  } catch (e) {
    console.warn('DB saveEntry error (already saved in localStorage):', e);
  }

  return toSave;
}

export async function deleteEntry(id) {
  const numericId = Number(id);

  try {
    const list = getLocalEntries().filter(e => Number(e.id) !== numericId);
    setLocalEntries(list);
  } catch (e) {
    console.warn('localStorage deleteEntry error:', e);
  }

  try {
    const db = await getDb();
    if (db) {
      await withTimeout(db.delete('entries', numericId), 1500);
      await withTimeout(db.delete('entries', id), 1000).catch(() => {});
    }
  } catch (e) {
    console.warn('DB deleteEntry error:', e);
  }
}
