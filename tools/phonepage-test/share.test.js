const fs = require('fs');
const { JSDOM } = require('jsdom');

const kt = fs.readFileSync(require('path').join(__dirname, '..', '..', 'app', 'src', 'main', 'java', 'com', 'luna', 'browser', 'remote', 'PhoneRemote.kt'), 'utf8');
let html = kt.slice(kt.indexOf('private val PAGE = """') + 'private val PAGE = """'.length);
html = html.slice(0, html.indexOf('""".replace("__LABELS__"'));
const labels = {};
html = html.replace(/\{\{(\w+)\}\}/g, (m, k) => { labels[k] = 'T-' + k; return k === 'labels' ? '__L__' : 'T-' + k; });
html = html.replace('__LABELS__', JSON.stringify(labels));

function run(hash, pin, fetchOk = true) {
  return new Promise((resolve) => {
    const calls = [];
    const dom = new JSDOM(html, {
      url: 'http://192.168.1.20:8765/' + hash,
      runScripts: 'dangerously',
      pretendToBeVisual: true,
      beforeParse(w) {
        if (pin) w.localStorage.setItem('luna.pin', pin);
        w.fetch = (u, o) => { calls.push(o.body); return Promise.resolve({ status: 200, json: () => Promise.resolve({ ok: fetchOk }) }); };
        w.document.execCommand = () => true;
      },
    });
    setTimeout(() => resolve({ dom, calls }), 150);
  });
}

(async () => {
  let fails = 0;
  const check = (c, m) => { if (!c) { fails++; console.log('FAIL', m); } else console.log('ok  ', m); };

  // 1. paired phone opens a shared link: card appears, nothing is sent until the tap
  let { dom, calls } = await run('#open=https%3A%2F%2Fexample.com%2Fwatch%3Fv%3D1', '123456');
  let d = dom.window.document;
  check(!d.getElementById('share').hidden, 'share card shown when paired');
  check(d.getElementById('shareUrl').textContent === 'https://example.com/watch?v=1', 'full address displayed');
  check(!calls.some(b => b.includes('act=open')), 'nothing sent before the tap');
  check(dom.window.location.hash === '', 'fragment removed from the address bar');
  d.getElementById('shareGo').click();
  await new Promise(r => setTimeout(r, 50));
  const sent = calls.find(b => b.includes('act=open'));
  check(sent && sent.includes('pin=123456') && sent.includes(encodeURIComponent('https://example.com/watch?v=1')), 'tap sends act=open with the address');
  check(d.getElementById('share').hidden, 'card closes after sending');

  // 2. dismiss
  ({ dom, calls } = await run('#open=https%3A%2F%2Fexample.com%2F', '123456'));
  d = dom.window.document;
  d.getElementById('shareNo').click();
  check(d.getElementById('share').hidden && !calls.some(b => b.includes('act=open')), '"Not now" sends nothing');

  // 3. non-web schemes are ignored
  for (const bad of ['javascript%3Aalert(1)', 'file%3A%2F%2F%2Fsdcard%2Fx', 'intent%3A%2F%2Fx', '%E0%A4%A']) {
    ({ dom, calls } = await run('#open=' + bad, '123456'));
    check(dom.window.document.getElementById('share').hidden, 'ignored: ' + bad.slice(0, 24));
  }

  // 4. not paired: card waits until the PIN is entered
  ({ dom, calls } = await run('#open=https%3A%2F%2Fexample.com%2F', null));
  d = dom.window.document;
  check(d.getElementById('share').hidden && !d.getElementById('pair').hidden, 'unpaired: pairing shown first, card hidden');

  // 5. QR pairing fragment still works together with a link
  ({ dom, calls } = await run('#pin=654321&open=https%3A%2F%2Fexample.org%2F', null));
  d = dom.window.document;
  check(calls.some(b => b.includes('pin=654321')), 'pin from fragment used');
  check(!d.getElementById('share').hidden, 'link kept after pairing via fragment');

  // 6. bookmarklet text
  ({ dom, calls } = await run('', '123456'));
  d = dom.window.document;
  let copied = null;
  d.execCommand = (c) => { const t = d.querySelector('textarea[style*="fixed"]'); copied = t && t.value; return true; };
  d.getElementById('bmcopy').click();
  check(copied && copied.startsWith("javascript:location.href='http://192.168.1.20:8765/#open='+encodeURIComponent(location.href)"), 'bookmarklet points back to this TV page: ' + copied);

  console.log(fails ? fails + ' FAILED' : 'ALL PASSED');
  process.exit(fails ? 1 : 0);
})();
