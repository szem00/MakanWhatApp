package com.budgetapp.voiceexpense.ui.theme

import android.annotation.SuppressLint
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.budgetapp.voiceexpense.R

// The library ships Play services' signing certificates, so fonts only come from the real provider.
@SuppressLint("PrivateResource")
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = androidx.compose.ui.text.googlefonts.R.array.com_google_android_gms_fonts_certs,
)

// Google Play services downloads these once and shares them between apps. Until they arrive, or on
// a phone without Play services, text uses the phone's own font.
private val fraunces = GoogleFont("Fraunces")
private val dmSans = GoogleFont("DM Sans")

/** The serif for the app's name, headings and big amounts. */
val Fraunces = FontFamily(
    Font(fraunces, provider, FontWeight.SemiBold),
    Font(fraunces, provider, FontWeight.Bold),
)

/** The sans serif for everything else. */
val DmSans = FontFamily(
    Font(dmSans, provider, FontWeight.Normal),
    Font(dmSans, provider, FontWeight.Medium),
    Font(dmSans, provider, FontWeight.SemiBold),
    Font(dmSans, provider, FontWeight.Bold),
    Font(dmSans, provider, FontWeight.ExtraBold),
)

private val base = Typography()

internal val KopiTypography = Typography(
    displayLarge = base.displayLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    displayMedium = base.displayMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    displaySmall = base.displaySmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    headlineLarge = base.headlineLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    headlineMedium = base.headlineMedium.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    headlineSmall = base.headlineSmall.copy(fontFamily = Fraunces, fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontFamily = Fraunces, fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.Bold),
    titleSmall = base.titleSmall.copy(fontFamily = DmSans, fontWeight = FontWeight.Bold),
    bodyLarge = base.bodyLarge.copy(fontFamily = DmSans),
    bodyMedium = base.bodyMedium.copy(fontFamily = DmSans),
    bodySmall = base.bodySmall.copy(fontFamily = DmSans),
    labelLarge = base.labelLarge.copy(fontFamily = DmSans, fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontFamily = DmSans, fontWeight = FontWeight.SemiBold),
    labelSmall = base.labelSmall.copy(fontFamily = DmSans, fontWeight = FontWeight.SemiBold),
)

/** Amounts line up in columns, like on a receipt. */
const val TABULAR_FIGURES = "tnum"

/** The large amount on the week card, set into the sentence around it. */
val HeroAmount = SpanStyle(
    fontFamily = Fraunces,
    fontWeight = FontWeight.Bold,
    fontSize = 44.sp,
    letterSpacing = (-0.01).em,
    fontFeatureSettings = TABULAR_FIGURES,
)

private val wordmarkStyle = TextStyle(
    fontFamily = Fraunces,
    fontWeight = FontWeight.Bold,
    fontSize = 24.sp,
    letterSpacing = (-0.01).em,
)

/** The app's name the way the logo sets it, with the question mark in coin gold. */
@Composable
fun Wordmark(modifier: Modifier = Modifier) {
    val name = stringResource(R.string.app_name)
    val coin = LocalBrandColors.current.coin
    Text(
        text = buildAnnotatedString {
            append(name)
            if (name.endsWith("?")) addStyle(SpanStyle(color = coin), name.length - 1, name.length)
        },
        style = wordmarkStyle,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}
