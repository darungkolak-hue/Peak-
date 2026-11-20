package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFamily = FontFamily(Font(R.font.space_grotesk, FontWeight.Normal))
val PlusJakartaFamily = FontFamily(Font(R.font.plus_jakarta_sans, FontWeight.Normal))
val SyneFamily = FontFamily(Font(R.font.syne, FontWeight.Normal))
val CormorantFamily = FontFamily(Font(R.font.cormorant_garamond, FontWeight.Normal))
val BebasNeueFamily = FontFamily(Font(R.font.bebas_neue, FontWeight.Normal))
val JetBrainsMonoFamily = FontFamily(Font(R.font.jetbrains_mono, FontWeight.Normal))

fun fontFamilyForName(name: String): FontFamily = when (name) {
    "Space Grotesk" -> SpaceGroteskFamily
    "Plus Jakarta Sans" -> PlusJakartaFamily
    "Syne" -> SyneFamily
    "Cormorant Garamond" -> CormorantFamily
    "Bebas Neue" -> BebasNeueFamily
    "JetBrains Mono" -> JetBrainsMonoFamily
    else -> SpaceGroteskFamily
}

fun fontResForName(name: String): Int = when (name) {
    "Space Grotesk" -> R.font.space_grotesk
    "Plus Jakarta Sans" -> R.font.plus_jakarta_sans
    "Syne" -> R.font.syne
    "Cormorant Garamond" -> R.font.cormorant_garamond
    "Bebas Neue" -> R.font.bebas_neue
    "JetBrains Mono" -> R.font.jetbrains_mono
    else -> R.font.space_grotesk
}

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SyneFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 36.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.5).sp
    ),
    headlineMedium = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 24.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp
    ),
    titleLarge = TextStyle(
        fontFamily = SpaceGroteskFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 19.sp,
        lineHeight = 25.sp
    ),
    titleMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 22.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = PlusJakartaFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp
    ),
    labelMedium = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.4.sp
    ),
    labelSmall = TextStyle(
        fontFamily = JetBrainsMonoFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.3.sp
    )
)
