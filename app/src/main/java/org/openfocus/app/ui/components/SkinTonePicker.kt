package org.openfocus.app.ui.components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.openfocus.app.ui.pixel.SkinTone

/** Row of hero previews, one per skin tone. */
@Composable
fun SkinTonePicker(selected: String?, colorId: String?, hatId: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val current = SkinTone.of(selected)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        SkinTone.entries.forEach { tone ->
            val isSel = tone == current
            HeroAvatar(
                colorId, hatId, null, size = 64.dp, skinTone = tone.id, animate = false,
                modifier = Modifier
                    .clip(CircleShape)
                    .border(3.dp, if (isSel) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape)
                    .clickable(role = Role.RadioButton) { onPick(tone.id) }
                    .semantics { contentDescription = "${tone.label} skin tone"; this.selected = isSel },
            )
        }
    }
}
