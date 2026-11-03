package com.ejournal.skaduta.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.ejournal.skaduta.data.local.entity.JournalEntry
import kotlinx.coroutines.flow.Flow

@Dao
interface JournalDao {

    @Query("SELECT * FROM journal_entries ORDER BY dateMillis DESC, id DESC")
    fun getAllEntries(): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE dateMillis >= :startMillis AND dateMillis <= :endMillis ORDER BY dateMillis ASC, id ASC")
    fun getEntriesForMonth(startMillis: Long, endMillis: Long): Flow<List<JournalEntry>>

    @Query("SELECT * FROM journal_entries WHERE dateMillis >= :startMillis AND dateMillis <= :endMillis ORDER BY dateMillis ASC, id ASC")
    suspend fun getEntriesForMonthSync(startMillis: Long, endMillis: Long): List<JournalEntry>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    fun getEntryById(id: Long): Flow<JournalEntry?>

    @Query("SELECT * FROM journal_entries WHERE id = :id LIMIT 1")
    suspend fun getEntryByIdSync(id: Long): JournalEntry?

    @Query("SELECT COUNT(*) FROM journal_entries WHERE dateMillis >= :startOfDay AND dateMillis <= :endOfDay")
    suspend fun getEntryCountForDate(startOfDay: Long, endOfDay: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEntry(entry: JournalEntry): Long

    @Update
    suspend fun updateEntry(entry: JournalEntry)

    @Delete
    suspend fun deleteEntry(entry: JournalEntry)

    @Query("DELETE FROM journal_entries WHERE id = :id")
    suspend fun deleteEntryById(id: Long)

    @Query("SELECT * FROM journal_entries WHERE description LIKE '%' || :query || '%' ORDER BY dateMillis DESC")
    fun searchEntries(query: String): Flow<List<JournalEntry>>
}

