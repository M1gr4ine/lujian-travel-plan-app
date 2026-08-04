package com.lujian.travelplan

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.lujian.travelplan.ui.LujianRoot
import com.lujian.travelplan.ui.theme.LujianTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val incomingUri = MutableStateFlow<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = true
            isAppearanceLightNavigationBars = true
        }
        window.decorView.post(::preferHighestRefreshRate)
        incomingUri.value = extractHtmlUri(intent)
        setContent {
            LujianTheme {
                LujianRoot(
                    graph = (application as LujianApplication).graph,
                    incomingUri = incomingUri,
                    reduceMotion = animationsDisabled(),
                    onIncomingUriHandled = { incomingUri.value = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingUri.value = extractHtmlUri(intent)
    }

    @Suppress("DEPRECATION")
    private fun preferHighestRefreshRate() {
        val targetDisplay = windowManager.defaultDisplay
        val currentMode = targetDisplay.mode
        val preferredModeId = DisplayRefreshPolicy.preferredModeId(
            currentWidth = currentMode.physicalWidth,
            currentHeight = currentMode.physicalHeight,
            modes = targetDisplay.supportedModes.map { mode ->
                DisplayModeSpec(mode.modeId, mode.physicalWidth, mode.physicalHeight, mode.refreshRate)
            },
        ) ?: return

        val attributes = window.attributes
        if (attributes.preferredDisplayModeId != preferredModeId) {
            attributes.preferredDisplayModeId = preferredModeId
            window.attributes = attributes
        }
    }

    private fun extractHtmlUri(intent: Intent?): Uri? = when (intent?.action) {
        Intent.ACTION_SEND -> if (Build.VERSION.SDK_INT >= 33) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }
        Intent.ACTION_VIEW -> intent.data
        else -> null
    }

    private fun animationsDisabled(): Boolean = runCatching {
        Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}
