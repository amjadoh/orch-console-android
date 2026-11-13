package com.amjadoh.sirfari

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp

// سيرفري 2.1: «لوحة قرارات ليلية» بنفس كحلي الأيقونة. الأزرق للإجراء، والبرتقالي لـ«ينتظرك»،
// والبنفسجي للشغال (عشان ما يختلط بأزرق الأزرار)، والأحمر للمشكلة.
private val Dark = darkColorScheme(
    primary = Color(0xFF5AB0FF), onPrimary = Color(0xFF04162B),
    primaryContainer = Color(0xFF123067), onPrimaryContainer = Color(0xFF9CCFFF),
    secondaryContainer = Color(0x295AB0FF), onSecondaryContainer = Color(0xFF5AB0FF),
    background = Color(0xFF070D1A), onBackground = Color(0xFFEAF0FF),
    surface = Color(0xFF0E1729), onSurface = Color(0xFFEAF0FF),
    surfaceVariant = Color(0xFF142038), onSurfaceVariant = Color(0xFF8C9BB8),
    surfaceContainer = Color(0xFF0E1729), surfaceContainerHigh = Color(0xFF142038), surfaceContainerLow = Color(0xFF0E1729),
    outline = Color(0x29FFFFFF), outlineVariant = Color(0x14FFFFFF),
    error = Color(0xFFF07A7A), errorContainer = Color(0xFF3A1620),
)
private val Light = lightColorScheme(
    primary = Color(0xFF1B5FD0), onPrimary = Color.White,
    primaryContainer = Color(0xFFDCE8FF), onPrimaryContainer = Color(0xFF123067),
    secondaryContainer = Color(0xFFDCE8FF), onSecondaryContainer = Color(0xFF1B5FD0),
    background = Color(0xFFF2F5FB), onBackground = Color(0xFF0B1630),
    surface = Color.White, onSurface = Color(0xFF0B1630),
    surfaceVariant = Color(0xFFE6ECF7), onSurfaceVariant = Color(0xFF55627D),
    surfaceContainer = Color.White, surfaceContainerHigh = Color(0xFFE6ECF7), surfaceContainerLow = Color.White,
    outline = Color(0x290B1630), outlineVariant = Color(0x140B1630),
    error = Color(0xFFC0392B), errorContainer = Color(0xFFFCE4E4),
)

/** ألوان إضافية ما في Material: الحد الرفيع، وخلفية البطاقة الرئيسية، وخلفية «آخر الحركات» */
@Immutable
data class SirfariColors(val line: Color, val hero: Color, val heroLine: Color, val live: Color, val dark: Boolean)

private val DarkExtra = SirfariColors(Color(0x14FFFFFF), Color(0xFF0C1628), Color(0x385AB0FF), Color(0xFF15132A), true)
private val LightExtra = SirfariColors(Color(0x170B1630), Color(0xFFE4EDFF), Color(0x401B5FD0), Color(0xFFF0ECFF), false)
val LocalSirfari = staticCompositionLocalOf { DarkExtra }

val Plex = FontFamily(
    Font(R.font.ibm_plex_sans_arabic_regular, FontWeight.Normal),
    Font(R.font.ibm_plex_sans_arabic_medium, FontWeight.Medium),
    Font(R.font.ibm_plex_sans_arabic_semibold, FontWeight.SemiBold),
    Font(R.font.ibm_plex_sans_arabic_bold, FontWeight.Bold),
)

private fun t(size: Int, weight: FontWeight, line: Int) = TextStyle(fontFamily = Plex, fontSize = size.sp, fontWeight = weight, lineHeight = line.sp)
private val Type = Typography(
    displaySmall = t(36, FontWeight.Bold, 44),
    headlineLarge = t(52, FontWeight.Bold, 56),
    headlineMedium = t(30, FontWeight.Bold, 40),
    headlineSmall = t(24, FontWeight.Bold, 34),
    titleLarge = t(22, FontWeight.Bold, 30),
    titleMedium = t(17, FontWeight.Bold, 26),
    titleSmall = t(15, FontWeight.SemiBold, 22),
    bodyLarge = t(16, FontWeight.Normal, 26),
    bodyMedium = t(15, FontWeight.Normal, 23),
    bodySmall = t(13, FontWeight.Normal, 19),
    labelLarge = t(15, FontWeight.SemiBold, 20),
    labelMedium = t(13, FontWeight.Medium, 18),
    labelSmall = t(12, FontWeight.Medium, 16),
)

/** dark = null: يتبع الجوال (للاختبارات). التطبيق يمرر اختيار أمجد (الداكن الافتراضي). */
@Composable
fun SirfariTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    val d = dark ?: isSystemInDarkTheme()
    MaterialTheme(colorScheme = if (d) Dark else Light, typography = Type) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, LocalSirfari provides (if (d) DarkExtra else LightExtra), content = content)
    }
}
