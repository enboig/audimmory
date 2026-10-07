package org.audimmory.mobile

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.AndroidEntryPoint
import org.audimmory.mobile.data.local.PlayerSettings
import org.audimmory.mobile.data.local.PlayerSettingsStore
import org.audimmory.mobile.ui.navigation.AppNavigation
import org.audimmory.mobile.ui.theme.AudimmoryTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var playerSettingsStore: PlayerSettingsStore

    private val requestNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        enableEdgeToEdge()
        setContent {
            val settings = playerSettingsStore.settings.collectAsStateWithLifecycle(PlayerSettings()).value

            AudimmoryTheme(themeMode = settings.themeMode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    AppNavigation()
                }
            }
        }
    }

    // The media notification (lock-screen controls) needs POST_NOTIFICATIONS on
    // Android 13+. Requested once at launch; playback still works if denied,
    // just without the notification UI.
    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted =
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS,
                ) == PackageManager.PERMISSION_GRANTED
            if (!granted) requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
