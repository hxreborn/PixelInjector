package eu.hxreborn.pixelinjector.prefs

import android.graphics.Typeface
import eu.hxreborn.pixelinjector.R
import java.io.File

private val GOOGLE_SANS_FLEX_PATHS =
    listOf(
        "/product/fonts/GoogleSansFlex-Regular.ttf",
        "/system/fonts/GoogleSansFlex-Regular.ttf",
    )
private const val ROND_0_VARIATION = "'ROND' 0"
private const val ROND_100_VARIATION = "'ROND' 100"

private val googleSansFlexFile: File? by lazy {
    GOOGLE_SANS_FLEX_PATHS.map(::File).firstOrNull(File::canRead)
}

enum class NowPlayingChipFont(
    val labelRes: Int,
    val preferenceValue: String,
    private val familyName: String? = null,
    private val googleSansFlexVariation: String? = null,
) {
    DEFAULT(R.string.now_playing_chip_font_default, "default"),
    SANS_SERIF_MEDIUM(
        R.string.now_playing_chip_font_medium,
        "sans-serif-medium",
        familyName = "sans-serif-medium",
    ),
    CONDENSED(
        R.string.now_playing_chip_font_condensed,
        "sans-serif-condensed",
        familyName = "sans-serif-condensed",
    ),
    SERIF(R.string.now_playing_chip_font_serif, "serif", familyName = "serif"),
    MONOSPACE(R.string.now_playing_chip_font_monospace, "monospace", familyName = "monospace"),
    GOOGLE_SANS_FLEX(
        R.string.now_playing_chip_font_google_sans_flex,
        "google-sans-flex",
        googleSansFlexVariation = ROND_0_VARIATION,
    ),
    GOOGLE_SANS_FLEX_ROUNDED(
        R.string.now_playing_chip_font_google_sans_flex_rounded,
        "google-sans-flex-rounded",
        googleSansFlexVariation = ROND_100_VARIATION,
    ),
    ;

    val isAvailable: Boolean get() = googleSansFlexVariation == null || googleSansFlexFile != null

    fun typeface(defaultTypeface: Typeface = Typeface.DEFAULT): Typeface {
        if (googleSansFlexVariation != null) {
            val file = googleSansFlexFile ?: return defaultTypeface
            return Typeface.Builder(file).setFontVariationSettings(googleSansFlexVariation).build()
        }
        return familyName?.let { Typeface.create(it, Typeface.NORMAL) } ?: defaultTypeface
    }

    companion object {
        fun fromPreference(value: String): NowPlayingChipFont =
            entries.firstOrNull { it.preferenceValue == value } ?: DEFAULT
    }
}
