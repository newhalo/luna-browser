# Summarises `dumpsys gfxinfo <pkg> framestats`: UI-thread, GPU and frame-to-frame timing.
# Usage: python frames.py framestats.txt   (tools/perf/measure.ps1 runs it for you)
import sys, statistics
txt = open(sys.argv[1], encoding="utf-8-sig", errors="ignore").read()
blocks = txt.split("---PROFILEDATA---")
rows, hdr = [], None
for b in blocks[1::2]:
    for line in b.strip().splitlines():
        line = line.strip().rstrip(",")
        if line.startswith("Flags,"):
            hdr = line.split(",")
        elif hdr and line[:1].isdigit():
            v = line.split(",")
            if len(v) >= len(hdr) - 1:
                rows.append(dict(zip(hdr, map(int, v))))
if not rows:
    print("no frames"); sys.exit()
def ms(r, a, b): return (r[b] - r[a]) / 1e6
ui = [ms(r, "IntendedVsync", "SyncQueued") for r in rows]
gpu = [ms(r, "SwapBuffers", "GpuCompleted") for r in rows if r.get("GpuCompleted", 0) > 0]
tot = [ms(r, "IntendedVsync", "FrameCompleted") for r in rows]
pct = lambda xs, p: sorted(xs)[min(len(xs) - 1, int(len(xs) * p))]
print(f"frames={len(rows)}")
for name, xs in (("UI thread", ui), ("GPU", gpu), ("total", tot)):
    print(f"{name:9s} median={statistics.median(xs):6.1f} ms  p90={pct(xs, .9):6.1f} ms  max={max(xs):6.1f} ms")
span = (rows[-1]["FrameCompleted"] - rows[0]["IntendedVsync"]) / 1e9
gaps = [(rows[i]["FrameCompleted"] - rows[i - 1]["FrameCompleted"]) / 1e6 for i in range(1, len(rows))]
print(f"delivered: {len(rows) / span:5.1f} fps over {span:4.1f} s   frame-to-frame median={statistics.median(gaps):5.1f} ms p90={pct(gaps, .9):5.1f} ms")
print(f"frames over 16.7 ms: {sum(1 for x in tot if x > 16.7) * 100 // len(tot)} %   over 33 ms: {sum(1 for x in tot if x > 33.4) * 100 // len(tot)} %")
