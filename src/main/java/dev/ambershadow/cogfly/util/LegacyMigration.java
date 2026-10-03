package dev.ambershadow.cogfly.util;

import com.google.gson.*;
import dev.ambershadow.cogfly.Cogfly;
import net.harawata.appdirs.AppDirsFactory;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.List;
import java.util.stream.Stream;

/**
 * Offers to copy data from the original Cogfly (separate data folders) into PoisonCogfly.
 * The original is never modified; everything is copied and absolute paths in settings.json are rewritten.
 */
public class LegacyMigration {

    private static final String LEGACY_NAME = "Cogfly";
    /** Folders (relative to the legacy local data dir) worth carrying over. Caches and extracted helpers are rebuilt. */
    private static final List<String> LOCAL_ENTRIES = List.of(
            "instances", "downloader/config", "BepInExPack", "doorstop", "pack_version.txt");

    private final Path oldLocal;
    private final Path oldRoaming;

    private LegacyMigration() {
        var dirs = AppDirsFactory.getInstance();
        oldLocal = Paths.get(dirs.getUserDataDir(LEGACY_NAME, null, ""));
        oldRoaming = Paths.get(dirs.getUserDataDir(LEGACY_NAME, null, "", true));
    }

    private boolean legacyDataExists() {
        return Files.exists(oldLocal.resolve("settings.json")) || Files.isDirectory(oldRoaming.resolve("profiles"));
    }

    /** Asks the user once; returns true if data was migrated and settings must be reloaded. */
    public static boolean offerIfNeeded() {
        if (Cogfly.settings.legacyMigrationHandled)
            return false;
        LegacyMigration migration = new LegacyMigration();
        if (!migration.legacyDataExists()) {
            Cogfly.settings.legacyMigrationHandled = true;
            Cogfly.settings.save();
            return false;
        }
        int choice = JOptionPane.showOptionDialog(null,
                "An existing Cogfly installation was found.\n\n"
                        + "Copy its profiles, game versions and settings into PoisonCogfly?\n"
                        + "Your original Cogfly data is left untouched, but this uses extra disk space.",
                "Migrate from Cogfly",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.QUESTION_MESSAGE,
                null,
                new Object[]{"Migrate", "Start fresh"},
                "Migrate");
        boolean migrated = false;
        if (choice == JOptionPane.YES_OPTION) {
            try {
                migrated = migration.runWithProgress();
            } catch (Exception e) {
                Cogfly.logger.error("Migration from Cogfly failed", e);
                JOptionPane.showMessageDialog(null,
                        "Migration failed: " + e.getMessage() + "\nYour original Cogfly data was not changed.",
                        "Migration failed", JOptionPane.ERROR_MESSAGE);
            }
        }
        // Ask only once either way. After a migration the flag is already in the migrated file and the caller
        // reloads it, so saving the stale in-memory settings here would overwrite it.
        if (!migrated) {
            Cogfly.settings.legacyMigrationHandled = true;
            Cogfly.settings.save();
        }
        return migrated;
    }

    private boolean runWithProgress() throws IOException {
        JDialog dialog = new JDialog((Frame) null, "Migrating...", false);
        JProgressBar bar = new JProgressBar();
        bar.setIndeterminate(true);
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        panel.add(new JLabel("Copying your Cogfly data. This can take a while..."), BorderLayout.NORTH);
        panel.add(bar, BorderLayout.CENTER);
        dialog.setContentPane(panel);
        dialog.pack();
        dialog.setSize(Math.max(dialog.getWidth(), 380), dialog.getHeight());
        dialog.setLocationRelativeTo(null);
        dialog.setVisible(true);
        try {
            run();
        } finally {
            dialog.dispose();
        }
        return true;
    }

    private void run() throws IOException {
        Cogfly.logger.info("Migrating data from {} and {}", oldLocal, oldRoaming);
        for (String entry : LOCAL_ENTRIES)
            copyTree(oldLocal.resolve(entry), Cogfly.localDataPath.resolve(entry));
        copyTree(oldRoaming.resolve("profiles"), Cogfly.roamingDataPath.resolve("profiles"));
        migrateSettings();
        Cogfly.logger.info("Migration finished.");
    }

    private void migrateSettings() throws IOException {
        Path oldSettings = oldLocal.resolve("settings.json");
        if (!Files.exists(oldSettings))
            return;
        JsonObject json = JsonParser.parseString(Files.readString(oldSettings)).getAsJsonObject();
        for (String key : List.copyOf(json.keySet()))
            json.add(key, rewritePaths(json.get(key)));
        json.addProperty("legacyMigrationHandled", true);
        Files.writeString(Cogfly.dataJson, new GsonBuilder().setPrettyPrinting().create().toJson(json));
    }

    private JsonElement rewritePaths(JsonElement element) {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString())
            return new JsonPrimitive(rewrite(element.getAsString()));
        if (element.isJsonArray()) {
            JsonArray array = new JsonArray();
            element.getAsJsonArray().forEach(e -> array.add(rewritePaths(e)));
            return array;
        }
        return element;
    }

    /** Points paths inside the old data folders at their copies; anything else (game installs etc.) is kept. */
    private String rewrite(String value) {
        String moved = moveUnder(value, oldLocal, Cogfly.localDataPath);
        if (!moved.equals(value))
            return moved;
        return moveUnder(value, oldRoaming, Cogfly.roamingDataPath);
    }

    private static String moveUnder(String value, Path from, Path to) {
        try {
            Path p = Paths.get(value);
            if (p.isAbsolute() && p.normalize().startsWith(from.normalize()))
                return to.resolve(from.relativize(p.normalize()).toString()).toString();
        } catch (InvalidPathException ignored) {
            // not a path
        }
        return value;
    }

    /** Copies a file or folder without overwriting anything that already exists in the target. */
    private static void copyTree(Path source, Path target) throws IOException {
        if (!Files.exists(source))
            return;
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path p : walk.toList()) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p))
                    Files.createDirectories(dest);
                else {
                    Files.createDirectories(dest.getParent());
                    if (!Files.exists(dest))
                        Files.copy(p, dest, StandardCopyOption.COPY_ATTRIBUTES);
                }
            }
        } catch (UncheckedIOException e) {
            throw e.getCause();
        }
    }
}
