package ai.wexa.benchmark.model;

import java.time.Instant;
import java.util.Map;

public record BenchmarkResult(
        String platform,
        Instant startedAt,
        int nodeCount,
        int relationshipCount,
        int iterations,
        int warmup,
        int concurrency,
        int durationSeconds,
        double writeRatio,
        Map<String, MetricSummary> metrics,
        MixedWorkloadStats mixedWorkload,
        ResourceStats resourceStats,
        LoadStats loadStats
) {
    public BenchmarkResult withLoadStats(LoadStats stats) {
        return new BenchmarkResult(platform, startedAt, nodeCount, relationshipCount, iterations, warmup, concurrency,
                durationSeconds, writeRatio, metrics, mixedWorkload, resourceStats, stats);
    }

    public String toHumanString() {
        StringBuilder out = new StringBuilder();
        out.append("Platform: ").append(platform).append('\n');
        for (Map.Entry<String, MetricSummary> entry : metrics.entrySet()) {
            out.append(entry.getKey()).append(": ").append(entry.getValue().toHumanString()).append('\n');
        }
        out.append("mixed: ").append(mixedWorkload.toHumanString()).append('\n');
        return out.toString();
    }
}

