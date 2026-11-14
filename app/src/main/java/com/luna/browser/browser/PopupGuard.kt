package com.luna.browser.browser

import android.net.Uri

/**
 * Defences against the pop-up / pop-under / click-hijack tricks used by free streaming sites.
 * A TV has no tabs, so any "new window" would otherwise replace the film the viewer is watching.
 */
object PopupGuard {
    /** Sign-in providers that legitimately open in a new window from another site. */
    private val authHosts = listOf(
        "accounts.google.com", "facebook.com", "appleid.apple.com",
        "login.microsoftonline.com", "login.live.com", "id.zalo.me", "zalo.me",
    )
    private val secondLevel = setOf("com", "net", "org", "gov", "edu", "co", "ac", "info", "biz")

    /** Approximate registrable domain: "phim.abc.com.vn" -> "abc.com.vn", "www.vn2d.my" -> "vn2d.my". */
    fun siteOf(host: String): String {
        val p = host.lowercase().removePrefix("www.").split('.')
        if (p.size <= 2) return p.joinToString(".")
        val take = if (p[p.size - 2] in secondLevel && p.last().length == 2) 3 else 2
        return p.takeLast(take).joinToString(".")
    }

    fun sameSite(a: String, b: String): Boolean {
        val ha = Uri.parse(a).host ?: return true
        val hb = Uri.parse(b).host ?: return true
        return siteOf(ha) == siteOf(hb)
    }

    fun isAuth(url: String): Boolean {
        val h = Uri.parse(url).host?.lowercase() ?: return false
        return authHosts.any { h == it || h.endsWith(".$it") }
    }

    /** A new window may open only for the same site or a sign-in provider. */
    fun allowPopup(target: String, current: String) = sameSite(target, current) || isAuth(target)

    /**
     * Injected at document start: removes invisible full-screen click-catchers (blank iframes or
     * transparent layers with a huge z-index) that ad scripts lay over the video player.
     */
    const val OVERLAY_JS = """(function(){if(window.__lunaGuard)return;window.__lunaGuard=1;
function bad(e){try{var s=getComputedStyle(e);if(s.position!=='fixed'&&s.position!=='absolute')return false;
var z=parseInt(s.zIndex,10)||0;if(z<99999)return false;var r=e.getBoundingClientRect();
if(r.width*r.height<innerWidth*innerHeight*0.35)return false;
if(e.tagName==='IFRAME'){var src=e.getAttribute('src')||'';return src===''||src==='about:blank'||parseFloat(s.opacity)<0.1;}
if(parseFloat(s.opacity)<0.1)return true;var bg=s.backgroundColor;
var clear=(bg==='transparent'||bg==='rgba(0, 0, 0, 0)')&&s.backgroundImage==='none';
return clear&&!(e.innerText||'').trim()&&!e.querySelector('img,video,canvas,iframe');}catch(x){return false;}}
function sweep(){var list=[];if(document.body)list=Array.prototype.slice.call(document.body.children);
list=list.concat(Array.prototype.slice.call(document.querySelectorAll('iframe')),Array.prototype.slice.call(document.documentElement.children));
for(var i=0;i<list.length;i++){var e=list[i];if(e!==document.body&&e!==document.head&&bad(e))e.remove();}}
var t;var mo=new MutationObserver(function(){clearTimeout(t);t=setTimeout(sweep,120);});
function start(){sweep();mo.observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['style']});}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
setInterval(sweep,2000);})()"""
}
