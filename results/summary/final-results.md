# Final Benchmark Results

Dataset: SNAP wiki-Vote network, converted to `source,target` CSV.

Run settings: 50 measured iterations, 10 warmup iterations, mixed workload concurrency 4, mixed workload duration 30 seconds, write ratio 10%.

Dataset size: 7,115 nodes and 103,689 relationships.

| Platform | Load rel/s | Point p50/p95 ms | Indexed p50/p95 ms | 1-hop p50/p95 ms | 2-hop p50/p95 ms | 3-hop p50/p95 ms | Aggregation p50/p95 ms | Mixed QPS |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| CognoDB Cloud | 441.494 | 773.785 / 964.322 | 763.257 / 1017.193 | 772.810 / 1081.796 | 770.050 / 1024.320 | 791.432 / 1232.692 | 780.472 / 919.002 | 3.133 |
| Neo4j AuraDB | 1477.416 | 274.563 / 549.242 | 273.944 / 444.394 | 273.243 / 579.798 | 276.633 / 460.438 | 267.222 / 333.862 | 268.563 / 367.228 | 13.167 |
| FalkorDB Cloud | 1718.901 | 28.013 / 42.094 | 28.604 / 41.501 | 30.847 / 87.562 | 28.152 / 42.993 | 29.865 / 40.446 | 30.102 / 40.587 | 104.800 |
| ArangoDB Cloud | 464.423 | 273.376 / 411.605 | 272.786 / 339.011 | 278.987 / 376.283 | 278.905 / 415.367 | 281.796 / 362.473 | 280.575 / 384.556 | 11.367 |
| Memgraph Cloud | 266.574 | 810.820 / 1036.674 | 817.416 / 992.934 | 818.221 / 1078.434 | 816.364 / 1094.331 | 810.667 / 1047.484 | 809.070 / 1106.967 | 4.600 |

Notes:

- These are single-run results from a local Windows client over public cloud endpoints, so network distance and free-tier throttling affect the numbers.
- CognoDB completed the run but emitted transient connection termination/retry warnings during the mixed workload.
- Memgraph required `bolt+ssc://` because the Java trust store did not accept its presented certificate chain.
- Memgraph and FalkorDB skipped at least one optional index/constraint statement because their Cypher dialects do not accept the same schema syntax as Neo4j-compatible Bolt systems.
- Resource usage was not observable through the portable harness for every platform.
