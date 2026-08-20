package ai.wexa.benchmark.model;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public record Dataset(List<Edge> edges, List<Long> nodeIds) {
    public Dataset(List<Edge> edges) {
        this(List.copyOf(edges), nodes(edges));
    }

    public int nodeCount() {
        return nodeIds.size();
    }

    public int relationshipCount() {
        return edges.size();
    }

    private static List<Long> nodes(List<Edge> edges) {
        Set<Long> ids = new LinkedHashSet<>();
        for (Edge edge : edges) {
            ids.add(edge.source());
            ids.add(edge.target());
        }
        if (ids.isEmpty()) {
            throw new IllegalArgumentException("Dataset has no edges");
        }
        return new ArrayList<>(ids);
    }
}

