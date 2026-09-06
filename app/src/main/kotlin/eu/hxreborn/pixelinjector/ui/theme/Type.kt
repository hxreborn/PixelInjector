package eu.hxreborn.pixelinjector.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import eu.hxreborn.pixelinjector.R

private fun variable(
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
        variable(R.font.roboto_flex, FontWeight.Normal),
        variable(R.font.roboto_flex, FontWeight.Medium),
        variable(R.font.roboto_flex, FontWeight.SemiBold),
        variable(R.font.roboto_flex, FontWeight.Bold),
    )

val RobotoMono =
    FontFamily(
        variable(R.font.roboto_mono, FontWeight.Normal),
        variable(R.font.roboto_mono, FontWeight.Medium),
    )

val AppTypography = Typography(RobotoFlex)

object AppText {
    val appBarTitle =
        TextStyle(
            fontFamily = RobotoFlex,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-0.36).sp,
        )
    val appBarVersion = TextStyle(fontFamily = RobotoMono, fontSize = 12.sp, lineHeight = 16.sp)
    val subheader = TextStyle(fontFamily = RobotoFlex, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val tileTitle = TextStyle(fontFamily = RobotoFlex, fontSize = 15.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium)
    val tileSupporting = TextStyle(fontFamily = RobotoFlex, fontSize = 13.sp, lineHeight = 18.sp)
    val count = TextStyle(fontFamily = RobotoMono, fontSize = 12.sp, lineHeight = 16.sp)
    val meta = TextStyle(fontFamily = RobotoMono, fontSize = 12.sp, lineHeight = 16.sp)
    val chip = TextStyle(fontFamily = RobotoMono, fontSize = 11.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
    val button = TextStyle(fontFamily = RobotoFlex, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
    val buttonCount = TextStyle(fontFamily = RobotoMono, fontSize = 13.sp, lineHeight = 20.sp)
    val sheetTitle = TextStyle(fontFamily = RobotoFlex, fontSize = 24.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold)
    val sheetSubtitle = TextStyle(fontFamily = RobotoFlex, fontSize = 14.sp, lineHeight = 20.sp)
    val progressTitle = TextStyle(fontFamily = RobotoFlex, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold)
    val progressCounter = TextStyle(fontFamily = RobotoMono, fontSize = 16.sp, lineHeight = 28.sp, fontWeight = FontWeight.Medium)
    val console = TextStyle(fontFamily = RobotoMono, fontSize = 11.5.sp, lineHeight = 18.sp)
}
