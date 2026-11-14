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
import com.luna.browser.ui.settings.SettingsScreen
import com.luna.browser.ui.theme.Bg
import com.luna.browser.ui.theme.LunaTheme
import com.luna.browser.ui.theme.T
import com.luna.browser.ui.theme.TextSecondary
import com.luna.browser.ui.theme.ms
import kotlinx.coroutines.delay

@Composable
fun LunaApp(app: AppState) {
    LunaTheme(reduceMotion = app.store.bool(K.REDUCE_MOTION)) {
        var splash by remember { mutableStateOf(true) }
        LaunchedEffect(Unit) {
            delay(900)
            splash = false
        }
        Box(Modifier.fillMaxSize().background(Bg)) {
            when (app.screen) {
                Screen.Home -> HomeScreen(app)
                Screen.Browser -> BrowserScreen(app)
                Screen.Settings -> SettingsScreen(app)
                Screen.History -> HistoryScreen(app)
                Screen.Favorites -> FavoritesScreen(app)
            }
            if (app.addressOpen) AddressDialog(app)
            ToastHost(app, Modifier.align(Alignment.BottomCenter))
            AnimatedVisibility(visible = splash, exit = fadeOut(tween(ms(250)))) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Aurora()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MoonLogo(Modifier.size(104.dp))
                        Txt("Luna", T.display.copy(fontSize = 56.sp), Modifier.padding(top = 18.dp))
                        Txt("Lướt web thoải mái trên TV", T.body, color = TextSecondary)
                    }
                }
            }
        }
    }
}
