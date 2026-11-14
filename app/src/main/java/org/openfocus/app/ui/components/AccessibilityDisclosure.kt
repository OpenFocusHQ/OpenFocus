package org.openfocus.app.ui.components

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Prominent disclosure shown *before* sending the user to Accessibility settings (required by
 * Google Play's Accessibility API policy, and simply the honest thing to do).
 */
@Composable
fun AccessibilityDisclosureDialog(onAgree: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
        title = { Text("Turn on OpenFocus Shield?") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("To block apps and Shorts/Reels, OpenFocus uses Android's Accessibility Service.")
                Text("What it uses", fontWeight = FontWeight.Bold)
                Text("• Which app is open, to show the pause screen for apps you chose.\n" +
                    "• Only inside the video apps you enabled (YouTube, Instagram…): the names of on-screen elements, " +
                    "to recognise their short-video feed and press Back.")
                Text("What it never does", fontWeight = FontWeight.Bold)
                Text("• Read what you type, passwords, messages or payments.\n" +
                    "• Record your screen.\n" +
                    "• Send anything anywhere: OpenFocus has no internet permission.")
                Text("Payment apps", fontWeight = FontWeight.Bold)
                Text(
                    "Some payment and banking apps (for example Google Pay) limit payments while an accessibility " +
                        "service installed outside Google Play is on. If that happens, switch the Shield off in " +
                        "Accessibility settings while you pay.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAgree) { Text("Agree and open settings") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Not now") } },
    )
}

fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
