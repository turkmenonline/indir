package com.example.gdl

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont

val googleFontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val spaceGroteskGoogleFont = GoogleFont("Space Grotesk")

val SpaceGroteskFontFamily = FontFamily(
    Font(googleFont = spaceGroteskGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Normal),
    Font(googleFont = spaceGroteskGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Medium),
    Font(googleFont = spaceGroteskGoogleFont, fontProvider = googleFontProvider, weight = FontWeight.Bold)
)
