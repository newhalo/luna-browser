package com.luna.browser.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import com.luna.browser.ui.theme.canvasDensity
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.luna.browser.AppState
import com.luna.browser.Screen
import com.luna.browser.data.K
import com.luna.browser.ui.browser.BrowserScreen
import com.luna.browser.ui.components.ToastHost
import com.luna.browser.ui.components.Txt
import com.luna.browser.ui.components.MoonLogo
import com.luna.browser.ui.components.Aurora
import androidx.compose.ui.unit.sp
import com.luna.browser.ui.home.AddressDialog
import com.luna.browser.ui.home.HomeScreen
import com.luna.browser.ui.library.FavoritesScreen
import com.luna.browser.ui.library.HistoryScreen
import com.luna.browser.ui.profiles.ProfilesScreen
import com.luna.browser.ui.settings.SettingsScreen
import com.luna.browser.ui.tabs.TabsScreen
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.LunaTheme
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextSecondary
import com.luna.browser.ui.theme.ms
import androidx.compose.ui.graphics.graphicsLayer

@Composable
fun LunaApp(app: AppState) {
    // Compose reads strings through LocalContext, so providing the language-aware context here makes
    // stringResource() follow the chosen language and switch live when it changes.
    val lctx = app.ctx
    // Keep the UI the same physical size on any TV: lay out on a 960 dp canvas whatever the density setting.
    val base = LocalDensity.current
    val config = LocalConfiguration.current
    val density = canvasDensity(config.screenWidthDp * base.density, config.screenHeightDp * base.density)
    CompositionLocalProvider(
        LocalContext provides lctx,
        LocalConfiguration provides lctx.resources.configuration,
        LocalDensity provides Density(density, base.fontScale),
    ) {
    LunaTheme(reduceMotion = app.store.bool(K.REDUCE_MOTION)) {
        // No in-app splash: Android 12+ already shows the system splash while the process starts,
        // and a second timed splash only made start-up feel slower. Home fades in instead.
        var shown by remember { mutableStateOf(false) }
        LaunchedEffect(Unit) { shown = true }
        val alpha by androidx.compose.animation.core.animateFloatAsState(if (shown) 1f else 0f, tween(ms(220)), label = "enter")
        // No background here: every screen paints its own opaque one (an extra full-screen
        // layer costs this TV's GPU ~5 ms per frame).
        Box(Modifier.fillMaxSize().graphicsLayer { this.alpha = alpha }) {
            when (app.screen) {
                Screen.Home -> HomeScreen(app)
                Screen.Browser -> BrowserScreen(app)
                Screen.Settings -> SettingsScreen(app)
                Screen.History -> HistoryScreen(app)
                Screen.Favorites -> FavoritesScreen(app)
                Screen.Tabs -> TabsScreen(app)
                Screen.Profiles -> ProfilesScreen(app)
            }
            if (app.addressOpen) AddressDialog(app)
            ToastHost(app, Modifier.align(Alignment.BottomCenter))
        }
    }
    }
}
