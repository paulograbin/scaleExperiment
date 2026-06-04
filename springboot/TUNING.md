# Spring Boot Tuning Results

Sequential changes and their impact on throughput (unconstrained, 24-core machine).

## Results

| # | Change | Req/s | p50 | p90 | p99 | Max | Δ Req/s |
|---|--------|-------|-----|-----|-----|-----|---------|
| 0 | Baseline (Undertow, virtual threads, io=4) | 71k | 3.70ms | 28.7ms | 107ms | 334ms | — |
| 1 | Disable metrics (http/jvm/system/process) + lazy init + JMX off | 73k | 3.65ms | 27.7ms | 88ms | 251ms | +3% |
| 2 | Disable virtual threads + io threads 4→24 | 131k | 2.23ms | 14.6ms | 60ms | 192ms | +80% |

## Change Details

### 0. Baseline
```yaml
spring.threads.virtual.enabled: true
server.undertow.threads.io: 4
server.undertow.threads.worker: 200
# All metrics enabled, eager initialization
```

### 1. Disable metrics + lazy init
```yaml
spring.main.lazy-initialization: true
spring.jmx.enabled: false
management.metrics.enable.http: false
management.metrics.enable.jvm: false
management.metrics.enable.system: false
management.metrics.enable.process: false
```
**Why:** Micrometer's per-request `Timer.record()` adds overhead on every request. JMX and eager bean init waste startup time.

**Result:** Marginal — the metrics interceptor was not the bottleneck.

### 2. Disable virtual threads + bump I/O threads
```yaml
spring.threads.virtual.enabled: false
server.undertow.threads.io: 24   # was 4
```
**Why:** Virtual threads add ForkJoinPool scheduling overhead on every request. For a non-blocking hello-world with zero I/O wait, they provide no benefit — only contention. Bumping I/O threads to match CPU cores lets Undertow's XNIO event loop fully utilize the hardware.

**Result:** Nearly doubled throughput. The ForkJoinPool was the main bottleneck.

## Key Insight

Virtual threads shine when requests block on I/O (database, HTTP calls, file reads). For a CPU-bound or non-blocking workload like a static response, they're pure overhead — every request pays the cost of:
1. Creating a virtual thread
2. ForkJoinPool scheduling
3. Carrier thread mounting/unmounting

Undertow's native I/O thread pool with direct dispatch is significantly faster for this workload pattern.
