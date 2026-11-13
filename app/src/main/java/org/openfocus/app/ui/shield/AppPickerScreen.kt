package org.openfocus.app.ui.shield

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openfocus.app.blocking.AppInfo
import org.openfocus.app.blocking.InstalledApps
import org.openfocus.app.data.db.BlockRuleEntity
import org.openfocus.app.ui.components.appContainer

/** mode = "block" edits the block list, "allow" edits the deep-focus allow-list. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerScreen(nav: NavController, mode: String) {
    val c = appContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var apps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var query by remember { mutableStateOf("") }
    val rules by c.blocks.rules.collectAsState(initial = emptyList())
    val settings by c.settings.state.collectAsState()
    LaunchedEffect(Unit) { apps = withContext(Dispatchers.IO) { InstalledApps.launchable(context) } }

    val selected: Set<String> = if (mode == "allow") settings.focusAllowlist else rules.map { it.packageName }.toSet()

    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text(if (mode == "allow") "Allowed during deep focus" else "Choose apps to block") },
            navigationIcon = { IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
        OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().padding(horizontal = 16.dp), label = { Text("Search") }, singleLine = true)
        val list = apps
        if (list == null) {
            CircularProgressIndicator(Modifier.padding(32.dp).align(Alignment.CenterHorizontally))
            return@Column
        }
        val filtered = list.filter { query.isBlank() || it.label.contains(query, true) || it.packageName.contains(query, true) }
            .sortedByDescending { it.packageName in selected }
        LazyColumn(Modifier.fillMaxSize()) {
            items(filtered, key = { it.packageName }) { app ->
                val checked = app.packageName in selected
                val toggle = {
                    if (mode == "allow") {
                        c.settings.update { s -> s.copy(focusAllowlist = if (checked) s.focusAllowlist - app.packageName else s.focusAllowlist + app.packageName) }
                    } else scope.launch {
                        if (checked) c.blocks.removeRule(app.packageName)
                        else c.blocks.upsertRule(BlockRuleEntity(app.packageName, app.label))
                    }
                    Unit
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    val bmp = remember(app.packageName) { runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
                    if (bmp != null) Image(bmp, null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(app.label, style = MaterialTheme.typography.bodyLarge)
                        Text(app.packageName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(checked, { toggle() })
                }
            }
        }
    }
}
