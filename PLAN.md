# High-Throughput Hello World Benchmark (10k req/s)

## Context

Compare multiple implementations of a hello world endpoint targeting 10,000+ requests per second. The focus is on server tuning, JVM configuration, and observability — not business logic. All implementations run on port 8080 with the same `/hello` endpoint.

## Implementations

| Implementation | Directory | Runtime | Framework/Server |
|----------------|-----------|---------|-----------------|
| Spring Boot | `/` (root) | Java 25, Gradle | Undertow + Virtual Threads |
| Quarkus | `quarkus/` | Java 25, Maven | Vert.x + RESTEasy Reactive |
| Go | `go/` | Go 1.22 | net/http (stdlib) |

## Architecture Decisions (shared)

| Decision | Choice | Why |
|----------|--------|-----|
| Java version | 25 (Temurin) | Latest LTS, virtual threads, modern GC |
| GC | ZGC Generational | Sub-1ms pauses vs G1's 5-20ms at this request rate |
| Protocol | HTTP/2 + keep-alive | Multiplexed connections reduce socket overhead |
| Compression | Disabled | 13-byte payload — compression adds CPU cost, not savings |
| Monitoring | Micrometer + Prometheus | Pull-based, no overhead on app, latency percentiles |
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
├── build.gradle.kts              # Spring Boot (root project)
├── settings.gradle.kts
├── src/main/java/com/paulograbin/scale/
│   ├── ScaleExperimentApplication.java
│   └── controller/HelloController.java
├── src/main/resources/
│   ├── application.yml
│   └── logback-spring.xml
├── docker/Dockerfile
├── quarkus/                      # Quarkus implementation
│   ├── pom.xml
│   ├── mvnw
│   ├── Dockerfile
│   └── src/main/java/com/paulograbin/scale/
│       └── HelloResource.java
├── go/                           # Go implementation
│   ├── main.go
│   ├── go.mod
│   └── Dockerfile
├── k6/load-test.js
└── wrk/benchmark.sh
```

## Running Each Implementation

### Spring Boot
```bash
sdk use java 25.0.1-tem
./gradlew bootRun -Dorg.gradle.jvmargs="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx512m"
```

### Quarkus
```bash
sdk use java 25.0.1-tem
cd quarkus
./mvnw quarkus:dev                # dev mode
# or
./mvnw package && java -XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx512m -jar target/quarkus-app/quarkus-run.jar
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
-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx512m -XX:+AlwaysPreTouch -Djava.security.egd=file:/dev/./urandom
```

## Verification

| Step | Spring Boot | Quarkus | Go |
|------|-------------|---------|-----|
| Hello | `curl localhost:8080/hello` | same | same |
| Metrics | `/actuator/prometheus` | `/q/metrics` | `/actuator/prometheus` |
| Health | `/actuator/health` | `/q/health` | `/actuator/health` |
| Benchmark | `wrk -t4 -c400 -d30s --latency http://localhost:8080/hello` | same | same |
| Target | >10k req/s, p99 < 10ms | same | same |

## Expected Results

On a modern 4-core machine: 15,000–40,000 req/s (well above target), p99 < 5ms, GC pauses < 1ms, memory ~200-300MB under load.
