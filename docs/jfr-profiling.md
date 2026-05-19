# Java Flight Recorder — Allocation Profiling in Docker

## Option 1: Auto-recording (30s from startup)

```bash
mkdir -p jfr

docker run --rm -p 8080:8080 -v $(pwd)/jfr:/app/jfr \
  -e JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational -Xms512m -Xmx512m -XX:+AlwaysPreTouch -XX:StartFlightRecording=duration=30s,filename=/app/jfr/alloc.jfr,settings=profile" \
  scale-quarkus
```

Run your load test within the first 30 seconds. The recording auto-dumps to `./jfr/alloc.jfr`.

## Option 2: On-demand recording (recommended)

Start the container:

```bash
mkdir -p jfr

docker run --rm -p 8080:8080 -v $(pwd)/jfr:/app/jfr --name quarkus-bench scale-quarkus
```

In another terminal, start the recording:

```bash
docker exec quarkus-bench jcmd 1 JFR.start name=alloc settings=profile
```

Run your load test:

```bash
wrk -t4 -c400 -d30s --latency http://localhost:8080/hello
```

Stop and dump the recording:

```bash
docker exec quarkus-bench jcmd 1 JFR.stop name=alloc filename=/app/jfr/alloc.jfr
```

## Analyzing the recording

Open with JDK Mission Control:

```bash
jmc jfr/alloc.jfr
```

Look at the "Memory" tab → "Allocations" view to see:
- Which classes are allocated the most (bytes and instances)
- Which stack traces are responsible for allocations
- Allocation rate over time

## CLI analysis (no GUI needed)

Summary of the recording:

```bash
jfr summary jfr/alloc.jfr
```

Print all allocation samples:

```bash
jfr print --events jdk.ObjectAllocationSample jfr/alloc.jfr
```

Top allocated classes during load (vert.x/executor threads only):

```bash
jfr print --events jdk.ObjectAllocationSample jfr/alloc.jfr | awk '
/^jdk.ObjectAllocationSample/ { rec=""; cls="" }
/objectClass/ { cls=$0 }
/eventThread.*vert.x/ || /eventThread.*executor/ { rec=1 }
/^$/ && rec { print cls }
' | sed 's/.*objectClass = //; s/ (.*//' | sort | uniq -c | sort -rn | head -20
```

Aggregate by thread to see which threads allocate the most:

```bash
jfr print --events jdk.ObjectAllocationSample jfr/alloc.jfr | grep "eventThread" | sort | uniq -c | sort -rn | head -10
```
