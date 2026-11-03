package com.ejournal.skaduta.data.repository

import com.ejournal.skaduta.data.local.dao.JournalDao
import com.ejournal.skaduta.data.local.dao.ProfileDao
import com.ejournal.skaduta.data.local.entity.JournalEntry
import com.ejournal.skaduta.data.local.entity.UserProfile
import kotlinx.coroutines.flow.Flow

class JournalRepository(
    private val journalDao: JournalDao,
    private val profileDao: ProfileDao
) {
    fun getAllEntries(): Flow<List<JournalEntry>> = journalDao.getAllEntries()

    fun getEntriesForMonth(startMillis: Long, endMillis: Long): Flow<List<JournalEntry>> =
        journalDao.getEntriesForMonth(startMillis, endMillis)

    suspend fun getEntriesForMonthSync(startMillis: Long, endMillis: Long): List<JournalEntry> =
        journalDao.getEntriesForMonthSync(startMillis, endMillis)

    fun getEntryById(id: Long): Flow<JournalEntry?> = journalDao.getEntryById(id)

    suspend fun getEntryByIdSync(id: Long): JournalEntry? = journalDao.getEntryByIdSync(id)

    suspend fun insertEntry(entry: JournalEntry): Long = journalDao.insertEntry(entry)

    suspend fun updateEntry(entry: JournalEntry) = journalDao.updateEntry(entry)

    suspend fun deleteEntry(entry: JournalEntry) = journalDao.deleteEntry(entry)

    suspend fun deleteEntryById(id: Long) = journalDao.deleteEntryById(id)

    fun searchEntries(query: String): Flow<List<JournalEntry>> = journalDao.searchEntries(query)

    suspend fun hasEntryToday(startOfDay: Long, endOfDay: Long): Boolean =
        journalDao.getEntryCountForDate(startOfDay, endOfDay) > 0

    // Profile
    fun getProfile(): Flow<UserProfile?> = profileDao.getProfile()

    suspend fun getProfileSync(): UserProfile? = profileDao.getProfileSync()

    suspend fun saveProfile(profile: UserProfile) = profileDao.saveProfile(profile)
}

