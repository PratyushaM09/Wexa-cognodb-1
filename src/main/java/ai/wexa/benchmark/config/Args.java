package ai.wexa.benchmark.config;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

public final class Args {
    private final Map<String, String> values;

    private Args(Map<String, String> values) {
        this.values = values;
    }

    public static Args parse(String[] args, int start) {
        Map<String, String> values = new HashMap<>();
        for (int i = start; i < args.length; i++) {
            String key = args[i];
            if (!key.startsWith("--")) {
                throw new IllegalArgumentException("Expected --key, got: " + key);
            }
            if (i + 1 >= args.length || args[i + 1].startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + key);
            }
            values.put(key.substring(2), args[++i]);
        }
        return new Args(values);
    }

    public String required(String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required argument --" + key);
        }
        return value;
    }

    public int integer(String key, int fallback) {
        String value = values.get(key);
        return value == null ? fallback : Integer.parseInt(value);
    }

    public long longValue(String key, long fallback) {
        String value = values.get(key);
        return value == null ? fallback : Long.parseLong(value);
    }

    public double doubleValue(String key, double fallback) {
        String value = values.get(key);
        return value == null ? fallback : Double.parseDouble(value);
    }

    public Path path(String key) {
        return Path.of(required(key));
    }

    public Path path(String key, Path fallback) {
        String value = values.get(key);
        return value == null ? fallback : Path.of(value);
    }
}

