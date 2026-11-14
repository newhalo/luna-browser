// Runs inside the page (via cdp.js). Reports what an ad blocker cares about, as JSON.
(function () {
  var vw = innerWidth, vh = innerHeight, adSlots = [], overlays = 0, banners = 0;
  // Standard IAB ad sizes: a visible iframe/img of exactly this size is very likely an ad.
  var sizes = [[728, 90], [300, 250], [320, 50], [970, 250], [970, 90], [160, 600], [300, 600], [468, 60], [320, 100], [336, 280]];
  var all = document.querySelectorAll("iframe,img,ins,div,a");
  for (var i = 0; i < all.length && i < 8000; i++) {
    var e = all[i], r = e.getBoundingClientRect();
    if (r.width < 20 || r.height < 20) continue;
    var s = getComputedStyle(e);
    if (s.display === "none" || s.visibility === "hidden" || parseFloat(s.opacity) < 0.05) continue;
    var z = parseInt(s.zIndex, 10) || 0;
    // Invisible click-catchers (Luna's guard should have removed them)
    if ((s.position === "fixed" || s.position === "absolute") && z >= 99999 && r.width * r.height > vw * vh * 0.35) overlays++;
    // "Catfish" banners stuck to the top/bottom edge
    if (s.position === "fixed" && r.height < 220 && r.width > vw * 0.4 && (r.bottom >= vh - 4 || r.top <= 4) && e.querySelector("img,iframe,a")) banners++;
    if (e.tagName === "IFRAME" || e.tagName === "IMG" || e.tagName === "INS") {
      for (var k = 0; k < sizes.length; k++) {
        if (Math.abs(r.width - sizes[k][0]) < 6 && Math.abs(r.height - sizes[k][1]) < 6) {
          adSlots.push(e.tagName + " " + Math.round(r.width) + "x" + Math.round(r.height) + " " + (e.src || "").slice(0, 90));
          break;
        }
      }
    }
  }
  var own = location.hostname.split(".").slice(-2).join("."), third = {};
  performance.getEntriesByType("resource").forEach(function (x) {
    try { var h = new URL(x.name).hostname; if (h.indexOf(own) < 0) third[h] = (third[h] || 0) + 1; } catch (err) {}
  });
  return JSON.stringify({
    url: location.href,
    title: document.title.slice(0, 70),
    guard: !!window.__lunaGuard,
    bridge: !!window.__lunaBridge,
    cosmetic: !!document.getElementById("__luna_cos"),
    videos: document.querySelectorAll("video").length,
    iframes: document.querySelectorAll("iframe").length,
    overlays: overlays,
    fixedBanners: banners,
    adSlots: adSlots.slice(0, 8),
    thirdParty: Object.keys(third).sort(function (a, b) { return third[b] - third[a]; }).slice(0, 12),
  });
})()
