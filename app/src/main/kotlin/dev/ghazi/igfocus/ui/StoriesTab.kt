package dev.ghazi.igfocus.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import dev.ghazi.igfocus.R
import dev.ghazi.igfocus.settings.SettingsStore
import dev.ghazi.igfocus.settings.UserSettings
import kotlinx.coroutines.launch

/** Stories: the switch, the user's own username and the people to hide. Stored now, applied later. */
@Composable
fun StoriesTab(settings: UserSettings, store: SettingsStore, padding: PaddingValues) {
    val scope = rememberCoroutineScope()
    var ownUsername by rememberSaveable { mutableStateOf(settings.ownUsername) }
    var toHide by rememberSaveable { mutableStateOf("") }
    val addHidden = {
        if (toHide.isNotBlank()) {
            val name = toHide
            scope.launch { store.addHiddenUsername(name) }
            toHide = ""
        }
    }

    TabPage(title = stringResource(R.string.stories_title), padding = padding) {
        FocusCard(color = MaterialTheme.colorScheme.secondaryContainer) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    stringResource(R.string.stories_later),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        SwitchCard(
            icon = Icons.Filled.AccountCircle,
            name = stringResource(R.string.stories_switch),
            note = stringResource(R.string.stories_switch_note),
            checked = settings.storiesOn,
            onChange = { on -> scope.launch { store.setStoriesOn(on) } },
        )

        SectionHeader(stringResource(R.string.stories_you))
        FocusCard {
            OutlinedTextField(
                value = ownUsername,
                onValueChange = {
                    ownUsername = it
                    scope.launch { store.setOwnUsername(it) }
                },
                label = { Text(stringResource(R.string.stories_own_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionHeader(stringResource(R.string.stories_hidden_heading, settings.hiddenUsernames.size))
        FocusCard {
            if (!settings.storiesOn) {
                Text(
                    stringResource(R.string.stories_hidden_while_off),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (settings.hiddenUsernames.isEmpty()) {
                Text(
                    stringResource(R.string.stories_hidden_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (username in settings.hiddenUsernames.sorted()) {
                        InputChip(
                            selected = false,
                            onClick = { scope.launch { store.removeHiddenUsername(username) } },
                            label = { Text(username) },
                            trailingIcon = {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = stringResource(R.string.remove),
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = toHide,
                    onValueChange = { toHide = it },
                    label = { Text(stringResource(R.string.stories_add_hint)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { addHidden() }),
                    modifier = Modifier.weight(1f),
                )
                FilledTonalButton(onClick = addHidden, enabled = toHide.isNotBlank()) {
                    Text(stringResource(R.string.add))
                }
            }
        }
        Note(stringResource(R.string.stories_note))
    }
}
