import React, { useState, useEffect } from 'react';
import { 
  getAllEntries, 
  getProfile, 
  saveProfile as saveProfileDb, 
  saveEntry, 
  deleteEntry as deleteEntryDb, 
  getEntriesByMonth 
} from './db';
import { 
  formatDate, 
  formatShortDate, 
  getDayOfWeek, 
  formatMonthYear, 
  indonesianMonths 
} from './dateUtils';
import { generateReportPdf } from './pdfGenerator';
import { 
  Calendar, 
  FileText, 
  User, 
  Plus, 
  Search, 
  ChevronLeft, 
  ChevronRight, 
  Trash2, 
  Edit3, 
  Camera, 
  Image as ImageIcon, 
  X, 
  Check, 
  Clock, 
  Bell, 
  ArrowLeft 
} from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState('home'); // 'home' | 'report' | 'profile' | 'form'
  const [entries, setEntries] = useState([]);
  const [profile, setProfile] = useState(null);
  const [searchQuery, setSearchQuery] = useState('');

  // Report state
  const [selectedMonth, setSelectedMonth] = useState(new Date().getMonth());
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear());
  const [monthlyEntries, setMonthlyEntries] = useState([]);

  // Form state (Add/Edit)
  const [editingId, setEditingId] = useState(null);
  const [formDate, setFormDate] = useState(new Date().toISOString().split('T')[0]);
  const [tasks, setTasks] = useState([{ description: '', evidenceText: '', evidenceImage: null }]);

  // Profile Form state
  const [profileForm, setProfileForm] = useState(null);
  const [profileSavedToast, setProfileSavedToast] = useState(false);

  useEffect(() => {
    loadData();
  }, []);

  useEffect(() => {
    loadMonthlyEntries();
  }, [selectedMonth, selectedYear, entries]);

  async function loadData() {
    const p = await getProfile();
    setProfile(p);
    setProfileForm(p);
    const all = await getAllEntries();
    setEntries(all);
  }

  async function loadMonthlyEntries() {
    const list = await getEntriesByMonth(selectedMonth, selectedYear);
    setMonthlyEntries(list);
  }

  // Filtered entries for Home
  const filteredEntries = entries.filter(e => {
    if (!searchQuery.trim()) return true;
    const q = searchQuery.toLowerCase();
    return (
      e.description.toLowerCase().includes(q) ||
      e.formattedDate.toLowerCase().includes(q) ||
      (e.evidenceText && e.evidenceText.toLowerCase().includes(q))
    );
  });

  // Grouped by date for Home
  const groupedEntries = {};
  filteredEntries.forEach(e => {
    if (!groupedEntries[e.formattedDate]) {
      groupedEntries[e.formattedDate] = {
        dayOfWeek: e.dayOfWeek,
        dateMillis: e.dateMillis,
        tasks: []
      };
    }
    groupedEntries[e.formattedDate].tasks.push(e);
  });

  // Save Task Form
  async function handleSaveTasks() {
    const validTasks = tasks.filter(t => t.description.trim());
    if (validTasks.length === 0) {
      alert('Harap isi minimal satu uraian kegiatan.');
      return;
    }

    try {
      const parts = formDate.split('-');
      const dateObj = new Date(parseInt(parts[0], 10), parseInt(parts[1], 10) - 1, parseInt(parts[2], 10), 12, 0, 0);
      const dateMillis = dateObj.getTime();
      const dayOfWeek = getDayOfWeek(dateMillis);
      const formattedDate = formatDate(dateMillis);

      for (const t of validTasks) {
        await saveEntry({
          id: editingId ? Number(editingId) : undefined,
          dateMillis,
          dayOfWeek,
          formattedDate,
          description: t.description.trim(),
          evidenceType: 'COMBINED',
          evidenceText: t.evidenceText.trim() || null,
          evidenceImage: t.evidenceImage || null,
          updatedAt: Date.now()
        });
      }

      await loadData();
      setActiveTab('home');
      setEditingId(null);
      setTasks([{ description: '', evidenceText: '', evidenceImage: null }]);
    } catch (err) {
      console.error("Gagal menyimpan kegiatan:", err);
      alert("Terjadi kesalahan saat menyimpan: " + err.message);
    }
  }

  // Handle Photo input
  function handleImageUpload(index, file) {
    if (!file) return;
    if (file.size > 15 * 1024 * 1024) {
      alert('Ukuran foto terlalu besar (maksimal 15 MB).');
      return;
    }
    const reader = new FileReader();
    reader.onload = (e) => {
      const newTasks = [...tasks];
      newTasks[index].evidenceImage = e.target.result;
      setTasks(newTasks);
    };
    reader.readAsDataURL(file);
  }

  // Delete Entry
  async function handleDelete(id) {
    if (confirm('Hapus catatan kegiatan ini?')) {
      await deleteEntryDb(id);
      await loadData();
    }
  }

  // Edit Entry
  function handleOpenEdit(entry) {
    setEditingId(entry.id);
    const d = new Date(entry.dateMillis);
    setFormDate(d.toISOString().split('T')[0]);
    setTasks([{
      description: entry.description,
      evidenceText: entry.evidenceText || '',
      evidenceImage: entry.evidenceImage || null
    }]);
    setActiveTab('form');
  }

  // Save Profile
  async function handleSaveProfile() {
    if (!profileForm) return;
    await saveProfileDb(profileForm);
    setProfile(profileForm);
    setProfileSavedToast(true);
    setTimeout(() => setProfileSavedToast(false), 2500);
  }

  return (
    <div className="flex flex-col h-screen max-w-md mx-auto bg-slate-background shadow-lg overflow-hidden border-x border-slate-borderLight">
      
      {/* Toast Notification */}
      {profileSavedToast && (
        <div className="fixed top-4 left-1/2 -translate-x-1/2 z-50 bg-[#0F172A] text-white px-4 py-2 rounded-lg text-sm shadow-md flex items-center gap-2 animate-bounce">
          <Check size={16} className="text-green-400" />
          <span>Profil & Pengesahan Berhasil Disimpan!</span>
        </div>
      )}

      {/* TOP HEADER */}
      <header className="bg-slate-surface border-b border-slate-borderLight px-4 py-2.5 flex items-center justify-between shrink-0">
        <div className="flex items-center gap-2.5">
          {activeTab === 'form' ? (
            <button onClick={() => { setActiveTab('home'); setEditingId(null); }} className="p-1 -ml-1 text-slate-textPrimary">
              <ArrowLeft size={20} />
            </button>
          ) : (
            <img src="./logo_smk2_tanjung.png" alt="Logo" className="w-8 h-8 object-contain" />
          )}
          <div>
            <h1 className="text-sm font-bold text-slate-textPrimary leading-tight">
              {activeTab === 'form' 
                ? (editingId ? 'Ubah Kegiatan' : 'Catat Kegiatan')
                : (activeTab === 'report' ? 'Laporan Bulanan' : (activeTab === 'profile' ? 'Profil & Pengesahan' : 'E-Journal • SMKN 2 Tanjung'))}
            </h1>
            {activeTab === 'home' && (
              <p className="text-[11px] font-semibold text-brand-orange leading-none">SMK Negeri 2 Tanjung</p>
            )}
          </div>
        </div>

        {/* Header Right Action */}
        {activeTab === 'home' && (
          <span className="text-[11px] font-bold px-2 py-0.5 rounded bg-brand-container text-brand-onContainer border border-brand-orange/20">
            {entries.length} Kegiatan
          </span>
        )}
        {activeTab === 'profile' && (
          <button 
            onClick={handleSaveProfile}
            className="flex items-center gap-1 bg-brand-orange text-white text-xs font-bold px-3 py-1.5 rounded-lg active:scale-95 transition"
          >
            <Check size={14} />
            Simpan
          </button>
        )}
      </header>

      {/* BODY CONTENT AREA */}
      <main className="flex-1 overflow-y-auto">
        
        {/* ==================== TAB 1: HOME ==================== */}
        {activeTab === 'home' && (
          <div className="p-3.5 space-y-3 pb-20">
            {/* Search Box */}
            <div className="relative">
              <Search className="absolute left-3 top-2.5 text-brand-orange" size={18} />
              <input 
                type="text"
                placeholder="Cari kegiatan..."
                value={searchQuery}
                onChange={(e) => setSearchQuery(e.target.value)}
                className="w-full bg-white border border-slate-border text-sm rounded-xl pl-9 pr-3 py-2 text-slate-textPrimary placeholder-slate-textMuted focus:outline-none focus:border-brand-orange"
              />
            </div>

            {/* List or Empty State */}
            {Object.keys(groupedEntries).length === 0 ? (
              <div className="flex flex-col items-center justify-center py-16 text-center px-4">
                <div className="w-14 h-14 rounded-full bg-brand-container flex items-center justify-center text-brand-orange mb-3">
                  <Calendar size={28} />
                </div>
                <h3 className="text-sm font-bold text-slate-textPrimary">
                  {searchQuery ? 'Kegiatan Tidak Ditemukan' : 'Belum Ada Catatan Kegiatan'}
                </h3>
                <p className="text-xs text-slate-textSecondary mt-1 max-w-[240px]">
                  {searchQuery ? 'Coba kata kunci lain' : 'Tekan tombol + di kanan bawah untuk mencatat pekerjaan hari ini'}
                </p>
              </div>
            ) : (
              <div className="space-y-3">
                {Object.entries(groupedEntries).map(([dateStr, group]) => (
                  <div key={dateStr} className="bg-white rounded-xl border border-slate-borderLight shadow-sm p-3">
                    <div className="flex items-center justify-between pb-2 mb-2 border-b border-slate-borderLight/60">
                      <span className="text-xs font-bold text-slate-textPrimary flex items-center gap-1.5">
                        <span className="w-2 h-2 rounded-full bg-brand-orange"></span>
                        {group.dayOfWeek}, {dateStr}
                      </span>
                      <span className="text-[10px] font-semibold text-slate-textSecondary bg-slate-100 px-2 py-0.5 rounded-full">
                        {group.tasks.length} Kegiatan
                      </span>
                    </div>

                    <div className="space-y-2.5">
                      {group.tasks.map((task, idx) => (
                        <div key={task.id} className="flex gap-2.5 text-xs group">
                          <span className="w-4 h-4 rounded-full bg-brand-container text-brand-onContainer text-[10px] font-bold flex items-center justify-center shrink-0 mt-0.5">
                            {idx + 1}
                          </span>
                          <div className="flex-1">
                            <p className="text-slate-textPrimary leading-relaxed font-medium">{task.description}</p>
                            {task.evidenceText && (
                              <p className="text-[11px] text-slate-textSecondary mt-0.5 flex items-center gap-1">
                                📎 {task.evidenceText}
                              </p>
                            )}
                            {task.evidenceImage && (
                              <div className="mt-1.5">
                                <img src={task.evidenceImage} alt="Bukti" className="w-16 h-16 object-cover rounded-md border border-slate-borderLight" />
                              </div>
                            )}
                          </div>
                          <div className="flex items-start gap-1 shrink-0">
                            <button onClick={() => handleOpenEdit(task)} className="p-1 text-blue-600 hover:bg-blue-50 rounded">
                              <Edit3 size={14} />
                            </button>
                            <button onClick={() => handleDelete(task.id)} className="p-1 text-red-600 hover:bg-red-50 rounded">
                              <Trash2 size={14} />
                            </button>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {/* ==================== TAB 2: LAPORAN BULANAN ==================== */}
        {activeTab === 'report' && (
          <div className="p-3.5 space-y-3 pb-20">
            {/* Periode Selector Card */}
            <div className="bg-white rounded-xl border border-slate-borderLight p-3 shadow-sm">
              <span className="text-[11px] font-bold text-brand-orange block mb-1">Pilih Periode Laporan</span>
              <div className="flex items-center justify-between">
                <button 
                  onClick={() => {
                    if (selectedMonth === 0) { setSelectedMonth(11); setSelectedYear(y => y - 1); }
                    else { setSelectedMonth(m => m - 1); }
                  }}
                  className="p-1.5 rounded-lg border border-slate-border text-slate-textSecondary"
                >
                  <ChevronLeft size={16} />
                </button>
                <div className="text-center">
                  <h3 className="text-base font-bold text-slate-textPrimary leading-tight">
                    {formatMonthYear(selectedMonth, selectedYear)}
                  </h3>
                  <p className="text-xs font-semibold text-slate-textSecondary mt-0.5">
                    {monthlyEntries.length} kegiatan tercatat
                  </p>
                </div>
                <button 
                  onClick={() => {
                    if (selectedMonth === 11) { setSelectedMonth(0); setSelectedYear(y => y + 1); }
                    else { setSelectedMonth(m => m + 1); }
                  }}
                  className="p-1.5 rounded-lg border border-slate-border text-slate-textSecondary"
                >
                  <ChevronRight size={16} />
                </button>
              </div>
            </div>

            {/* Action Download Card */}
            <div className="bg-brand-container/30 border border-brand-orange/20 rounded-xl p-3">
              <div className="flex items-center gap-2 mb-2">
                <FileText className="text-brand-orange" size={20} />
                <div>
                  <h4 className="text-sm font-bold text-brand-dark">Cetak Berkas PDF A4</h4>
                  <p className="text-[11px] text-slate-textSecondary">Format tabel rapi dengan Kop & Lembar Tanda Tangan</p>
                </div>
              </div>
              <button 
                disabled={monthlyEntries.length === 0}
                onClick={() => generateReportPdf(monthlyEntries, profile, selectedMonth, selectedYear)}
                className="w-full bg-brand-orange disabled:opacity-50 text-white font-bold text-sm py-2.5 rounded-lg flex items-center justify-center gap-2 shadow-sm active:scale-98 transition"
              >
                <FileText size={16} />
                Buat Laporan PDF ({monthlyEntries.length} Kegiatan)
              </button>
            </div>

            {/* Preview List */}
            <div className="space-y-2">
              <span className="text-xs font-bold text-slate-textPrimary block">Daftar Kegiatan Bulan Ini ({monthlyEntries.length})</span>
              {monthlyEntries.length === 0 ? (
                <p className="text-xs text-slate-textMuted py-4 text-center">Tidak ada kegiatan di bulan ini.</p>
              ) : (
                monthlyEntries.map((e, idx) => (
                  <div key={e.id} className="bg-white p-2.5 rounded-lg border border-slate-borderLight text-xs flex gap-2">
                    <span className="font-bold text-brand-orange shrink-0">{idx + 1}.</span>
                    <div>
                      <span className="text-[10px] font-semibold text-slate-textSecondary block">{e.dayOfWeek}, {e.formattedDate}</span>
                      <p className="font-medium text-slate-textPrimary mt-0.5">{e.description}</p>
                    </div>
                  </div>
                ))
              )}
            </div>
          </div>
        )}

        {/* ==================== TAB 3: PROFIL PEGAWAI ==================== */}
        {activeTab === 'profile' && profileForm && (
          <div className="p-3.5 space-y-3 pb-20">
            {/* Header Badge */}
            <div className="bg-gradient-to-r from-orange-50 to-amber-50 border border-brand-orange/20 rounded-xl p-2.5 flex items-center gap-2.5">
              <img src="./logo_smk2_tanjung.png" alt="Logo" className="w-7 h-7 object-contain" />
              <div>
                <p className="text-xs font-bold text-slate-textPrimary">SMK Negeri 2 Tanjung</p>
                <p className="text-[10px] text-slate-textSecondary">Kabupaten Tabalong • Kalimantan Selatan</p>
              </div>
            </div>

            {/* Section 1: Pegawai */}
            <div className="bg-white rounded-xl border border-slate-borderLight p-3 shadow-sm space-y-2">
              <span className="text-xs font-bold text-brand-orange flex items-center gap-1.5">
                <User size={14} /> 1. Identitas Staf Tata Usaha
              </span>
              <p className="text-[10px] text-slate-textSecondary">Data Anda selaku penyusun laporan jurnal kegiatan kerja harian.</p>
              <input 
                type="text" 
                placeholder="Nama Lengkap & Gelar" 
                value={profileForm.fullName} 
                onChange={(e) => setProfileForm({ ...profileForm, fullName: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
              <input 
                type="text" 
                placeholder="NIP / NUPTK / ID Pegawai" 
                value={profileForm.employeeId} 
                onChange={(e) => setProfileForm({ ...profileForm, employeeId: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
              <input 
                type="text" 
                placeholder="Jabatan" 
                value={profileForm.position} 
                onChange={(e) => setProfileForm({ ...profileForm, position: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
              <input 
                type="text" 
                placeholder="Nama Sekolah / Instansi" 
                value={profileForm.schoolName} 
                onChange={(e) => setProfileForm({ ...profileForm, schoolName: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
              <input 
                type="text" 
                placeholder="Kota Penetapan Laporan" 
                value={profileForm.signatureCity} 
                onChange={(e) => setProfileForm({ ...profileForm, signatureCity: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
            </div>

            {/* Section 2: Kasubbag TU */}
            <div className="bg-white rounded-xl border border-slate-borderLight p-3 shadow-sm space-y-2">
              <span className="text-xs font-bold text-brand-orange flex items-center gap-1.5">
                <User size={14} /> 2. Kepala Sub Bagian Tata Usaha
              </span>
              <p className="text-[10px] text-slate-textSecondary">Atasan langsung yang memeriksa dan menandatangani laporan.</p>
              <input 
                type="text" 
                placeholder="Nama Lengkap Kasubbag TU & Gelar" 
                value={profileForm.subbagName} 
                onChange={(e) => setProfileForm({ ...profileForm, subbagName: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
              <input 
                type="text" 
                placeholder="NIP Kasubbag TU" 
                value={profileForm.subbagId} 
                onChange={(e) => setProfileForm({ ...profileForm, subbagId: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
              <input 
                type="text" 
                placeholder="Nama Jabatan" 
                value={profileForm.subbagPosition} 
                onChange={(e) => setProfileForm({ ...profileForm, subbagPosition: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
            </div>

            {/* Section 3: Kepala Sekolah */}
            <div className="bg-white rounded-xl border border-slate-borderLight p-3 shadow-sm space-y-2">
              <span className="text-xs font-bold text-brand-orange flex items-center gap-1.5">
                <User size={14} /> 3. Kepala Sekolah (Mengesahkan)
              </span>
              <p className="text-[10px] text-slate-textSecondary">Pimpinan instansi yang mengesahkan lembar laporan bulanan.</p>
              <input 
                type="text" 
                placeholder="Nama Lengkap Kepala Sekolah & Gelar" 
                value={profileForm.headmasterName} 
                onChange={(e) => setProfileForm({ ...profileForm, headmasterName: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
              <input 
                type="text" 
                placeholder="NIP Kepala Sekolah" 
                value={profileForm.headmasterId} 
                onChange={(e) => setProfileForm({ ...profileForm, headmasterId: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
              <input 
                type="text" 
                placeholder="Nama Jabatan" 
                value={profileForm.headmasterPosition} 
                onChange={(e) => setProfileForm({ ...profileForm, headmasterPosition: e.target.value })}
                className="w-full text-xs p-2 rounded-lg border border-slate-border" 
              />
            </div>
          </div>
        )}

        {/* ==================== FORM: CATAT KEGIATAN ==================== */}
        {activeTab === 'form' && (
          <div className="p-3.5 space-y-3 pb-20">
            {/* Tanggal */}
            <div className="bg-white p-3 rounded-xl border border-slate-borderLight shadow-sm">
              <label className="text-[11px] font-bold text-brand-orange block mb-1">Hari & Tanggal Pekerjaan</label>
              <input 
                type="date" 
                value={formDate} 
                onChange={(e) => setFormDate(e.target.value)}
                className="w-full text-xs p-2 border border-slate-border rounded-lg"
              />
            </div>

            {/* Tasks Form */}
            {tasks.map((task, idx) => (
              <div key={idx} className="bg-white p-3 rounded-xl border border-slate-borderLight shadow-sm space-y-2">
                <div className="flex items-center justify-between">
                  <span className="text-xs font-bold text-brand-orange">Uraian Kegiatan #{idx + 1}</span>
                  {tasks.length > 1 && !editingId && (
                    <button onClick={() => setTasks(tasks.filter((_, i) => i !== idx))} className="text-red-600 p-1">
                      <Trash2 size={14} />
                    </button>
                  )}
                </div>

                <textarea 
                  rows={3} 
                  placeholder="Contoh: Menginput data absensi guru/karyawan, mengarsipkan surat masuk dinas..."
                  value={task.description}
                  onChange={(e) => {
                    const newT = [...tasks];
                    newT[idx].description = e.target.value;
                    setTasks(newT);
                  }}
                  className="w-full text-xs p-2 border border-slate-border rounded-lg"
                />

                <span className="text-[11px] font-semibold text-slate-textMuted block">Bukti Dukung (Teks & Lampiran Foto):</span>
                <input 
                  type="text"
                  placeholder="Tulis no. surat, link berkas drive, atau catatan bukti (opsional)"
                  value={task.evidenceText}
                  onChange={(e) => {
                    const newT = [...tasks];
                    newT[idx].evidenceText = e.target.value;
                    setTasks(newT);
                  }}
                  className="w-full text-xs p-2 border border-slate-border rounded-lg"
                />

                {/* Upload foto */}
                {task.evidenceImage ? (
                  <div className="relative w-full h-32 rounded-lg overflow-hidden border border-slate-border">
                    <img src={task.evidenceImage} alt="Bukti" className="w-full h-full object-cover" />
                    <button 
                      onClick={() => {
                        const newT = [...tasks];
                        newT[idx].evidenceImage = null;
                        setTasks(newT);
                      }}
                      className="absolute top-2 right-2 bg-black/60 text-white p-1 rounded-full"
                    >
                      <X size={14} />
                    </button>
                  </div>
                ) : (
                  <div className="flex gap-2">
                    <label className="flex-1 border border-slate-border rounded-lg p-2 flex items-center justify-center gap-1.5 text-xs text-slate-textSecondary cursor-pointer hover:bg-slate-50">
                      <Camera size={14} />
                      Ambil Foto
                      <input 
                        type="file" 
                        accept="image/*" 
                        capture="environment" 
                        className="hidden" 
                        onChange={(e) => handleImageUpload(idx, e.target.files[0])} 
                      />
                    </label>
                    <label className="flex-1 border border-slate-border rounded-lg p-2 flex items-center justify-center gap-1.5 text-xs text-slate-textSecondary cursor-pointer hover:bg-slate-50">
                      <ImageIcon size={14} />
                      Pilih Galeri
                      <input 
                        type="file" 
                        accept="image/*" 
                        className="hidden" 
                        onChange={(e) => handleImageUpload(idx, e.target.files[0])} 
                      />
                    </label>
                  </div>
                )}
              </div>
            ))}

            {/* Tambah tugas hari ini */}
            {!editingId && (
              <button 
                onClick={() => setTasks([...tasks, { description: '', evidenceText: '', evidenceImage: null }])}
                className="w-full border border-dashed border-brand-orange text-brand-orange font-bold text-xs py-2 rounded-lg flex items-center justify-center gap-1 hover:bg-orange-50"
              >
                <Plus size={14} />
                + Tambah Kegiatan Lain untuk Hari Ini
              </button>
            )}

            {/* Simpan Kegiatan */}
            <button 
              onClick={handleSaveTasks}
              className="w-full bg-brand-orange text-white font-bold text-sm py-2.5 rounded-xl flex items-center justify-center gap-1.5 shadow-md active:scale-98 transition"
            >
              <Check size={16} />
              {editingId ? 'Perbarui Catatan' : `Simpan Semua Kegiatan (${tasks.length})`}
            </button>
          </div>
        )}
      </main>

      {/* FLOATING ACTION BUTTON (Hanya di Home) */}
      {activeTab === 'home' && (
        <button 
          onClick={() => {
            setEditingId(null);
            setFormDate(new Date().toISOString().split('T')[0]);
            setTasks([{ description: '', evidenceText: '', evidenceImage: null }]);
            setActiveTab('form');
          }}
          className="fixed bottom-16 right-4 sm:right-[calc(50%-200px)] w-12 h-12 rounded-2xl bg-brand-orange text-white shadow-lg flex items-center justify-center active:scale-95 transition z-20"
        >
          <Plus size={24} />
        </button>
      )}

      {/* BOTTOM NAVIGATION BAR */}
      {activeTab !== 'form' && (
        <nav className="bg-slate-surface border-t border-slate-borderLight flex items-center justify-around py-1.5 shrink-0 z-10">
          <button 
            onClick={() => setActiveTab('home')}
            className={`flex flex-col items-center gap-0.5 py-1 px-4 rounded-xl transition ${
              activeTab === 'home' ? 'text-brand-dark font-bold' : 'text-slate-textSecondary'
            }`}
          >
            <div className={`p-1 rounded-full ${activeTab === 'home' ? 'bg-brand-container' : ''}`}>
              <Calendar size={18} className={activeTab === 'home' ? 'text-brand-dark' : 'text-slate-textSecondary'} />
            </div>
            <span className="text-[10px]">Jurnal Harian</span>
          </button>

          <button 
            onClick={() => setActiveTab('report')}
            className={`flex flex-col items-center gap-0.5 py-1 px-4 rounded-xl transition ${
              activeTab === 'report' ? 'text-brand-dark font-bold' : 'text-slate-textSecondary'
            }`}
          >
            <div className={`p-1 rounded-full ${activeTab === 'report' ? 'bg-brand-container' : ''}`}>
              <FileText size={18} className={activeTab === 'report' ? 'text-brand-dark' : 'text-slate-textSecondary'} />
            </div>
            <span className="text-[10px]">Laporan Bulanan</span>
          </button>

          <button 
            onClick={() => setActiveTab('profile')}
            className={`flex flex-col items-center gap-0.5 py-1 px-4 rounded-xl transition ${
              activeTab === 'profile' ? 'text-brand-dark font-bold' : 'text-slate-textSecondary'
            }`}
          >
            <div className={`p-1 rounded-full ${activeTab === 'profile' ? 'bg-brand-container' : ''}`}>
              <User size={18} className={activeTab === 'profile' ? 'text-brand-dark' : 'text-slate-textSecondary'} />
            </div>
            <span className="text-[10px]">Profil Pegawai</span>
          </button>
        </nav>
      )}

    </div>
  );
}

