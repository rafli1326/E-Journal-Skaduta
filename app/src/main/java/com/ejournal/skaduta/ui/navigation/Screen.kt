package com.ejournal.skaduta.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Person
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector? = null) {
    object Home : Screen("home", "Jurnal Harian", Icons.AutoMirrored.Filled.ListAlt)
    object MonthlyReport : Screen("monthly_report", "Laporan Bulanan", Icons.Default.DateRange)
    object Profile : Screen("profile", "Profil Pegawai", Icons.Default.Person)

    object AddEditJournal : Screen("add_edit_journal?entryId={entryId}", "Catat Kegiatan") {
        fun createRoute(entryId: Long? = null): String {
            return if (entryId != null && entryId > 0) {
                "add_edit_journal?entryId=$entryId"
            } else {
                "add_edit_journal?entryId=-1"
            }
        }
    }
}

