package dev.ambershadow.cogfly.profile;

import dev.ambershadow.cogfly.instance.GameInstance;
import dev.ambershadow.cogfly.instance.InstanceManager;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.loader.ModFetcher;

import javax.swing.*;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Profile {
    private HashMap<String, ModData> installedMods = new HashMap<>();
    private Set<String> disabledMods = new HashSet<>();
    private final Path path;
    private final String name;
    private String instanceId;
    private String gameVersion;
    private boolean persistMeta = true;
    private Icon icon;
    private final Path iconPath;
    public Profile(String name, Path path) {
        this(name, path, null, null);
    }
    public Profile(String name, Path path, Path iconPath, Icon icon) {
        this.path = path;
        this.name = name;
        this.icon = icon;
        this.iconPath = iconPath;
    }

    public Path getPath() {
        return path;
    }

    public Path getBepInExPath() {
        return path.resolve("BepInEx");
    }

    public Path getPluginsPath() {
        return getBepInExPath().resolve("plugins");
    }

    public Path getIconPath() {
        return iconPath;
    }
    public List<ModData> getInstalledMods() {
        return installedMods.values().stream().toList();
    }

    public void removeMod(ModData mod) {
        installedMods.remove(mod.getFullName());
    }

    public void addMod(ModData mod) {
        installedMods.put(mod.getFullName(), mod);
    }

    public void setEnabled(ModData mod, boolean enabled) {
        if (enabled)
            disabledMods.remove(mod.getFullName());
        else
            disabledMods.add(mod.getFullName());
    }

    public boolean isEnabled(ModData mod) {
        return !disabledMods.contains(mod.getFullName());
    }

    public String getInstalledVersion(ModData mod) {
        return installedMods.get(mod.getFullName()).getVersionNumber();
    }

    public Icon getIcon() {
        return icon;
    }
    public void setIcon(Icon icon) {
        this.icon = icon;
    }
    public String getInstanceId() {
        return instanceId;
    }

    /** The game version this profile was last bound to; survives its instance being deleted. */
    public String getGameVersion() {
        return gameVersion;
    }

    public GameInstance getInstance() {
        return InstanceManager.getById(instanceId);
    }

    /** Game folder of this profile's instance, or null when the instance is missing or gone from disk. */
    public String getGamePath() {
        GameInstance instance = getInstance();
        return instance != null && instance.isAvailable() ? instance.getPath().toString() : null;
    }

    public boolean isInstanceMissing() {
        return getGamePath() == null;
    }

    public void setInstance(GameInstance instance) {
        bind(instance);
        saveMeta();
    }

    /** Binds without writing to disk; the base game profile lives in the game folder itself. */
    void bind(GameInstance instance) {
        instanceId = instance != null ? instance.getId() : null;
        if (instance != null && instance.getVersion() != null)
            gameVersion = instance.getVersion();
    }

    void restore(GameInstance instance, ProfileMeta meta) {
        bind(instance);
        if (instance == null)
            instanceId = meta.instanceId;
        if (gameVersion == null)
            gameVersion = meta.gameVersion;
    }

    void disablePersistence() {
        persistMeta = false;
    }

    public ProfileMeta toMeta() {
        ProfileMeta meta = new ProfileMeta();
        meta.instanceId = instanceId;
        meta.gameVersion = gameVersion;
        return meta;
    }

    public void saveMeta() {
        if (persistMeta)
            toMeta().write(getPath().resolve("cogfly_data.json"));
    }

    public void refreshMods() {
        installedMods = ModFetcher.getInstalledMods(getPluginsPath())
                .stream().collect(Collectors.toMap(
                        ModData::getFullName,
                        Function.identity(),
                        (_, v) -> v,
                        HashMap::new
                ));
        disabledMods = installedMods.values().stream()
                .map(ModData::getFullName)
                .filter(fullName -> ModData.containsOldFile(getPluginsPath().resolve(fullName)))
                .collect(Collectors.toSet());
    }

    public List<ModData> getManualMods() {
        return installedMods.values().stream().filter(ModData::isManual).toList();
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof Profile prof &&
                prof.getPath().equals(path)
                && prof.getName().equals(name);
    }
}
