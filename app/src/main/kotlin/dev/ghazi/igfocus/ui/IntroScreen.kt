package dev.ghazi.igfocus.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.ghazi.igfocus.R

/** A page of the introduction; a null [icon] shows the app's own icon. */
private class IntroPage(val icon: ImageVector?, val title: Int, val text: Int, val source: Int? = null)

private val PAGES = listOf(
    IntroPage(null, R.string.intro_what_title, R.string.intro_what_text),
    IntroPage(Icons.Filled.Info, R.string.why_title, R.string.why_text, R.string.why_sources),
    IntroPage(Icons.Filled.Lock, R.string.intro_access_title, R.string.intro_access_text),
    IntroPage(PauseIcon, R.string.intro_pause_title, R.string.intro_pause_text),
    IntroPage(Icons.Filled.Settings, R.string.intro_setup_title, R.string.intro_setup_text),
)

/**
 * Five short pages shown on first start: what the app does, why it exists, why it needs accessibility access,
 * how to pause or turn it off, and what setup involves. It asks for nothing itself; the last page
 * leads to the Settings tab, where each step opens Android's own page.
 *
 * [onFinish] gets true when setup should open next.
 */
@Composable
fun IntroScreen(serviceOn: Boolean, onFinish: (toSetup: Boolean) -> Unit) {
    var page by rememberSaveable { mutableIntStateOf(0) }
    val last = page == PAGES.lastIndex
    val current = PAGES[page]
    val colors = MaterialTheme.colorScheme

    BackHandler(enabled = page > 0) { page-- }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = { onFinish(false) }) { Text(stringResource(R.string.intro_skip)) }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val pageIcon = current.icon
            if (pageIcon == null) {
                UnscrollIcon(104.dp)
            } else {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(AccentBrush),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(pageIcon, contentDescription = null, tint = OnAccent, modifier = Modifier.size(44.dp))
                }
            }
            Spacer(modifier = Modifier.height(28.dp))
            Text(
                stringResource(current.title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                stringResource(current.text),
                style = MaterialTheme.typography.bodyLarge,
                color = colors.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            current.source?.let {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    stringResource(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.primary,
                    textAlign = TextAlign.Center,
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
        ) {
            for (index in PAGES.indices) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (index == page) 20.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (index == page) colors.primary else colors.surfaceContainerHighest),
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (page > 0) TextButton(onClick = { page-- }) { Text(stringResource(R.string.back)) }
            Spacer(modifier = Modifier.weight(1f))
            when {
                !last -> Button(onClick = { page++ }) { Text(stringResource(R.string.intro_next)) }
                serviceOn -> Button(onClick = { onFinish(false) }) { Text(stringResource(R.string.intro_done)) }
                else -> {
                    TextButton(onClick = { onFinish(false) }) { Text(stringResource(R.string.intro_later)) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { onFinish(true) }) { Text(stringResource(R.string.intro_to_setup)) }
                }
            }
        }
    }
}
