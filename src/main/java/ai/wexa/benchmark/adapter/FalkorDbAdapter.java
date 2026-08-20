package ai.wexa.benchmark.adapter;

import ai.wexa.benchmark.config.PlatformConfig;
import ai.wexa.benchmark.model.Dataset;
import ai.wexa.benchmark.model.Edge;
import ai.wexa.benchmark.model.LoadStats;
import ai.wexa.benchmark.model.ResourceStats;

import javax.net.ssl.SSLSocketFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.net.Socket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FalkorDbAdapter implements GraphAdapter {
    private final PlatformConfig config;
    private final URI uri;
    private final ThreadLocal<Connection> threadConnection;
    private final List<Connection> connections = Collections.synchronizedList(new ArrayList<>());

    public FalkorDbAdapter(PlatformConfig config) {
        this.config = config;
        this.uri = URI.create(config.uri());
        this.threadConnection = ThreadLocal.withInitial(this::newConnection);
    }

    @Override
    public String name() {
        return config.name();
    }

    @Override
    public void verifyConnectivity() {
        command("PING");
    }

    @Override
    public LoadStats load(Dataset dataset) {
        long started = System.nanoTime();
        try {
            command("GRAPH.DELETE", graph());
        } catch (RuntimeException ignored) {
            System.err.println("No existing FalkorDB graph to delete for " + name());
        }
        createSchema();

        List<Edge> edges = dataset.edges();
        int batchSize = config.batchSize();
        for (int offset = 0; offset < edges.size(); offset += batchSize) {
            int end = Math.min(offset + batchSize, edges.size());
            query(loadQuery(edges.subList(offset, end)));
        }

        Duration elapsed = Duration.ofNanos(System.nanoTime() - started);
        return new LoadStats(name(), dataset.nodeCount(), dataset.relationshipCount(), elapsed);
    }

    @Override
    public void pointLookup(long id) {
        query("MATCH (p:Person {id: " + id + "}) RETURN p.id LIMIT 1");
    }

    @Override
    public void indexedLookup(long bucket) {
        query("MATCH (p:Person) WHERE p.bucket = " + bucket + " RETURN count(p)");
    }

    @Override
    public void traversal(long startId, int depth) {
        query("MATCH (:Person {id: " + startId + "})-[:KNOWS*" + depth + "]->(n) RETURN count(n)");
    }

    @Override
    public void aggregation() {
        query("MATCH (p:Person) RETURN p.bucket AS bucket, count(*) AS people ORDER BY people DESC LIMIT 20");
    }

    @Override
    public void writeRelationship(long sourceId, long targetId) {
        query(loadQuery(List.of(new Edge(sourceId, targetId))));
    }

    @Override
    public ResourceStats resourceStats() {
        return new ResourceStats(config.resourceSpec(), "not observable through the portable FalkorDB RESP harness");
    }

    @Override
    public void close() {
        synchronized (connections) {
            for (Connection connection : connections) {
                connection.close();
            }
            connections.clear();
        }
    }

    private void createSchema() {
        bestEffort("CREATE INDEX FOR (p:Person) ON (p.id)");
        bestEffort("CREATE INDEX FOR (p:Person) ON (p.bucket)");
        bestEffort("CREATE INDEX ON :Person(id)");
        bestEffort("CREATE INDEX ON :Person(bucket)");
    }

    private void bestEffort(String cypher) {
        try {
            query(cypher);
        } catch (RuntimeException ignored) {
            System.err.println("Schema statement skipped for " + name() + ": " + cypher);
        }
    }

    private void query(String cypher) {
        command("GRAPH.QUERY", graph(), cypher, "--compact");
    }

    private Object command(String... args) {
        return threadConnection.get().command(args);
    }

    private String graph() {
        return config.database().isBlank() ? "wexa_benchmark" : config.database();
    }

    private String loadQuery(List<Edge> edges) {
        StringBuilder rows = new StringBuilder();
        for (Edge edge : edges) {
            if (!rows.isEmpty()) {
                rows.append(", ");
            }
            rows.append("{source: ")
                    .append(edge.source())
                    .append(", target: ")
                    .append(edge.target())
                    .append(", sourceBucket: ")
                    .append(edge.source() % 1_000)
                    .append(", targetBucket: ")
                    .append(edge.target() % 1_000)
                    .append("}");
        }
        return "UNWIND [" + rows + "] AS row "
                + "MERGE (a:Person {id: row.source}) SET a.bucket = row.sourceBucket "
                + "MERGE (b:Person {id: row.target}) SET b.bucket = row.targetBucket "
                + "MERGE (a)-[:KNOWS]->(b)";
    }

    private Connection newConnection() {
        try {
            Connection connection = new Connection(uri, config.username(), config.password());
            connections.add(connection);
            return connection;
        } catch (IOException e) {
            throw new IllegalStateException("Cannot connect to FalkorDB", e);
        }
    }

    private static final class Connection implements Closeable {
        private final Socket socket;
        private final BufferedInputStream input;
        private final BufferedOutputStream output;

        private Connection(URI uri, String username, String password) throws IOException {
            int port = uri.getPort() > 0 ? uri.getPort() : defaultPort(uri);
            if ("rediss".equalsIgnoreCase(uri.getScheme())) {
                this.socket = SSLSocketFactory.getDefault().createSocket(uri.getHost(), port);
            } else {
                this.socket = new Socket(uri.getHost(), port);
            }
            this.socket.setSoTimeout(120_000);
            this.input = new BufferedInputStream(socket.getInputStream());
            this.output = new BufferedOutputStream(socket.getOutputStream());
            if (!password.isBlank()) {
                if (username.isBlank()) {
                    command("AUTH", password);
                } else {
                    command("AUTH", username, password);
                }
            }
        }

        private Object command(String... args) {
            try {
                writeCommand(args);
                return readValue();
            } catch (IOException e) {
                throw new IllegalStateException("FalkorDB command failed", e);
            }
        }

        private void writeCommand(String... args) throws IOException {
            output.write(("*" + args.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            for (String arg : args) {
                byte[] bytes = arg.getBytes(StandardCharsets.UTF_8);
                output.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
                output.write(bytes);
                output.write("\r\n".getBytes(StandardCharsets.UTF_8));
            }
            output.flush();
        }

        private Object readValue() throws IOException {
            int marker = input.read();
            if (marker < 0) {
                throw new IOException("Connection closed");
            }
            return switch (marker) {
                case '+' -> readLine();
                case '-' -> throw new IllegalStateException(readLine());
                case ':' -> Long.parseLong(readLine());
                case '$' -> readBulk();
                case '*' -> readArray();
                default -> throw new IOException("Unexpected RESP marker: " + (char) marker);
            };
        }

        private String readBulk() throws IOException {
            int length = Integer.parseInt(readLine());
            if (length < 0) {
                return null;
            }
            byte[] bytes = input.readNBytes(length);
            readCrLf();
            return new String(bytes, StandardCharsets.UTF_8);
        }

        private List<Object> readArray() throws IOException {
            int length = Integer.parseInt(readLine());
            if (length < 0) {
                return List.of();
            }
            List<Object> values = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                values.add(readValue());
            }
            return values;
        }

        private String readLine() throws IOException {
            StringBuilder line = new StringBuilder();
            while (true) {
                int value = input.read();
                if (value < 0) {
                    throw new IOException("Connection closed");
                }
                if (value == '\r') {
                    int next = input.read();
                    if (next != '\n') {
                        throw new IOException("Malformed RESP line ending");
                    }
                    return line.toString();
                }
                line.append((char) value);
            }
        }

        private void readCrLf() throws IOException {
            if (input.read() != '\r' || input.read() != '\n') {
                throw new IOException("Malformed RESP bulk string ending");
            }
        }

        @Override
        public void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }

        private static int defaultPort(URI uri) {
            return "rediss".equalsIgnoreCase(uri.getScheme()) ? 6380 : 6379;
        }
    }
}
