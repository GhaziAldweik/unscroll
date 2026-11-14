package dev.ghazi.igfocus.ui

import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.net.Uri
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ghazi.igfocus.LOG_TAG
import dev.ghazi.igfocus.R
import dev.ghazi.igfocus.inspector.InspectorState
import dev.ghazi.igfocus.policy.Pause
import dev.ghazi.igfocus.rules.InstagramRules
import dev.ghazi.igfocus.service.BlockingState
import dev.ghazi.igfocus.service.InstagramInfo
import dev.ghazi.igfocus.service.ServiceStatus
import dev.ghazi.igfocus.settings.SettingsStore
import dev.ghazi.igfocus.settings.UserSettings
import dev.ghazi.igfocus.tile.PauseTileService
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/** How a setup step shows: done (a tick), still to do (an empty circle), or advice only. */
private enum class StepState { DONE, TODO, ADVICE }

/** How far a part of Unscroll is built, as the Protection overview states it. */
private enum class Support(val label: Int) {
    WORKS(R.string.support_works),
    PARTIAL(R.string.support_partial),
    LATER(R.string.coming_later),
}

/** One line of the protection check: a fact, whether it is fine, and where to fix it if not. */
private class Finding(val level: Level, val text: String, val fix: String? = null, val onFix: () -> Unit = {}) {
    enum class Level { OK, INFO, PROBLEM }
}

/**
 * Permissions, behaviour, an overview of what is built, a protection check, privacy, and the ways
 * to reset or turn off. Every button only opens a system page or a system prompt, or changes this
 * app's own settings; none changes a phone setting itself.
 */
