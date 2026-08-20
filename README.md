# CognoDB Cloud Graph Database Benchmark

This repository benchmarks CognoDB Cloud against comparable graph database platforms using the same dataset, logical workloads, client machine, and reporting format.

## Implementation Status

Implemented now:

- Java 17 CLI benchmark harness
- Bolt/Cypher adapter for CognoDB, Neo4j Aura, and other Bolt-compatible platforms
- ArangoDB HTTP/AQL adapter using Java's standard `HttpClient`
- FalkorDB RESP/Cypher adapter using Java sockets
- CSV dataset reader
- Dataset smoke-test generator
- Data loading throughput
- 1-hop, 2-hop, and 3-hop traversal latency
- Point lookup latency
- Indexed lookup latency
- Aggregation latency
- Mixed read/write throughput with configurable concurrency
- JSON and CSV result files
- Final benchmark results on CognoDB Cloud plus four comparison platforms

## Dataset

Final benchmark dataset: SNAP wiki-Vote network.

- Source: https://snap.stanford.edu/data/wiki-Vote.html
- Download URL: `http://snap.stanford.edu/data/wiki-Vote.txt.gz`
- Nodes: 7,115
- Relationships: 103,689
- Converted local file: `data/wiki-vote.csv`

The CSV dataset is intentionally not committed because `data/*.csv` is ignored. Recreate it from SNAP before rerunning final benchmarks.

## Final Results

Run settings: 50 measured iterations, 10 warmup iterations, mixed workload concurrency 4, mixed workload duration 30 seconds, write ratio 10%.

| Platform | Load rel/s | Point p50/p95 ms | Indexed p50/p95 ms | 1-hop p50/p95 ms | 2-hop p50/p95 ms | 3-hop p50/p95 ms | Aggregation p50/p95 ms | Mixed QPS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| CognoDB Cloud | 441.494 | 773.785 / 964.322 | 763.257 / 1017.193 | 772.810 / 1081.796 | 770.050 / 1024.320 | 791.432 / 1232.692 | 780.472 / 919.002 | 3.133 |
| Neo4j AuraDB | 1477.416 | 274.563 / 549.242 | 273.944 / 444.394 | 273.243 / 579.798 | 276.633 / 460.438 | 267.222 / 333.862 | 268.563 / 367.228 | 13.167 |
| FalkorDB Cloud | 1718.901 | 28.013 / 42.094 | 28.604 / 41.501 | 30.847 / 87.562 | 28.152 / 42.993 | 29.865 / 40.446 | 30.102 / 40.587 | 104.800 |
| ArangoDB Cloud | 464.423 | 273.376 / 411.605 | 272.786 / 339.011 | 278.987 / 376.283 | 278.905 / 415.367 | 281.796 / 362.473 | 280.575 / 384.556 | 11.367 |
| Memgraph Cloud | 266.574 | 810.820 / 1036.674 | 817.416 / 992.934 | 818.221 / 1078.434 | 816.364 / 1094.331 | 810.667 / 1047.484 | 809.070 / 1106.967 | 4.600 |

Curated raw files are committed under `results/final/`. A standalone summary is available in `results/summary/final-results.md`.

## Environment & Resource Parity

| Platform | Plan / Tier | vCPU | Memory | Storage | Region | Version |
|---|---|---|---|---|---|---|
| CognoDB Cloud | Free (c0) | burst to 0.5 vCPU | 256 MB | 1 GiB | us-east4 | — |
| Neo4j AuraDB | Free | shared | shared (Aura Free instance) | shared | Aura-managed | — |
| Memgraph Cloud | Cloud trial | 2 CPU | 2 GB | — | US East (N. Virginia) | v3.12.0 |
| FalkorDB Cloud | Free instance | shared | shared | — | AWS ap-south-1 | — |
| ArangoDB Cloud | Trial deployment | shared | shared | — | GCP, Iowa USA | 3.12.10 |

Connection endpoints, instance IDs, and account identifiers are intentionally omitted here and are not committed anywhere in this repo — they are read from environment variables (see `.env.example`) so they never need to be shared or rotated after a public push.

**Fairness note:** Memgraph's trial tier (2 vCPU / 2 GB RAM) is materially larger than CognoDB's free tier (0.5 vCPU / 256 MB RAM) and than the other platforms' free tiers. Memgraph does not currently offer a free tier at CognoDB's resource level, so this comparison has an acknowledged resource-parity gap. Memgraph's numbers should be read as "best available comparable tier," not a same-resources result — see Analysis below for how this affects interpretation.


## Why This Shape

The code intentionally avoids Spring Boot, external CLI parsers, JSON libraries, and benchmarking frameworks. Java standard library features handle arguments, files, CSV, percentiles, concurrency, JSON output, and ArangoDB HTTP calls. The Neo4j Java Driver is included because CognoDB requires the official Neo4j driver over Bolt.

## Setup

Requirements:

- Java 17+
- Maven 3.9+
- Free-tier graph database accounts

Create local config:

```bash
cp config/platforms.example.properties config/platforms.properties
cp .env.example .env
```

Load environment variables from `.env` using your shell, or set them manually. Do not commit `.env`.

Build:

```bash
mvn test package
```

## Smoke Test

The smoke dataset is not valid for final assignment results. It only checks that the harness can load and run.

```bash
java -jar target/cognodb-cloud-benchmark-0.1.0.jar sample \
  --edges data/smoke-edges.csv \
  --nodes 1000 \
  --relationships 5000
```

