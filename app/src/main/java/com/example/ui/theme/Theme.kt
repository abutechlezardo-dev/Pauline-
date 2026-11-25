package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme =
  darkColorScheme(
    primary = TerracottaDarkPrimary,
    onPrimary = Color(0xFF4D1508),
    primaryContainer = TerracottaDarkContainer,
    onPrimaryContainer = TerracottaContainer,
    secondary = SageDarkSecondary,
    onSecondary = Color(0xFF0A2E22),
    tertiary = HoneyDarkTertiary,
    background = DarkPlumBackground,
    onBackground = CreamLightInk,
    surface = DarkSurface,
    onSurface = CreamLightInk,
    surfaceVariant = DarkSurfaceContainer,
    onSurfaceVariant = MutedSandInk,
    outline = DarkOutline,
  )

private val LightColorScheme =
  lightColorScheme(
    primary = TerracottaPrimary,
    onPrimary = Color.White,
    primaryContainer = TerracottaContainer,
    onPrimaryContainer = OnTerracottaContainer,
    secondary = SageSecondary,
    onSecondary = Color.White,
    secondaryContainer = SageContainer,
    onSecondaryContainer = OnSageContainer,
    tertiary = HoneyTertiary,
    tertiaryContainer = HoneyContainer,
    background = CreamBackground,
    onBackground = InkDark,
    surface = WarmSurface,
    onSurface = InkDark,
    surfaceVariant = SurfaceContainerWarm,
    onSurfaceVariant = MutedCocoa,
    outline = SoftOutline,
  )

@Composable
fun PaulineTheme(
  darkTheme: Boolean = isSystemInDarkTheme(),
  dynamicColor: Boolean = true,
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

  MaterialTheme(colorScheme = colorScheme, typography = PaulineTypography, content = content)
}
