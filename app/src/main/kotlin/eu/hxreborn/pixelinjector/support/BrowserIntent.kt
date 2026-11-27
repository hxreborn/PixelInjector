package eu.hxreborn.pixelinjector.support

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.net.toUri

fun browserIntent(
    context: Context,
    url: String,
): Intent {
    val view = Intent(Intent.ACTION_VIEW, url.toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val packages = context.packageManager
    val anyWebPage =
        Intent(Intent.ACTION_VIEW, "https://".toUri()).addCategory(Intent.CATEGORY_BROWSABLE)
    val browsers =
        packages
            .queryIntentActivities(anyWebPage, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    val defaultBrowser =
        packages
            .resolveActivity(anyWebPage, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
    return when {
        defaultBrowser in browsers -> {
            view.setPackage(defaultBrowser)
        }

        browsers.size == 1 -> {
            view.setPackage(browsers.single())
        }

        else -> {
            val appLinkHandlers =
                packages
                    .queryIntentActivities(view, PackageManager.MATCH_ALL)
                    .filter { it.activityInfo.packageName !in browsers }
                    .map { ComponentName(it.activityInfo.packageName, it.activityInfo.name) }
            Intent
                .createChooser(view, null)
                .putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, appLinkHandlers.toTypedArray())
        }
    }
}
