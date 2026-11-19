package eu.hxreborn.pixelinjector.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eu.hxreborn.pixelinjector.R

private const val MAX_FONT_WEIGHT = 1000

private fun variableFont(
    resId: Int,
    weight: FontWeight,
): Font =
    Font(
        resId = resId,
        weight = weight,
        style = FontStyle.Normal,
        variationSettings = FontVariation.Settings(weight, FontStyle.Normal),
    )

val RobotoFlex =
    FontFamily(
        variableFont(R.font.roboto_flex, FontWeight.Normal),
        variableFont(R.font.roboto_flex, FontWeight.Medium),
        variableFont(R.font.roboto_flex, FontWeight.SemiBold),
        variableFont(R.font.roboto_flex, FontWeight.Bold),
        variableFont(R.font.roboto_flex, FontWeight.ExtraBold),
        variableFont(R.font.roboto_flex, FontWeight.Black),
        variableFont(R.font.roboto_flex, FontWeight(MAX_FONT_WEIGHT)),
    )

val RobotoMono =
    FontFamily(
        variableFont(R.font.roboto_mono, FontWeight.Normal),
        variableFont(R.font.roboto_mono, FontWeight.Medium),
    )

val AppTypography = Typography(RobotoFlex)

internal val DefaultAppText = AppTextStyles(RobotoFlex)
internal val LocalAppText = staticCompositionLocalOf { DefaultAppText }

@Composable
internal fun systemFontFamily(): FontFamily {
    val resources = LocalResources.current
    val resourceId = resources.getIdentifier("config_bodyFontFamily", "string", "android")
    val familyName = if (resourceId == 0) "" else resources.getString(resourceId)
    return remember(familyName) {
        if (familyName.isBlank() || familyName == "sans-serif") {
            FontFamily.Default
        } else {
            FontFamily(
                (100..MAX_FONT_WEIGHT step 100).map { weight ->
                    Font(DeviceFontFamilyName(familyName), FontWeight(weight))
                },
            )
        }
    }
}

val AppText: AppTextStyles
    @Composable
    @ReadOnlyComposable
    get() = LocalAppText.current

class AppTextStyles internal constructor(
    fontFamily: FontFamily,
) {
    val appBarTitle =
        TextStyle(
            fontFamily = fontFamily,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.36).sp,
        )
    val appBarVersion = TextStyle(fontFamily = RobotoMono, fontSize = 12.sp, lineHeight = 16.sp)
    val subheader = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val tileTitle = TextStyle(fontFamily = fontFamily, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val tileSupporting = TextStyle(fontFamily = fontFamily, fontSize = 13.sp, lineHeight = 18.sp)
    val count = TextStyle(fontFamily = RobotoMono, fontSize = 12.sp, lineHeight = 16.sp)
    val meta = TextStyle(fontFamily = RobotoMono, fontSize = 12.sp, lineHeight = 16.sp)
    val chip = TextStyle(fontFamily = RobotoMono, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val button = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val buttonCount = TextStyle(fontFamily = RobotoMono, fontSize = 13.sp, lineHeight = 20.sp)
    val sheetTitle = TextStyle(fontFamily = fontFamily, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    val sheetSubtitle = TextStyle(fontFamily = fontFamily, fontSize = 14.sp, lineHeight = 20.sp)
    val progressTitle = TextStyle(fontFamily = fontFamily, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val progressCounter = TextStyle(fontFamily = RobotoMono, fontSize = 16.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    val console = TextStyle(fontFamily = RobotoMono, fontSize = 11.5.sp, lineHeight = 18.sp)
}
