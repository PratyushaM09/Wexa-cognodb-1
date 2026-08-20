package ai.wexa.benchmark.adapter;

import ai.wexa.benchmark.model.Dataset;
import ai.wexa.benchmark.model.LoadStats;
import ai.wexa.benchmark.model.ResourceStats;

public interface GraphAdapter extends AutoCloseable {
    String name();

    void verifyConnectivity();

    LoadStats load(Dataset dataset);

    void pointLookup(long id);

    void indexedLookup(long bucket);

    void traversal(long startId, int depth);

    void aggregation();

    void writeRelationship(long sourceId, long targetId);

    ResourceStats resourceStats();

    @Override
    void close();
}

