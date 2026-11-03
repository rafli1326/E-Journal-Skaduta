package com.ejournal.skaduta.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ejournal.skaduta.MainActivity
import com.ejournal.skaduta.R
import com.ejournal.skaduta.data.local.AppDatabase
import com.ejournal.skaduta.data.repository.JournalRepository
import com.ejournal.skaduta.util.DateUtils
import java.util.Calendar
import java.util.concurrent.TimeUnit

class DailyReminderWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getInstance(context)
        val repository = JournalRepository(database.journalDao(), database.profileDao())

        val profile = repository.getProfileSync()
        if (profile?.reminderEnabled == false) {
            return Result.success()
        }

        // Check if user already recorded an entry today
        val (startOfDay, endOfDay) = DateUtils.getTodayStartAndEndMillis()
        val alreadyRecorded = repository.hasEntryToday(startOfDay, endOfDay)

        if (!alreadyRecorded) {
            sendReminderNotification()
        }

        return Result.success()
    }

    private fun sendReminderNotification() {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val channelId = CHANNEL_ID
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                context.getString(R.string.channel_reminder_name),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.channel_reminder_desc)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_menu_agenda)
            .setContentTitle("Pengingat E-Journal Kantor")
            .setContentText("Jangan lupa mencatat rekap kegiatan pekerjaan Anda hari ini!")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("Waktunya rekap pekerjaan hari ini! Catat tugas dan lampirkan bukti dukung agar laporan bulanan siap cetak.")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val CHANNEL_ID = "ejournal_daily_reminder"
        private const val NOTIFICATION_ID = 1001
        private const val WORK_TAG = "ejournal_reminder_work"

        fun scheduleReminder(context: Context, hour: Int, minute: Int) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, hour)
                set(Calendar.MINUTE, minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }

            if (target.before(now)) {
                target.add(Calendar.DAY_OF_YEAR, 1)
            }

            val initialDelay = target.timeInMillis - now.timeInMillis

            val workRequest = PeriodicWorkRequestBuilder<DailyReminderWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(initialDelay, TimeUnit.MILLISECONDS)
                .setConstraints(Constraints.NONE)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_TAG,
                ExistingPeriodicWorkPolicy.UPDATE,
                workRequest
            )
        }

        fun cancelReminder(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(WORK_TAG)
        }
    }
}