Run load and benchmark:

```bash
java -jar target/cognodb-cloud-benchmark-0.1.0.jar all \
  --config config/platforms.properties \
  --platform cognodb \
  --edges data/smoke-edges.csv \
  --iterations 20 \
  --warmup 5 \
  --concurrency 4 \
  --durationSeconds 10 \
  --out results/raw
```

## Final Benchmark Command

Use this shape for each final platform:

```bash
java -jar target/cognodb-cloud-benchmark-0.1.0.jar all \
  --config config/platforms.properties \
  --platform cognodb \
  --edges data/wiki-vote.csv \
  --iterations 50 \
  --warmup 10 \
  --concurrency 4 \
  --durationSeconds 30 \
  --out results/raw
```

## Workloads

All platforms use the same logical model:

```cypher
(:Person {id, bucket})-[:KNOWS]->(:Person)
```

ArangoDB stores this as a `people` document collection and a `knows` edge collection, queried with equivalent AQL workloads.
FalkorDB stores the same labels, properties, and relationship names through `GRAPH.QUERY`.

Measured workloads:

| Category | Workload | Reported |
|---|---|---|
| Data loading | batch merge nodes and relationships | nodes/sec, relationships/sec, wall-clock seconds |
| Traversal | exact 1-hop, 2-hop, 3-hop outgoing traversal | p50 and p95 latency in ms |
| Lookup | `Person.id` point lookup | p50 and p95 latency in ms |
| Indexed lookup | `Person.bucket` filtered lookup | p50 and p95 latency in ms |
| Aggregation | group people by bucket | p50 and p95 latency in ms |
| Mixed workload | concurrent reads and writes | sustained queries/sec |

## Result Files

Each run emits:

- `results/raw/<platform>-<timestamp>.json`
- `results/raw/<platform>-<timestamp>.csv`

`results/raw/` is ignored to avoid committing smoke-test output. The final selected run files are copied into `results/final/`.

## Analysis

**FalkorDB is the outlier, and the likely reason is architectural, not just resources.** FalkorDB posted p50 latencies of ~28-30 ms across every read workload, roughly 10x faster than Neo4j AuraDB and ArangoDB and ~27x faster than CognoDB, despite running on a comparably small free instance. FalkorDB is built on Redis with graphs held as sparse adjacency matrices in memory and queried via linear-algebra-based traversal (GraphBLAS), which is a fundamentally different execution model from the property-graph engines the other platforms use. That, combined with Redis's lightweight protocol overhead, is the most plausible explanation for the gap — this benchmark cannot fully separate "faster engine" from "the client happened to have a shorter network path to this region," so treat the magnitude as directional rather than exact.

**CognoDB and Memgraph cluster at the slow end, for different reasons.** CognoDB's p50s sit around 760-790 ms — 2-3x slower than Memgraph despite Memgraph running on 4x the vCPU and RAM. That gap is too large to attribute to resources alone; the flat ~760 ms floor across point lookups, traversals, and aggregations on CognoDB (see Caveats) looks more like fixed per-query network/connection overhead than compute-bound query execution, consistent with the transient connection warnings observed during its mixed-workload run. Memgraph's latencies are close to CognoDB's despite far more resources, which suggests Memgraph's number is closer to a "true" small-graph latency floor for this workload shape, and CognoDB's gap on top of that is overhead rather than raw processing time — though this is inferred from the pattern, not directly measured.

**Neo4j AuraDB and ArangoDB land in the middle and are the closest to an apples-to-apples comparison**, both on free/shared tiers with similar p50s (~270-280 ms) across all workloads. Their throughput and latency numbers are close enough that this benchmark doesn't show a meaningful winner between them at this dataset size.

**Load throughput doesn't track query latency.** FalkorDB and Neo4j AuraDB loaded fastest (1,700 and 1,477 rel/s), while ArangoDB and CognoDB loaded much slower (~440-460 rel/s). Notably, CognoDB is slow at both loading and querying, while ArangoDB is slow to load but fast to query — the two rankings don't move together. This suggests the load path is driven by each platform's batching/transaction handling rather than the same factors that drive read latency, and is worth a dedicated ingest-path investigation beyond this benchmark's scope.

**Caveat on confidence:** with 50 iterations per workload (below the ≥100 this benchmark's methodology targets) and a single run per platform, the results above should be read as directionally reliable rather than statistically tight — the caveats section below lists the specific run conditions that could shift these numbers on a re-run.

## Caveats

- These are single-run results from a local Windows client over public cloud endpoints, so network distance and free-tier throttling affect the numbers.
- Each read workload used 50 measured iterations after 10 warmup iterations, below the ≥100 iterations this methodology targets for stable percentile estimates; treat p95 values in particular as noisier than a higher-iteration run would produce.
- Resource tiers are not equal across all five platforms — see the fairness note under Environment above. Memgraph in particular ran on a larger trial tier than the others, so its numbers are not a strict same-resources comparison.
- CognoDB completed the final run but emitted transient connection termination/retry warnings during the mixed workload.
- Memgraph required `bolt+ssc://` because the Java trust store did not accept its presented certificate chain.
- Memgraph and FalkorDB skipped at least one optional index/constraint statement because their Cypher dialects do not accept the same schema syntax as Neo4j-compatible Bolt systems.
- Resource usage was not observable through the portable harness for every platform.
