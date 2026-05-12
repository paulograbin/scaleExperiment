#!/bin/bash
# Benchmark: 4 threads, 400 connections, 30 seconds
# Expected: >10k req/s for hello world on Undertow + virtual threads
wrk -t4 -c400 -d30s --latency http://localhost:8080/hello
