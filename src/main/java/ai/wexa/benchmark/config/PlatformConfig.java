package ai.wexa.benchmark.config;

public record PlatformConfig(
        String name,
        String type,
        String uri,
        String username,
        String password,
        String database,
        int batchSize,
        String resourceSpec
) {
}

