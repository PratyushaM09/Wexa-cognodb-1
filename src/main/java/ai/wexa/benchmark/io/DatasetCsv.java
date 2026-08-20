package ai.wexa.benchmark.io;

import ai.wexa.benchmark.model.Dataset;
import ai.wexa.benchmark.model.Edge;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public final class DatasetCsv {
    private DatasetCsv() {
    }

    public static Dataset read(Path path) throws IOException {
        List<Edge> edges = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            String[] parts = trimmed.split("[,\\s]+");
            if (parts.length < 2 || !isLong(parts[0])) {
                continue;
            }
            edges.add(new Edge(Long.parseLong(parts[0]), Long.parseLong(parts[1])));
        }
        return new Dataset(edges);
    }

    public static void writeSynthetic(Path path, int nodes, int relationships) throws IOException {
        Files.createDirectories(path.toAbsolutePath().getParent());
        Random random = new Random(7L);
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write("source,target");
            writer.newLine();
            for (int i = 0; i < relationships; i++) {
                long source = 1 + random.nextInt(nodes);
                long target = 1 + random.nextInt(nodes);
                if (source == target) {
                    target = (target % nodes) + 1;
                }
                writer.write(source + "," + target);
                writer.newLine();
            }
        }
        System.out.println("Wrote synthetic smoke dataset: " + path);
    }

    private static boolean isLong(String value) {
        try {
            Long.parseLong(value);
            return true;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }
}

