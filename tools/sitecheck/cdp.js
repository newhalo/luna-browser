// Evaluates JavaScript in the page open in Luna on the TV, via Chrome DevTools Protocol.
// Usage: node cdp.js <expression | path/to/file.js>
// Needs: adb forward tcp:9222 localabstract:webview_devtools_remote_<pid>  (sitecheck.ps1 does this)
const fs = require("fs");
const arg = process.argv[2] || "location.href";
const expr = fs.existsSync(arg) ? fs.readFileSync(arg, "utf8") : arg;

(async () => {
  let list;
  try {
    list = await (await fetch("http://127.0.0.1:9222/json")).json();
  } catch (e) {
    console.log(JSON.stringify({ error: "devtools not reachable: " + e.message }));
    return;
  }
  const page = list.find((p) => p.type === "page" && p.url.startsWith("http")) || list[0];
  if (!page) { console.log(JSON.stringify({ error: "no page" })); return; }
  const ws = new WebSocket(page.webSocketDebuggerUrl);
  const timer = setTimeout(() => { console.log(JSON.stringify({ error: "timeout" })); process.exit(0); }, 15000);
  ws.onopen = () => ws.send(JSON.stringify({ id: 1, method: "Runtime.evaluate", params: { expression: expr, returnByValue: true, awaitPromise: true } }));
  ws.onmessage = (m) => {
    const r = JSON.parse(m.data);
    if (r.id !== 1) return;
    clearTimeout(timer);
    const v = r.result && r.result.result;
    const out = v && v.value !== undefined ? v.value : r;
    console.log(typeof out === "string" ? out : JSON.stringify(out));
    ws.close();
  };
  ws.onerror = (e) => console.log(JSON.stringify({ error: "ws " + (e.message || "error") }));
})();
