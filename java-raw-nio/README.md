# Java Raw NIO Server

A bare-metal HTTP server using `java.nio.channels` with zero external dependencies. Achieves ~270k req/s with sub-4ms p99 latency on a 24-core machine.

## How it works

### Architecture

```
                      ┌──────────────────┐
   clients ──TCP──▶  │  Main Thread     │  blocking accept() loop
                      │  ServerSocket    │
                      └────────┬─────────┘
                               │ round-robin assignment
            ┌──────────────────┼──────────────────┐
            ▼                  ▼                  ▼
     ┌─────────────┐   ┌─────────────┐   ┌─────────────┐
     │  worker-0   │   │  worker-1   │   │  worker-N   │
     │  Selector   │   │  Selector   │   │  Selector   │
     │  (epoll)    │   │  (epoll)    │   │  (epoll)    │
     └─────────────┘   └─────────────┘   └─────────────┘
```

One thread per CPU core. Each worker handles thousands of connections concurrently through non-blocking I/O.

### Accept loop (main thread)

The main thread opens a `ServerSocketChannel` in blocking mode and calls `accept()` in a loop. Each new connection is assigned to the next worker in round-robin order. Blocking accept is intentional here — it's the simplest approach and never becomes a bottleneck because accepting a TCP connection is cheap compared to handling requests.

### Worker event loops

Each worker thread owns a `Selector` (backed by epoll on Linux). The loop:

1. `selector.select()` — blocks until at least one connection has data to read
2. Iterates over ready `SelectionKey`s
3. For each readable key: reads into a pre-allocated buffer, determines the route, writes the response

### Routing

Instead of parsing the full HTTP request, we peek at byte position 5 of the request:

- `GET /hello` — byte 5 is `h`
- `GET /actuator/health` — byte 5 is `a`

This is enough to distinguish our two endpoints. We never allocate strings, never split headers, never build a request object.

### Response

Responses are pre-built `byte[]` constants containing the full HTTP response (status line + headers + body). Writing a response is a single `channel.write(ByteBuffer.wrap(RESPONSE_BYTES))` call.

### Connection state

Each connection gets a 1KB `ByteBuffer` attached to its `SelectionKey`. This buffer is reused across requests on the same connection (HTTP keep-alive). No per-request allocation happens.

## Build and run

```bash
# Compile
javac -d out src/Server.java

# Run
java -XX:+UseZGC -Xms512m -Xmx512m -cp out com.paulograbin.scale.Server

# Run with remote debug
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=\*:5005 \
  -XX:+UseZGC -Xms512m -Xmx512m -cp out com.paulograbin.scale.Server

# Docker
docker build -t scale-java-raw-nio .
docker run --rm -p 8080:8080 scale-java-raw-nio
```

## Benchmark results (24-core, 512M heap)

```
wrk -t4 -c400 -d30s --latency http://localhost:8080/hello

Requests/sec: 270,794
Latency:  p50=0.75ms  p99=3.4ms  max=54ms
Errors:   0
GC:       Major only, every ~3s, 5-14ms pauses, 8-12M live data
```

## Pros

- **Maximum throughput** — no framework overhead, no middleware, no object creation per request
- **Predictable latency** — no GC pressure means no surprise pauses; p99 stays tight
- **Zero dependencies** — nothing to upgrade, no CVEs from transitive deps, no classpath conflicts
- **Tiny footprint** — 8-12MB live heap under full load, ~135 lines of code
- **Fast startup** — ready in milliseconds, no classpath scanning, no DI container initialization
- **Easy to reason about** — single file, linear control flow, no magic

## Cons

- **Not a real HTTP server** — no header parsing, no chunked transfer encoding, no content negotiation, no HTTP/2
- **Fragile routing** — byte-position check only works for known endpoints; adding routes requires manual offset math
- **No pipelining support** — assumes one request per read; a pipelined client would get partial responses
- **No graceful request draining** — shutdown kills in-flight requests immediately
- **No observability** — no metrics, no access logs, no tracing (you'd have to add them manually)
- **No TLS** — would need SSLEngine wrapping, which adds significant complexity
- **Partial writes not handled** — if the kernel buffer is full, `channel.write()` may write fewer bytes than expected; we don't retry the remainder
- **Not production-safe** — no input validation, no request size limits, no timeout on idle connections, no protection against slowloris

## When to use this

- Throughput benchmarks comparing language/runtime overhead
- Understanding how NIO and event loops work under the hood
- Establishing a performance ceiling for the hardware
- Situations where you genuinely need raw speed and control every byte

## When NOT to use this

- Anything that faces the internet
- Anything that needs more than a handful of static endpoints
- Anything where developer productivity matters more than the last 10% of throughput
