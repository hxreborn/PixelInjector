package eu.hxreborn.pixelinjector.support

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.app.NotificationCompat
import eu.hxreborn.pixelinjector.App
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.ui.MainActivity
import eu.hxreborn.pixelinjector.util.detail
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlin.time.Duration.Companion.hours

private const val JOB_ID = 0x7570
internal const val UPDATE_NOTIFICATION_CHANNEL = "update"
private const val NOTIFICATION_ID = 0x7570
private val Period = 12.hours
private val Flex = 2.hours
const val EXTRA_OPEN_UPDATE = "eu.hxreborn.pixelinjector.extra.OPEN_UPDATE"
const val EXTRA_UPDATE_VERSION = "eu.hxreborn.pixelinjector.extra.UPDATE_VERSION"
const val ACTION_INSTALL_UPDATE = "eu.hxreborn.pixelinjector.action.INSTALL_UPDATE"
private const val EXTRA_LATER_VERSION = "eu.hxreborn.pixelinjector.extra.LATER_VERSION"

enum class UpdateNotificationAccess { Allowed, AppBlocked, ChannelBlocked }

class UpdateWatch : JobService() {
    private val scope = MainScope()
    private var task: Job? = null

    override fun onStartJob(params: JobParameters): Boolean {
        val support = App.from(this).support
        if (!support.updateNotificationsEnabled ||
            notificationAccess(this) != UpdateNotificationAccess.Allowed
        ) {
            return false
        }
        task =
            scope.launch {
                try {
                    val release =
                        runInterruptible(Dispatchers.IO) {
                            support.releaseToNotify(params.network, coroutineContext::ensureActive)
                        }
                    if (release != null &&
                        support.updateNotificationsEnabled &&
                        release.versionCode > support.postponedVersion.value
                    ) {
                        notify(this@UpdateWatch, release)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.w(ModuleConstants.LOG_TAG, "update watch failed reason=${e.detail()}", e)
                }
                jobFinished(params, false)
            }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        task?.cancel()
        task = null
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        fun isScheduled(context: Context): Boolean =
            context.getSystemService(JobScheduler::class.java).getPendingJob(JOB_ID) != null

        fun schedule(
            context: Context,
            enabled: Boolean,
        ): Boolean {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (!enabled) {
                if (scheduler.getPendingJob(JOB_ID) != null) scheduler.cancel(JOB_ID)
                cancelNotification(context)
                return true
            }
            if (scheduler.getPendingJob(JOB_ID) != null) return true
            val job =
                JobInfo
                    .Builder(JOB_ID, ComponentName(context, UpdateWatch::class.java))
                    .setPeriodic(Period.inWholeMilliseconds, Flex.inWholeMilliseconds)
                    .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                    .setPersisted(true)
                    .build()
            val result =
                try {
                    scheduler.schedule(job)
                } catch (e: IllegalStateException) {
                    Log.w(ModuleConstants.LOG_TAG, "update notification job rejected", e)
                    return false
                }
            if (result != JobScheduler.RESULT_SUCCESS) {
                Log.w(
                    ModuleConstants.LOG_TAG,
                    "update notification job not scheduled result=$result",
                )
                return false
            }
            return true
        }

        fun cancelNotification(context: Context) {
            context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID)
        }

        fun notificationAccess(context: Context): UpdateNotificationAccess {
            val manager = context.getSystemService(NotificationManager::class.java)
            return when {
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED ||
                    !manager.areNotificationsEnabled() -> {
                    UpdateNotificationAccess.AppBlocked
                }

                manager.getNotificationChannel(UPDATE_NOTIFICATION_CHANNEL)?.importance ==
                    NotificationManager.IMPORTANCE_NONE -> {
                    UpdateNotificationAccess.ChannelBlocked
                }

                else -> {
                    UpdateNotificationAccess.Allowed
                }
            }
        }

        private fun notify(
            context: Context,
            release: Release,
        ) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    UPDATE_NOTIFICATION_CHANNEL,
                    context.getString(R.string.update_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            if (notificationAccess(context) != UpdateNotificationAccess.Allowed) return
            val launch =
                Intent(context, MainActivity::class.java)
                    .putExtra(EXTRA_OPEN_UPDATE, true)
                    .putExtra(EXTRA_UPDATE_VERSION, release.versionCode)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val open =
                PendingIntent.getActivity(
                    context,
                    release.versionCode,
                    launch,
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val later =
                PendingIntent.getBroadcast(
                    context,
                    release.versionCode,
                    Intent(context, UpdateLaterReceiver::class.java)
                        .putExtra(EXTRA_LATER_VERSION, release.versionCode),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val install =
                PendingIntent.getActivity(
                    context,
                    release.versionCode,
                    Intent(launch).setAction(ACTION_INSTALL_UPDATE),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
            val notification =
                NotificationCompat
                    .Builder(context, UPDATE_NOTIFICATION_CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(context.getString(R.string.update_notification_title))
                    .setContentText(
                        context.getString(R.string.update_card_title, release.versionName),
                    ).setContentIntent(open)
                    .addAction(0, context.getString(R.string.update_install), install)
                    .addAction(0, context.getString(R.string.later), later)
                    .setAllowSystemGeneratedContextualActions(false)
                    .setOnlyAlertOnce(true)
                    .setAutoCancel(true)
                    .build()
            manager.notify(NOTIFICATION_ID, notification)
            Log.i(ModuleConstants.LOG_TAG, "update notified version=${release.versionName}")
            App.from(context).support.markNotified(release)
        }
    }
}

class UpdateLaterReceiver : BroadcastReceiver() {
    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val versionCode = intent.getIntExtra(EXTRA_LATER_VERSION, 0)
        if (versionCode > 0) App.from(context).support.postponeUpdate(versionCode)
    }
}
