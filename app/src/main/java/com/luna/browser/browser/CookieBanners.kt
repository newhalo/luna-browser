package com.luna.browser.browser

/**
 * Hides cookie-consent banners and dialogs, which cover the video and are hard to reach with a pointer.
 *
 * Nothing is clicked and nothing is accepted: the banner is only hidden, so the site gets no consent (for most sites that
 * means only the cookies they need). Pages that lock scrolling while their dialog is open are unlocked again.
 * Setting: `K.COOKIE_BANNERS` (on by default).
 */
object CookieBanners {
    /** The containers of the common consent tools (OneTrust, Cookiebot, Usercentrics, Didomi, Quantcast, …) and generic names. */
    val SELECTORS = listOf(
        "#onetrust-consent-sdk", "#onetrust-banner-sdk", "#CybotCookiebotDialog", "#CybotCookiebotDialogBodyUnderlay",
        "#usercentrics-root", "#usercentrics-cmp-ui", "#didomi-host", "#truste-consent-track", "#consent_blackbar",
        ".truste_overlay", ".truste_box_overlay", "#cookie-law-info-bar", "#cmplz-cookiebanner-container", ".cmplz-cookiebanner",
        "#cookie-notice", ".cookie-notice-container", "#cookieConsent", "#cookie-consent", ".cookie-consent-banner",
        "#cookie-banner", ".cookie-banner", "#cookiebanner", ".cookiebanner", ".cc-window", ".cc-banner", ".cky-consent-container",
        ".cky-overlay", "#qc-cmp2-container", ".qc-cmp2-container", ".fc-consent-root", "[id^=sp_message_container_]",
        "#gdpr-cookie-message", ".osano-cm-window", "#hs-eu-cookie-confirmation", "#BorlabsCookieBox",
        "#moove_gdpr_cookie_info_bar", ".termly-styles-root", "#iubenda-cs-banner", "#axeptio_overlay", "#tarteaucitronRoot",
        "#klaro", ".cookiefirst-root", "#cookiescript_injected", "#_evidon_banner", ".evidon-banner", "#cmpbox", "#cmpbox2",
        ".cmp-root", "#cmp-app-container",
    )

    /** Classes some tools put on <html> / <body> to stop the page from scrolling while their dialog is open. */
    private val LOCK_CLASSES = listOf("didomi-popup-open", "sp-message-open", "qc-cmp-ui-showing", "cmp-open", "ot-overflow-hidden")

    val JS: String = run {
        val sel = SELECTORS.joinToString(",")
        val css = "$sel{display:none!important;visibility:hidden!important;pointer-events:none!important}"
        val locks = LOCK_CLASSES.joinToString(",") { "'$it'" }
        """(function(){if(window.__lunaCk)return;window.__lunaCk=1;
var s=document.createElement('style');s.id='__luna_ck';s.textContent=${quote(css)};(document.head||document.documentElement).appendChild(s);
function unlock(){var b=document.body,h=document.documentElement;if(!b||!document.querySelector(${quote(sel)}))return;
[$locks].forEach(function(c){b.classList.remove(c);h.classList.remove(c);});
[h,b].forEach(function(e){if(getComputedStyle(e).overflowY==='hidden'){e.style.setProperty('overflow','auto','important');}});}
[600,1500,3000,6000].forEach(function(t){setTimeout(unlock,t);});})()"""
    }

    /** A JavaScript string literal (the selectors hold no line breaks). */
    private fun quote(s: String) = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\""
}
