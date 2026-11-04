package com.ejournal.skaduta.ui.screens.report

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejournal.skaduta.data.local.AppDatabase
import com.ejournal.skaduta.data.local.entity.JournalEntry
import com.ejournal.skaduta.data.local.entity.UserProfile
import com.ejournal.skaduta.data.repository.JournalRepository
import com.ejournal.skaduta.util.DateUtils
import com.ejournal.skaduta.util.FileUtils
import com.ejournal.skaduta.util.PdfGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar

class MonthlyReportViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: JournalRepository

    private val _selectedMonth = MutableStateFlow(Calendar.getInstance().get(Calendar.MONTH))
    val selectedMonth: StateFlow<Int> = _selectedMonth

    private val _selectedYear = MutableStateFlow(Calendar.getInstance().get(Calendar.YEAR))
    val selectedYear: StateFlow<Int> = _selectedYear

    val profile: StateFlow<UserProfile?>

    private val _dateRange = MutableStateFlow(DateUtils.getMonthRangeMillis(_selectedMonth.value, _selectedYear.value))

    val monthlyEntries: StateFlow<List<JournalEntry>>

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating

    private val _includePhotoAppendix = MutableStateFlow(true)
    val includePhotoAppendix: StateFlow<Boolean> = _includePhotoAppendix

    private val _generatedPdfFile = MutableStateFlow<File?>(null)
    val generatedPdfFile: StateFlow<File?> = _generatedPdfFile

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        val db = AppDatabase.getInstance(application)
        repository = JournalRepository(db.journalDao(), db.profileDao())

        profile = repository.getProfile().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

        @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
        monthlyEntries = _dateRange.flatMapLatest { (start, end) ->
            repository.getEntriesForMonth(start, end)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun setMonthAndYear(month: Int, year: Int) {
        val safeMonth = month.coerceIn(0, 11)
        val safeYear = year.coerceIn(2000, 2100)
        _selectedMonth.value = safeMonth
        _selectedYear.value = safeYear
        _dateRange.value = DateUtils.getMonthRangeMillis(safeMonth, safeYear)
        _generatedPdfFile.value = null // reset previously generated file
    }

    fun setIncludePhotoAppendix(include: Boolean) {
        _includePhotoAppendix.value = include
    }

    fun generatePdf() {
        if (_isGenerating.value) return

        val entries = monthlyEntries.value
        if (entries.isEmpty()) {
            _errorMessage.value = "Tidak ada catatan kegiatan pada bulan ini untuk dicetak"
            return
        }

        _isGenerating.value = true
        _errorMessage.value = null

        viewModelScope.launch {
            try {
                val currentProfile = repository.getProfileSync()
                val pdfFile = withContext(Dispatchers.IO) {
                    val file = PdfGenerator.generateMonthlyReport(
                        context = getApplication(),
                        entries = entries,
                        profile = currentProfile,
                        monthIndex = _selectedMonth.value,
                        year = _selectedYear.value,
                        includePhotoAppendix = _includePhotoAppendix.value
                    )
                    // Otomatis simpan ke folder Download publik HP
                    FileUtils.savePdfToPublicDownloads(getApplication(), file)
                    file
                }
                _generatedPdfFile.value = pdfFile
            } catch (_: Exception) {
                _errorMessage.value = "Gagal membuat dokumen PDF. Silakan coba lagi."
            } finally {
                _isGenerating.value = false
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

