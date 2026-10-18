package com.scarlett.geekradar.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** 方案 C「苹果式柔和层次」配色：暖白底、深灰正文、单一克制蓝色。 */
@Immutable
data class GeekColors(
    val bg: Color, val card: Color, val text: Color, val secondary: Color, val tertiary: Color,
    val accent: Color, val accentSoft: Color, val divider: Color, val bar: Color, val fieldBg: Color,
    val warn: Color, val warnSoft: Color, val ok: Color, val danger: Color, val chipNeutral: Color,
)

val LightGeek = GeekColors(
    bg = Color(0xFFF4F2EE), card = Color.White, text = Color(0xFF1F2024), secondary = Color(0xFF76777D), tertiary = Color(0xFF8E8F95),
    accent = Color(0xFF3B67D6), accentSoft = Color(0x1A3B67D6), divider = Color(0xFFEBE9E4), bar = Color(0xE6FAF8F4), fieldBg = Color(0xFFF1F0EC),
    warn = Color(0xFF8A6A2C), warnSoft = Color(0xFFF5ECDA), ok = Color(0xFF3D8B5A), danger = Color(0xFFC0392B), chipNeutral = Color(0xFFEFEEE9),
)
val DarkGeek = GeekColors(
    bg = Color(0xFF121214), card = Color(0xFF1E1E21), text = Color(0xFFEDEDEF), secondary = Color(0xFF9A9BA1), tertiary = Color(0xFF7D7E84),
    accent = Color(0xFF7A9BF0), accentSoft = Color(0x297A9BF0), divider = Color(0xFF2C2C30), bar = Color(0xE61A1A1D), fieldBg = Color(0xFF29292D),
    warn = Color(0xFFE0BE7A), warnSoft = Color(0xFF3A3122), ok = Color(0xFF6CC08A), danger = Color(0xFFFF6B5E), chipNeutral = Color(0xFF2A2A2E),
)

val LocalGeek = staticCompositionLocalOf { LightGeek }

object G { val c: GeekColors @Composable get() = LocalGeek.current }

@Composable
fun GeekTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val gc = if (dark) DarkGeek else LightGeek
    val scheme = if (dark) darkColorScheme(primary = gc.accent, background = gc.bg, surface = gc.card, onSurface = gc.text, onBackground = gc.text, surfaceVariant = gc.fieldBg)
    else lightColorScheme(primary = gc.accent, background = gc.bg, surface = gc.card, onSurface = gc.text, onBackground = gc.text, surfaceVariant = gc.fieldBg)
    CompositionLocalProvider(LocalGeek provides gc) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}
