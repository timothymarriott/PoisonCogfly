package dev.ambershadow.cogfly.instance;

import java.nio.file.Files;
import java.nio.file.Path;

public class GameInstance {

    public enum Source { DOWNLOADED, EXTERNAL }

    private String id;
    private String label;
    private String version;
    private Source source;
    private String externalPath;
    private long installedAt;
    private Long manifest;
    private String branch;
    private transient Path dir;

    GameInstance() {}

    GameInstance(String id, Source source, Path dir) {
        this.id = id;
        this.source = source;
        this.dir = dir;
        this.installedAt = System.currentTimeMillis();
    }

    public String getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null || label.isBlank() ? null : label.trim();
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public Long getManifest() {
        return manifest;
    }

    public String getBranch() {
        return branch;
    }

    public void setBuild(Long manifest, String branch) {
        this.manifest = manifest;
        this.branch = branch;
    }

    public Source getSource() {
        return source;
    }

    public long getInstalledAt() {
        return installedAt;
    }

    /** Folder holding instance.json (and the game files, for downloaded instances). */
    public Path getDir() {
        return dir;
    }

    void setDir(Path dir) {
        this.dir = dir;
    }

    void setExternalPath(String externalPath) {
        this.externalPath = externalPath;
    }

    public Path getPath() {
        return source == Source.EXTERNAL ? Path.of(externalPath) : dir.resolve("game");
    }

    public boolean isAvailable() {
        return Files.isDirectory(getPath());
    }

    public String getDisplayName() {
        String v = version != null ? version : "unknown version";
        return label != null ? label + " (" + v + ")" : v;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
