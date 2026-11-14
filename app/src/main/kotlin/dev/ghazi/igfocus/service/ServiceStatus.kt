package dev.ghazi.igfocus.service

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import kotlinx.coroutines.flow.MutableSharedFlow

object ServiceStatus {
    /** Whether the service is switched on in Android's Accessibility settings. */
    fun isEnabledInSettings(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        val mine = ComponentName(context, FocusAccessibilityService::class.java)
        return enabled.split(':').any { ComponentName.unflattenFromString(it) == mine }
    }

    /**
     * "Turn off Unscroll" in the app: the running service switches itself off, exactly as the
     * switch in Android's Accessibility settings would. Turning it back on is done there.
     */
    val turnOffRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
}
