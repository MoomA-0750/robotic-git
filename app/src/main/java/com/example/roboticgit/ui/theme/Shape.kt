package com.example.roboticgit.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Material 3 Shape System
 * Based on M3 shape scale: https://m3.material.io/styles/shape/overview
 */
val AppShapes = Shapes(
    // Small components: Chips, small buttons
    small = RoundedCornerShape(8.dp),
    // Medium components: Cards, dialogs content
    medium = RoundedCornerShape(12.dp),
    // Large components: FABs, Navigation drawers
    large = RoundedCornerShape(16.dp),
    // Extra large components: Dialogs, Bottom sheets
    extraLarge = RoundedCornerShape(28.dp)
)

/**
 * Component-specific shape tokens for consistency
 */
object ShapeTokens {
    val TextField = RoundedCornerShape(12.dp)
    val Button = RoundedCornerShape(12.dp)
    val Card = RoundedCornerShape(16.dp)
    val Dialog = RoundedCornerShape(28.dp)
    val FAB = RoundedCornerShape(16.dp)
    val ListItem = RoundedCornerShape(16.dp)
}
