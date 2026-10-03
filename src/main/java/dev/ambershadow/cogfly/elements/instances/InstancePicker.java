package dev.ambershadow.cogfly.elements.instances;

import dev.ambershadow.cogfly.instance.GameInstance;
import dev.ambershadow.cogfly.instance.InstanceManager;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.util.swing.FrameManager;

import javax.swing.*;
import java.awt.*;
import java.util.List;

public class InstancePicker {

    /** Asks the user which instance a profile should use, then applies it. */
    public static void changeInstance(Profile profile, Runnable after) {
        List<GameInstance> all = InstanceManager.getAll();
        Component parent = FrameManager.getOrCreate().frame;
        if (all.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "You don't have any game instances yet. Add or download one on the Instances page.",
                    "No instances", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JComboBox<GameInstance> box = new JComboBox<>(all.toArray(GameInstance[]::new));
        box.setSelectedItem(profile.getInstance());
        int result = JOptionPane.showConfirmDialog(parent, new Object[]{"Game instance for " + profile.getName() + ":", box},
                "Choose Instance", JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);
        GameInstance chosen = (GameInstance) box.getSelectedItem();
        if (result != JOptionPane.OK_OPTION || chosen == null || chosen.getId().equals(profile.getInstanceId()))
            return;
        if (!confirmVersionChange(profile, chosen))
            return;
        profile.setInstance(chosen);
        if (after != null)
            after.run();
    }

    /** Mods are built against a specific game version, so warn before moving a populated profile to another one. */
    public static boolean confirmVersionChange(Profile profile, GameInstance target) {
        String old = profile.getGameVersion();
        if (profile.getInstalledMods().isEmpty() || old == null || old.equals(target.getVersion()))
            return true;
        return JOptionPane.showConfirmDialog(FrameManager.getOrCreate().frame,
                "This profile has mods installed and was made for version " + old + ".\n"
                        + "They may not work on " + target.getDisplayName() + ". Switch anyway?",
                "Different game version", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE) == JOptionPane.YES_OPTION;
    }
}
