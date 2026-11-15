package com.luna.browser.browser

import android.net.Uri

/**
 * Defences against the pop-up / pop-under / click-hijack tricks used by free streaming sites.
 * A TV has no tabs, so any "new window" would otherwise replace the video the viewer is watching.
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
     * Injected into every frame at document start. Lets the remote control a video that lives in a
     * cross-site player iframe (common on video sites): the top page relays commands down to every
     * frame with postMessage, and the frame that owns the biggest video reports its state back up.
     * State array: [paused, position, duration, buffering, failed, area].
     */
    const val VIDEO_BRIDGE_JS = """(function(){if(window.__lunaBridge)return;window.__lunaBridge=1;
function best(){var vs=document.getElementsByTagName('video'),b=null,ba=-1;for(var i=0;i<vs.length;i++){var r=vs[i].getBoundingClientRect(),a=r.width*r.height;if(a>ba){ba=a;b=vs[i];}}return b;}
function st(b){var r=b.getBoundingClientRect();return [b.paused,b.currentTime||0,isFinite(b.duration)?b.duration:0,(b.readyState<3&&!b.paused&&!b.ended),!!b.error,r.width*r.height];}
function act(b,a,v){if(a==='toggle'){if(b.paused){var p=b.play();if(p&&p.catch)p.catch(function(){});}else b.pause();}
else if(a==='play'){var q=b.play();if(q&&q.catch)q.catch(function(){});}else if(a==='pause'){b.pause();}
else if(a==='seek'){b.currentTime=Math.max(0,Math.min(isFinite(b.duration)?b.duration:1e9,b.currentTime+v));}}
function relay(m){for(var i=0;i<window.frames.length;i++){try{window.frames[i].postMessage(m,'*');}catch(e){}}}
window.__lunaCmd=function(a,v){var b=best();var s=null;if(b){act(b,a,v);s=st(b);}relay({__luna:1,a:a,v:v});return s||window.__lunaRemote||null;};
window.addEventListener('message',function(e){var d=e.data;if(!d||!d.__luna)return;
if(d.a){var b=best();if(b){act(b,d.a,d.v);try{window.top.postMessage({__luna:1,s:st(b)},'*');}catch(x){}}relay(d);}
else if(d.s&&window===window.top){var c=window.__lunaRemote;if(!c||d.s[5]>=c[5]*0.5)window.__lunaRemote=d.s;}});})()"""

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
var abRe=/(t[ắa]t|v[ôo]\s*hi[ệe]u\s*h[óo]a|g[ỡo]\s*b[ỏo])\s*(tr[ìi]nh\s*)?(ch[ặa]n\s*qu[ảa]ng\s*c[áa]o|ad\s*-?block)|disable\s+(your\s+)?ad\s*-?block|turn\s+off\s+(your\s+)?ad\s*-?block|ad\s*-?block(er)?\s+(detected|is\s+enabled)|whitelist\s+(this|our)\s+site|desactiv\w*\s+(tu\s+|su\s+)?(bloqueador|adblock)|d[ée]sactiv\w*\s+(votre\s+)?(bloqueur|adblock)|(werbeblocker|adblocker)\s+(deaktivier|ausschalt)|desativ\w*\s+(o\s+)?(bloqueador|adblock)|(отключ|выключ)\w*\s+(блокировщик|adblock)|广告拦截|广告屏蔽|広告ブロック|광고\s*차단/i;
function antiAdblock(){if(!document.body)return;var found=false;
var cand=document.querySelectorAll('body > *, body > * > *, [class*=modal], [id*=modal], [class*=popup], [id*=popup], [class*=adblock], [id*=adblock]');
for(var i=0;i<cand.length;i++){var e=cand[i];if(!e.isConnected)continue;var s=getComputedStyle(e);
if(s.position!=='fixed'&&s.position!=='absolute')continue;if((parseInt(s.zIndex,10)||0)<10)continue;
var t=e.innerText||'';if(t.length<800&&abRe.test(t)){e.remove();found=true;}}
if(!found)return;
var top=document.body.children;for(var j=top.length-1;j>=0;j--){var b=top[j],bs=getComputedStyle(b),br=b.getBoundingClientRect();
if(bs.position==='fixed'&&br.width*br.height>innerWidth*innerHeight*0.8&&!(b.innerText||'').trim())b.remove();}
document.documentElement.style.setProperty('overflow','auto','important');document.body.style.setProperty('overflow','auto','important');}
function sweep(){antiAdblock();var list=[];if(document.body)list=Array.prototype.slice.call(document.body.children);
list=list.concat(Array.prototype.slice.call(document.querySelectorAll('iframe')),Array.prototype.slice.call(document.documentElement.children));
for(var i=0;i<list.length;i++){var e=list[i];if(e!==document.body&&e!==document.head&&bad(e))e.remove();}}
var t;var mo=new MutationObserver(function(){clearTimeout(t);t=setTimeout(sweep,120);});
function start(){sweep();mo.observe(document.documentElement,{childList:true,subtree:true,attributes:true,attributeFilter:['style']});}
if(document.readyState==='loading')document.addEventListener('DOMContentLoaded',start);else start();
setInterval(sweep,2000);})()"""
}
