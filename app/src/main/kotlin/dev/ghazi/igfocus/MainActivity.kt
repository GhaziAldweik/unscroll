package dev.ghazi.igfocus

import android.os.Bundle
import android.os.PowerManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.ghazi.igfocus.service.ServiceStatus
import dev.ghazi.igfocus.ui.FocusApp
import dev.ghazi.igfocus.ui.FocusTheme

class MainActivity : ComponentActivity() {

    private var enabledInSettings by mutableStateOf(false)
    private var batteryUnrestricted by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FocusTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FocusApp(enabledInSettings = enabledInSettings, batteryUnrestricted = batteryUnrestricted)
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        // Re-read on every return, since both are changed in Android's Settings app.
        enabledInSettings = ServiceStatus.isEnabledInSettings(this)
        // "Unrestricted" battery use is Android's battery-optimisation exemption. Reading it
        // needs no permission.
        batteryUnrestricted = getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(packageName)
    }
}
