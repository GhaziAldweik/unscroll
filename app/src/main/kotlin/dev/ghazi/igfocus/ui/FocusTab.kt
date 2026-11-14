package dev.ghazi.igfocus.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.ghazi.igfocus.R
import dev.ghazi.igfocus.inspector.InspectorState
import dev.ghazi.igfocus.policy.BlockingSettings
import dev.ghazi.igfocus.policy.Pause
import dev.ghazi.igfocus.service.BlockingState
import dev.ghazi.igfocus.settings.SettingsStore
import dev.ghazi.igfocus.settings.UserSettings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.time.LocalDate
import java.util.Date

/** A blocking rule as the Focus tab shows it. */
private class Rule(
    val name: Int,
    val note: Int,
    val info: Int,
    val icon: ImageVector,
    val area: BlockingState.Area,
    val isOn: (BlockingSettings) -> Boolean,
    val set: suspend (SettingsStore, Boolean) -> Unit,
)

private val RULES = listOf(
    Rule(
        R.string.rule_reels_tab, R.string.rule_reels_tab_note, R.string.rule_reels_tab_info,
        Icons.Filled.PlayArrow, BlockingState.Area.REELS, { it.reelsTab },
    ) { s, on -> s.setBlockReelsTab(on) },
    Rule(
        R.string.rule_home_feed, R.string.rule_home_feed_note, R.string.rule_home_feed_info,
        Icons.Filled.Home, BlockingState.Area.HOME_FEED, { it.homeFeed },
    ) { s, on -> s.setCoverHomeFeed(on) },
    Rule(
        R.string.rule_explore, R.string.rule_explore_note, R.string.rule_explore_info,
        Icons.Filled.Search, BlockingState.Area.EXPLORE, { it.explore },
    ) { s, on -> s.setCoverExplore(on) },
    Rule(
        R.string.rule_search_results, R.string.rule_search_results_note, R.string.rule_search_results_info,
        Icons.AutoMirrored.Filled.List, BlockingState.Area.SEARCH_RESULTS, { it.searchResults },
    ) { s, on -> s.setBlockSearchResults(on) },
)

private val PAUSE_CHOICES = listOf(R.string.pause_5 to 5, R.string.pause_15 to 15, R.string.pause_60 to 60)

private val KEPT = listOf(
    R.string.kept_messages,
    R.string.kept_posting,
    R.string.kept_profile,
    R.string.kept_stories,
    R.string.kept_friends_reels,
    R.string.kept_saved_reels,
)

/**
 * The first tab: what is blocked right now and whether it is really working, Pause, and one
 * switch per blocked area. [onOpenSettings] leads to setup and the permission steps.
 */
