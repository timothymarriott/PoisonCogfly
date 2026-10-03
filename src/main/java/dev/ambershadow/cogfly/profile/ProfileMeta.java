package dev.ambershadow.cogfly.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.ambershadow.cogfly.Cogfly;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Contents of a profile's cogfly_data.json. */
public class ProfileMeta {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    static final int FORMAT = 2;

    public int formatVersion = FORMAT;
    /** Local id of the game instance; only meaningful on the machine that wrote it. */
    public String instanceId;
    /** Game version the profile was made for; used to re-match an instance after import. */
    public String gameVersion;
    /** Pre-instance format. Read for migration, never written. */
    public String gamePath;

    public static ProfileMeta parse(String json) {
        try {
            ProfileMeta meta = GSON.fromJson(json, ProfileMeta.class);
            return meta != null ? meta : new ProfileMeta();
        } catch (RuntimeException e) {
            Cogfly.logger.error("Corrupt profile metadata", e);
            return new ProfileMeta();
        }
    }

    public static ProfileMeta read(Path file) {
        if (!Files.exists(file))
            return new ProfileMeta();
        try {
            return parse(Files.readString(file));
        } catch (IOException e) {
            Cogfly.logger.error("Failed to read {}", file, e);
            return new ProfileMeta();
        }
    }

    public String toJson() {
        return GSON.toJson(this);
    }

    public void write(Path file) {
        try {
            Files.writeString(file, toJson());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isEmpty() {
        return instanceId == null && gameVersion == null && (gamePath == null || gamePath.isBlank());
    }
}
