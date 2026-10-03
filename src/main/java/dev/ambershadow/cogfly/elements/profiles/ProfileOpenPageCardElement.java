package dev.ambershadow.cogfly.elements.profiles;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.ModPanelElement;
import dev.ambershadow.cogfly.elements.instances.InstancePicker;
import dev.ambershadow.cogfly.instance.GameInstance;
import dev.ambershadow.cogfly.loader.ModData;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.profile.ProfileManager;
import dev.ambershadow.cogfly.util.*;
import dev.ambershadow.cogfly.util.swing.FrameManager;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public class ProfileOpenPageCardElement extends JPanel {

    private final Profile profile;
    private final JButton updateAll;
    private final JProgressBar progressBar;
    private final JLabel instanceLabel = new JLabel();
    private final JButton changeInstance = new JButton("Change Instance");
    public void setBar(boolean val) {
        progressBar.setVisible(val);
        long total = ModUtils.getTotalDownloadCount(profile);
        long remaining = ModUtils.getDownloadCount(profile);
        progressBar.setMinimum(0);
        progressBar.setMaximum((int) total);
        progressBar.setValue((int) (total - remaining));
    }
    public ProfileOpenPageCardElement(Profile profile) {
        super(new BorderLayout());
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        this.profile = profile;
        JPanel upperPanel = new JPanel();
        upperPanel.setPreferredSize(new Dimension(getWidth(), 100));

        JButton launch = new JButton("Launch");
        launch.addActionListener(_ -> {
            List<ModData> outdated = profile.getInstalledMods().stream().filter(m -> m.isOutdated(profile)).toList();
            if (!outdated.isEmpty()) {
                List<Object> msg = new ArrayList<>();
                msg.add("This profile has outdated mods.");
                msg.add("");
                for (ModData modData : outdated) {
                    msg.add("• " + modData.getName());
                }
                msg.add("");
                msg.add("Would you like to update them?");
                int result = JOptionPane.showConfirmDialog(
                        FrameManager.getOrCreate().frame,
                        msg.toArray(),
                        "Outdated Mods",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    List<CompletableFuture<Void>> voids = new ArrayList<>();
                    for (ModData modData : outdated) {
                        voids.add(ModUtils.downloadLatestMod(
                                ModData.getMod(modData.getFullName()),
                                profile,
                                false
                        ));
                    }
                    CompletableFuture
                            .allOf(voids.toArray(CompletableFuture[]::new))
                            .thenRun(() -> GameUtils.launchModdedGame(profile))
                            .exceptionally(e -> {
                                throw new RuntimeException(e);
                            });
                    return;
                }
            }
            GameUtils.launchModdedGame(profile);
        });

        updateAll = new JButton("Update All");
        updateAll.setEnabled(false);
        updateAll.addActionListener(_ -> {
            updateAll.setEnabled(false);
            for (ModData modData : profile.getInstalledMods()) {
                if (!modData.isOutdated(profile)) continue;
                ModUtils.downloadLatestMod(
                        ModData.getMod(modData.getFullName()),
                        profile,
                        false
                );
            }
        });

        JButton copyLogToClipboard = new JButton("Copy Log To Clipboard");
        copyLogToClipboard.addActionListener(_ -> {
            if (Files.exists(profile.getBepInExPath().resolve("LogOutput.log"))) {
                Cogfly.copyFile(profile.getBepInExPath().resolve("LogOutput.log"));
            }
        });

        JButton exportAsId = new JButton("Export As Code");
        exportAsId.addActionListener(_ -> {
            String id = ProfileManager.toId(profile);
            Cogfly.copyString(id);
            JOptionPane.showMessageDialog(
                null, 
                "Your code: " + id + " has been copied to your clipboard!", 
                "Copied!",
                JOptionPane.PLAIN_MESSAGE
            );
        });

        JButton exportAsFile = new JButton("Export As File");
        exportAsFile.addActionListener(_ -> FileUtils.pickFolder(path -> ProfileManager.toFile(profile, path)));

        JButton openFileLocation = new JButton("Open Profile Folder");
        openFileLocation.addActionListener(_ -> FileUtils.openProfilePath(profile));

        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(_ -> {
            profile.refreshMods();
            ModPanelElement.redraw(profile);
        });

        JButton install = new JButton("Install Manually");
        install.addActionListener(_ -> FileUtils.pickFile((path) -> ModUtils.downloadManualMod(path, profile, true), "*", "zip", "dll"));

        JButton copyLaunchArgs = new JButton("Copy Launch Arguments");
        copyLaunchArgs.addActionListener(_ -> {
            if (profile.getGamePath() == null) {
                JOptionPane.showMessageDialog(FrameManager.getOrCreate().frame, "This profile's game instance is not installed.", "Instance missing!", JOptionPane.WARNING_MESSAGE);
                return;
            }
            if (Cogfly.isWindows()) {
                Cogfly.copyString("--doorstop-enabled true --doorstop-target-assembly \"" + profile.getBepInExPath().resolve("core", "BepInEx.Preloader.dll") + "\"");
            } else {
                Cogfly.copyString(Paths.get(profile.getGamePath()).resolve("run_bepinex.sh") + " %command% --doorstop_enabled true --doorstop_target_assembly " + profile.getBepInExPath().resolve("core", "BepInEx.Preloader.dll"));
            }
        });

        upperPanel.add(launch);
        upperPanel.add(updateAll);
        upperPanel.add(copyLogToClipboard);
        upperPanel.add(exportAsId);
        upperPanel.add(exportAsFile);
        upperPanel.add(openFileLocation);
        upperPanel.add(refresh);
        upperPanel.add(copyLaunchArgs);
        upperPanel.add(install);

        JPanel b = new JPanel();
        JLabel name = new JLabel(profile.getName());
        name.setFont(name.getFont().deriveFont(32f));
        b.add(name);
        JPanel instancePanel = new JPanel();
        changeInstance.setVisible(profile != ProfileManager.baseGame);
        changeInstance.addActionListener(_ -> InstancePicker.changeInstance(profile, this::reload));
        instancePanel.add(instanceLabel);
        instancePanel.add(changeInstance);
        updateInstanceLabel();
        JPanel panel = new JPanel();
        progressBar = new JProgressBar();
        progressBar.setVisible(false);
        panel.add(progressBar);

        add(upperPanel);
        add(Box.createVerticalGlue());
        add(b);
        add(instancePanel);
        add(Box.createVerticalGlue());
        add(panel);
        add(Box.createVerticalGlue());
        add(new ModPanelElement(profile, this));
    }

    private void updateInstanceLabel() {
        GameInstance instance = profile.getInstance();
        if (profile.isInstanceMissing()) {
            String wanted = profile.getGameVersion() != null ? " (version " + profile.getGameVersion() + ")" : "";
            instanceLabel.setText("⚠ Game instance" + wanted + " is not installed. Change instance or install it on the Instances page.");
            instanceLabel.setForeground(new Color(0xE0A030));
        } else {
            instanceLabel.setText("Game instance: " + instance.getDisplayName());
            instanceLabel.setForeground(UIManager.getColor("Label.foreground"));
        }
    }

    public void reload() {
        updateInstanceLabel();
        boolean anyOutdated = profile.getInstalledMods()
                .stream().anyMatch(mod -> mod.isOutdated(profile));
        updateAll.setEnabled(anyOutdated);
        ModPanelElement.redraw(profile);
        progressBar.setVisible(ModUtils.isDownloading(profile));
    }
}
