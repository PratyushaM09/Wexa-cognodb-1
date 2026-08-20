package ai.wexa.benchmark.io;

import ai.wexa.benchmark.model.BenchmarkResult;
import ai.wexa.benchmark.model.MetricSummary;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class ResultsWriter {
    private ResultsWriter() {
    }

    public static void write(BenchmarkResult result, Path directory) throws IOException {
        Files.createDirectories(directory);
        String base = result.platform() + "-" + result.startedAt().toString().replace(":", "").replace(".", "");
        Path json = directory.resolve(base + ".json");
        Path csv = directory.resolve(base + ".csv");
        Files.writeString(json, toJson(result));
        Files.writeString(csv, toCsv(result));
        System.out.println("Wrote " + json);
        System.out.println("Wrote " + csv);
    }

    private static String toCsv(BenchmarkResult result) {
        StringBuilder out = new StringBuilder("platform,metric,samples,min_ms,p50_ms,p95_ms,max_ms\n");
        for (Map.Entry<String, MetricSummary> entry : result.metrics().entrySet()) {
            MetricSummary metric = entry.getValue();
            out.append(result.platform()).append(',')
                    .append(entry.getKey()).append(',')
                    .append(metric.samples()).append(',')
                    .append(format(metric.minMs())).append(',')
                    .append(format(metric.p50Ms())).append(',')
                    .append(format(metric.p95Ms())).append(',')
                    .append(format(metric.maxMs())).append('\n');
        }
        return out.toString();
    }

    private static String toJson(BenchmarkResult result) {
        StringBuilder out = new StringBuilder();
        out.append("{\n");
        field(out, "platform", result.platform(), true);
        field(out, "startedAt", result.startedAt().toString(), true);
        field(out, "nodeCount", result.nodeCount(), true);
        field(out, "relationshipCount", result.relationshipCount(), true);
        field(out, "iterations", result.iterations(), true);
        field(out, "warmup", result.warmup(), true);
        field(out, "concurrency", result.concurrency(), true);
        field(out, "durationSeconds", result.durationSeconds(), true);
        field(out, "writeRatio", result.writeRatio(), true);
        out.append("  \"loadStats\": ");
        if (result.loadStats() == null) {
            out.append("null,\n");
        } else {
            out.append(result.loadStats().toJson()).append(",\n");
        }
        out.append("  \"metrics\": {\n");
        int i = 0;
        for (Map.Entry<String, MetricSummary> entry : result.metrics().entrySet()) {
            out.append("    \"").append(escape(entry.getKey())).append("\": ")
                    .append(entry.getValue().toJson())
                    .append(++i < result.metrics().size() ? "," : "")
                    .append('\n');
        }
        out.append("  },\n");
        out.append("  \"mixedWorkload\": ").append(result.mixedWorkload().toJson()).append(",\n");
        out.append("  \"resourceStats\": ").append(result.resourceStats().toJson()).append('\n');
        out.append("}\n");
        return out.toString();
    }

    private static void field(StringBuilder out, String key, String value, boolean comma) {
        out.append("  \"").append(key).append("\": \"").append(escape(value)).append('"').append(comma ? "," : "").append('\n');
    }

    private static void field(StringBuilder out, String key, long value, boolean comma) {
        out.append("  \"").append(key).append("\": ").append(value).append(comma ? "," : "").append('\n');
    }

    private static void field(StringBuilder out, String key, double value, boolean comma) {
        out.append("  \"").append(key).append("\": ").append(format(value)).append(comma ? "," : "").append('\n');
    }

    public static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}

