package ai.wexa.benchmark.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public final class Platforms {
    private Platforms() {
    }

    public static Map<String, PlatformConfig> load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            properties.load(reader);
        }

        Map<String, PlatformConfig> configs = new HashMap<>();
        for (String name : required(properties, "platforms").split(",")) {
            String platform = name.trim();
            configs.put(platform, new PlatformConfig(
                    platform,
                    value(properties, platform, "type", "bolt"),
                    value(properties, platform, "uri", ""),
                    value(properties, platform, "username", ""),
                    value(properties, platform, "password", ""),
                    value(properties, platform, "database", ""),
                    Integer.parseInt(value(properties, platform, "batchSize", "500")),
                    value(properties, platform, "resourceSpec", "not documented")
            ));
        }
        return configs;
    }

    private static String value(Properties properties, String platform, String key, String fallback) {
        return interpolate(properties.getProperty(platform + "." + key, fallback));
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing config property: " + key);
        }
        return value;
    }

    private static String interpolate(String value) {
        String result = value;
        int start = result.indexOf("${");
        while (start >= 0) {
            int end = result.indexOf('}', start);
            if (end < 0) {
                break;
            }
            String name = result.substring(start + 2, end);
            String replacement = System.getenv(name);
            if (replacement == null) {
                throw new IllegalArgumentException("Missing environment variable: " + name);
            }
            result = result.substring(0, start) + replacement + result.substring(end + 1);
            start = result.indexOf("${");
        }
        return result;
    }
}

