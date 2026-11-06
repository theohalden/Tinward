package dev.kosh.financetracker.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.sp
import dev.kosh.financetracker.R

/** Google Play Services' downloadable-fonts provider: no font binaries in the APK;
 * resolved and cached on-device, falling back to the system font if unavailable. */
private val fontProvider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

private val manrope = GoogleFont("Manrope")
private val instrumentSerif = GoogleFont("Instrument Serif")

/** Body, UI, labels and numbers. */
val ManropeFamily = FontFamily(
    Font(googleFont = manrope, fontProvider = fontProvider, weight = FontWeight.Normal),
    Font(googleFont = manrope, fontProvider = fontProvider, weight = FontWeight.Medium),
    Font(googleFont = manrope, fontProvider = fontProvider, weight = FontWeight.SemiBold),
    Font(googleFont = manrope, fontProvider = fontProvider, weight = FontWeight.Bold),
)

/** Headings, the hero figure and emphasis (editorial voice). Single weight by design. */
val InstrumentSerifFamily = FontFamily(
    Font(googleFont = instrumentSerif, fontProvider = fontProvider, weight = FontWeight.Normal),
)

private const val TNUM = "tnum"

val KoshTypography = Typography(
    displayLarge = TextStyle(fontFamily = InstrumentSerifFamily, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.Normal, letterSpacing = 0.sp),
    displaySmall = TextStyle(fontFamily = InstrumentSerifFamily, fontSize = 40.sp, lineHeight = 46.sp, fontWeight = FontWeight.Normal, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = InstrumentSerifFamily, fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Normal),
    titleLarge = TextStyle(fontFamily = InstrumentSerifFamily, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.Normal),
    titleMedium = TextStyle(fontFamily = ManropeFamily, fontSize = 17.sp, lineHeight = 24.sp, fontWeight = FontWeight.SemiBold),
    titleSmall = TextStyle(fontFamily = ManropeFamily, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = ManropeFamily, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TNUM),
    bodyMedium = TextStyle(fontFamily = ManropeFamily, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = TNUM),
    bodySmall = TextStyle(fontFamily = ManropeFamily, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = ManropeFamily, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
    labelMedium = TextStyle(fontFamily = ManropeFamily, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TNUM),
    labelSmall = TextStyle(fontFamily = ManropeFamily, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal, fontFeatureSettings = TNUM),
)
