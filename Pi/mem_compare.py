import sys, time, statistics
def rss():
    d = {}
    for l in open("/proc/self/status"):
        if l.startswith(("VmRSS", "VmHWM")):
            k, v = l.split(":"); d[k] = int(v.split()[0]) / 1024
    return d["VmRSS"], d["VmHWM"]

import torch
from executorch.runtime import Runtime
base = rss()[0]
m = Runtime.get().load_program(sys.argv[1]).load_method("forward")
loaded = rss()[0]
x = torch.randn(1, 3, 224, 224)
m.execute([x]); warm = rss()[0]
t = []
for _ in range(100):
    s = time.perf_counter(); m.execute([x]); t.append((time.perf_counter() - s) * 1000)
t.sort(); cur, peak = rss()
print(sys.argv[1])
print(f"  baseline (torch + executorch imported): {base:.0f} MB")
print(f"  after load: {loaded:.0f} MB  (model cost +{loaded-base:.0f} MB)")
print(f"  after warm-up: {warm:.0f} MB, peak: {peak:.0f} MB")
print(f"  median {statistics.median(t):.1f} ms, p95 {t[94]:.1f} ms")
