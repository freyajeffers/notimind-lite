package com.jeffers.notimindlite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.jeffers.notimindlite.BuildConfig

private const val DARK_PRIMARY = 0xFFD0BCFF
private const val DARK_ON_PRIMARY = 0xFF381E72
private const val DARK_PRIMARY_CONTAINER = 0xFF4F378B
private const val DARK_ON_PRIMARY_CONTAINER = 0xFFEADDFF
private const val DARK_SECONDARY = 0xFFCCC2DC
private const val DARK_ON_SECONDARY = 0xFF332D41
private const val DARK_SECONDARY_CONTAINER = 0xFF4A4458
private const val DARK_ON_SECONDARY_CONTAINER = 0xFFE8DEF8
private const val DARK_TERTIARY = 0xFFEFB8C8
private const val DARK_ON_TERTIARY = 0xFF492532
private const val DARK_TERTIARY_CONTAINER = 0xFF633B48
private const val DARK_ON_TERTIARY_CONTAINER = 0xFFFFD8E4
private const val DARK_BACKGROUND = 0xFF1C1B1F
private const val DARK_ON_BACKGROUND = 0xFFE6E1E5
private const val DARK_SURFACE = 0xFF1C1B1F
private const val DARK_ON_SURFACE = 0xFFE6E1E5
private const val DARK_SURFACE_VARIANT = 0xFF49454F
private const val DARK_ON_SURFACE_VARIANT = 0xFFCAC4D0
private const val DARK_OUTLINE = 0xFF938F99
private const val DARK_OUTLINE_VARIANT = 0xFF49454F
private const val DARK_SCRIM = 0x80000000

private const val LIGHT_PRIMARY = 0xFF6750A4
private const val LIGHT_ON_PRIMARY = 0xFFFFFFFF
private const val LIGHT_PRIMARY_CONTAINER = 0xFFEADDFF
private const val LIGHT_ON_PRIMARY_CONTAINER = 0xFF21005D
private const val LIGHT_SECONDARY = 0xFF625B71
private const val LIGHT_ON_SECONDARY = 0xFFFFFFFF
private const val LIGHT_SECONDARY_CONTAINER = 0xFFE8DEF8
private const val LIGHT_ON_SECONDARY_CONTAINER = 0xFF1D192B
private const val LIGHT_TERTIARY = 0xFF7D5260
private const val LIGHT_ON_TERTIARY = 0xFFFFFFFF
private const val LIGHT_TERTIARY_CONTAINER = 0xFFFFD8E4
private const val LIGHT_ON_TERTIARY_CONTAINER = 0xFF31111D
private const val LIGHT_BACKGROUND = 0xFFFFFBFE
private const val LIGHT_ON_BACKGROUND = 0xFF1C1B1F
private const val LIGHT_SURFACE = 0xFFFFFBFE
private const val LIGHT_ON_SURFACE = 0xFF1C1B1F
private const val LIGHT_SURFACE_VARIANT = 0xFFE7E0EC
private const val LIGHT_ON_SURFACE_VARIANT = 0xFF49454F
private const val LIGHT_OUTLINE = 0xFF79747E
private const val LIGHT_OUTLINE_VARIANT = 0xFFCAC4D0
private const val LIGHT_SCRIM = 0x80000000

private val DarkColorScheme = darkColorScheme(
    primary = Color(DARK_PRIMARY),
    onPrimary = Color(DARK_ON_PRIMARY),
    primaryContainer = Color(DARK_PRIMARY_CONTAINER),
    onPrimaryContainer = Color(DARK_ON_PRIMARY_CONTAINER),
    secondary = Color(DARK_SECONDARY),
    onSecondary = Color(DARK_ON_SECONDARY),
    secondaryContainer = Color(DARK_SECONDARY_CONTAINER),
    onSecondaryContainer = Color(DARK_ON_SECONDARY_CONTAINER),
    tertiary = Color(DARK_TERTIARY),
    onTertiary = Color(DARK_ON_TERTIARY),
    tertiaryContainer = Color(DARK_TERTIARY_CONTAINER),
    onTertiaryContainer = Color(DARK_ON_TERTIARY_CONTAINER),
    background = Color(DARK_BACKGROUND),
    onBackground = Color(DARK_ON_BACKGROUND),
    surface = Color(DARK_SURFACE),
    onSurface = Color(DARK_ON_SURFACE),
    surfaceVariant = Color(DARK_SURFACE_VARIANT),
    onSurfaceVariant = Color(DARK_ON_SURFACE_VARIANT),
    outline = Color(DARK_OUTLINE),
    outlineVariant = Color(DARK_OUTLINE_VARIANT),
    scrim = Color(DARK_SCRIM)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(LIGHT_PRIMARY),
    onPrimary = Color(LIGHT_ON_PRIMARY),
    primaryContainer = Color(LIGHT_PRIMARY_CONTAINER),
    onPrimaryContainer = Color(LIGHT_ON_PRIMARY_CONTAINER),
    secondary = Color(LIGHT_SECONDARY),
    onSecondary = Color(LIGHT_ON_SECONDARY),
    secondaryContainer = Color(LIGHT_SECONDARY_CONTAINER),
    onSecondaryContainer = Color(LIGHT_ON_SECONDARY_CONTAINER),
    tertiary = Color(LIGHT_TERTIARY),
    onTertiary = Color(LIGHT_ON_TERTIARY),
    tertiaryContainer = Color(LIGHT_TERTIARY_CONTAINER),
    onTertiaryContainer = Color(LIGHT_ON_TERTIARY_CONTAINER),
    background = Color(LIGHT_BACKGROUND),
    onBackground = Color(LIGHT_ON_BACKGROUND),
    surface = Color(LIGHT_SURFACE),
    onSurface = Color(LIGHT_ON_SURFACE),
    surfaceVariant = Color(LIGHT_SURFACE_VARIANT),
    onSurfaceVariant = Color(LIGHT_ON_SURFACE_VARIANT),
    outline = Color(LIGHT_OUTLINE),
    outlineVariant = Color(LIGHT_OUTLINE_VARIANT),
    scrim = Color(LIGHT_SCRIM)
)

private val AmoledColorScheme = DarkColorScheme.copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceVariant = Color.Black,
    surfaceTint = Color.Black,
    surfaceContainer = Color.Black,
    surfaceContainerLow = Color.Black,
    surfaceContainerHigh = Color.Black,
    surfaceContainerHighest = Color.Black,
    outlineVariant = Color.Black,
    scrim = Color.Black
)

@Composable
fun NotiMindLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    amoledTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        amoledTheme -> {
            AmoledColorScheme
        }

        dynamicColor && BuildConfig.SUPPORTS_DYNAMIC_COLOR -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> {
            DarkColorScheme
        }

        else -> {
            LightColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
