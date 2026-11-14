package dev.ghazi.igfocus.ui

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dev.ghazi.igfocus.R
import dev.ghazi.igfocus.settings.SettingsStore
import kotlinx.coroutines.launch

private enum class Tab(@StringRes val label: Int, val icon: ImageVector) {
    FOCUS(R.string.tab_focus, Icons.Filled.Home),
    STORIES(R.string.tab_stories, Icons.Filled.AccountCircle),
    SETTINGS(R.string.tab_settings, Icons.Filled.Settings),
}

/**
 * Three tabs along the bottom (Focus, Stories, Settings), and Diagnostics as a page opened from
 * Settings. Too few screens to justify a navigation library. Back from another tab returns to
 * Focus, then leaves the app. The introduction comes first until it has been seen once.
 *
 * [enabledInSettings] is whether the service is switched on in Android's Accessibility settings;
 * [batteryUnrestricted] whether Android lets it run without battery limits.
 */
@Composable
fun FocusApp(enabledInSettings: Boolean, batteryUnrestricted: Boolean) {
    val context = LocalContext.current
    val store = remember { SettingsStore(context) }
    val loaded by store.settings.collectAsState(initial = null)
    var tab by rememberSaveable { mutableStateOf(Tab.FOCUS) }
    var diagnosticsOpen by rememberSaveable { mutableStateOf(false) }
    var introAsked by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    BackHandler(enabled = diagnosticsOpen || tab != Tab.FOCUS) {
        if (diagnosticsOpen) diagnosticsOpen = false else tab = Tab.FOCUS
    }

    // Draw nothing for the instant before settings load, rather than flashing default values.
    val settings = loaded ?: return

    if (!settings.introDone || introAsked) {
        IntroScreen(serviceOn = enabledInSettings) { toSetup ->
            introAsked = false
            if (toSetup) tab = Tab.SETTINGS
            scope.launch { store.setIntroDone(true) }
        }
        return
    }

    if (diagnosticsOpen) {
        DiagnosticsScreen(enabledInSettings = enabledInSettings, onBack = { diagnosticsOpen = false })
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                for (item in Tab.entries) {
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Icon(item.icon, contentDescription = null) },
                        label = { Text(stringResource(item.label)) },
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            Tab.FOCUS -> FocusTab(settings, store, batteryUnrestricted, padding, onOpenSettings = { tab = Tab.SETTINGS })
            Tab.STORIES -> StoriesTab(settings, store, padding)
            Tab.SETTINGS -> SettingsTab(
                settings = settings,
                store = store,
                enabledInSettings = enabledInSettings,
                batteryUnrestricted = batteryUnrestricted,
                padding = padding,
                onOpenDiagnostics = { diagnosticsOpen = true },
                onShowIntro = { introAsked = true },
            )
        }
    }
}
