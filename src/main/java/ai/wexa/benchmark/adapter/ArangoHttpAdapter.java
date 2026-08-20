package ai.wexa.benchmark.adapter;

import ai.wexa.benchmark.config.PlatformConfig;
import ai.wexa.benchmark.model.Dataset;
import ai.wexa.benchmark.model.Edge;
import ai.wexa.benchmark.model.LoadStats;
import ai.wexa.benchmark.model.ResourceStats;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;

public final class ArangoHttpAdapter implements GraphAdapter {
    private static final String PEOPLE = "people";
    private static final String KNOWS = "knows";

    private final PlatformConfig config;
    private final HttpClient httpClient;
    private final String baseUri;
    private final String authorization;

    public ArangoHttpAdapter(PlatformConfig config) {
        this.config = config;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
        this.baseUri = stripTrailingSlash(config.uri());
        this.authorization = "Basic " + Base64.getEncoder().encodeToString(
                (config.username() + ":" + config.password()).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public void verifyConnectivity() {
        get("/_api/version", status -> status >= 200 && status < 300);
    }

    @Override
    public LoadStats load(Dataset dataset) {
        long started = System.nanoTime();
        ensureDatabase();
        ensureCollection(PEOPLE, 2);
        ensureCollection(KNOWS, 3);
        truncate(KNOWS);
        truncate(PEOPLE);
        createSchema();

        List<Edge> edges = dataset.edges();
        int batchSize = config.batchSize();
        for (int offset = 0; offset < edges.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, edges.size());
            upsertBatch(edges.subList(offset, end));
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        return new LoadStats(name(), dataset.nodeCount(), dataset.relationshipCount(), elapsed);
    }

    @Override
    public void pointLookup(long id) {
        aql("FOR p IN people FILTER p.id == @id LIMIT 1 RETURN p.id", Map.of("id", id));
    }

    @Override
    public void indexedLookup(long bucket) {
        aql("FOR p IN people FILTER p.bucket == @bucket COLLECT WITH COUNT INTO c RETURN c", Map.of("bucket", bucket));
    }

    @Override
    public void traversal(long startId, int depth) {
        aql("FOR v IN " + depth + ".." + depth + " OUTBOUND @start knows COLLECT WITH COUNT INTO c RETURN c",
                Map.of("start", PEOPLE + "/" + key(startId)));
    }

    @Override
    public void aggregation() {
        aql("""
                FOR p IN people
                COLLECT bucket = p.bucket WITH COUNT INTO people
                SORT people DESC
                LIMIT 20
                RETURN { bucket: bucket, people: people }
                """, Map.of());
    }

    @Override
    public void writeRelationship(long sourceId, long targetId) {
        upsertBatch(List.of(new Edge(sourceId, targetId)));
    }

    @Override
    public ResourceStats resourceStats() {
        return new ResourceStats(config.resourceSpec(), "not observable through the portable ArangoDB HTTP harness");
    }

    @Override
    public void close() {
    }

    private void ensureDatabase() {
        post("/_api/database", Map.of("name", database()), status -> (status >= 200 && status < 300) || status == 409);
    }

    private void ensureCollection(String name, int type) {
        post(dbPath("/_api/collection"), Map.of("name", name, "type", type),
                status -> (status >= 200 && status < 300) || status == 409);
    }

    private void truncate(String collection) {
        put(dbPath("/_api/collection/" + encode(collection) + "/truncate"), Map.of(),
                status -> status >= 200 && status < 300);
    }

    private void createSchema() {
        post(dbPath("/_api/index?collection=" + encode(PEOPLE)),
                Map.of("type", "persistent", "fields", List.of("id"), "unique", true),
                status -> status >= 200 && status < 300 || status == 409);
        post(dbPath("/_api/index?collection=" + encode(PEOPLE)),
                Map.of("type", "persistent", "fields", List.of("bucket")),
                status -> status >= 200 && status < 300 || status == 409);
    }

    private void upsertBatch(List<Edge> edges) {
        Map<Long, Map<String, Object>> nodes = new LinkedHashMap<>();
        Map<String, Map<String, Object>> relationships = new LinkedHashMap<>();
        for (Edge edge : edges) {
            nodes.putIfAbsent(edge.source(), node(edge.source()));
            nodes.putIfAbsent(edge.target(), node(edge.target()));
            relationships.putIfAbsent(key(edge.source()) + ":" + key(edge.target()), relationship(edge));
        }

        aql("""
                FOR row IN @rows
                UPSERT { _key: row.key }
                INSERT { _key: row.key, id: row.id, bucket: row.bucket }
                UPDATE {}
                IN people
                """, Map.of("rows", new ArrayList<>(nodes.values())));
        aql("""
                FOR row IN @rows
                UPSERT { _from: row.from, _to: row.to }
                INSERT { _from: row.from, _to: row.to, type: "KNOWS" }
                UPDATE {}
                IN knows
                """, Map.of("rows", new ArrayList<>(relationships.values())));
    }

    private Map<String, Object> node(long id) {
        return Map.of("key", key(id), "id", id, "bucket", id % 1_000);
    }

    private Map<String, Object> relationship(Edge edge) {
        return Map.of(
                "from", PEOPLE + "/" + key(edge.source()),
                "to", PEOPLE + "/" + key(edge.target())
        );
    }

    private void aql(String query, Map<String, Object> bindVars) {
        post(dbPath("/_api/cursor"), Map.of("query", query, "bindVars", bindVars), status -> status >= 200 && status < 300);
    }

    private String database() {
        return config.database().isBlank() ? "_system" : config.database();
    }

    private String dbPath(String path) {
        return "/_db/" + encode(database()) + path;
    }

    private String get(String path, IntPredicate ok) {
        HttpRequest request = request(path).GET().build();
        return send(request, ok);
    }

    private String post(String path, Map<String, Object> body, IntPredicate ok) {
        HttpRequest request = request(path)
                .POST(HttpRequest.BodyPublishers.ofString(json(body), StandardCharsets.UTF_8))
                .build();
        return send(request, ok);
    }

    private String put(String path, Map<String, Object> body, IntPredicate ok) {
        HttpRequest request = request(path)
                .PUT(HttpRequest.BodyPublishers.ofString(json(body), StandardCharsets.UTF_8))
                .build();
        return send(request, ok);
    }

    private HttpRequest.Builder request(String path) {
        return HttpRequest.newBuilder(URI.create(baseUri + path))
                .timeout(Duration.ofMinutes(2))
                .header("Authorization", authorization)
                .header("Content-Type", "application/json");
    }

    private String send(HttpRequest request, IntPredicate ok) {
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (!ok.test(response.statusCode())) {
                throw new IllegalStateException("ArangoDB request failed: HTTP "
                        + response.statusCode() + " " + response.body());
            }
            return response.body();
        } catch (IOException e) {
            throw new IllegalStateException("ArangoDB request failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("ArangoDB request interrupted", e);
        }
    }

    private static String key(long id) {
        return "n" + id;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private static String json(Object value) {
        if (value == null) {
            return "null";
        }
        if (value instanceof String string) {
            return "\"" + escape(string) + "\"";
        }
        if (value instanceof Number || value instanceof Boolean) {
            return value.toString();
        }
        if (value instanceof Map<?, ?> map) {
            List<String> fields = new ArrayList<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                fields.add(json(entry.getKey().toString()) + ":" + json(entry.getValue()));
            }
            return "{" + String.join(",", fields) + "}";
        }
        if (value instanceof Collection<?> collection) {
            List<String> items = new ArrayList<>();
            for (Object item : collection) {
                items.add(json(item));
            }
            return "[" + String.join(",", items) + "]";
        }
        throw new IllegalArgumentException("Unsupported JSON value: " + value.getClass().getName());
    }

    private static String escape(String value) {
        StringBuilder escaped = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> escaped.append("\\\\");
                case '"' -> escaped.append("\\\"");
                case '\n' -> escaped.append("\\n");
                case '\r' -> escaped.append("\\r");
                case '\t' -> escaped.append("\\t");
                default -> escaped.append(c);
            }
        }
        return escaped.toString();
    }
}
