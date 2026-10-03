package dev.ambershadow.cogfly.instance;

import dev.ambershadow.cogfly.Cogfly;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** The known Silksong builds, read from the bundled versions.txt ("1.0.30000 Public = Windows 442..."). */
public class VersionCatalog {

    public record Entry(String version, String branch, String platform, long manifest) {
        public boolean isBeta() {
            return branch.equals("PublicBeta");
        }
    }

    private static final Pattern LINE = Pattern.compile("^(\\S+)\\s+(\\S+)\\s*=\\s*(\\S+)\\s+(\\d+)$");
    private static final Pattern ACF_MANIFEST = Pattern.compile("\"manifest\"\\s*\"(\\d+)\"");
    private static List<Entry> entries;

    public static synchronized List<Entry> all() {
        if (entries != null)
            return entries;
        List<Entry> list = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Cogfly.getResource("/versions.txt").openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                Matcher m = LINE.matcher(line.trim().replace("\uFEFF", ""));
                if (m.matches())
                    list.add(new Entry(m.group(1), m.group(2), m.group(3), Long.parseLong(m.group(4))));
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        entries = list;
        return entries;
    }

    public static String currentPlatform() {
        return switch (Cogfly.getOs()) {
            case WINDOWS -> "Windows";
            case MAC -> "MacOS";
            default -> "Linux";
        };
    }

    public static List<Entry> forCurrentPlatform() {
        String platform = currentPlatform();
        return all().stream().filter(e -> e.platform().equals(platform)).toList();
    }

    public static Optional<Entry> byManifest(long manifest) {
        return all().stream().filter(e -> e.manifest() == manifest).findFirst();
    }

    /** Known manifests first (cheap), then reading the version out of the game's own assemblies. */
    public static String detectVersion(Path gameDir) {
        String version = detectSteamInstall(gameDir);
        return version != null ? version : Downloader.identify(gameDir);
    }

    /**
     * Identifies the version of a Steam install by matching the manifest ids in its appmanifest_1030300.acf
     * against the catalog. Returns null when it can't be determined (non-Steam installs, unknown builds).
     */
    public static String detectSteamInstall(Path gameDir) {
        try {
            Path acf = gameDir.toAbsolutePath().normalize().resolve("../../appmanifest_1030300.acf").normalize();
            if (!Files.exists(acf))
                return null;
            Matcher m = ACF_MANIFEST.matcher(Files.readString(acf));
            while (m.find()) {
                Optional<Entry> entry = byManifest(Long.parseLong(m.group(1)));
                if (entry.isPresent())
                    return entry.get().version();
            }
        } catch (IOException | RuntimeException e) {
            Cogfly.logger.warn("Failed to detect game version for {}", gameDir, e);
        }
        return null;
    }
}
