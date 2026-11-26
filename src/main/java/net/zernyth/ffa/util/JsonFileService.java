package net.zernyth.ffa.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.Path;
import java.util.Optional;

public final class JsonFileService {

    private final Gson gson;

    public JsonFileService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
    }

    public Gson gson() {
        return gson;
    }

    public void ensureDirectory(Path directory) throws IOException {
        Files.createDirectories(directory);
    }

    public <T> Optional<T> read(Path path, Class<T> type) throws IOException {
        if (Files.notExists(path)) {
            return Optional.empty();
        }
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            return Optional.ofNullable(gson.fromJson(reader, type));
        }
    }

    public void write(Path path, Object value) throws IOException {
        Path parent = path.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        // Written next to the target and moved over it: a crash mid-write leaves the old file intact
        // instead of half a profile.
        Path temp = Files.createTempFile(parent != null ? parent : Path.of("."), path.getFileName().toString(), ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                gson.toJson(value, writer);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}


