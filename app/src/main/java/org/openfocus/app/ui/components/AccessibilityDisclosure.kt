package org.openfocus.app.ui.components

import org.openfocus.app.R
import androidx.compose.ui.res.stringResource
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
        title = { Text(stringResource(R.string.turn_on_openfocus_shield)) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.to_block_apps_and_shorts_reels))
                Text(stringResource(R.string.what_it_uses), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.which_app_is_open_to_show) +
                    "• Only inside the video apps you enabled (YouTube, Instagram…): the names of on-screen elements, " +
                    "to recognise their short-video feed and press Back.")
                Text(stringResource(R.string.what_it_never_does), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.read_what_you_type_passwords_messages) +
                    "• Record your screen.\n" +
                    "• Send anything anywhere: OpenFocus has no internet permission.")
                Text(stringResource(R.string.payment_apps), fontWeight = FontWeight.Bold)
                Text(
                    "Some payment and banking apps (for example Google Pay) limit payments while an accessibility " +
                        "service installed outside Google Play is on. If that happens, switch the Shield off in " +
                        "Accessibility settings while you pay.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = { TextButton(onClick = onAgree) { Text(stringResource(R.string.agree_and_open_settings)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.not_now)) } },
    )
}

fun openAccessibilitySettings(context: Context) {
    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
