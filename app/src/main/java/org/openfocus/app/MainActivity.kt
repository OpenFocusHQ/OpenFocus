package org.openfocus.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import kotlinx.coroutines.launch
import androidx.lifecycle.lifecycleScope
import org.openfocus.app.ui.nav.OpenFocusRoot
import org.openfocus.app.ui.theme.OpenFocusTheme

class MainActivity : ComponentActivity() {
    private val pendingRoute = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            OpenFocusTheme {
                OpenFocusRoot(pendingRoute = pendingRoute.value, onRouteConsumed = { pendingRoute.value = null })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }

    override fun onResume() {
        super.onResume()
        val c = (application as OpenFocusApp).container
        lifecycleScope.launch {
            c.habits.rollover()
            c.focus.recoverIfNeeded()
        }
    }

    companion object {
        const val EXTRA_ROUTE = "route"
    }
}
