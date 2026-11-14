package dev.ghazi.igfocus.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dev.ghazi.igfocus.policy.BlockingSettings
import dev.ghazi.igfocus.policy.Pause
import dev.ghazi.igfocus.policy.StoriesRule
import dev.ghazi.igfocus.stats.StepIns
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore by preferencesDataStore(name = "settings")

data class UserSettings(
    val inspectorEnabled: Boolean = false,
    /** Four switches; the Reels switch covers both reel rules. */
    val blocking: BlockingSettings = BlockingSettings(),
    val storiesOn: Boolean = true,
    /** Normalized usernames (see [StoriesRule.normalize]). Kept when [storiesOn] is toggled. */
    val hiddenUsernames: Set<String> = emptySet(),
    val ownUsername: String = "",
    val pauseUntilMs: Long = Pause.NOT_PAUSED,
    /** The first-run introduction has been seen or skipped. */
    val introDone: Boolean = false,
    /** Exact daily counts of the times Unscroll stepped in; see [StepIns]. */
    val stepIns: StepIns = StepIns.EMPTY,
)

/** All user settings, stored on the phone only. */
class SettingsStore(context: Context) {
    private val dataStore = context.applicationContext.dataStore

    val settings: Flow<UserSettings> = dataStore.data
        // A read error falls back to defaults rather than crashing the service.
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map {
            UserSettings(
                inspectorEnabled = it[INSPECTOR_ENABLED] ?: false,
                blocking = BlockingSettings(
                    reelsTab = it[BLOCK_REELS_TAB] ?: false,
                    // One "Reels" switch: the Reels tab and reels opened from Explore, search or
                    // profiles are the same thing to the user, and either alone leaves a way in.
                    otherReels = it[BLOCK_REELS_TAB] ?: false,
                    searchResults = it[BLOCK_SEARCH_RESULTS] ?: false,
                    homeFeed = it[COVER_HOME_FEED] ?: false,
                    explore = it[COVER_EXPLORE] ?: false,
                    openOnMessages = it[OPEN_ON_MESSAGES] ?: true,
                ),
                storiesOn = it[STORIES_ON] ?: true,
                hiddenUsernames = it[HIDDEN_USERNAMES] ?: emptySet(),
                ownUsername = it[OWN_USERNAME] ?: "",
                pauseUntilMs = it[PAUSE_UNTIL_MS] ?: Pause.NOT_PAUSED,
                introDone = it[INTRO_DONE] ?: false,
                stepIns = StepIns.decode(it[STEP_INS]),
            )
        }

    val inspectorEnabled: Flow<Boolean> = settings.map { it.inspectorEnabled }.distinctUntilChanged()

    val pauseUntilMs: Flow<Long> = settings.map { it.pauseUntilMs }.distinctUntilChanged()

    suspend fun setInspectorEnabled(enabled: Boolean) {
        dataStore.edit { it[INSPECTOR_ENABLED] = enabled }
    }

    suspend fun setBlockReelsTab(enabled: Boolean) {
        dataStore.edit { it[BLOCK_REELS_TAB] = enabled }
    }

    suspend fun setBlockSearchResults(enabled: Boolean) {
        dataStore.edit { it[BLOCK_SEARCH_RESULTS] = enabled }
    }

    suspend fun setCoverHomeFeed(enabled: Boolean) {
        dataStore.edit { it[COVER_HOME_FEED] = enabled }
    }

    suspend fun setCoverExplore(enabled: Boolean) {
        dataStore.edit { it[COVER_EXPLORE] = enabled }
    }

    /** Only the switch changes. The hidden list is a separate key and is left alone. */
    suspend fun setStoriesOn(on: Boolean) {
        dataStore.edit { it[STORIES_ON] = on }
    }

    suspend fun setOwnUsername(username: String) {
        dataStore.edit { it[OWN_USERNAME] = StoriesRule.normalize(username) }
    }

    suspend fun addHiddenUsername(username: String) {
        val name = StoriesRule.normalize(username)
        if (name.isEmpty()) return
        dataStore.edit { it[HIDDEN_USERNAMES] = (it[HIDDEN_USERNAMES] ?: emptySet()) + name }
    }

    suspend fun removeHiddenUsername(username: String) {
        dataStore.edit { it[HIDDEN_USERNAMES] = (it[HIDDEN_USERNAMES] ?: emptySet()) - username }
    }

    suspend fun setPauseUntil(pauseUntilMs: Long) {
        dataStore.edit { it[PAUSE_UNTIL_MS] = pauseUntilMs }
    }

    suspend fun setOpenOnMessages(enabled: Boolean) {
        dataStore.edit { it[OPEN_ON_MESSAGES] = enabled }
    }

    /** Read and written in one transaction, so two quick step-ins never lose one. */
    suspend fun addStepIn(epochDay: Long) {
        dataStore.edit { it[STEP_INS] = StepIns.decode(it[STEP_INS]).plusOne(epochDay).encode() }
    }

    suspend fun setIntroDone(done: Boolean) {
        dataStore.edit { it[INTRO_DONE] = done }
    }

    /**
     * Back to the defaults: every rule off, Stories on, the hidden list and username cleared, not
     * paused. The introduction is not shown again. Inspector captures are a separate store and stay.
     */
    suspend fun resetAll() {
        dataStore.edit {
            it.clear()
            it[INTRO_DONE] = true
        }
    }

    private companion object {
        val INSPECTOR_ENABLED = booleanPreferencesKey("inspector_enabled")
        val BLOCK_REELS_TAB = booleanPreferencesKey("block_reels_tab")
        val BLOCK_SEARCH_RESULTS = booleanPreferencesKey("block_search_results")
        val COVER_HOME_FEED = booleanPreferencesKey("cover_home_feed")
        val COVER_EXPLORE = booleanPreferencesKey("cover_explore")
        val STORIES_ON = booleanPreferencesKey("stories_on")
        val HIDDEN_USERNAMES = stringSetPreferencesKey("hidden_usernames")
        val OWN_USERNAME = stringPreferencesKey("own_username")
        val PAUSE_UNTIL_MS = longPreferencesKey("pause_until_ms")
        val OPEN_ON_MESSAGES = booleanPreferencesKey("open_on_messages")
        val INTRO_DONE = booleanPreferencesKey("intro_done")
        val STEP_INS = stringPreferencesKey("step_ins")
    }
}
