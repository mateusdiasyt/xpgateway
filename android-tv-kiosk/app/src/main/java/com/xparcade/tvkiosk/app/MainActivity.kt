package com.xparcade.tvkiosk.app

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.xparcade.tvkiosk.domain.state.AppState
import com.xparcade.tvkiosk.ui.theme.XPArcadeTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: KioskViewModel by viewModels()
    private var backgroundedSessionId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()
        observeSessionVisibility()

        setContent {
            XPArcadeTheme {
                KioskApp(viewModel = viewModel)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshLauncherStatus()
        viewModel.refreshAccessibilityGuardStatus()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            enableImmersiveMode()
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            viewModel.onRemoteKeyDown(event.keyCode)

            if (event.keyCode == KeyEvent.KEYCODE_BACK && viewModel.shouldBlockBack()) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun observeSessionVisibility() {
        lifecycleScope.launch {
            viewModel.uiState.collect { state ->
                val activeSessionId = state.activeSession?.sessionId
                val shouldReleaseScreen =
                    state.appState == AppState.SESSION_ACTIVE ||
                        state.appState == AppState.SESSION_WARNING

                if (shouldReleaseScreen && activeSessionId != null && backgroundedSessionId != activeSessionId) {
                    backgroundedSessionId = activeSessionId
                    viewModel.openConsoleInputForActiveSession()
                }

                if (!shouldReleaseScreen) {
                    backgroundedSessionId = null
                }
            }
        }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }
}
