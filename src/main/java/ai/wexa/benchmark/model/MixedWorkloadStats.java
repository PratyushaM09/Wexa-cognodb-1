package ai.wexa.benchmark.model;

public record MixedWorkloadStats(long operations, long reads, long writes, double queriesPerSecond) {
    public String toHumanString() {
        return "ops=" + operations
                + " reads=" + reads
                + " writes=" + writes
                + " qps=" + format(queriesPerSecond);
    }

    public String toJson() {
        return "{"
                + "\"operations\":" + operations
                + ",\"reads\":" + reads
                + ",\"writes\":" + writes
                + ",\"queriesPerSecond\":" + format(queriesPerSecond)
                + "}";
    }

    private static String format(double value) {
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }
}

