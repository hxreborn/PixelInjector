package eu.hxreborn.pixelinjector.ui.util

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Immutable
data class LauncherApp(
    val packageName: String,
    val label: String,
)

suspend fun loadLauncherApps(context: Context): List<LauncherApp> =
    withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        pm
            .queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(0))
            .map { info ->
                val app = info.activityInfo.applicationInfo
                LauncherApp(
                    packageName = app.packageName,
                    label = runCatching { info.loadLabel(pm).toString() }.getOrDefault(app.packageName),
                )
            }.distinctBy { it.packageName }
            .sortedBy { it.label.lowercase() }
    }
