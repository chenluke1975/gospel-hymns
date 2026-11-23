package org.gospelhymns.jukebox.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import org.gospelhymns.jukebox.GospelApp
import java.util.concurrent.TimeUnit

class LyricSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repository = (applicationContext as GospelApp).repository
        if (repository.lyricsSavedCount() >= repository.lyricLibrarySize()) return Result.success()
        val failures = try {
            repository.downloadMissingLyrics { progress ->
                setProgress(
                    workDataOf(
                        "saved" to progress.saved,
                        "total" to progress.total,
                        "current" to (progress.current ?: -1),
                    ),
                )
                if (progress.running) {
                    runCatching { setForeground(foreground(progress.saved, progress.total)) }
                }
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            return Result.retry()
        }
        return if (failures == 0 || runAttemptCount >= 4) Result.success() else Result.retry()
    }

    private fun foreground(saved: Int, total: Int): ForegroundInfo {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "歌詞下載", NotificationManager.IMPORTANCE_LOW),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle("正在下載歌詞")
            .setContentText("$saved / $total")
            .setOngoing(true)
            .setSilent(true)
            .setOnlyAlertOnce(true)
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        const val NAME = "lyric-library"
        private const val CHANNEL = "lyric-sync"
        private const val NOTIFICATION_ID = 42

        fun enqueue(context: Context, replace: Boolean = false) {
            val request = OneTimeWorkRequestBuilder<LyricSyncWorker>()
                .setConstraints(
                    Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build(),
                )
                .setBackoffCriteria(BackoffPolicy.LINEAR, 30, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP,
                request,
            )
        }
    }
}
