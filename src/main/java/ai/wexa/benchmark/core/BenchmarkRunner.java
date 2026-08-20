package ai.wexa.benchmark.core;

import ai.wexa.benchmark.adapter.GraphAdapter;
import ai.wexa.benchmark.config.Args;
import ai.wexa.benchmark.model.BenchmarkResult;
import ai.wexa.benchmark.model.Dataset;
import ai.wexa.benchmark.model.MetricSummary;
import ai.wexa.benchmark.model.MixedWorkloadStats;
import ai.wexa.benchmark.model.ResourceStats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public final class BenchmarkRunner {
    private final int iterations;
    private final int warmup;
    private final int concurrency;
    private final int durationSeconds;
    private final double writeRatio;
    private final Random random;

    public BenchmarkRunner(int iterations, int warmup, int concurrency, int durationSeconds, double writeRatio, long seed) {
        this.iterations = iterations;
        this.warmup = warmup;
        this.concurrency = concurrency;
        this.durationSeconds = durationSeconds;
        this.writeRatio = writeRatio;
        this.random = new Random(seed);
    }

    public static BenchmarkRunner fromArgs(Args args) {
        return new BenchmarkRunner(
                args.integer("iterations", 100),
                args.integer("warmup", 20),
                args.integer("concurrency", 10),
                args.integer("durationSeconds", 60),
                args.doubleValue("writeRatio", 0.10),
                args.longValue("seed", 7L)
        );
    }

    public BenchmarkResult run(GraphAdapter adapter, Dataset dataset) throws InterruptedException {
        Map<String, MetricSummary> metrics = new LinkedHashMap<>();

        metrics.put("point_lookup", measure(() -> adapter.pointLookup(randomNode(dataset))));
        metrics.put("indexed_lookup", measure(() -> adapter.indexedLookup(randomBucket())));
        metrics.put("traversal_1_hop", measure(() -> adapter.traversal(randomNode(dataset), 1)));
        metrics.put("traversal_2_hop", measure(() -> adapter.traversal(randomNode(dataset), 2)));
        metrics.put("traversal_3_hop", measure(() -> adapter.traversal(randomNode(dataset), 3)));
        metrics.put("aggregation", measure(adapter::aggregation));

        MixedWorkloadStats mixed = runMixed(adapter, dataset);
        ResourceStats resources = adapter.resourceStats();

        return new BenchmarkResult(
                adapter.name(),
                Instant.now(),
                dataset.nodeCount(),
                dataset.relationshipCount(),
                iterations,
                warmup,
                concurrency,
                durationSeconds,
                writeRatio,
                metrics,
                mixed,
                resources,
                null
        );
    }

    private MetricSummary measure(Workload workload) {
        for (int i = 0; i < warmup; i++) {
            workload.run();
        }

        List<Long> nanos = new ArrayList<>(iterations);
        for (int i = 0; i < iterations; i++) {
            long started = System.nanoTime();
            workload.run();
            nanos.add(System.nanoTime() - started);
        }
        return Percentiles.summary(nanos);
    }

    private MixedWorkloadStats runMixed(GraphAdapter adapter, Dataset dataset) throws InterruptedException {
        AtomicLong operations = new AtomicLong();
        AtomicLong reads = new AtomicLong();
        AtomicLong writes = new AtomicLong();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(durationSeconds);
        ExecutorService executor = Executors.newFixedThreadPool(concurrency);

        for (int i = 0; i < concurrency; i++) {
            int workerId = i;
            executor.submit(() -> {
                Random workerRandom = new Random(10_000L + workerId);
                while (System.nanoTime() < deadline) {
                    if (workerRandom.nextDouble() < writeRatio) {
                        long source = 9_000_000_000L + operations.get();
                        long target = randomNode(dataset);
                        adapter.writeRelationship(source, target);
                        writes.incrementAndGet();
                    } else {
                        adapter.traversal(randomNode(dataset), 1 + workerRandom.nextInt(3));
                        reads.incrementAndGet();
                    }
                    operations.incrementAndGet();
                }
            });
        }

        executor.shutdown();
        executor.awaitTermination(durationSeconds + 30L, TimeUnit.SECONDS);
        double qps = operations.get() / Math.max(1.0, durationSeconds);
        return new MixedWorkloadStats(operations.get(), reads.get(), writes.get(), qps);
    }

    private long randomNode(Dataset dataset) {
        return dataset.nodeIds().get(random.nextInt(dataset.nodeIds().size()));
    }

    private long randomBucket() {
        return random.nextInt(1_000);
    }

    @FunctionalInterface
    private interface Workload {
        void run();
    }
}

