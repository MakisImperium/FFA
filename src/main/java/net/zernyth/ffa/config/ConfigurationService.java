package net.zernyth.ffa.config;

import org.powernukkitx.plugin.PluginBase;
import net.zernyth.ffa.util.JsonFileService;

import java.io.IOException;
import java.nio.file.Path;

public final class ConfigurationService {

    private final PluginBase plugin;
    private final JsonFileService jsonFileService;
    private final Path configPath;

    public ConfigurationService(PluginBase plugin, JsonFileService jsonFileService) {
        this.plugin = plugin;
        this.jsonFileService = jsonFileService;
        this.configPath = plugin.getDataFolder().toPath().resolve("config.json");
    }

    /** Reads config.json for a reload; fails (keeping the running one) if it is missing or broken. */
    public PluginConfiguration readForReload() throws IOException {
        PluginConfiguration configuration = jsonFileService.read(configPath, PluginConfiguration.class)
                .orElseThrow(() -> new IOException("config.json is missing"));
        configuration.normalize();
        return configuration;
    }

    public PluginConfiguration loadOrCreate() throws IOException {
        PluginConfiguration configuration = jsonFileService.read(configPath, PluginConfiguration.class).orElse(null);
        if (configuration == null) {
            configuration = new PluginConfiguration();
            configuration.normalize();
            jsonFileService.write(configPath, configuration);
            plugin.getLogger().info("Created default config.json");
            return configuration;
        }

        configuration.normalize();
        addMissingSections(configuration);
        return configuration;
    }

    /**
     * A config.json from an older version lacks the newer sections (economy, events, ...). They are
     * added with their defaults so admins can see and change them; values already set stay as they are.
     */
    private void addMissingSections(PluginConfiguration configuration) {
        try {
            com.google.gson.JsonObject existing;
            try (java.io.Reader reader = java.nio.file.Files.newBufferedReader(configPath, java.nio.charset.StandardCharsets.UTF_8)) {
                existing = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
            }
            com.google.gson.JsonObject complete = jsonFileService.gson().toJsonTree(configuration).getAsJsonObject();
            java.util.List<String> missing = complete.keySet().stream().filter(key -> !existing.has(key)).toList();
            if (missing.isEmpty()) {
                return;
            }
            missing.forEach(key -> existing.add(key, complete.get(key)));
            jsonFileService.write(configPath, existing);
            plugin.getLogger().info("config.json: added new sections with defaults: " + String.join(", ", missing));
        } catch (Exception failure) {
            plugin.getLogger().warning("config.json: could not add new sections (" + failure.getMessage() + "); defaults are used.");
        }
    }
}


