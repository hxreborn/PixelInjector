package eu.hxreborn.pixelinjector.support

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import eu.hxreborn.pixelinjector.App
import eu.hxreborn.pixelinjector.ModuleConstants
import eu.hxreborn.pixelinjector.ModuleConstants.FIREFOX_PACKAGES
import eu.hxreborn.pixelinjector.R
import eu.hxreborn.pixelinjector.betterfox.BETTERFOX_ASSET
import eu.hxreborn.pixelinjector.betterfox.BetterfoxList
import eu.hxreborn.pixelinjector.betterfox.chooseList
import eu.hxreborn.pixelinjector.betterfox.geckoMajor
import eu.hxreborn.pixelinjector.prefs.Prefs
import eu.hxreborn.pixelinjector.ui.MainActivity
import eu.hxreborn.pixelinjector.util.detail
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.hours

private const val JOB_ID = 0x6b0f
private const val CHANNEL = "gecko"
private const val STATE_PREFS = "geckoWatch"
private const val SEQUENCE = "sequence"
private const val BOOT = "boot"
private const val NOTIFIED = "notified"
private val Period = 12.hours

class GeckoWatch : JobService() {
    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "GeckoWatch") {
            try {
                notifyNewerGecko(this)
            } catch (e: Exception) {
                Log.w(ModuleConstants.LOG_TAG, "gecko watch failed reason=${e.detail()}", e)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (scheduler.getPendingJob(JOB_ID) != null) return
            scheduler.schedule(
                JobInfo
                    .Builder(JOB_ID, ComponentName(context, GeckoWatch::class.java))
                    .setPeriodic(Period.inWholeMilliseconds)
                    .setPersisted(false)
                    .build(),
            )
        }

        fun notifyNewerGecko(context: Context) {
            val state = context.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE)
            val boot =
                Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT, 0)
            val sequence = if (state.getInt(BOOT, -1) == boot) state.getInt(SEQUENCE, 0) else 0
            val changed = context.packageManager.getChangedPackages(sequence) ?: return
            state.edit {
                putInt(BOOT, boot)
                putInt(SEQUENCE, changed.sequenceNumber)
            }
            if (changed.packageNames.none { it in FIREFOX_PACKAGES } && sequence != 0) return
            val app = App.from(context)
            val bundled =
                context.assets.open(BETTERFOX_ASSET).use {
                    BetterfoxList.parse(it.readBytes().decodeToString())
                }
            val list =
                chooseList(
                    bundled,
                    BetterfoxList.parseOrNull(app.prefs.snapshot()[Prefs.FIREFOX_BETTERFOX_LIST]),
                )
            val listMajor = geckoMajor(list.gecko) ?: return
            var notified = state.getStringSet(NOTIFIED, emptySet()).orEmpty()
            for (browser in installedBrowsers(context, FIREFOX_PACKAGES.toList())) {
                val gecko = browser.gecko ?: continue
                val major = geckoMajor(gecko) ?: continue
                val key = "${browser.packageName}:$gecko"
                Log.i(
                    ModuleConstants.LOG_TAG,
                    "gecko watch pkg=${browser.packageName} gecko=$gecko list=${list.gecko}",
                )
                if (major <= listMajor || key in notified) continue
                if (notify(context, browser, list.gecko)) {
                    notified = notified + key
                    state.edit { putStringSet(NOTIFIED, notified) }
                }
            }
        }

        private fun notify(
            context: Context,
            browser: Browser,
            listGecko: String,
        ): Boolean {
            if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                return false
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL,
                    context.getString(R.string.betterfox_gecko_channel),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ),
            )
            val launch =
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val open = PendingIntent.getActivity(context, 0, launch, PendingIntent.FLAG_IMMUTABLE)
            val title =
                context.getString(
                    R.string.betterfox_gecko_newer,
                    browser.label,
                    browser.gecko,
                )
            val notification =
                NotificationCompat
                    .Builder(context, CHANNEL)
                    .setSmallIcon(R.drawable.ic_notification)
                    .setContentTitle(title)
                    .setContentText(context.getString(R.string.betterfox_gecko_list, listGecko))
                    .setContentIntent(open)
                    .setAutoCancel(true)
                    .build()
            manager.notify(browser.packageName.hashCode(), notification)
            return true
        }
    }
}
