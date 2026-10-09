package io.github.passvarsling

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.passvarsling.data.ThemeMode
import io.github.passvarsling.ui.AppRoot
import io.github.passvarsling.ui.AppViewModel
import io.github.passvarsling.ui.Tab
import io.github.passvarsling.ui.theme.PassvarslingTheme
import io.github.passvarsling.ui.letContentFrameTakeFocus

class MainActivity : ComponentActivity() {

    private val viewModel: AppViewModel by viewModels { AppViewModel.Factory }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val dark = when (settings.themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Status bar icons must follow the app's theme, not just the system's.
            LaunchedEffect(dark) {
                val style = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark }
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            PassvarslingTheme(dark = dark, dynamicColor = settings.dynamicColor) {
                AppRoot(viewModel)
            }
        }
        window.decorView.letContentFrameTakeFocus()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(EXTRA_OFFICE_ID)?.let(viewModel::openOffice)
        if (intent.getBooleanExtra(EXTRA_SHOW_WATCHES, false)) viewModel.selectTab(Tab.Watches)
    }

    companion object {
        private const val EXTRA_OFFICE_ID = "office_id"
        private const val EXTRA_SHOW_WATCHES = "show_watches"

        /** Opens the app on one office, used by notifications. */
        fun officeIntent(context: Context, officeId: String): Intent =
            Intent(context, MainActivity::class.java)
                .setData("passvarsling://office/$officeId".toUri())
                .putExtra(EXTRA_OFFICE_ID, officeId)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)

        /** Opens the list of followed offices, used by the summary notification. */
        fun watchesIntent(context: Context): Intent =
            Intent(context, MainActivity::class.java)
                .setData("passvarsling://watches".toUri())
                .putExtra(EXTRA_SHOW_WATCHES, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
