package eu.hxreborn.pixelinjector.prefs

import android.graphics.Typeface
import eu.hxreborn.pixelinjector.R

enum class NowPlayingChipFont(
    val labelRes: Int,
    private val familyName: String?,
) {
    DEFAULT(R.string.now_playing_chip_font_default, null),
    SANS_SERIF_MEDIUM(R.string.now_playing_chip_font_medium, "sans-serif-medium"),
    CONDENSED(R.string.now_playing_chip_font_condensed, "sans-serif-condensed"),
    SERIF(R.string.now_playing_chip_font_serif, "serif"),
    MONOSPACE(R.string.now_playing_chip_font_monospace, "monospace"),
    ;

    val preferenceValue: String get() = familyName ?: "default"

    fun typeface(defaultTypeface: Typeface = Typeface.DEFAULT): Typeface =
        familyName?.let { Typeface.create(it, Typeface.NORMAL) } ?: defaultTypeface

    companion object {
        fun fromPreference(value: String): NowPlayingChipFont =
            entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}
