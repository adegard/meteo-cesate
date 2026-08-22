package com.adegard.meteocesate

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class StormWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        return try {
            val w = WeatherRepo.fetch()
            val storm = WeatherRepo.firstStorm(w) ?: return Result.success()

            val prefs = applicationContext.getSharedPreferences("storm", Context.MODE_PRIVATE)
            if (prefs.getString("last", null) == storm.iso) return Result.success()
            prefs.edit().putString("last", storm.iso).apply()

            notify(storm)
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }

    private fun notify(s: Hour) {
        val ctx = applicationContext
        val nm = NotificationManagerCompat.from(ctx)
        if (Build.VERSION.SDK_INT >= 26) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Avvisi temporali", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Temporali previsti a Cesate"
                }
            )
        }
        val hail = Wmo.isHail(s.code)
        val text = buildString {
            append("Temporali previsti ").append(Wmo.whenLabel(s.iso))
            if (hail) append(" · possibile grandine")
            append(". Dettagli nell'app.")
        }
        val notif = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat)
            .setContentTitle("⚠️ Temporali a Cesate")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()
        try {
            val granted = Build.VERSION.SDK_INT < 33 ||
                ActivityCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (granted) nm.notify(s.iso.hashCode(), notif)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CHANNEL = "temporali"
        private const val WORK = "storm_check"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<StormWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
                )
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
