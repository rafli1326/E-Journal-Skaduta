package com.ejournal.skaduta

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.ejournal.skaduta.data.local.AppDatabase
import com.ejournal.skaduta.data.local.entity.UserProfile
import com.ejournal.skaduta.util.FileUtils
import com.ejournal.skaduta.worker.DailyReminderWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class EJournalApp : Application() {

    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        // Seed default profile if empty & schedule initial reminder
        CoroutineScope(Dispatchers.IO).launch {
            FileUtils.cleanupTempCameraFiles(this@EJournalApp)
            val db = AppDatabase.getInstance(this@EJournalApp)
            val profile = db.profileDao().getProfileSync()
            if (profile == null) {
                val defaultProfile = UserProfile(
                    id = 1,
                    fullName = "",
                    employeeId = "",
                    position = "Staf Tata Usaha",
                    schoolName = "SMK Negeri 2 Tanjung",
                    department = "Tata Usaha",
                    signatureCity = "Tabalong",
                    subbagName = "Erliana Indarti, S.Pd., M.M.",
                    subbagId = "198012172006042010",
                    subbagPosition = "Kepala Sub Bagian Tata Usaha",
                    headmasterName = "Bainah, S.Pd., M.A.Pd.",
                    headmasterId = "196812061994122003",
                    headmasterPosition = "Kepala Sekolah",
                    reminderEnabled = true,
                    reminderHour = 16,
                    reminderMinute = 30
                )
                db.profileDao().saveProfile(defaultProfile)
                DailyReminderWorker.scheduleReminder(this@EJournalApp, 16, 30)
            } else {
                // Update default supervisor and headmaster if empty or using previous placeholder
                val updatedProfile = profile.copy(
                    subbagName = profile.subbagName.ifBlank { "Erliana Indarti, S.Pd., M.M." },
                    subbagId = profile.subbagId.ifBlank { "198012172006042010" },
                    headmasterName = profile.headmasterName.ifBlank { "Bainah, S.Pd., M.A.Pd." },
                    headmasterId = profile.headmasterId.ifBlank { "196812061994122003" },
                    signatureCity = if (profile.signatureCity.isBlank() || profile.signatureCity == "Jakarta" || profile.signatureCity == "Tanjung") "Tabalong" else profile.signatureCity
                )
                db.profileDao().saveProfile(updatedProfile)

                if (updatedProfile.reminderEnabled) {
                    DailyReminderWorker.scheduleReminder(
                        this@EJournalApp,
                        updatedProfile.reminderHour,
                        updatedProfile.reminderMinute
                    )
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                DailyReminderWorker.CHANNEL_ID,
                getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = getString(R.string.channel_reminder_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

