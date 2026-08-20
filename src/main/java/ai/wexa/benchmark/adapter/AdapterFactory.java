package ai.wexa.benchmark.adapter;

import ai.wexa.benchmark.config.PlatformConfig;

public final class AdapterFactory {
    private AdapterFactory() {
    }

    public static GraphAdapter create(PlatformConfig config) {
        return switch (config.type()) {
            case "arangodb" -> new ArangoHttpAdapter(config);
            case "bolt" -> new BoltCypherAdapter(config);
            case "falkordb" -> new FalkorDbAdapter(config);
            default -> throw new IllegalArgumentException("Unsupported adapter type: " + config.type());
        };
    }
}
