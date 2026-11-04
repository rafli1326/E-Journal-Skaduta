package com.ejournal.skaduta.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "journal_entries",
    indices = [Index(value = ["dateMillis"])]
)
data class JournalEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val dateMillis: Long,
    val dayOfWeek: String,
    val formattedDate: String,
    val description: String,
    val evidenceType: String = EVIDENCE_TYPE_TEXT, // "TEXT" or "IMAGE"
    val evidenceText: String? = null,
    val evidenceImagePath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val EVIDENCE_TYPE_TEXT = "TEXT"
        const val EVIDENCE_TYPE_IMAGE = "IMAGE"
    }

    fun getEvidenceImagePaths(): List<String> {
        if (evidenceImagePath.isNullOrBlank()) return emptyList()
        return evidenceImagePath.split("|").map { it.trim() }.filter { it.isNotEmpty() }
    }
}

