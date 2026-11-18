// Summarises `dumpsys gfxinfo <pkg> framestats`: UI-thread, GPU and frame-to-frame timing.
// The same numbers as frames.py, for machines with Node but no Python.
// Usage: node frames.js framestats.txt
const fs = require('fs');
const txt = fs.readFileSync(process.argv[2], 'utf8').replace(/^﻿/, '');
const blocks = txt.split('---PROFILEDATA---');
const rows = [];
let hdr = null;
for (let i = 1; i < blocks.length; i += 2) {
  for (let line of blocks[i].trim().split(/\r?\n/)) {
    line = line.trim().replace(/,$/, '');
    if (line.startsWith('Flags,')) hdr = line.split(',');
    else if (hdr && /^\d/.test(line)) {
      const v = line.split(',');
      if (v.length >= hdr.length - 1) rows.push(Object.fromEntries(hdr.map((h, k) => [h, Number(v[k])])));
    }
  }
}
if (!rows.length) { console.log('no frames'); process.exit(); }
const ms = (r, a, b) => (r[b] - r[a]) / 1e6;
const median = xs => { const s = [...xs].sort((a, b) => a - b); const m = s.length >> 1; return s.length % 2 ? s[m] : (s[m - 1] + s[m]) / 2; };
const pct = (xs, p) => [...xs].sort((a, b) => a - b)[Math.min(xs.length - 1, Math.floor(xs.length * p))];
const ui = rows.map(r => ms(r, 'IntendedVsync', 'SyncQueued'));
const gpu = rows.filter(r => r.GpuCompleted > 0).map(r => ms(r, 'SwapBuffers', 'GpuCompleted'));
const tot = rows.map(r => ms(r, 'IntendedVsync', 'FrameCompleted'));
console.log(`frames=${rows.length}`);
for (const [name, xs] of [['UI thread', ui], ['GPU', gpu], ['total', tot]]) {
  if (!xs.length) continue;
  console.log(`${name.padEnd(9)} median=${median(xs).toFixed(1).padStart(6)} ms  p90=${pct(xs, .9).toFixed(1).padStart(6)} ms  max=${Math.max(...xs).toFixed(1).padStart(6)} ms`);
}
const span = (rows[rows.length - 1].FrameCompleted - rows[0].IntendedVsync) / 1e9;
const gaps = rows.slice(1).map((r, i) => (r.FrameCompleted - rows[i].FrameCompleted) / 1e6);
console.log(`delivered: ${(rows.length / span).toFixed(1)} fps over ${span.toFixed(1)} s   frame-to-frame median=${median(gaps).toFixed(1)} ms p90=${pct(gaps, .9).toFixed(1)} ms`);
console.log(`frames over 16.7 ms: ${Math.floor(tot.filter(x => x > 16.7).length * 100 / tot.length)} %   over 33 ms: ${Math.floor(tot.filter(x => x > 33.4).length * 100 / tot.length)} %`);
