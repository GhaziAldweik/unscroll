package dev.ghazi.igfocus.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ghazi.igfocus.LOG_TAG
import dev.ghazi.igfocus.R
import dev.ghazi.igfocus.inspector.Capture
import dev.ghazi.igfocus.inspector.CaptureStore
import dev.ghazi.igfocus.inspector.InspectorState
import dev.ghazi.igfocus.service.InstagramInfo
import dev.ghazi.igfocus.settings.SettingsStore
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Service status, the Inspector switch, and the saved captures with export. Opened from Settings.
 * [enabledInSettings] is whether the service is switched on in Android's Accessibility settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(enabledInSettings: Boolean, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { SettingsStore(context) }
    val store = remember { CaptureStore.get(context) }

    val running by InspectorState.serviceConnected.collectAsState()
    val inspectorOn by settings.inspectorEnabled.collectAsState(initial = false)
    val lastScreen by InspectorState.lastScreen.collectAsState()
    val distinctScreens by InspectorState.distinctScreens.collectAsState()
    val captures by store.captures.collectAsState()
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.dash_open_diagnostics)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionHeader(stringResource(R.string.section_service))
            FocusCard {
                Text(
                    stringResource(
                        when {
                            running -> R.string.service_running
                            enabledInSettings -> R.string.service_enabled_not_running
                            else -> R.string.service_off
                        }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                )
                // Opens the page only. The user flips the switch.
                OutlinedButton(onClick = { openSettingsPage(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }) {
                    Text(stringResource(R.string.open_accessibility_settings))
                }
            }

            SectionHeader(stringResource(R.string.section_inspector))
            SwitchCard(
                icon = Icons.Filled.Search,
                name = stringResource(R.string.inspector_switch),
                note = stringResource(R.string.inspector_explanation),
                checked = inspectorOn,
                onChange = { on -> scope.launch { settings.setInspectorEnabled(on) } },
            )
            FocusCard {
                val detail = MaterialTheme.typography.bodyMedium
                Text(
                    stringResource(
                        R.string.instagram_version,
                        InstagramInfo.installedVersion(context) ?: stringResource(R.string.instagram_not_found),
                    ),
                    style = detail,
                )
                Text(
                    lastScreen?.let {
                        val count = it.idCount
                        if (count != null) {
                            stringResource(R.string.last_screen, it.screen.name, count, formatTime(it.timestampMs))
                        } else {
                            stringResource(R.string.last_screen_no_count, it.screen.name, formatTime(it.timestampMs))
                        }
                    }
                        ?: stringResource(R.string.last_screen_none),
                    style = detail,
                )
                Text(stringResource(R.string.distinct_screens, distinctScreens), style = detail)
            }

            SectionHeader(stringResource(R.string.section_captures, captures.size))
            FocusCard {
                if (captures.isEmpty()) {
                    Text(stringResource(R.string.captures_empty), style = MaterialTheme.typography.bodyMedium)
                } else {
                    captures.forEachIndexed { index, capture ->
                        CaptureRow(capture, onDelete = { store.removeAt(index) })
                    }
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { shareCaptures(context, store.exportJson()) }) {
                            Text(stringResource(R.string.share_captures))
                        }
                        OutlinedButton(onClick = { copyCaptures(context, store.exportJson()) }) {
                            Text(stringResource(R.string.copy_captures))
                        }
                        TextButton(onClick = { confirmClear = true }) {
                            Text(stringResource(R.string.clear_captures))
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.clear_confirm_title)) },
            text = { Text(stringResource(R.string.clear_confirm_text)) },
            confirmButton = {
                TextButton(onClick = {
                    store.clear()
                    confirmClear = false
                }) { Text(stringResource(R.string.clear_captures)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

@Composable
private fun CaptureRow(capture: Capture, onDelete: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(capture.label, style = MaterialTheme.typography.bodyLarge)
            Text(
                stringResource(
                    R.string.capture_row_detail,
                    formatDateTime(capture.timestampMs),
                    capture.ids.size,
                    capture.instagramVersion,
                ),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        TextButton(onClick = onDelete) { Text(stringResource(R.string.delete)) }
    }
}

private fun formatTime(timestampMs: Long): String =
    DateFormat.getTimeInstance(DateFormat.MEDIUM).format(Date(timestampMs))

private fun formatDateTime(timestampMs: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.MEDIUM).format(Date(timestampMs))

private fun shareCaptures(context: Context, json: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, context.getString(R.string.export_label))
        .putExtra(Intent.EXTRA_TEXT, json)
    try {
        context.startActivity(Intent.createChooser(send, null))
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not open the share sheet", e)
    }
}

private fun copyCaptures(context: Context, json: String) {
    // Android shows its own "copied" confirmation.
    context.getSystemService(ClipboardManager::class.java)
        .setPrimaryClip(ClipData.newPlainText(context.getString(R.string.export_label), json))
}
