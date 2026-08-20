package ai.wexa.benchmark.core;

import ai.wexa.benchmark.model.MetricSummary;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Percentiles {
    private Percentiles() {
    }

    public static MetricSummary summary(List<Long> nanos) {
        if (nanos.isEmpty()) {
            throw new IllegalArgumentException("Cannot summarize an empty sample");
        }
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        return new MetricSummary(
                sorted.size(),
                toMillis(sorted.get(0)),
                toMillis(percentile(sorted, 50)),
                toMillis(percentile(sorted, 95)),
                toMillis(sorted.get(sorted.size() - 1))
        );
    }

    static long percentile(List<Long> sorted, int percentile) {
        int index = (int) Math.ceil((percentile / 100.0) * sorted.size()) - 1;
        return sorted.get(Math.max(0, Math.min(index, sorted.size() - 1)));
    }

    private static double toMillis(long nanos) {
        return nanos / 1_000_000.0;
    }
}

