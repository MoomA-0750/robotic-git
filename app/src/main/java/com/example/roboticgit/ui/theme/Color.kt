package com.example.roboticgit.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

val Purple80 = Color(0xFFD0BCFF)
val PurpleGrey80 = Color(0xFFCCC2DC)
val Pink80 = Color(0xFFEFB8C8)

val Purple40 = Color(0xFF6650a4)
val PurpleGrey40 = Color(0xFF625b71)
val Pink40 = Color(0xFF7D5260)

/**
 * Extended color scheme for semantic colors not covered by Material 3
 * Used for file status indicators, diff views, and other domain-specific colors
 */
@Immutable
data class ExtendedColors(
    // File status colors
    val statusAdded: Color,
    val statusAddedContainer: Color,
    val statusModified: Color,
    val statusModifiedContainer: Color,
    val statusDeleted: Color,
    val statusDeletedContainer: Color,
    // Diff view colors
    val diffAddedBackground: Color,
    val diffAddedText: Color,
    val diffRemovedBackground: Color,
    val diffRemovedText: Color
)

val LightExtendedColors = ExtendedColors(
    statusAdded = Color(0xFF1B5E20),
    statusAddedContainer = Color(0xFFE8F5E9),
    statusModified = Color(0xFF0D47A1),
    statusModifiedContainer = Color(0xFFE3F2FD),
    statusDeleted = Color(0xFFB71C1C),
    statusDeletedContainer = Color(0xFFFFEBEE),
    diffAddedBackground = Color(0xFFE6FFEC),
    diffAddedText = Color(0xFF1B5E20),
    diffRemovedBackground = Color(0xFFFFEBEE),
    diffRemovedText = Color(0xFFB71C1C)
)

val DarkExtendedColors = ExtendedColors(
    statusAdded = Color(0xFF81C784),
    statusAddedContainer = Color(0xFF1B5E20),
    statusModified = Color(0xFF64B5F6),
    statusModifiedContainer = Color(0xFF0D47A1),
    statusDeleted = Color(0xFFE57373),
    statusDeletedContainer = Color(0xFFB71C1C),
    diffAddedBackground = Color(0xFF1B3D1F),
    diffAddedText = Color(0xFF81C784),
    diffRemovedBackground = Color(0xFF3D1B1B),
    diffRemovedText = Color(0xFFE57373)
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtendedColors }
