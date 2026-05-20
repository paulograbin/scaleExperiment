# High-Throughput Hello World Benchmark (10k req/s)

## Context

Compare multiple implementations of a hello world endpoint targeting 10,000+ requests per second. The focus is on server tuning, JVM configuration, and observability — not business logic. All implementations run on port 8080 with the same `/hello` endpoint.

## Implementations

| Implementation | Directory | Runtime | Framework/Server |
|----------------|-----------|---------|-----------------|
| Spring Boot | `springboot/` | Java 25, Gradle | Undertow + Virtual Threads |
| Quarkus | `quarkus/` | Java 25, Maven | Vert.x + RESTEasy Reactive |
| Java Pure | `java-pure/` | Java 25, javac | JDK HttpServer + Virtual Threads |
| Java Raw NIO | `java-raw-nio/` | Java 25, javac | Raw NIO Selectors, zero dependencies |
| Go | `go/` | Go 1.22 | net/http (stdlib) |

## Architecture Decisions (shared)

| Decision | Choice | Why |
|----------|--------|-----|
| Java version | 25 (Temurin) | Latest LTS, virtual threads, modern GC |
| GC | ZGC (always generational since Java 24) | Sub-1ms pauses vs G1's 5-20ms at this request rate |
| Protocol | HTTP/1.1 keep-alive | Simplest common denominator across all implementations |
| Compression | Disabled | 13-byte payload — compression adds CPU cost, not savings |
| Monitoring | Micrometer + Prometheus (framework impls) | Pull-based, no overhead on app, latency percentiles |
| Native image | Not recommended | No ZGC support in native; JIT wins for sustained throughput |

### Spring Boot specifics
| Decision | Choice | Why |
|----------|--------|-----|
| Server | Undertow (not Tomcat) | 5-15% better throughput on small payloads, lower memory |
| Concurrency | Virtual threads | Eliminates thread-pool ceiling, near-zero scheduling overhead |
| Build tool | Gradle (Kotlin DSL) | Faster builds, native GraalVM plugin support |

### Quarkus specifics
| Decision | Choice | Why |
|----------|--------|-----|
| REST layer | RESTEasy Reactive | Non-blocking by default, minimal allocations |
| Server | Vert.x (event-loop) | No thread-per-request overhead, built-in to Quarkus |
| Build tool | Maven | Standard for Quarkus ecosystem |

## Project Structure

```
scaleExperiment/
├── springboot/                   # Spring Boot + Undertow + Virtual Threads
│   ├── build.gradle.kts
│   ├── Dockerfile
│   └── src/
├── quarkus/                      # Quarkus + Vert.x + RESTEasy Reactive
│   ├── pom.xml
│   ├── Dockerfile
│   └── src/
├── java-pure/                    # JDK HttpServer + Virtual Threads (zero deps)
│   ├── Dockerfile
│   └── src/Server.java
├── java-raw-nio/                 # Raw NIO Selectors (zero deps, max throughput)
│   ├── Dockerfile
│   └── src/Server.java
├── go/                           # Go net/http (stdlib)
│   ├── Dockerfile
│   └── main.go
├── k6/load-test.js
├── wrk/benchmark.sh
├── docs/jfr-profiling.md
├── Makefile
└── PLAN.md
```

## Running Each Implementation

### Spring Boot
```bash
sdk use java 25.0.1-tem
cd springboot
./gradlew bootRun -Dorg.gradle.jvmargs="-XX:+UseZGC -Xms512m -Xmx512m"
```

### Quarkus
```bash
sdk use java 25.0.1-tem
cd quarkus
./mvnw quarkus:dev                # dev mode
# or
./mvnw package && java -XX:+UseZGC -Xms512m -Xmx512m -jar target/quarkus-app/quarkus-run.jar
```

### Java Pure (HttpServer)
```bash
sdk use java 25.0.1-tem
cd java-pure
javac -d out src/Server.java
java -XX:+UseZGC -Xms512m -Xmx512m -cp out com.paulograbin.scale.Server
```

### Java Raw NIO
```bash
sdk use java 25.0.1-tem
cd java-raw-nio
javac -d out src/Server.java
java -XX:+UseZGC -Xms512m -Xmx512m -cp out com.paulograbin.scale.Server
```

### Go
```bash
cd go
go run .
# or
go build -o server . && ./server
```

## Load Testing

- **wrk**: `wrk -t4 -c400 -d30s --latency http://localhost:8080/hello`
- **k6**: `k6 run k6/load-test.js` (constant-arrival-rate at 10k/s for 60s, p99 < 10ms threshold)

## JVM Flags (Java implementations, local and Docker)

```
-XX:+UseZGC -Xms512m -Xmx512m -XX:+AlwaysPreTouch -Djava.security.egd=file:/dev/./urandom
```

Note: `-XX:+ZGenerational` was removed in Java 24 — ZGC is always generational now.

## Docker

Build all images in parallel:
```bash
make          # builds all 5
make clean    # removes build artifacts and images
```

## Verification

| Step | Spring Boot | Quarkus | Java Pure | Java Raw NIO | Go |
|------|-------------|---------|-----------|--------------|-----|
| Hello | `curl localhost:8080/hello` | same | same | same | same |
| Metrics | `/actuator/prometheus` | `/q/metrics` | — | — | `/actuator/prometheus` |
| Health | `/actuator/health` | `/q/health` | `/actuator/health` | `/actuator/health` | `/actuator/health` |
| Benchmark | `wrk -t4 -c400 -d30s --latency http://localhost:8080/hello` | same | same | same | same |

## Observed Results (24-core machine, 512M heap, Docker)

| Implementation | Req/s | p50 | p99 | Errors | GC pauses |
|----------------|-------|-----|-----|--------|-----------|
| Java Raw NIO | 270k | 0.75ms | 3.4ms | 0 | 5–14ms major, every ~3s |
| Java Pure (HttpServer) | 192k | 1.12ms | 79ms | ~100k read errors | 5–14ms minor, every ~120ms |
| Quarkus | TBD | — | — | — | 6–14ms minor, every ~300ms |
| Spring Boot | TBD | — | — | — | TBD |
| Go | TBD | — | — | — | N/A |
