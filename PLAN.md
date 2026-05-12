# High-Throughput Spring Boot Hello World (10k req/s)

## Context

Build a Java Spring Boot application from scratch that sustains 10,000 requests per second on a hello world endpoint. The project lives in `/home/paulograbin/Desktop/scaleExperiment` (currently empty). The focus is on server tuning, JVM configuration, and observability — not business logic.

## Architecture Decisions

| Decision | Choice | Why |
|----------|--------|-----|
| Server | Undertow (not Tomcat) | 5-15% better throughput on small payloads, lower memory |
| Concurrency | Virtual threads (Java 21) | Eliminates thread-pool ceiling, near-zero scheduling overhead |
| GC | ZGC Generational | Sub-1ms pauses vs G1's 5-20ms at this request rate |
| Protocol | HTTP/2 + keep-alive | Multiplexed connections reduce socket overhead |
| Compression | Disabled | 13-byte payload — compression adds CPU cost, not savings |
| Build tool | Gradle (Kotlin DSL) | Faster builds, native GraalVM plugin support |
| Monitoring | Micrometer + Prometheus | Pull-based, no overhead on app, latency percentiles |
| Native image | Not recommended | No ZGC support in native; JIT wins for sustained throughput |

## Project Structure

```
scaleExperiment/
├── build.gradle.kts
├── settings.gradle.kts
├── src/main/java/com/paulograbin/scale/
│   ├── ScaleExperimentApplication.java
│   └── controller/HelloController.java
├── src/main/resources/
│   ├── application.yml
│   └── logback-spring.xml
├── src/test/java/com/paulograbin/scale/
│   └── ScaleExperimentApplicationTest.java
├── docker/Dockerfile
├── k6/load-test.js
└── wrk/benchmark.sh
```

## Implementation Steps

### 1. Generate project skeleton
- Create `build.gradle.kts` with Spring Boot 3.4.x, Java 21 toolchain
- Exclude Tomcat, include Undertow, Actuator, Prometheus registry
- Create `settings.gradle.kts`
- Generate Gradle wrapper

### 2. Main application class
- `ScaleExperimentApplication.java` — standard `@SpringBootApplication` entry point

### 3. Hello controller
- Pre-built static `ResponseEntity<String>` with "Hello, World!" (avoids per-request allocation)
- `GET /hello` returning `text/plain`
- No parameters, no validation, no serialization

### 4. Application configuration (`application.yml`)
```yaml
spring.threads.virtual.enabled: true
server.http2.enabled: true
server.compression.enabled: false
server.undertow.threads.io: 4
server.undertow.threads.worker: 200
server.undertow.buffer-size: 1024
server.undertow.direct-buffers: true
management.endpoints.web.exposure.include: health,prometheus,metrics
```

### 5. Logging (`logback-spring.xml`)
- Async appender with queue size 1024
- Root level WARN to minimize I/O during load

### 6. Docker setup
- `eclipse-temurin:21-jre-alpine` base
- JVM flags: `-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx512m -XX:+AlwaysPreTouch`
- Expose 8080, recommend `--cpus=4 --memory=768m`

### 7. Load testing scripts
- **wrk script**: `wrk -t4 -c400 -d30s --latency http://localhost:8080/hello`
- **k6 script**: `constant-arrival-rate` executor at 10k/s for 60s, with p99 < 10ms threshold

### 8. Basic test
- Spring Boot test that loads context and hits `/hello`

## JVM Flags (for both local and Docker)

```
-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx512m -XX:+AlwaysPreTouch -Djava.security.egd=file:/dev/./urandom -Dspring.jmx.enabled=false
```

## Verification Plan

1. `./gradlew bootRun` with JVM flags
2. `curl http://localhost:8080/hello` → "Hello, World!"
3. `curl http://localhost:8080/actuator/prometheus` → metrics visible
4. Run `wrk -t4 -c400 -d30s --latency http://localhost:8080/hello`
5. Confirm output shows > 10k req/s and p99 < 10ms
6. Check `/actuator/prometheus` for `http_server_requests_seconds` metrics

## Expected Results

On a modern 4-core machine: 15,000–40,000 req/s (well above target), p99 < 5ms, GC pauses < 1ms, memory ~200-300MB under load.
