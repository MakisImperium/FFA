package net.zernyth.ffa.i18n;

import org.powernukkitx.plugin.PluginBase;
import net.zernyth.ffa.player.model.PlayerLanguage;
import net.zernyth.ffa.util.JsonFileService;

import java.io.IOException;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LocalizationService {

    private final PluginBase plugin;
    private final JsonFileService jsonFileService;
    private final Path languageDirectory;
    private final Path englishPath;
    private final Path germanPath;
    private final Object translationLock = new Object();
    /** New keys are written in the background, a moment after the last one: menus never wait for the disk. */
    private final ScheduledExecutorService writer = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "ffa-lang-writer");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean writeScheduled = new AtomicBoolean(false);

    private Map<String, String> englishTranslations = Map.of();
    private Map<String, String> germanTranslations = Map.of();

    public LocalizationService(PluginBase plugin, JsonFileService jsonFileService, Path dataDirectory) {
        this.plugin = plugin;
        this.jsonFileService = jsonFileService;
        this.languageDirectory = dataDirectory.resolve("lang");
        this.englishPath = languageDirectory.resolve("en.json");
        this.germanPath = languageDirectory.resolve("de.json");
    }

    public void loadOrCreate() throws IOException {
        jsonFileService.ensureDirectory(languageDirectory);
        LanguageBundle english = loadOrCreateBundle(englishPath, defaultEnglishTranslations());
        LanguageBundle german = loadOrCreateBundle(germanPath, defaultGermanTranslations());
        this.englishTranslations = english.translations == null ? Map.of() : new HashMap<>(english.translations);
        this.germanTranslations = german.translations == null ? Map.of() : new HashMap<>(german.translations);
    }

    /**
     * Reads both language files (off the main thread) and returns the step that swaps them in, to run
     * on the main thread.
     */
    public Runnable prepareReload() throws IOException {
        LanguageBundle english = loadOrCreateBundle(englishPath, defaultEnglishTranslations());
        LanguageBundle german = loadOrCreateBundle(germanPath, defaultGermanTranslations());
        Map<String, String> nextEnglish = english.translations == null ? new HashMap<>() : new HashMap<>(english.translations);
        Map<String, String> nextGerman = german.translations == null ? new HashMap<>() : new HashMap<>(german.translations);
        return () -> {
            synchronized (translationLock) {
                this.englishTranslations = nextEnglish;
                this.germanTranslations = nextGerman;
            }
        };
    }

    public String resolve(PlayerLanguage language, String englishFallback, String germanFallback) {
        ensureTranslationKey(englishFallback, germanFallback);
        if (language == PlayerLanguage.GERMAN) {
            return germanTranslations.getOrDefault(englishFallback, germanFallback);
        }
        return englishTranslations.getOrDefault(englishFallback, englishFallback);
    }

    private void ensureTranslationKey(String englishFallback, String germanFallback) {
        if (englishFallback == null || englishFallback.isBlank()) {
            return;
        }

        synchronized (translationLock) {
            boolean changed = false;
            if (!englishTranslations.containsKey(englishFallback)) {
                englishTranslations.put(englishFallback, englishFallback);
                changed = true;
            }
            if (!germanTranslations.containsKey(englishFallback)) {
                germanTranslations.put(englishFallback, germanFallback == null ? englishFallback : germanFallback);
                changed = true;
            }
            if (!changed) {
                return;
            }

            if (writeScheduled.compareAndSet(false, true)) {
                writer.schedule(this::writeBundles, 2, TimeUnit.SECONDS);
            }
        }
    }

    private void writeBundles() {
        writeScheduled.set(false);
        Map<String, String> english;
        Map<String, String> german;
        synchronized (translationLock) {
            english = new HashMap<>(englishTranslations);
            german = new HashMap<>(germanTranslations);
        }
        try {
            writeBundle(englishPath, english);
            writeBundle(germanPath, german);
        } catch (IOException exception) {
            plugin.getLogger().warning("Failed to persist language bundle updates: " + exception.getMessage());
        }
    }

    /** Writes pending keys now and stops the writer; on disable. */
    public void shutdown() {
        writer.shutdownNow();
        if (writeScheduled.getAndSet(false)) {
            writeBundles();
        }
    }

    private LanguageBundle loadOrCreateBundle(Path path, Map<String, String> defaultTranslations) throws IOException {
        LanguageBundle bundle = jsonFileService.read(path, LanguageBundle.class).orElse(null);
        Map<String, String> merged = new HashMap<>();
        if (bundle != null && bundle.translations != null) {
            merged.putAll(bundle.translations);
        }

        boolean changed = false;
        for (Map.Entry<String, String> entry : defaultTranslations.entrySet()) {
            if (!merged.containsKey(entry.getKey())) {
                merged.put(entry.getKey(), entry.getValue());
                changed = true;
            }
        }

        if (bundle == null || bundle.translations == null) {
            changed = true;
        }

        LanguageBundle normalized = new LanguageBundle();
        normalized.translations = merged;

        if (changed) {
            jsonFileService.write(path, normalized);
            plugin.getLogger().info((bundle == null ? "Created" : "Updated") + " language bundle " + path.getFileName());
        }
        return normalized;
    }

    private void writeBundle(Path path, Map<String, String> translations) throws IOException {
        LanguageBundle bundle = new LanguageBundle();
        bundle.translations = new HashMap<>(translations);
        jsonFileService.write(path, bundle);
    }

    private Map<String, String> defaultEnglishTranslations() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("FFA Control Center", "FFA Control Center");
        defaults.put("Arena Hub", "Arena Hub");
        defaults.put("Arena Session", "Arena Session");
        defaults.put("Statistics", "Statistics");
        defaults.put("Standby", "Standby");
        defaults.put("Live", "Live");
        defaults.put("Offline", "Offline");
        defaults.put("Maintenance", "Maintenance");
        defaults.put("Open", "Open");
        defaults.put("Closed", "Closed");
        defaults.put("Ready", "Ready");
        defaults.put("Not Ready", "Not Ready");
        defaults.put("Readiness", "Readiness");
        defaults.put("Unknown readiness error.", "Unknown readiness error.");
        defaults.put("Arena is not ready for Live: ", "Arena is not ready for Live: ");
        defaults.put("Arena not found.", "Arena not found.");
        defaults.put("No spawn points configured.", "No spawn points configured.");
        defaults.put("World name is missing.", "World name is missing.");
        defaults.put("Safe-zone reference is invalid.", "Safe-zone reference is invalid.");
        return defaults;
    }

    private Map<String, String> defaultGermanTranslations() {
        Map<String, String> defaults = new HashMap<>();
        defaults.put("FFA Control Center", "FFA Kontrollzentrum");
        defaults.put("Arena Hub", "Arena-Hub");
        defaults.put("Arena Session", "Arena-Session");
        defaults.put("Statistics", "Statistiken");
        defaults.put("Standby", "Standby");
        defaults.put("Live", "Live");
        defaults.put("Offline", "Offline");
        defaults.put("Maintenance", "Wartung");
        defaults.put("Open", "Offen");
        defaults.put("Closed", "Gesperrt");
        defaults.put("Ready", "Bereit");
        defaults.put("Not Ready", "Nicht bereit");
        defaults.put("Readiness", "Bereitschaft");
        defaults.put("Unknown readiness error.", "Unbekannter Bereitschaftsfehler.");
        defaults.put("Arena is not ready for Live: ", "Arena ist nicht bereit fuer Live: ");
        defaults.put("Arena not found.", "Arena nicht gefunden.");
        defaults.put("No spawn points configured.", "Keine Spawnpunkte konfiguriert.");
        defaults.put("World name is missing.", "Weltname fehlt.");
        defaults.put("Safe-zone reference is invalid.", "Safe-Zone Referenz ist ungueltig.");
        return defaults;
    }

    private static final class LanguageBundle {
        private Map<String, String> translations = new HashMap<>();
    }
}


