package com.jeffers.notimindlite.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.jeffers.notimindlite.R
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val INITIAL_SPLASH_SCALE = 0.88f
private const val SPLASH_SCALE_UP_DELAY_MS = 650L
private const val SPLASH_HANDOFF_DELAY_MS = 450L

/**
 * SplashScreen provides a branded entry point for the application.
 * It handles initial resource loading and transitions to the main navigation.
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var scale by remember { mutableFloatStateOf(INITIAL_SPLASH_SCALE) }

    LaunchedEffect(Unit) {
        // Keep the hand-off short; startup work runs in parallel in MainActivity.
        delay(SPLASH_SCALE_UP_DELAY_MS)
        scale = 1.0f
        delay(SPLASH_HANDOFF_DELAY_MS)
        onTimeout()
    }

    val animatedScale by animateFloatAsState(
        targetValue = scale,
        animationSpec = tween(durationMillis = 800),
        label = "splashScale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .scale(animatedScale)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.onPrimary)
        ) {
            Image(
                painter = painterResource(id = R.mipmap.ic_launcher),
                contentDescription = stringResource(R.string.splash_logo_description),
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
