package com.ejournal.skaduta.ui.screens.entry

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejournal.skaduta.data.local.AppDatabase
import com.ejournal.skaduta.data.local.entity.JournalEntry
import com.ejournal.skaduta.data.repository.JournalRepository
import com.ejournal.skaduta.util.DateUtils
import com.ejournal.skaduta.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

data class TaskDraft(
    val id: String = UUID.randomUUID().toString(),
    val existingEntryId: Long = 0,
    val description: String = "",
    val evidenceText: String = "",
    val evidenceImagePath: String? = null
)

class AddEditViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val MAX_DESC_LENGTH = 2000
        private const val MAX_EVIDENCE_TEXT_LENGTH = 500
        private const val MAX_TASKS_PER_DAY_BATCH = 25
    }

    private val repository: JournalRepository
    private var editingEntryId: Long = -1
    private var originalEvidencePath: String? = null

    private val _dateMillis = MutableStateFlow(System.currentTimeMillis())
    val dateMillis: StateFlow<Long> = _dateMillis

    private val _dayOfWeek = MutableStateFlow(DateUtils.getDayOfWeek(System.currentTimeMillis()))
    val dayOfWeek: StateFlow<String> = _dayOfWeek

    private val _formattedDate = MutableStateFlow(DateUtils.formatDate(System.currentTimeMillis()))
    val formattedDate: StateFlow<String> = _formattedDate

    private val _taskList = MutableStateFlow(listOf(TaskDraft()))
    val taskList: StateFlow<List<TaskDraft>> = _taskList

    private val _isSaved = MutableStateFlow(false)
    val isSaved: StateFlow<Boolean> = _isSaved

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage

    init {
        val db = AppDatabase.getInstance(application)
        repository = JournalRepository(db.journalDao(), db.profileDao())
    }

    fun loadEntry(entryId: Long) {
        if (entryId <= 0) return
        editingEntryId = entryId

        viewModelScope.launch {
            val entry = repository.getEntryByIdSync(entryId)
            if (entry != null) {
                originalEvidencePath = entry.evidenceImagePath
                _dateMillis.value = entry.dateMillis
                _dayOfWeek.value = entry.dayOfWeek
                _formattedDate.value = entry.formattedDate
                _taskList.value = listOf(
                    TaskDraft(
                        existingEntryId = entry.id,
                        description = entry.description,
                        evidenceText = entry.evidenceText ?: "",
                        evidenceImagePath = entry.evidenceImagePath
                    )
                )
            }
        }
    }

    fun setDate(millis: Long) {
        _dateMillis.value = millis
        _dayOfWeek.value = DateUtils.getDayOfWeek(millis)
        _formattedDate.value = DateUtils.formatDate(millis)
    }

    fun addTask() {
        if (_taskList.value.size >= MAX_TASKS_PER_DAY_BATCH) {
            _errorMessage.value = "Maksimal $MAX_TASKS_PER_DAY_BATCH kegiatan sekaligus."
            return
        }
        _taskList.value = _taskList.value + TaskDraft()
    }

    fun removeTask(index: Int) {
        if (_taskList.value.size > 1 && index in _taskList.value.indices) {
            val current = _taskList.value.toMutableList()
            val removed = current.removeAt(index)
            if (removed.evidenceImagePath != null && removed.evidenceImagePath != originalEvidencePath) {
                viewModelScope.launch(Dispatchers.IO) {
                    FileUtils.deleteEvidenceFile(getApplication(), removed.evidenceImagePath)
                }
            }
            _taskList.value = current
        }
    }

    fun updateTaskDescription(index: Int, desc: String) {
        val safeDesc = if (desc.length > MAX_DESC_LENGTH) desc.take(MAX_DESC_LENGTH) else desc
        val current = _taskList.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(description = safeDesc)
            _taskList.value = current
        }
    }

    fun updateTaskEvidenceText(index: Int, text: String) {
        val safeText = if (text.length > MAX_EVIDENCE_TEXT_LENGTH) text.take(MAX_EVIDENCE_TEXT_LENGTH) else text
        val current = _taskList.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(evidenceText = safeText)
            _taskList.value = current
        }
    }

    fun onImageSelected(index: Int, uri: Uri) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                FileUtils.saveUriToInternalStorage(getApplication(), uri)
            }
            if (savedPath == null) {
                _errorMessage.value = "File gambar tidak valid atau melebihi batas ukuran."
                return@launch
            }
            val current = _taskList.value.toMutableList()
            if (index in current.indices) {
                val oldPath = current[index].evidenceImagePath
                if (oldPath != null && oldPath != originalEvidencePath) {
                    withContext(Dispatchers.IO) {
                        FileUtils.deleteEvidenceFile(getApplication(), oldPath)
                    }
                }
                current[index] = current[index].copy(evidenceImagePath = savedPath)
                _taskList.value = current
            }
        }
    }

    fun onPhotoCaptured(index: Int, tempFile: File) {
        viewModelScope.launch {
            val savedPath = withContext(Dispatchers.IO) {
                FileUtils.finalizeCapturedPhoto(getApplication(), tempFile)
            }
            if (savedPath == null) {
                _errorMessage.value = "Gagal memproses foto kamera."
                return@launch
            }
            val current = _taskList.value.toMutableList()
            if (index in current.indices) {
                val oldPath = current[index].evidenceImagePath
                if (oldPath != null && oldPath != originalEvidencePath) {
                    withContext(Dispatchers.IO) {
                        FileUtils.deleteEvidenceFile(getApplication(), oldPath)
                    }
                }
                current[index] = current[index].copy(evidenceImagePath = savedPath)
                _taskList.value = current
            }
        }
    }

    fun removeImage(index: Int) {
        val current = _taskList.value.toMutableList()
        if (index in current.indices) {
            val oldPath = current[index].evidenceImagePath
            if (oldPath != null && oldPath != originalEvidencePath) {
                viewModelScope.launch(Dispatchers.IO) {
                    FileUtils.deleteEvidenceFile(getApplication(), oldPath)
                }
            }
            current[index] = current[index].copy(evidenceImagePath = null)
            _taskList.value = current
        }
    }

    fun saveAllTasks() {
        val tasks = _taskList.value.filter { it.description.trim().isNotBlank() }
        if (tasks.isEmpty()) {
            _errorMessage.value = "Harap isi minimal satu uraian kegiatan pekerjaan."
            return
        }

        viewModelScope.launch {
            for (task in tasks) {
                val entry = JournalEntry(
                    id = task.existingEntryId,
                    dateMillis = _dateMillis.value,
                    dayOfWeek = _dayOfWeek.value,
                    formattedDate = _formattedDate.value,
                    description = task.description.trim().take(MAX_DESC_LENGTH),
                    evidenceType = "COMBINED",
                    evidenceText = task.evidenceText.trim().take(MAX_EVIDENCE_TEXT_LENGTH).ifBlank { null },
                    evidenceImagePath = task.evidenceImagePath,
                    updatedAt = System.currentTimeMillis()
                )

                if (task.existingEntryId > 0) {
                    if (originalEvidencePath != null && originalEvidencePath != task.evidenceImagePath) {
                        withContext(Dispatchers.IO) {
                            FileUtils.deleteEvidenceFile(getApplication(), originalEvidencePath)
                        }
                    }
                    repository.updateEntry(entry)
                } else {
                    repository.insertEntry(entry)
                }
            }
            _isSaved.value = true
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}
