package dev.ghazi.igfocus.tile

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import dev.ghazi.igfocus.R
import dev.ghazi.igfocus.policy.Pause
import dev.ghazi.igfocus.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Quick Settings tile. Lit means Unscroll is on. One tap pauses for [TILE_PAUSE_MINUTES];
 * a tap while paused resumes. Longer pauses are chosen in the app.
 */
class PauseTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var listening: Job? = null

    override fun onStartListening() {
        listening?.cancel()
        listening = scope.launch {
            SettingsStore(applicationContext).pauseUntilMs.collect(::render)
        }
    }

    override fun onStopListening() {
        listening?.cancel()
        listening = null
    }

    override fun onClick() {
        scope.launch {
            val store = SettingsStore(applicationContext)
            val now = System.currentTimeMillis()
            val paused = Pause.isPaused(store.pauseUntilMs.first(), now)
            store.setPauseUntil(if (paused) Pause.NOT_PAUSED else Pause.until(now, TILE_PAUSE_MINUTES))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun render(pauseUntilMs: Long) {
        val tile = qsTile ?: return
        val paused = Pause.isPaused(pauseUntilMs, System.currentTimeMillis())
        tile.state = if (paused) Tile.STATE_INACTIVE else Tile.STATE_ACTIVE
        tile.label = getString(R.string.tile_label)
        tile.subtitle = when {
            !paused -> getString(R.string.tile_on)
            pauseUntilMs == Pause.UNTIL_RESUMED -> getString(R.string.tile_paused)
            else -> getString(
                R.string.tile_paused_until,
                DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(pauseUntilMs)),
            )
        }
        tile.updateTile()
    }

    companion object {
        const val TILE_PAUSE_MINUTES = 15
    }
}
