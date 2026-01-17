package com.example.roboticgit.ui.theme

import android.os.Build
import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.roboticgit.R
import com.example.roboticgit.data.model.AppFont

@OptIn(ExperimentalTextApi::class)
private fun createGoogleSansRounded(weight: FontWeight): FontFamily {
    return FontFamily(
        Font(
            resId = R.font.google_sans_flex_rounded,
            weight = weight,
            variationSettings = FontVariation.Settings(
                FontVariation.Setting("wght", weight.weight.toFloat()),
                FontVariation.Setting("ROND", 100f)
            )
        )
    )
}

@OptIn(ExperimentalTextApi::class)
fun getTypography(appFont: AppFont): Typography {
    val isVariableFontSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O

    fun getFontFamily(weight: FontWeight): FontFamily {
        return if (appFont == AppFont.GOOGLE_SANS_ROUNDED && isVariableFontSupported) {
            createGoogleSansRounded(weight)
        } else {
            FontFamily.Default
        }
    }

    return Typography(
        displayLarge = TextStyle(
            fontFamily = getFontFamily(FontWeight.Normal),
            fontWeight = FontWeight.Normal,
            fontSize = 57.sp,
            lineHeight = 64.sp,
            letterSpacing = (-0.25).sp
        ),
        displayMedium = TextStyle(
            fontFamily = getFontFamily(FontWeight.Normal),
            fontWeight = FontWeight.Normal,
            fontSize = 45.sp,
            lineHeight = 52.sp,
            letterSpacing = 0.sp
        ),
        displaySmall = TextStyle(
            fontFamily = getFontFamily(FontWeight.Normal),
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            lineHeight = 44.sp,
            letterSpacing = 0.sp
        ),
        headlineLarge = TextStyle(
            fontFamily = getFontFamily(FontWeight.SemiBold),
            fontWeight = FontWeight.SemiBold,
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = 0.sp
        ),
        headlineMedium = TextStyle(
            fontFamily = getFontFamily(FontWeight.SemiBold),
            fontWeight = FontWeight.SemiBold,
            fontSize = 28.sp,
            lineHeight = 36.sp,
            letterSpacing = 0.sp
        ),
        headlineSmall = TextStyle(
            fontFamily = getFontFamily(FontWeight.SemiBold),
            fontWeight = FontWeight.SemiBold,
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp
        ),
        titleLarge = TextStyle(
            fontFamily = getFontFamily(FontWeight.Medium),
            fontWeight = FontWeight.Medium,
            fontSize = 22.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp
        ),
        titleMedium = TextStyle(
            fontFamily = getFontFamily(FontWeight.Medium),
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.15.sp
        ),
        titleSmall = TextStyle(
            fontFamily = getFontFamily(FontWeight.Medium),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        bodyLarge = TextStyle(
            fontFamily = getFontFamily(FontWeight.Normal),
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.5.sp
        ),
        bodyMedium = TextStyle(
            fontFamily = getFontFamily(FontWeight.Normal),
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.25.sp
        ),
        bodySmall = TextStyle(
            fontFamily = getFontFamily(FontWeight.Normal),
            fontWeight = FontWeight.Normal,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.4.sp
        ),
        labelLarge = TextStyle(
            fontFamily = getFontFamily(FontWeight.Medium),
            fontWeight = FontWeight.Medium,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.1.sp
        ),
        labelMedium = TextStyle(
            fontFamily = getFontFamily(FontWeight.Medium),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        ),
        labelSmall = TextStyle(
            fontFamily = getFontFamily(FontWeight.Medium),
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.5.sp
        )
    )
}

val Typography = getTypography(AppFont.GOOGLE_SANS_ROUNDED)
