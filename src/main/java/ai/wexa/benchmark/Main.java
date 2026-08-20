package ai.wexa.benchmark;

import ai.wexa.benchmark.adapter.AdapterFactory;
import ai.wexa.benchmark.adapter.GraphAdapter;
import ai.wexa.benchmark.config.Args;
import ai.wexa.benchmark.config.PlatformConfig;
import ai.wexa.benchmark.config.Platforms;
import ai.wexa.benchmark.core.BenchmarkRunner;
import ai.wexa.benchmark.core.SelfTest;
import ai.wexa.benchmark.io.DatasetCsv;
import ai.wexa.benchmark.io.ResultsWriter;
import ai.wexa.benchmark.model.BenchmarkResult;
import ai.wexa.benchmark.model.Dataset;
import ai.wexa.benchmark.model.LoadStats;

import java.nio.file.Path;

public final class Main {
    private Main() {
    }

    public static void main(String[] rawArgs) throws Exception {
        if (rawArgs.length == 0 || "--help".equals(rawArgs[0])) {
            printHelp();
            return;
        }

        String command = rawArgs[0];
        Args args = Args.parse(rawArgs, 1);

        switch (command) {
            case "selftest" -> SelfTest.run();
            case "sample" -> DatasetCsv.writeSynthetic(
                    args.path("edges"),
                    args.integer("nodes", 1_000),
                    args.integer("relationships", 5_000)
            );
            case "load" -> withAdapter(args, (adapter, dataset, runner) -> {
                LoadStats stats = adapter.load(dataset);
                System.out.println(stats.toHumanString());
                return null;
            });
            case "run" -> withAdapter(args, (adapter, dataset, runner) -> {
                BenchmarkResult result = runner.run(adapter, dataset);
                ResultsWriter.write(result, args.path("out", Path.of("results/raw")));
                System.out.println(result.toHumanString());
                return null;
            });
            case "all" -> withAdapter(args, (adapter, dataset, runner) -> {
                LoadStats loadStats = adapter.load(dataset);
                BenchmarkResult result = runner.run(adapter, dataset).withLoadStats(loadStats);
                ResultsWriter.write(result, args.path("out", Path.of("results/raw")));
                System.out.println(result.toHumanString());
                return null;
            });
            default -> throw new IllegalArgumentException("Unknown command: " + command);
        }
    }

    private static <T> T withAdapter(Args args, AdapterWork<T> work) throws Exception {
        Path configPath = args.path("config", Path.of("config/platforms.properties"));
        String platformName = args.required("platform");
        PlatformConfig platform = Platforms.load(configPath).get(platformName);
        Dataset dataset = DatasetCsv.read(args.path("edges"));
        BenchmarkRunner runner = BenchmarkRunner.fromArgs(args);

        try (GraphAdapter adapter = AdapterFactory.create(platform)) {
            adapter.verifyConnectivity();
            return work.run(adapter, dataset, runner);
        }
    }

    private static void printHelp() {
        System.out.println("""
                Usage:
                  java -jar target/cognodb-cloud-benchmark-0.1.0.jar sample --edges data/smoke.csv --nodes 1000 --relationships 5000
                  java -jar target/cognodb-cloud-benchmark-0.1.0.jar selftest
                  java -jar target/cognodb-cloud-benchmark-0.1.0.jar load --config config/platforms.properties --platform cognodb --edges data/pokec.csv
                  java -jar target/cognodb-cloud-benchmark-0.1.0.jar run  --config config/platforms.properties --platform cognodb --edges data/pokec.csv --iterations 100 --warmup 20
                  java -jar target/cognodb-cloud-benchmark-0.1.0.jar all  --config config/platforms.properties --platform cognodb --edges data/pokec.csv --iterations 100 --warmup 20
                """);
    }

    @FunctionalInterface
    private interface AdapterWork<T> {
        T run(GraphAdapter adapter, Dataset dataset, BenchmarkRunner runner) throws Exception;
    }
}
