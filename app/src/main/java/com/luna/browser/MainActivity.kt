package com.luna.browser

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import com.luna.browser.ui.LunaApp

class MainActivity : ComponentActivity() {
    private lateinit var app: AppState

    private val speech = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        val text = r.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!text.isNullOrBlank()) app.openUrl(text)
    }

    private fun voiceIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_WEB_SEARCH)
        .putExtra(RecognizerIntent.EXTRA_LANGUAGE, app.locale.toLanguageTag())
        .putExtra(RecognizerIntent.EXTRA_PROMPT, app.s(R.string.voice_prompt))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        app = AppState(this)
        app.onExit = { finish() }
        app.voiceAvailable = packageManager.resolveActivity(voiceIntent(), 0) != null
        app.startVoice = {
            try {
                speech.launch(voiceIntent())
            } catch (e: ActivityNotFoundException) {
                app.toast(R.string.toast_no_voice)
            }
        }
        app.registerNetwork()
        if (!handleIntent(intent)) app.launch()
        setContent { LunaApp(app) }
        // Screens paint their own opaque background; drop the window's to save a full-screen pass.
        window.decorView.post { window.setBackgroundDrawable(null) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?): Boolean {
        val data = i?.data
        // Only web addresses: another app must not be able to point the browser at file:// or content:// URIs.
        if (i?.action == Intent.ACTION_VIEW && data != null && (data.scheme == "http" || data.scheme == "https")) {
            app.openUrl(data.toString())
            return true
        }
        return false
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        com.luna.browser.ui.components.Interaction.touch()
        if (app.handleKey(event)) return true
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        app.foreground = true
    }

    override fun onStop() {
        app.foreground = false
        super.onStop()
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
