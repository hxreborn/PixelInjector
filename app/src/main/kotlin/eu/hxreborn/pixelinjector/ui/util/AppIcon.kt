package eu.hxreborn.pixelinjector.ui.util

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Process
import android.util.LruCache
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.createBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

val AppIconSize = 40.dp

private const val MAX_ICON_PX = 512

private val iconCache = LruCache<String, ImageBitmap>(200)

fun Drawable.toIconBitmap(size: Int): Bitmap {
    val side = size.coerceIn(1, MAX_ICON_PX)
    val bitmap = createBitmap(side, side)
    val previousBounds = Rect(bounds)
    setBounds(0, 0, side, side)
    draw(Canvas(bitmap))
    bounds = previousBounds
    return bitmap
}

@Composable
fun rememberAppIcon(
    packageName: String,
    sizePx: Int,
): ImageBitmap? {
    val context = LocalContext.current
    val key = "$packageName:$sizePx"
    return produceState<ImageBitmap?>(initialValue = iconCache.get(key), key1 = key) {
        if (value != null) return@produceState
        value =
            withContext(Dispatchers.IO) {
                runCatching {
                    val launcherApps = context.getSystemService(Context.LAUNCHER_APPS_SERVICE) as LauncherApps
                    val info = launcherApps.getActivityList(packageName, Process.myUserHandle()).firstOrNull()
                    val drawable = info?.getIcon(0) ?: context.packageManager.getApplicationIcon(packageName)
                    drawable.toIconBitmap(sizePx).asImageBitmap()
                }.getOrNull()?.also { iconCache.put(key, it) }
            }
    }.value
}
