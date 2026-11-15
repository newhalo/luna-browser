package com.luna.browser.browser

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import androidx.webkit.Profile
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.luna.browser.data.ProfileRules

/**
 * Keeps each Luna profile's cookies and site data apart, so family members don't share sign-ins. This needs a recent
 * system WebView (the multi-profile feature); on an older one every profile shares the same cookies and
 * [supported] is false, which the profile screen says plainly.
 */
object WebProfiles {
    fun supported(): Boolean = runCatching { WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE) }.getOrDefault(false)

    /** The web engine's name for a profile: the original data stays in the default one. */
    fun nameFor(profileId: String): String = if (profileId == ProfileRules.DEFAULT) Profile.DEFAULT_PROFILE_NAME else "luna_$profileId"

    /** Must be called right after the WebView is created, before it loads anything. */
    fun apply(webView: WebView, profileId: String) {
        if (!supported()) return
        runCatching { WebViewCompat.setProfile(webView, nameFor(profileId)) }
    }

    fun cookies(profileId: String): CookieManager {
        if (supported()) runCatching { ProfileStore.getInstance().getOrCreateProfile(nameFor(profileId)).cookieManager }.getOrNull()?.let { return it }
        return CookieManager.getInstance()
    }

    fun storage(profileId: String): WebStorage {
        if (supported()) runCatching { ProfileStore.getInstance().getOrCreateProfile(nameFor(profileId)).webStorage }.getOrNull()?.let { return it }
        return WebStorage.getInstance()
    }

    /** Erases a profile's web data (a deleted profile, or the guest at the end of its session). Never the default one. */
    fun delete(profileId: String) {
        if (profileId == ProfileRules.DEFAULT || !supported()) return
        runCatching { ProfileStore.getInstance().deleteProfile(nameFor(profileId)) }
    }
}
