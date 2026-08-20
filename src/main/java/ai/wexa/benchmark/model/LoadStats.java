package ai.wexa.benchmark.model;

import java.time.Duration;

public record LoadStats(String platform, int nodes, int relationships, Duration elapsed) {
    public double nodesPerSecond() {
        return nodes / seconds();
    }

    public double relationshipsPerSecond() {
        return relationships / seconds();
    }

    public String toHumanString() {
        return "load: nodes/s=" + format(nodesPerSecond())
                + " relationships/s=" + format(relationshipsPerSecond())
                + " seconds=" + format(seconds());
    }

    public String toJson() {
        return "{"
                + "\"nodes\":" + nodes
                + ",\"relationships\":" + relationships
                + ",\"seconds\":" + format(seconds())
                + ",\"nodesPerSecond\":" + format(nodesPerSecond())
                + ",\"relationshipsPerSecond\":" + format(relationshipsPerSecond())
                + "}";
    }

    private double seconds() {
        return Math.max(0.001, elapsed.toNanos() / 1_000_000_000.0);
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}

