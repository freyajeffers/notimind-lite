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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

/**
 * SplashScreen provides a branded entry point for the application.
 * It handles initial resource loading and transitions to the main navigation.
 */
@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    var scale by remember { mutableFloatStateOf(0.8f) }
    
    LaunchedEffect(Unit) {
        delay(1500) // Branded delay
        scale = 1.0f
        delay(500)
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
            // Note: Using a system icon as a placeholder until branded assets are added
            Image(
                painter = painterResource(id = android.R.drawable.ic_menu_info_details),
                contentDescription = "NotiMind Logo",
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
