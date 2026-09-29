package com.masterroot.presentation.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// ─── Color palette ────────────────────────────────────────────────────────────

object MasterRootColors {
    // Backgrounds
    val Background      = Color(0xFF0A0A0A)
    val Surface         = Color(0xFF111111)
    val SurfaceVariant  = Color(0xFF1A1A1A)
    val Card            = Color(0xFF161616)
    val CardBorder      = Color(0xFF2A2A2A)

    // Primary brand
    val Primary         = Color(0xFF00E5FF)  // Electric cyan
    val PrimaryDim      = Color(0xFF0097A7)
    val PrimaryContainer= Color(0xFF003740)

    // Status colors
    val Success         = Color(0xFF00E676)  // Green
    val SuccessDim      = Color(0xFF1B5E20)
    val Warning         = Color(0xFFFFAB00)  // Amber
    val WarningDim      = Color(0xFF4E3100)
    val Error           = Color(0xFFFF1744)  // Red
    val ErrorDim        = Color(0xFF4E0000)
    val Unknown         = Color(0xFF757575)  // Grey

    // Connected indicator
    val Connected       = Color(0xFF00E676)
    val Disconnected    = Color(0xFF757575)
    val Connecting      = Color(0xFFFFAB00)

    // Text
    val OnBackground    = Color(0xFFE0E0E0)
    val OnSurface       = Color(0xFFBDBDBD)
    val OnSurfaceDim    = Color(0xFF757575)
    val OnPrimary       = Color(0xFF000000)

    // Bootloader states
    val Unlocked        = Color(0xFF00E676)
    val Locked          = Color(0xFFFF1744)
}

// ─── Typography ───────────────────────────────────────────────────────────────

object MasterRootType {
    val DisplayLarge = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp,
        letterSpacing = 0.sp,
        color = MasterRootColors.OnBackground
    )
    val TitleLarge = TextStyle(
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        letterSpacing = 0.5.sp,
        color = MasterRootColors.OnBackground
    )
    val TitleMedium = TextStyle(
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        letterSpacing = 1.sp,
        color = MasterRootColors.OnBackground
    )
    val Label = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        letterSpacing = 1.5.sp,
        color = MasterRootColors.OnSurfaceDim
    )
    val Body = TextStyle(
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = MasterRootColors.OnSurface
    )
    val Mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = MasterRootColors.OnSurface
    )
    val MonoSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        color = MasterRootColors.OnSurfaceDim
    )
    val StatusChip = TextStyle(
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        letterSpacing = 2.sp,
        color = MasterRootColors.OnBackground
    )
}

// ─── Material3 theme ──────────────────────────────────────────────────────────

private val DarkColorScheme = darkColorScheme(
    primary          = MasterRootColors.Primary,
    onPrimary        = MasterRootColors.OnPrimary,
    primaryContainer = MasterRootColors.PrimaryContainer,
    background       = MasterRootColors.Background,
    surface          = MasterRootColors.Surface,
    surfaceVariant   = MasterRootColors.SurfaceVariant,
    onBackground     = MasterRootColors.OnBackground,
    onSurface        = MasterRootColors.OnSurface,
    error            = MasterRootColors.Error,
    onError          = Color.White
)

@Composable
fun MasterRootTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
