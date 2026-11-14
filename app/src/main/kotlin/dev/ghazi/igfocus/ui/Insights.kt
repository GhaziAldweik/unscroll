package dev.ghazi.igfocus.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.ghazi.igfocus.R
import dev.ghazi.igfocus.stats.StepIns
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

/** Which research note shows on [epochDay]: a new one each day, cycling through all of them. */
fun scienceIndex(epochDay: Long, count: Int): Int = Math.floorMod(epochDay, count.toLong()).toInt()

/** Today's exact step-in count, the week's total and one bar per day. No time estimates. */
@Composable
fun StepInsCard(stepIns: StepIns, today: LocalDate) {
    var infoOpen by rememberSaveable { mutableStateOf(false) }
    val day = today.toEpochDay()
    val labels = (6 downTo 0).map {
        today.minusDays(it.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    }
    FocusCard(modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                AccentNumber(stepIns.on(day).toString(), MaterialTheme.typography.displayMedium)
                Text(stringResource(R.string.stepins_today_label), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.stepins_week, stepIns.weekTotal(day)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { infoOpen = !infoOpen }) {
                Icon(
                    Icons.Outlined.Info,
                    contentDescription = stringResource(R.string.about_stepins),
                    tint = if (infoOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        WeekBars(stepIns.week(day), labels)
        if (infoOpen) Note(stringResource(R.string.stepins_info))
    }
}

/** The neuroscience behind the app, short first, with its sources. */
@Composable
fun WhyCard() {
    var open by rememberSaveable { mutableStateOf(false) }
    FocusCard(modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            UnscrollIcon(36.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Text(stringResource(R.string.why_title), style = MaterialTheme.typography.titleMedium)
        }
        Text(
            stringResource(if (open) R.string.why_text else R.string.why_short),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (open) Source(stringResource(R.string.why_sources))
        TextButton(onClick = { open = !open }, contentPadding = PaddingValues(horizontal = 0.dp)) {
            Text(stringResource(if (open) R.string.read_less else R.string.read_more))
        }
    }
}

/** One short research note on scrolling, a different one each day, with its source. */
@Composable
fun TodayScienceCard(today: LocalDate) {
    val headlines = stringArrayResource(R.array.science_headlines)
    val texts = stringArrayResource(R.array.science_texts)
    val sources = stringArrayResource(R.array.science_sources)
    val count = minOf(headlines.size, texts.size, sources.size)
    if (count == 0) return
    val i = scienceIndex(today.toEpochDay(), count)
    // The section heading above says "Today's science", so the card starts with the headline.
    FocusCard {
        Text(headlines[i], style = MaterialTheme.typography.titleLarge)
        Text(texts[i], style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Source(sources[i])
        Text(
            stringResource(R.string.science_note),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Source(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .size(width = 3.dp, height = 14.dp)
                .clip(CircleShape)
                .background(AccentBrush),
        )
        Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
    }
}
