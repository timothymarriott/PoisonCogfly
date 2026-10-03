package dev.ambershadow.cogfly.instance;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.profile.ProfileManager;
import dev.ambershadow.cogfly.util.FileUtils;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

public class InstanceManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<GameInstance> instances = new ArrayList<>();

    public static Path getInstancesDir() {
        return Cogfly.localDataPath.resolve("instances");
    }

    public static synchronized void load() {
        instances.clear();
        Path root = getInstancesDir();
        try {
            Files.createDirectories(root);
            try (Stream<Path> dirs = Files.list(root)) {
                for (Path dir : dirs.filter(Files::isDirectory).toList()) {
                    Path file = dir.resolve("instance.json");
                    if (!Files.exists(file)) {
                        // left behind by a download that was interrupted
                        FileUtils.deleteFolder(dir);
                        continue;
                    }
                    try (Reader reader = Files.newBufferedReader(file)) {
                        GameInstance instance = GSON.fromJson(reader, GameInstance.class);
                        if (instance == null || instance.getId() == null)
                            continue;
                        instance.setDir(dir);
                        instances.add(instance);
                    } catch (Exception e) {
                        Cogfly.logger.error("Failed to read instance at {}", dir, e);
                    }
                }
            }
        } catch (IOException e) {
            Cogfly.logger.error("Failed to load instances", e);
        }
        instances.sort(Comparator.comparingLong(GameInstance::getInstalledAt));
        ensureExternal(Cogfly.settings.gamePath);
        for (GameInstance instance : instances) {
            if (instance.getVersion() == null && instance.getSource() == GameInstance.Source.EXTERNAL && instance.isAvailable()) {
                instance.setVersion(VersionCatalog.detectVersion(instance.getPath()));
                if (instance.getVersion() != null)
                    save(instance);
            }
        }
    }

    public static synchronized List<GameInstance> getAll() {
        return List.copyOf(instances);
    }

    public static synchronized GameInstance getById(String id) {
        if (id == null)
            return null;
        return instances.stream().filter(i -> i.getId().equals(id)).findFirst().orElse(null);
    }

    public static synchronized GameInstance findByVersion(String version) {
        if (version == null)
            return null;
        return instances.stream()
                .filter(i -> version.equals(i.getVersion()) && i.isAvailable())
                .findFirst().orElse(null);
    }

    /** The instance for the game path configured in settings, falling back to the first instance. */
    public static synchronized GameInstance getDefault() {
        GameInstance byPath = findByPath(Cogfly.settings.gamePath);
        if (byPath != null)
            return byPath;
        return instances.isEmpty() ? null : instances.getFirst();
    }

    public static synchronized GameInstance findByPath(String path) {
        if (path == null || path.isBlank())
            return null;
        Path normalized = Path.of(path).toAbsolutePath().normalize();
        return instances.stream()
                .filter(i -> i.getPath().toAbsolutePath().normalize().equals(normalized))
                .findFirst().orElse(null);
    }

    /** Registers an existing game folder as an instance (once per path). Returns null if it isn't a folder. */
    public static synchronized GameInstance ensureExternal(String path) {
        if (path == null || path.isBlank() || !Files.isDirectory(Path.of(path)))
            return null;
        GameInstance existing = findByPath(path);
        if (existing != null)
            return existing;
        GameInstance instance = create(GameInstance.Source.EXTERNAL);
        instance.setExternalPath(Path.of(path).toAbsolutePath().normalize().toString());
        instance.setVersion(VersionCatalog.detectVersion(Path.of(path)));
        save(instance);
        return instance;
    }

    /**
     * Allocates a downloaded instance without registering it, so a half-finished download never shows up.
     * Download into {@code getPath()}, then call {@link #completeDownload}; on failure delete {@code getDir()}.
     */
    public static GameInstance createDownloaded() {
        String id = UUID.randomUUID().toString();
        return new GameInstance(id, GameInstance.Source.DOWNLOADED, getInstancesDir().resolve(id));
    }

    public static synchronized void completeDownload(GameInstance instance) {
        instances.add(instance);
        save(instance);
    }

    private static GameInstance create(GameInstance.Source source) {
        String id = UUID.randomUUID().toString();
        GameInstance instance = new GameInstance(id, source, getInstancesDir().resolve(id));
        instances.add(instance);
        return instance;
    }

    public static synchronized void save(GameInstance instance) {
        try {
            Files.createDirectories(instance.getDir());
            try (Writer writer = Files.newBufferedWriter(instance.getDir().resolve("instance.json"))) {
                GSON.toJson(instance, writer);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /** Deletes downloaded game files; external installs are only unregistered, never touched. */
    public static synchronized void remove(GameInstance instance) {
        instances.remove(instance);
        FileUtils.deleteFolder(instance.getDir());
    }

    public static List<Profile> getProfilesUsing(GameInstance instance) {
        return ProfileManager.profiles.stream()
                .filter(p -> instance.getId().equals(p.getInstanceId()))
                .toList();
    }
}