@Composable
fun FocusTab(
    settings: UserSettings,
    store: SettingsStore,
    batteryUnrestricted: Boolean,
    padding: PaddingValues,
    onOpenSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val running by InspectorState.serviceConnected.collectAsState()
    val lastScreen by InspectorState.lastScreen.collectAsState()
    val stopped by BlockingState.stopped.collectAsState()
    val lastWorked by BlockingState.lastWorked.collectAsState()
    // Re-read the clock now and then, so "2 min ago" and an expiring pause stay current.
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(10_000)
            value = System.currentTimeMillis()
        }
    }
    val paused = Pause.isPaused(settings.pauseUntilMs, now)

    val today = remember(now / 60_000) { LocalDate.now() }

    TabPage(title = stringResource(R.string.app_name), padding = padding, leading = { UnscrollIcon(40.dp) }) {
        StatusCard(
            running = running,
            settings = settings,
            instagramSeenMs = lastScreen?.timestampMs,
            batteryUnrestricted = batteryUnrestricted,
            stopped = stopped?.takeIf { it.untilMs > now },
            now = now,
            onPauseUntil = { scope.launch { store.setPauseUntil(it) } },
            onOpenSettings = onOpenSettings,
        )

        SectionHeader(stringResource(R.string.section_today))
        StepInsCard(settings.stepIns, today)

        SectionHeader(stringResource(R.string.section_blocked))
        for (rule in RULES) {
            val on = rule.isOn(settings.blocking)
            val workedMs = lastWorked[rule.area]
            RuleCard(
                rule = rule,
                checked = on,
                status = when {
                    !on -> stringResource(R.string.rule_status_off)
                    !running -> stringResource(R.string.rule_status_no_service)
                    paused -> stringResource(R.string.rule_status_paused)
                    workedMs != null -> stringResource(R.string.rule_status_worked, agoText(workedMs, now))
                    else -> stringResource(R.string.rule_status_waiting)
                },
                statusColor = when {
                    !on || paused -> MaterialTheme.colorScheme.onSurfaceVariant
                    !running -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.primary
                },
                onChange = { scope.launch { rule.set(store, it) } },
            )
        }
        ComingLaterCard(
            icon = Icons.Filled.ThumbUp,
            name = stringResource(R.string.rule_suggested),
            note = stringResource(R.string.rule_suggested_note),
        )

        SectionHeader(stringResource(R.string.science_title))
        TodayScienceCard(today)
        WhyCard()

        SectionHeader(stringResource(R.string.section_kept))
        FocusCard {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for (kept in KEPT) KeptPill(stringResource(kept))
            }
        }

        FocusCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconBadge(Icons.Filled.Star, active = false)
                Spacer(modifier = Modifier.width(16.dp))
                Text(stringResource(R.string.saved_title), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.saved_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * States plainly what is happening right now, below that whether the parts it depends on are in
 * order, and holds Pause / Resume.
 */
@Composable
private fun StatusCard(
    running: Boolean,
    settings: UserSettings,
    instagramSeenMs: Long?,
    batteryUnrestricted: Boolean,
    stopped: BlockingState.Stopped?,
    now: Long,
    onPauseUntil: (Long) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val paused = Pause.isPaused(settings.pauseUntilMs, now)
    val onNames = RULES.filter { it.isOn(settings.blocking) }.map { context.getString(it.name) }
    val separator = stringResource(R.string.list_separator)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT)
    val active = running && !paused && onNames.isNotEmpty()

    val icon = when {
        !running -> Icons.Filled.Warning
        paused -> PauseIcon
        onNames.isEmpty() -> Icons.Filled.Info
        else -> Icons.Filled.CheckCircle
    }
    val title = when {
        !running -> stringResource(R.string.status_not_running)
        paused && settings.pauseUntilMs == Pause.UNTIL_RESUMED -> stringResource(R.string.status_paused_indefinitely)
        paused -> stringResource(R.string.status_paused_until, time.format(Date(settings.pauseUntilMs)))
        onNames.isEmpty() -> stringResource(R.string.status_nothing_on)
        else -> stringResource(R.string.status_on)
    }
    val subtitle = when {
        !running -> stringResource(R.string.status_not_running_text)
        paused && settings.pauseUntilMs == Pause.UNTIL_RESUMED -> stringResource(R.string.status_paused_text)
        // Rounded up, so the last minute reads "1 min left" rather than "0".
        paused -> stringResource(R.string.status_minutes_left, ((settings.pauseUntilMs - now + 59_999) / 60_000).toInt())
        onNames.isEmpty() -> stringResource(R.string.status_nothing_on_text)
        else -> stringResource(R.string.status_blocking_now, onNames.joinToString(separator))
    }

    // The icon's teal, fading into the page, while it is on; plain otherwise.
    val glow = Brush.linearGradient(listOf(colors.primaryContainer, colors.primaryContainer.copy(alpha = 0.35f)))
    FocusCard(color = colors.surfaceContainerHigh, brush = if (active) glow else null) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (running && !paused) {
                // The app's own emblem; greyed while nothing is switched on.
                UnscrollIcon(56.dp, dim = !active)
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .background(colors.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = if (!running) colors.error else colors.onSurfaceVariant,
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium)
            }
        }

        // What the status above depends on. Only facts read from the phone, never guesses.
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (running) {
                HealthRow(ok = true, text = stringResource(R.string.health_service_running))
                HealthRow(
                    ok = instagramSeenMs != null,
                    text = instagramSeenMs?.let { stringResource(R.string.health_instagram_seen, agoText(it, now)) }
                        ?: stringResource(R.string.health_instagram_waiting),
                )
            }
            if (!batteryUnrestricted) {
                HealthRow(ok = false, warn = true, text = stringResource(R.string.health_battery), onClick = onOpenSettings)
            }
        }

        when {
            !running -> Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.status_set_up))
            }
            paused -> Button(onClick = { onPauseUntil(Pause.NOT_PAUSED) }, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.resume))
            }
            active -> PauseChoices(onPauseUntil)
        }

        stopped?.let {
            StoppedNotice(
                stringResource(
                    when (it.reason) {
                        BlockingState.Reason.ACTION_REPEATING -> R.string.status_stopped_actions
                        BlockingState.Reason.COVERS_FLAPPING -> R.string.status_stopped_covers
                    },
                    time.format(Date(it.atMs)),
                    it.screen.name,
                    time.format(Date(it.untilMs)),
                ),
            )
        }
    }
}

/** One fact under the status: a small tick, or a warning that leads to where it is fixed. */
@Composable
private fun HealthRow(ok: Boolean, text: String, warn: Boolean = false, onClick: (() -> Unit)? = null) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            when {
                ok -> Icons.Filled.CheckCircle
                warn -> Icons.Filled.Warning
                else -> Icons.Filled.Info
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (warn) colors.error else colors.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
        if (onClick != null) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PauseChoices(onPauseUntil: (Long) -> Unit) {
    val compact = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.status_pause_label), style = MaterialTheme.typography.labelMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((label, minutes) in PAUSE_CHOICES) {
                FilledTonalButton(
                    onClick = { onPauseUntil(Pause.until(System.currentTimeMillis(), minutes)) },
                    contentPadding = compact,
                    modifier = Modifier.heightIn(min = 36.dp),
                ) {
                    Text(stringResource(label))
                }
            }
            FilledTonalButton(
                onClick = { onPauseUntil(Pause.UNTIL_RESUMED) },
                contentPadding = compact,
                modifier = Modifier.heightIn(min = 36.dp),
            ) {
                Text(stringResource(R.string.pause_until_resumed))
            }
        }
    }
}

/**
 * A rule's card: tapping it toggles the switch. Under the name, what the rule is doing right now;
 * the info button opens a fuller explanation.
 */
@Composable
private fun RuleCard(rule: Rule, checked: Boolean, status: String, statusColor: Color, onChange: (Boolean) -> Unit) {
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    val name = stringResource(rule.name)
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        border = cardBorder(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch, onValueChange = onChange)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = if (infoOpen) 8.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(rule.icon, active = checked)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.labelMedium, color = statusColor)
                Text(
                    stringResource(rule.note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { infoOpen = !infoOpen }) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.about_rule, name),
                    tint = if (infoOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = checked, onCheckedChange = null)
        }
        if (infoOpen) {
            Text(
                stringResource(rule.info),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 74.dp, end = 16.dp, bottom = 16.dp),
            )
        }
    }
}

/** A rule that is planned but not built: no switch, so nothing pretends to work. */
@Composable
private fun ComingLaterCard(icon: ImageVector, name: String, note: String) {
    FocusCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon, active = false)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                Text(
                    stringResource(R.string.coming_later),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun StoppedNotice(text: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.errorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
        }
    }
}
