package ai.wexa.benchmark.core;

import ai.wexa.benchmark.model.Dataset;
import ai.wexa.benchmark.model.Edge;

import java.util.List;

public final class SelfTest {
    private SelfTest() {
    }

    public static void run() {
        List<Long> sorted = List.of(10L, 20L, 30L, 40L, 50L);
        check(Percentiles.percentile(sorted, 50) == 30L, "p50 nearest-rank calculation");
        check(Percentiles.percentile(sorted, 95) == 50L, "p95 nearest-rank calculation");

        Dataset dataset = new Dataset(List.of(new Edge(1, 2), new Edge(2, 3), new Edge(1, 3)));
        check(dataset.nodeCount() == 3, "unique node count");
        check(dataset.relationshipCount() == 3, "relationship count");

        System.out.println("Self-test passed");
    }

    private static void check(boolean condition, String label) {
        if (!condition) {
            throw new AssertionError("Failed: " + label);
        }
    }
}

