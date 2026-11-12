package com.amjadoh.sirfari

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

// ألوان سيرفري نفسها (ops/console/public/app.css)
private val Light = lightColorScheme(
    primary = Color(0xFF1F6F5C), onPrimary = Color.White,
    primaryContainer = Color(0xFFE1F2EC), onPrimaryContainer = Color(0xFF1F6F5C),
    secondaryContainer = Color(0xFFE1F2EC), onSecondaryContainer = Color(0xFF1F6F5C),
    background = Color(0xFFF6F5F1), onBackground = Color(0xFF1D1D1B),
    surface = Color.White, onSurface = Color(0xFF1D1D1B),
    surfaceVariant = Color(0xFFEFEDE7), onSurfaceVariant = Color(0xFF6B6A65),
    error = Color(0xFFA32D2D), errorContainer = Color(0xFFFCEBEB),
)
private val Dark = darkColorScheme(
    primary = Color(0xFF5DCAA5), onPrimary = Color(0xFF04342C),
    primaryContainer = Color(0xFF0F3B30), onPrimaryContainer = Color(0xFF5DCAA5),
    secondaryContainer = Color(0xFF0F3B30), onSecondaryContainer = Color(0xFF5DCAA5),
    background = Color(0xFF161615), onBackground = Color(0xFFF1EFE8),
    surface = Color(0xFF1F1F1D), onSurface = Color(0xFFF1EFE8),
    surfaceVariant = Color(0xFF2A2A27), onSurfaceVariant = Color(0xFFB4B2A9),
    error = Color(0xFFF09595), errorContainer = Color(0xFF501313),
)


/** dark = null: يتبع الجوال (للاختبارات). التطبيق يمرر اختيار أمجد (الداكن الافتراضي مثل سيرفري القديم). */
@Composable
fun SirfariTheme(dark: Boolean? = null, content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (dark ?: isSystemInDarkTheme()) Dark else Light) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl, content = content)
    }
}
