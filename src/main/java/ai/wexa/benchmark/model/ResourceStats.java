package ai.wexa.benchmark.model;

import ai.wexa.benchmark.io.ResultsWriter;

public record ResourceStats(String advertisedSpec, String observedUsage) {
    public String toJson() {
        return "{"
                + "\"advertisedSpec\":\"" + ResultsWriter.escape(advertisedSpec) + "\""
                + ",\"observedUsage\":\"" + ResultsWriter.escape(observedUsage) + "\""
                + "}";
    }
}

