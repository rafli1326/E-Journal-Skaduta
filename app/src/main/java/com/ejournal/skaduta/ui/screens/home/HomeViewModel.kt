package com.ejournal.skaduta.ui.screens.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ejournal.skaduta.data.local.AppDatabase
import com.ejournal.skaduta.data.local.entity.JournalEntry
import com.ejournal.skaduta.data.local.entity.UserProfile
import com.ejournal.skaduta.data.repository.JournalRepository
import com.ejournal.skaduta.util.FileUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: JournalRepository

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    val entries: StateFlow<List<JournalEntry>>
    val profile: StateFlow<UserProfile?>

    init {
        val db = AppDatabase.getInstance(application)
        repository = JournalRepository(db.journalDao(), db.profileDao())

        profile = repository.getProfile().stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            null
        )

        entries = combine(repository.getAllEntries(), _searchQuery) { list, query ->
            if (query.isBlank()) {
                list
            } else {
                list.filter {
                    it.description.contains(query, ignoreCase = true) ||
                            it.formattedDate.contains(query, ignoreCase = true) ||
                            (it.evidenceText?.contains(query, ignoreCase = true) == true)
                }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = if (query.length > 100) query.take(100) else query
    }

    fun deleteEntry(entry: JournalEntry) {
        viewModelScope.launch {
            val imagePath = entry.evidenceImagePath
            repository.deleteEntry(entry)
            if (!imagePath.isNullOrBlank()) {
                withContext(Dispatchers.IO) {
                    FileUtils.deleteEvidenceFile(getApplication(), imagePath)
                }
            }
        }
    }
}
