import { openDB } from 'idb';

const DB_NAME = 'ejournal_skaduta_db';
const DB_VERSION = 1;

let dbPromise = null;

function getDb() {
  if (!dbPromise) {
    dbPromise = openDB(DB_NAME, DB_VERSION, {
      upgrade(db) {
        if (!db.objectStoreNames.contains('entries')) {
          const entryStore = db.createObjectStore('entries', { keyPath: 'id', autoIncrement: true });
          entryStore.createIndex('dateMillis', 'dateMillis');
        }
        if (!db.objectStoreNames.contains('profile')) {
          db.createObjectStore('profile', { keyPath: 'id' });
        }
      },
    }).catch(err => {
      console.warn("IndexedDB failed, falling back to localStorage", err);
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

// Fallback LocalStorage helpers
function getLocalEntries() {
  try {
    return JSON.parse(localStorage.getItem('skaduta_entries') || '[]');
  } catch {
    return [];
  }
}
function setLocalEntries(arr) {
  try {
    localStorage.setItem('skaduta_entries', JSON.stringify(arr));
  } catch (e) {
    console.error("Storage quota exceeded", e);
  }
}

export async function getProfile() {
  try {
    const db = await getDb();
    if (db) {
      const profile = await db.get('profile', 1);
      if (profile) return profile;
      await db.put('profile', defaultProfile);
      return defaultProfile;
    }
  } catch (e) {
    console.warn("DB getProfile error", e);
  }

  // fallback localStorage
  try {
    const p = localStorage.getItem('skaduta_profile');
    if (p) return JSON.parse(p);
    localStorage.setItem('skaduta_profile', JSON.stringify(defaultProfile));
  } catch {}
  return defaultProfile;
}

export async function saveProfile(profile) {
  const data = { ...profile, id: 1 };
  try {
    const db = await getDb();
    if (db) {
      await db.put('profile', data);
    }
  } catch (e) {
    console.warn("DB saveProfile error", e);
  }
  try {
    localStorage.setItem('skaduta_profile', JSON.stringify(data));
  } catch {}
}

export async function getAllEntries() {
  try {
    const db = await getDb();
    if (db) {
      const entries = await db.getAll('entries');
      if (entries && entries.length > 0) {
        return entries.sort((a, b) => b.dateMillis - a.dateMillis || b.id - a.id);
      }
    }
  } catch (e) {
    console.warn("DB getAllEntries error", e);
  }

  // Fallback
  return getLocalEntries().sort((a, b) => b.dateMillis - a.dateMillis || b.id - a.id);
}

export async function getEntriesByMonth(monthIndex, year) {
  const all = await getAllEntries();
  return all.filter(e => {
    const d = new Date(e.dateMillis);
    return d.getMonth() === monthIndex && d.getFullYear() === year;
  }).sort((a, b) => a.dateMillis - b.dateMillis || a.id - b.id);
}

export async function saveEntry(entry) {
  const toSave = { ...entry };
  if (!toSave.id) {
    delete toSave.id; // pastikan autoIncrement IDB bekerja
  }

  try {
    const db = await getDb();
    if (db) {
      if (toSave.id) {
        await db.put('entries', toSave);
      } else {
        const newId = await db.add('entries', toSave);
        toSave.id = newId;
      }
    }
  } catch (e) {
    console.warn("DB saveEntry error, saving to localStorage", e);
  }

  // Sync to localStorage as backup
  try {
    const list = getLocalEntries();
    if (toSave.id) {
      const idx = list.findIndex(item => item.id === toSave.id);
      if (idx >= 0) list[idx] = toSave;
      else list.push(toSave);
    } else {
      toSave.id = Date.now();
      list.push(toSave);
    }
    setLocalEntries(list);
  } catch (e) {
    console.warn("localStorage save error", e);
  }

  return toSave.id;
}

export async function deleteEntry(id) {
  try {
    const db = await getDb();
    if (db) {
      await db.delete('entries', id);
    }
  } catch (e) {
    console.warn("DB deleteEntry error", e);
  }

  try {
    const list = getLocalEntries().filter(e => e.id !== id);
    setLocalEntries(list);
  } catch {}
}
