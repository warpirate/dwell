package com.dwell.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import com.dwell.app.R

// Fraunces is pulled at runtime via the Google Fonts provider (no bundled
// binary). If the provider is unavailable the system falls back gracefully.
private val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val fraunces = GoogleFont("Fraunces")

// Display face: Fraunces, used sparingly for titles only (UI/UX doc 2).
val DisplayFontFamily = FontFamily(
    Font(googleFont = fraunces, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = fraunces, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = fraunces, fontProvider = googleFontProvider, weight = FontWeight.SemiBold),
)

// Body/UI face: clean system sans (spec allows Inter or system; system keeps
// the base small and adds no dependency). Swap to Inter later if desired.
val BodyFontFamily = FontFamily.Default

// Type scale from UI/UX doc 2: Display L 28 / Title 20 / Body 15 / Label 13 /
// Caption 11. Generous line height. Sentence case is enforced in copy, not here.
val DwellTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
    ),
    titleLarge = TextStyle(
        fontFamily = DisplayFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = BodyFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)
