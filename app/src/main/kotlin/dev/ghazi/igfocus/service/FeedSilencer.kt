package dev.ghazi.igfocus.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.SystemClock
import android.util.Log
import dev.ghazi.igfocus.LOG_TAG

/**
 * Keeps hidden videos quiet. A video under the Home cover is hidden but still plays its sound.
 * Instagram asks for audio focus when it plays with sound and stops when another app takes focus,
 * so while a content cover is up this holds short-term focus. If Instagram takes it back to start
 * another video, focus is taken again, at most every [RETAKE_MS].
 *
 * Side effect: music from another app also pauses while the cover is up, and resumes after.
 * Call from the main thread only.
 */
class FeedSilencer(context: Context, private val handler: Handler) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private var holding = false
    private var lastTakeMs = 0L

    /** Retakes since the hold began, so two apps can never trade focus back and forth for long. */
    private var retakes = 0

    private val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                .build(),
        )
        .setOnAudioFocusChangeListener(::onFocusChange, handler)
        .build()

    private val retake = Runnable {
        if (!holding) return@Runnable
        retakes++
        take()
    }

    fun hold() {
        if (holding) return
        holding = true
        retakes = 0
        take()
    }

    fun release() {
        if (!holding) return
        holding = false
        handler.removeCallbacks(retake)
        try {
            audio.abandonAudioFocusRequest(request)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not give up audio focus", e)
        }
    }

    private fun take() {
        lastTakeMs = SystemClock.uptimeMillis()
        val result = try {
            audio.requestAudioFocus(request)
        } catch (e: Exception) {
            Log.w(LOG_TAG, "Could not ask for audio focus", e)
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        val outcome = when (result) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "granted"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "delayed"
            else -> "refused"
        }
        Log.i(LOG_TAG, "Hidden video sound: audio focus $outcome" + if (retakes > 0) " (retake $retakes)" else "")
    }

    /** A loss means another app, usually Instagram starting the next video, asked for focus. */
    private fun onFocusChange(change: Int) {
        if (!holding || change >= 0) return
        if (retakes >= MAX_RETAKES) return
        val wait = (lastTakeMs + RETAKE_MS - SystemClock.uptimeMillis()).coerceAtLeast(0)
        handler.removeCallbacks(retake)
        handler.postDelayed(retake, wait)
    }

    private companion object {
        const val RETAKE_MS = 300L
        const val MAX_RETAKES = 30
    }
}
