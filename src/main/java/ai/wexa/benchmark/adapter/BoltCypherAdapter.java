package ai.wexa.benchmark.adapter;

import ai.wexa.benchmark.config.PlatformConfig;
import ai.wexa.benchmark.model.Dataset;
import ai.wexa.benchmark.model.Edge;
import ai.wexa.benchmark.model.LoadStats;
import ai.wexa.benchmark.model.ResourceStats;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class BoltCypherAdapter implements GraphAdapter {
    private final PlatformConfig config;
    private final Object driver;
    private final Class<?> queryConfigClass;

    public BoltCypherAdapter(PlatformConfig config) {
        this.config = config;
        try {
            Class<?> graphDatabaseClass = Class.forName("org.neo4j.driver.GraphDatabase");
            Class<?> authTokensClass = Class.forName("org.neo4j.driver.AuthTokens");
            Class<?> authTokenClass = Class.forName("org.neo4j.driver.AuthToken");
            this.queryConfigClass = Class.forName("org.neo4j.driver.QueryConfig");
            Object authToken = authTokensClass
                    .getMethod("basic", String.class, String.class)
                    .invoke(null, config.username(), config.password());
            this.driver = graphDatabaseClass
                    .getMethod("driver", String.class, authTokenClass)
                    .invoke(null, config.uri(), authToken);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Neo4j Java Driver is required at runtime for Bolt platforms", e);
        }
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public void verifyConnectivity() {
        invoke(driver, "verifyConnectivity");
    }

    @Override
    public LoadStats load(Dataset dataset) {
        long started = System.nanoTime();
        write("MATCH (n) DETACH DELETE n", Map.of());
        createSchema();

        List<Edge> edges = dataset.edges();
        int batchSize = config.batchSize();
        for (int offset = 0; offset < edges.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, edges.size());
            write("""
                    UNWIND $rows AS row
                    MERGE (a:Person {id: row.source})
                    ON CREATE SET a.bucket = row.source % 1000
                    MERGE (b:Person {id: row.target})
                    ON CREATE SET b.bucket = row.target % 1000
                    MERGE (a)-[:KNOWS]->(b)
                    """, Map.of("rows", rows(edges.subList(offset, end))));
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        return new LoadStats(name(), dataset.nodeCount(), dataset.relationshipCount(), elapsed);
    }

    @Override
    public void pointLookup(long id) {
        read("MATCH (p:Person {id: $id}) RETURN p.id LIMIT 1", Map.of("id", id));
    }

    @Override
    public void indexedLookup(long bucket) {
        read("MATCH (p:Person) WHERE p.bucket = $bucket RETURN count(p)", Map.of("bucket", bucket));
    }

    @Override
    public void traversal(long startId, int depth) {
        read("MATCH (:Person {id: $id})-[:KNOWS*" + depth + "]->(n) RETURN count(n)", Map.of("id", startId));
    }

    @Override
    public void aggregation() {
        read("MATCH (p:Person) RETURN p.bucket AS bucket, count(*) AS people ORDER BY people DESC LIMIT 20", Map.of());
    }

    @Override
    public void writeRelationship(long sourceId, long targetId) {
        write("""
                MERGE (a:Person {id: $source})
                ON CREATE SET a.bucket = $source % 1000
                MERGE (b:Person {id: $target})
                ON CREATE SET b.bucket = $target % 1000
                MERGE (a)-[:KNOWS]->(b)
                """, Map.of("source", sourceId, "target", targetId));
    }

    @Override
    public ResourceStats resourceStats() {
        return new ResourceStats(config.resourceSpec(), "not observable through the portable Bolt harness");
    }

    @Override
    public void close() {
        invoke(driver, "close");
    }

    private void createSchema() {
        writeBestEffort("CREATE CONSTRAINT person_id_unique IF NOT EXISTS FOR (p:Person) REQUIRE p.id IS UNIQUE");
        writeBestEffort("CREATE INDEX person_bucket IF NOT EXISTS FOR (p:Person) ON (p.bucket)");
    }

    private void writeBestEffort(String cypher) {
        try {
            write(cypher, Map.of());
        } catch (RuntimeException ignored) {
            System.err.println("Schema statement skipped for " + name() + ": " + cypher);
        }
    }

    private void write(String cypher, Map<String, Object> params) {
        execute(cypher, params);
    }

    private void read(String cypher, Map<String, Object> params) {
        execute(cypher, params);
    }

    private void execute(String cypher, Map<String, Object> params) {
        Object query = invoke(driver, "executableQuery", new Class<?>[]{String.class}, cypher);
        if (!config.database().isBlank()) {
            Object builder = invokeStatic(queryConfigClass, "builder");
            invoke(builder, "withDatabase", new Class<?>[]{String.class}, config.database());
            Object queryConfig = invoke(builder, "build");
            query = invoke(query, "withConfig", new Class<?>[]{queryConfigClass}, queryConfig);
        }
        query = invoke(query, "withParameters", new Class<?>[]{Map.class}, params);
        invoke(query, "execute");
    }

    private static List<Map<String, Object>> rows(List<Edge> edges) {
        List<Map<String, Object>> rows = new ArrayList<>(edges.size());
        for (Edge edge : edges) {
            Map<String, Object> row = new HashMap<>();
            row.put("source", edge.source());
            row.put("target", edge.target());
            rows.add(row);
        }
        return rows;
    }

    private static Object invokeStatic(Class<?> target, String methodName) {
        try {
            return target.getMethod(methodName).invoke(null);
        } catch (ReflectiveOperationException e) {
            throw unwrap(e);
        }
    }

    private static Object invoke(Object target, String methodName) {
        return invoke(target, methodName, new Class<?>[0]);
    }

    private static Object invoke(Object target, String methodName, Class<?>[] parameterTypes, Object... args) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterTypes);
            return method.invoke(target, args);
        } catch (ReflectiveOperationException e) {
            throw unwrap(e);
        }
    }

    private static RuntimeException unwrap(ReflectiveOperationException e) {
        if (e instanceof InvocationTargetException invocation && invocation.getCause() != null) {
            Throwable cause = invocation.getCause();
            if (cause instanceof RuntimeException runtimeException) {
                return runtimeException;
            }
            return new RuntimeException(cause);
        }
        return new RuntimeException(e);
    }
}
