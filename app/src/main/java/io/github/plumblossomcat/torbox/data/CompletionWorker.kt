package io.github.plumblossomcat.torbox.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.plumblossomcat.torbox.MainActivity
import io.github.plumblossomcat.torbox.R
import io.github.plumblossomcat.torbox.TorBoxApp
import io.github.plumblossomcat.torbox.core.model.Transfer
import io.github.plumblossomcat.torbox.core.model.TransferKind
import io.github.plumblossomcat.torbox.core.util.Format
import java.util.concurrent.TimeUnit

/** Polls the library in the background and posts a notification when something finishes. */
class CompletionWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as TorBoxApp).container
        val settings = container.settings
        if (!settings.current().notifyOnComplete || settings.apiKey() == null) return Result.success()

        val all = mutableListOf<Transfer>()
        for (kind in TransferKind.entries) {
            all += runCatching { container.client.list(kind, bypassCache = true) }.getOrElse { return Result.retry() }
        }
        val done = all.filter { it.state.isDone }
        val doneKeys = done.map { it.key }.toSet()
        val known = settings.knownDone()
        if (known != null) {
            done.filter { it.key !in known }.take(5).forEach { notify(applicationContext, it) }
        }
        settings.setKnownDone(doneKeys)
        return Result.success()
    }

    companion object {
        private const val WORK = "torbox.completion"
        const val CHANNEL = "completed"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK)
                return
            }
            val request = PeriodicWorkRequestBuilder<CompletionWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val channel = NotificationChannel(CHANNEL, context.getString(R.string.channel_completed), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.channel_completed_desc)
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        fun canNotify(context: Context): Boolean =
            Build.VERSION.SDK_INT < 33 ||
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

        private fun notify(context: Context, item: Transfer) {
            if (!canNotify(context)) return
            ensureChannel(context)
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(IncomingIntent.EXTRA_OPEN_KIND, item.kind.name)
                putExtra(IncomingIntent.EXTRA_OPEN_ID, item.id)
            }
            val pending = PendingIntent.getActivity(
                context, item.key.hashCode(), open,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_done)
                .setContentTitle(item.name)
                .setContentText("Ready to download · ${item.kind.label} · ${Format.bytes(item.size)}")
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build()
            @Suppress("MissingPermission")
            NotificationManagerCompat.from(context).notify(item.key.hashCode(), notification)
        }
    }
}
