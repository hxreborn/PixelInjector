package eu.hxreborn.pixelinjector.icons

import android.graphics.Color
import android.graphics.Path
import android.graphics.drawable.Drawable
import android.graphics.drawable.ShapeDrawable
import android.graphics.drawable.shapes.PathShape
import androidx.core.graphics.PathParser

private const val VIEWPORT = 960

object ShadeIcons {
    const val NO_ICON = "none"

    fun fromPathData(pathData: String): Drawable {
        val path =
            if (pathData == NO_ICON) {
                Path()
            } else {
                PathParser.createPathFromPathData(pathData).apply { offset(0f, VIEWPORT.toFloat()) }
            }
        return ShapeDrawable(PathShape(path, VIEWPORT.toFloat(), VIEWPORT.toFloat())).apply {
            intrinsicWidth = VIEWPORT
            intrinsicHeight = VIEWPORT
            paint.color = Color.WHITE
        }
    }
}
