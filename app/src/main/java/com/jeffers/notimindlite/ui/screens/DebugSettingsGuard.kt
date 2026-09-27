package com.jeffers.notimindlite.ui.screens

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha

import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import com.jeffers.notimindlite.BuildConfig

/** Applies debug-only visual/semantic state without consuming gestures. */
fun Modifier.debugSettingsCard(): Modifier {
    if (!BuildConfig.DEBUG) return fillMaxWidth()
    return fillMaxWidth()
        .alpha(0.6f)
        .semantics { disabled() }
}
