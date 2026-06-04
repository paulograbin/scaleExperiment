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

## Benchmark Method

To produce comparable results, follow this exact procedure for each implementation:

### 1. Build all images
```bash
make all
```

### 2. For each implementation, run in order:

```bash
# Start container (constrained: 4 CPUs, 768MB RAM)
docker run --rm -p 8080:8080 --cpus=4 --memory=768m --name bench scale-<name>

# In another terminal:

# Verify it's up
curl http://localhost:8080/hello

# Warm up (discard results — lets JIT compile, pools initialize)
wrk -t4 -c400 -d10s http://localhost:8080/hello

# Benchmark (this is the real run)
wrk -t4 -c400 -d30s --latency http://localhost:8080/hello

# Stop container
docker stop bench
```

### Image names
| Implementation | Image | Container name |
|----------------|-------|----------------|
| Spring Boot | `scale-springboot` | bench |
| Quarkus | `scale-quarkus` | bench |
| Java Pure | `scale-java-pure` | bench |
| Java Raw NIO | `scale-java-raw-nio` | bench |
| Go | `scale-go` | bench |

### Environment
- Machine: 24-core (note: container limited to 4 CPUs)
- Memory: 768MB container limit
- wrk: 4 threads, 400 connections, 30s duration
- Warm-up: 10s before each measurement

## Results (--cpus=4 --memory=768m)

| Implementation | Req/s | p50 | p90 | p99 | Max | Errors | Transfer/sec |
|----------------|-------|-----|-----|-----|-----|--------|--------------|
| Java Raw NIO | 249k | 1.25ms | 2.17ms | 3.93ms | 26ms | 0 | 24.26 MB/s |
| Java Pure (HttpServer) | 134k | 1.80ms | 10.89ms | 20.16ms | 44ms | 330k read errors | 14.77 MB/s |
| Quarkus | 164k | 2.27ms | 3.17ms | 4.74ms | 27ms | 0 | 14.46 MB/s |
| Spring Boot | 32k | 6.76ms | 138ms | 413ms | 864ms | 0 | 4.23 MB/s |
| Go | 138k | 2.66ms | 4.91ms | 7.17ms | 18ms | 0 | 15.16 MB/s |

## Results (unconstrained, no heap limit)

| Implementation | Req/s | p50 | p90 | p99 | Max | Errors | Transfer/sec |
|----------------|-------|-----|-----|-----|-----|--------|--------------|
| Java Raw NIO | 270k | 0.75ms | — | 3.4ms | 54ms | 0 | 26.34 MB/s |
| Quarkus | 158k | 2.32ms | 3.42ms | 4.93ms | 34ms | 0 | 13.94 MB/s |
| Java Pure (HttpServer) | 192k | 1.12ms | — | 79ms | 121ms | ~100k read errors | 21.12 MB/s |
| Go (GOMAXPROCS=24) | 189k | 1.44ms | 4.05ms | 7.17ms | 19ms | 0 | 20.80 MB/s |
| Spring Boot | 71k | 3.70ms | 28.77ms | 107ms | 334ms | 0 | 9.43 MB/s |