@Composable
fun SettingsTab(
    settings: UserSettings,
    store: SettingsStore,
    enabledInSettings: Boolean,
    batteryUnrestricted: Boolean,
    padding: PaddingValues,
    onOpenDiagnostics: () -> Unit,
    onShowIntro: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val running by InspectorState.serviceConnected.collectAsState()
    var detailsOpen by rememberSaveable { mutableStateOf(false) }
    var checkShown by rememberSaveable { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var confirmTurnOff by remember { mutableStateOf(false) }

    TabPage(title = stringResource(R.string.tab_settings), padding = padding) {
        SectionHeader(stringResource(R.string.section_permissions))
        FocusCard {
            Note(stringResource(R.string.setup_intro), modifier = Modifier.padding(bottom = 4.dp))
            Step(
                state = if (enabledInSettings && running) StepState.DONE else StepState.TODO,
                title = stringResource(R.string.setup_step1_title),
                text = stringResource(
                    when {
                        running -> R.string.service_running_text
                        enabledInSettings -> R.string.service_enabled_not_running
                        else -> R.string.setup_step1_text
                    },
                ),
                action = stringResource(R.string.open_accessibility_settings),
                onAction = { openSettingsPage(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) },
            )
            // Only matters while the service cannot be switched on.
            if (!enabledInSettings) {
                StepDivider()
                Step(
                    state = StepState.ADVICE,
                    title = stringResource(R.string.setup_step2_title),
                    text = stringResource(R.string.setup_step2_text),
                    action = stringResource(R.string.open_app_info),
                    onAction = { openSettingsPage(context, appInfoIntent(context)) },
                )
            }
            StepDivider()
            Step(
                state = if (batteryUnrestricted) StepState.DONE else StepState.TODO,
                title = stringResource(R.string.setup_step3_title),
                text = stringResource(R.string.setup_step3_text),
                action = stringResource(R.string.open_app_info).takeUnless { batteryUnrestricted },
                onAction = { openSettingsPage(context, appInfoIntent(context)) },
            )
            StepDivider()
            Step(
                state = StepState.ADVICE,
                title = stringResource(R.string.setup_step4_title),
                text = stringResource(R.string.setup_step4_text),
            )
            Note(stringResource(R.string.permissions_none_else))
        }

        SectionHeader(stringResource(R.string.section_behaviour))
        SwitchCard(
            icon = Icons.AutoMirrored.Filled.Send,
            name = stringResource(R.string.open_on_messages),
            note = stringResource(
                if (settings.blocking.homeFeed) R.string.open_on_messages_note else R.string.open_on_messages_needs_home,
            ),
            checked = settings.blocking.openOnMessages,
            onChange = { on -> scope.launch { store.setOpenOnMessages(on) } },
        )
        FocusCard {
            Text(stringResource(R.string.stop_title), style = MaterialTheme.typography.titleMedium)
            Bullet(stringResource(R.string.stop_pause))
            Bullet(stringResource(R.string.stop_tile))
            Bullet(stringResource(R.string.stop_off))
            OutlinedButton(onClick = { requestPauseTile(context) }) { Text(stringResource(R.string.add_tile)) }
        }

        SectionHeader(stringResource(R.string.section_protection))
        FocusCard {
            val blocking = settings.blocking
            ProtectionRow(stringResource(R.string.rule_reels_tab), blocking.reelsTab, Support.WORKS)
            ProtectionRow(stringResource(R.string.rule_home_feed), blocking.homeFeed, Support.WORKS)
            ProtectionRow(stringResource(R.string.rule_explore), blocking.explore, Support.WORKS)
            ProtectionRow(
                stringResource(R.string.rule_search_results),
                blocking.searchResults,
                Support.PARTIAL,
                stringResource(R.string.support_search_partial),
            )
            ProtectionRow(stringResource(R.string.rule_suggested), null, Support.LATER)
            ProtectionRow(stringResource(R.string.protection_stories), null, Support.LATER)
        }

        SectionHeader(stringResource(R.string.section_diagnostics))
        FocusCard {
            Text(
                stringResource(R.string.check_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (checkShown) {
                ProtectionCheck(settings, store, enabledInSettings, batteryUnrestricted)
            } else {
                Button(onClick = { checkShown = true }) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.check_run))
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            LinkRow(
                icon = Icons.Filled.Build,
                text = stringResource(R.string.dash_open_diagnostics),
                note = stringResource(R.string.diagnostics_link_note),
                onClick = onOpenDiagnostics,
            )
        }

        SectionHeader(stringResource(R.string.permissions_title))
        FocusCard {
            PrivacyPoint(Icons.Filled.Lock, stringResource(R.string.privacy_no_internet))
            PrivacyPoint(Icons.Filled.Info, stringResource(R.string.privacy_reads))
            PrivacyPoint(Icons.Filled.CheckCircle, stringResource(R.string.privacy_local))
            if (detailsOpen) {
                Text(
                    stringResource(R.string.permissions_text),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            // No side padding, so the text lines up with the lines above.
            TextButton(onClick = { detailsOpen = !detailsOpen }, contentPadding = PaddingValues(horizontal = 0.dp)) {
                Text(stringResource(if (detailsOpen) R.string.privacy_less else R.string.privacy_more))
            }
        }

        SectionHeader(stringResource(R.string.section_maintenance))
        FocusCard {
            LinkRow(icon = Icons.Filled.Info, text = stringResource(R.string.intro_again), onClick = onShowIntro)
            LinkRow(
                icon = Icons.Filled.Refresh,
                text = stringResource(R.string.reset_settings),
                note = stringResource(R.string.reset_settings_note),
                onClick = { confirmReset = true },
            )
            if (running) {
                LinkRow(
                    icon = Icons.Filled.Close,
                    text = stringResource(R.string.turn_off),
                    note = stringResource(R.string.turn_off_note),
                    onClick = { confirmTurnOff = true },
                )
            }
        }

        Note(
            stringResource(
                R.string.about_versions,
                appVersion(context),
                InstagramInfo.installedVersion(context) ?: stringResource(R.string.instagram_not_found),
            ),
            modifier = Modifier.padding(top = 8.dp),
        )
    }

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.reset_confirm_title),
            text = stringResource(R.string.reset_confirm_text),
            confirm = stringResource(R.string.reset_settings),
            onConfirm = { scope.launch { store.resetAll() } },
            onDismiss = { confirmReset = false },
        )
    }
    if (confirmTurnOff) {
        ConfirmDialog(
            title = stringResource(R.string.turn_off_confirm_title),
            text = stringResource(R.string.turn_off_confirm_text),
            confirm = stringResource(R.string.turn_off),
            onConfirm = { ServiceStatus.turnOffRequests.tryEmit(Unit) },
            onDismiss = { confirmTurnOff = false },
        )
    }
}

/**
 * Checks what blocking depends on, from facts the phone reports, and says how to fix each
 * problem. Live: the lines update as things change, for example after opening Instagram.
 */
@Composable
private fun ProtectionCheck(
    settings: UserSettings,
    store: SettingsStore,
    enabledInSettings: Boolean,
    batteryUnrestricted: Boolean,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val running by InspectorState.serviceConnected.collectAsState()
    val lastScreen by InspectorState.lastScreen.collectAsState()
    val recognised by InspectorState.recognised.collectAsState()
    val stopped by BlockingState.stopped.collectAsState()
    val now = System.currentTimeMillis()
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    val instagramVersion = InstagramInfo.installedVersion(context)
    val openAccessibility = stringResource(R.string.open_accessibility_settings)
    val openInstagram = stringResource(R.string.open_instagram)
    val paused = Pause.isPaused(settings.pauseUntilMs, now)
    val anyOn = settings.blocking.anyOn

    val findings = buildList {
        when {
            !enabledInSettings -> add(
                Finding(Finding.Level.PROBLEM, stringResource(R.string.check_service_off), openAccessibility) {
                    openSettingsPage(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
            !running -> add(
                Finding(Finding.Level.PROBLEM, stringResource(R.string.service_enabled_not_running), openAccessibility) {
                    openSettingsPage(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                },
            )
            else -> add(Finding(Finding.Level.OK, stringResource(R.string.health_service_running)))
        }
        add(
            if (instagramVersion != null) {
                Finding(Finding.Level.OK, stringResource(R.string.check_instagram_installed, instagramVersion))
            } else {
                Finding(Finding.Level.PROBLEM, stringResource(R.string.check_instagram_missing))
            },
        )
        if (!anyOn) {
            add(Finding(Finding.Level.INFO, stringResource(R.string.check_nothing_on)))
        } else if (running && instagramVersion != null) {
            // Instagram is only read while a rule is on, so these lines need one.
            val seen = lastScreen
            when {
                seen == null -> add(
                    Finding(Finding.Level.INFO, stringResource(R.string.check_instagram_not_seen), openInstagram) {
                        openInstagram(context)
                    },
                )
                recognised.isEmpty() -> add(Finding(Finding.Level.PROBLEM, stringResource(R.string.check_nothing_recognised)))
                else -> add(
                    Finding(
                        Finding.Level.OK,
                        stringResource(
                            R.string.check_recognised,
                            recognised.size,
                            agoText(seen.timestampMs, now),
                        ),
                    ),
                )
            }
        }
        if (paused) {
            add(
                Finding(
                    Finding.Level.INFO,
                    if (settings.pauseUntilMs == Pause.UNTIL_RESUMED) {
                        stringResource(R.string.status_paused_indefinitely)
                    } else {
                        stringResource(R.string.status_paused_until, time.format(Date(settings.pauseUntilMs)))
                    },
                    stringResource(R.string.resume),
                ) { scope.launch { store.setPauseUntil(Pause.NOT_PAUSED) } },
            )
        }
        stopped?.takeIf { it.untilMs > now }?.let {
            add(Finding(Finding.Level.PROBLEM, stringResource(R.string.check_stopped, time.format(Date(it.untilMs)))))
        }
        add(
            if (batteryUnrestricted) {
                Finding(Finding.Level.OK, stringResource(R.string.check_battery_ok))
            } else {
                Finding(Finding.Level.PROBLEM, stringResource(R.string.health_battery), stringResource(R.string.open_app_info)) {
                    openSettingsPage(context, appInfoIntent(context))
                }
            },
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        for (finding in findings) FindingRow(finding)
        val problems = findings.count { it.level == Finding.Level.PROBLEM }
        Text(
            if (problems == 0) {
                stringResource(R.string.check_all_good)
            } else {
                pluralStringResource(R.plurals.check_problems, problems, problems)
            },
            style = MaterialTheme.typography.labelLarge,
            color = if (problems == 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        if (anyOn && running) {
            OutlinedButton(onClick = { openInstagram(context) }) { Text(openInstagram) }
        }
    }
}

@Composable
private fun FindingRow(finding: Finding) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            when (finding.level) {
                Finding.Level.OK -> Icons.Filled.CheckCircle
                Finding.Level.INFO -> Icons.Filled.Info
                Finding.Level.PROBLEM -> Icons.Filled.Warning
            },
            contentDescription = null,
            modifier = Modifier
                .padding(top = 1.dp)
                .size(18.dp),
            tint = when (finding.level) {
                Finding.Level.OK -> colors.primary
                Finding.Level.INFO -> colors.onSurfaceVariant
                Finding.Level.PROBLEM -> colors.error
            },
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(finding.text, style = MaterialTheme.typography.bodyMedium)
            if (finding.fix != null) {
                TextButton(onClick = finding.onFix, contentPadding = PaddingValues(horizontal = 0.dp)) {
                    Text(finding.fix)
                }
            }
        }
    }
}

/** A part of Unscroll: whether it is switched on ([on] is null for one with no switch yet) and how far it is built. */
@Composable
private fun ProtectionRow(name: String, on: Boolean?, support: Support, detail: String? = null) {
    val colors = MaterialTheme.colorScheme
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyLarge)
            Text(
                listOfNotNull(stringResource(support.label), detail).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = if (support == Support.WORKS) colors.primary else colors.onSurfaceVariant,
            )
        }
        if (on != null) {
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                shape = CircleShape,
                color = if (on) colors.primaryContainer else colors.surfaceContainerHighest,
            ) {
                Text(
                    stringResource(if (on) R.string.state_on else R.string.state_off),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = {
                onConfirm()
                onDismiss()
            }) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun Step(
    state: StepState,
    title: String,
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(verticalAlignment = Alignment.Top) {
        StepMark(state)
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (action != null) {
                OutlinedButton(onClick = onAction, modifier = Modifier.padding(top = 4.dp)) { Text(action) }
            }
        }
    }
}

@Composable
private fun StepMark(state: StepState) {
    val colors = MaterialTheme.colorScheme
    val base = Modifier
        .padding(top = 2.dp)
        .size(24.dp)
        .clip(CircleShape)
    when (state) {
        StepState.DONE -> Box(base.background(colors.primary), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Check, contentDescription = null, tint = colors.onPrimary, modifier = Modifier.size(16.dp))
        }
        StepState.TODO -> Box(base.border(2.dp, colors.outline, CircleShape))
        StepState.ADVICE -> Box(base.background(colors.surfaceContainerHighest), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = colors.onSurfaceVariant, modifier = Modifier.size(16.dp))
        }
    }
}

@Composable
private fun StepDivider() {
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

@Composable
private fun PrivacyPoint(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
    }
}

private fun appVersion(context: Context): String = try {
    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
} catch (e: Exception) {
    "?"
}

private fun appInfoIntent(context: Context) = Intent(
    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
    Uri.fromParts("package", context.packageName, null),
)

/** Opens the official Instagram app, so the check has something to look at. */
private fun openInstagram(context: Context) {
    try {
        context.packageManager.getLaunchIntentForPackage(InstagramRules.PACKAGE)?.let(context::startActivity)
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not open Instagram", e)
    }
}

/** Asks Android to offer the tile. Android shows its own prompt and the user decides. */
private fun requestPauseTile(context: Context) {
    try {
        context.getSystemService(StatusBarManager::class.java).requestAddTileService(
            ComponentName(context, PauseTileService::class.java),
            context.getString(R.string.tile_label),
            Icon.createWithResource(context, R.drawable.ic_tile_pause),
            context.mainExecutor,
        ) { result ->
            val message = when (result) {
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED -> R.string.tile_added
                StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED -> R.string.tile_already_added
                else -> R.string.tile_not_added
            }
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    } catch (e: Exception) {
        Log.w(LOG_TAG, "Could not request the Quick Settings tile", e)
        Toast.makeText(context, R.string.tile_not_added, Toast.LENGTH_LONG).show()
    }
}
