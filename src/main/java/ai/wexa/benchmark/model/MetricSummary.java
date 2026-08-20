package ai.wexa.benchmark.model;

public record MetricSummary(int samples, double minMs, double p50Ms, double p95Ms, double maxMs) {
    public String toHumanString() {
        return "samples=" + samples
                + " p50_ms=" + format(p50Ms)
                + " p95_ms=" + format(p95Ms)
                + " min_ms=" + format(minMs)
                + " max_ms=" + format(maxMs);
    }

    public String toJson() {
        return "{"
                + "\"samples\":" + samples
                + ",\"minMs\":" + format(minMs)
                + ",\"p50Ms\":" + format(p50Ms)
                + ",\"p95Ms\":" + format(p95Ms)
                + ",\"maxMs\":" + format(maxMs)
                + "}";
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}

