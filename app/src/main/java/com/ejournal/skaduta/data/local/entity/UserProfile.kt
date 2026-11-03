package com.ejournal.skaduta.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey
    val id: Int = 1,
    // Pegawai (Staf Tata Usaha)
    val fullName: String = "",
    val employeeId: String = "",
    val position: String = "Staf Tata Usaha",
    val schoolName: String = "SMK Negeri 2 Tanjung",
    val department: String = "Tata Usaha",
    val signatureCity: String = "Tabalong",

    // Atasan Langsung (Kepala Sub Bagian Tata Usaha)
    val subbagName: String = "Erliana Indarti, S.Pd., M.M.",
    val subbagId: String = "198012172006042010",
    val subbagPosition: String = "Kepala Sub Bagian Tata Usaha",

    // Pejabat Pengesah (Kepala Sekolah)
    val headmasterName: String = "Bainah, S.Pd., M.A.Pd.",
    val headmasterId: String = "196812061994122003",
    val headmasterPosition: String = "Kepala Sekolah",

    // Pengingat Harian
    val reminderEnabled: Boolean = true,
    val reminderHour: Int = 16,
    val reminderMinute: Int = 30
)
