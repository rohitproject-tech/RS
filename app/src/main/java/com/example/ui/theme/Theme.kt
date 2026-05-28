package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color

private val DarkColorScheme =
  darkColorScheme(
    primary = AccentRed,
    secondary = BloodSecondary,
    tertiary = PolishSecondaryContainer,
    background = Color(0xFF1B1B1F),
    surface = Color(0xFF222225),
    errorContainer = Color(0xFF410002),
    onErrorContainer = Color(0xFFFFDAD6),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = Color(0xFFFDFBFF),
    onSurface = Color(0xFFFDFBFF)
  )

private val LightColorScheme =
  lightColorScheme(
    primary = BloodPrimary, // #BA1A1A
    secondary = BloodSecondary, // #410002
    tertiary = PolishSecondaryContainer, // #DDE2F1
    primaryContainer = PolishPrimaryContainer, // #D7E3FF
    onPrimaryContainer = PolishOnPrimaryContainer, // #001B3E
    errorContainer = BloodTertiary, // #FFDAD6
    onErrorContainer = BloodSecondary, // #410002
    background = PolishBackground, // #FDFBFF
    surface = PolishSurface, // #FFFFFF
    onPrimary = Color.White,
    onSecondary = Color.White,
    onBackground = PolishOnBackground, // #1B1B1F
    onSurface = PolishOnSurface, // #1B1B1F
    surfaceVariant = Color(0xFFF2F0F4),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFFDEDEE7)
  )

@Composable
fun MyApplicationTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = false, // Disable forced dynamic colors to respect the Professional Polish theme styling
  content: @Composable () -> Unit,
) {
  val colorScheme =
    when {
      dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
        val context = LocalContext.current
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
      }

      darkTheme -> DarkColorScheme
      else -> LightColorScheme
    }

  MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
