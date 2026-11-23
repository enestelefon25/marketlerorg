package org.marketler.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.RoundedCornerShape

/** Marka ve arayüz renkleri: sitenin modern teması (tema-modern.css) ile aynı değerler. */
object MkColors {
    val Navy = Color(0xFF172554)      // marka lacivert
    val Blue = Color(0xFF2DA8FF)      // marka mavi
    val Background = Color(0xFFF8FAFC)
    val Surface = Color(0xFFFFFFFF)
    val Ink = Color(0xFF0F172A)
    val Muted = Color(0xFF475569)
    val Subtle = Color(0xFF64748B)
    val Line = Color(0xFFE2E8F0)
    val LogoRed = Color(0xFFE53935)   // logodaki ".ORG" (modern tema)
    val DiscountBg = Color(0xFFFEE2E2)
    val DiscountFg = Color(0xFFB91C1C)
    val Best = Color(0xFF16A34A)
    val BestBg = Color(0xFFDCFCE7)
    val SoonFg = Color(0xFF1D4ED8)
    val SoonBg = Color(0xFFDBEAFE)
    val PastFg = Color(0xFF475569)
    val PastBg = Color(0xFFF1F5F9)
}

private val scheme = lightColorScheme(
    primary = MkColors.Navy,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDBE4FF),
    onPrimaryContainer = MkColors.Navy,
    secondary = MkColors.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFE0F2FE),
    onSecondaryContainer = MkColors.Navy,
    background = MkColors.Background,
    onBackground = MkColors.Ink,
    surface = MkColors.Surface,
    onSurface = MkColors.Ink,
    surfaceVariant = Color(0xFFF1F5F9),
    onSurfaceVariant = MkColors.Muted,
    outline = Color(0xFFCBD5E1),
    outlineVariant = MkColors.Line,
    error = MkColors.DiscountFg,
)

private val typography = Typography(
    headlineSmall = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.ExtraBold, color = MkColors.Ink),
    titleLarge = TextStyle(fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.Bold),
    titleMedium = TextStyle(fontSize = 17.sp, lineHeight = 23.sp, fontWeight = FontWeight.Bold),
    titleSmall = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 23.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.5.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold),
)

private val shapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(18.dp),
)

/** Uygulama teması. FAZ 2.1: yalnızca açık tema (koyu tema FAZ 2.8 kalite aşamasında). */
@Composable
fun MarketlerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
}
