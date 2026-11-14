package com.luna.browser

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.luna.browser.ui.LunaApp

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = AppState(this)
        app.onExit = { finish() }
        app.registerNetwork()
        if (!handleIntent(intent)) app.launch()
        setContent { LunaApp(app) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?): Boolean {
        val data = i?.data
        if (i?.action == Intent.ACTION_VIEW && data != null) {
            app.openUrl(data.toString())
            return true
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (app.handleKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        super.onPause()
        if (app.screen == Screen.Browser) app.browser.webView.onPause()
    }

    override fun onResume() {
        super.onResume()
        if (app.screen == Screen.Browser) app.browser.webView.onResume()
    }

    override fun onDestroy() {
        app.destroy()
        super.onDestroy()
    }
}
