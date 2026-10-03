package dev.ambershadow.cogfly.elements.instances;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.instance.GameInstance;
import dev.ambershadow.cogfly.instance.InstanceManager;
import dev.ambershadow.cogfly.profile.Profile;
import dev.ambershadow.cogfly.util.FileUtils;
import dev.ambershadow.cogfly.util.GameUtils;
import dev.ambershadow.cogfly.util.swing.FrameManager;
import dev.ambershadow.cogfly.util.swing.ReloadablePage;

import javax.swing.*;
import java.awt.*;
import java.nio.file.Path;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;

public class InstancesScreenElement extends JPanel implements ReloadablePage {

    private final JPanel listPanel = new JPanel();

    public InstancesScreenElement() {
        setLayout(new BorderLayout());
        JPanel upper = new JPanel();
        upper.setPreferredSize(new Dimension(getWidth(), 30));

        JButton addExisting = new JButton("Add Existing Install");
        addExisting.setToolTipText("Register a game folder that is already on your computer.");
        addExisting.addActionListener(_ -> FileUtils.pickFile(path -> {
            GameInstance instance = InstanceManager.ensureExternal(path.toFile().getParentFile().getAbsolutePath());
            if (instance != null)
                SwingUtilities.invokeLater(this::reload);
        }, "Hollow Knight Silksong", "exe", "app", "*"));
        JButton download = new JButton("Download Version");
        download.addActionListener(_ -> new DownloadDialog(FrameManager.getOrCreate().frame, _ -> refreshAll()).setVisible(true));
        upper.add(download);
        upper.add(addExisting);

        listPanel.setLayout(new BoxLayout(listPanel, BoxLayout.Y_AXIS));
        listPanel.setBorder(BorderFactory.createEmptyBorder(16, 16, 16, 16));
        JScrollPane pane = new JScrollPane(listPanel, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        add(upper, BorderLayout.NORTH);
        add(pane, BorderLayout.CENTER);
    }

    @Override
    public void reload() {
        listPanel.removeAll();
        List<GameInstance> instances = InstanceManager.getAll();
        if (instances.isEmpty()) {
            listPanel.add(new JLabel("No game instances yet. Set your game path in Settings or add an existing install."));
        }
        for (GameInstance instance : instances) {
            listPanel.add(createCard(instance));
            listPanel.add(Box.createVerticalStrut(10));
        }
        listPanel.revalidate();
        listPanel.repaint();
    }

    private JPanel createCard(GameInstance instance) {
        JPanel card = new JPanel(new BorderLayout(12, 4));
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.DARK_GRAY),
                BorderFactory.createEmptyBorder(10, 12, 10, 12)));
        card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 110));

        String title = instance.getLabel() != null ? instance.getLabel() : "Unnamed instance";
        JLabel name = new JLabel(title + " — " + (instance.getVersion() != null ? instance.getVersion() : "unknown version"));
        name.setFont(name.getFont().deriveFont(Font.BOLD, 16f));

        int used = InstanceManager.getProfilesUsing(instance).size();
        boolean isDefault = instance == InstanceManager.getDefault();
        String source = instance.getSource() == GameInstance.Source.EXTERNAL ? "Existing install" : "Downloaded";
        String details = source + " · " + used + (used == 1 ? " profile" : " profiles")
                + (isDefault ? " · default" : "")
                + (instance.getSource() == GameInstance.Source.DOWNLOADED
                ? " · added " + DateFormat.getDateInstance().format(new Date(instance.getInstalledAt())) : "");
        JLabel info = new JLabel(details);
        JLabel path = new JLabel(instance.getPath().toString());
        path.setEnabled(false);

        JPanel text = new JPanel();
        text.setLayout(new BoxLayout(text, BoxLayout.Y_AXIS));
        text.add(name);
        text.add(info);
        if (!instance.isAvailable()) {
            JLabel missing = new JLabel("⚠ Game files not found at this location.");
            missing.setForeground(new Color(0xE0A030));
            text.add(missing);
        }
        text.add(path);

        JButton launch = new JButton("Launch Vanilla");
        launch.setEnabled(instance.isAvailable());
        launch.addActionListener(_ -> GameUtils.launchVanilla(instance));
        JButton rename = new JButton("Label");
        rename.addActionListener(_ -> {
            Object input = JOptionPane.showInputDialog(FrameManager.getOrCreate().frame,
                    "Label for this instance (leave empty to clear):", "Label Instance",
                    JOptionPane.PLAIN_MESSAGE, null, null, instance.getLabel() != null ? instance.getLabel() : "");
            if (input == null)
                return;
            instance.setLabel(input.toString());
            InstanceManager.save(instance);
            refreshAll();
        });
        JButton open = new JButton("Open Folder");
        open.setEnabled(instance.isAvailable());
        open.addActionListener(_ -> FileUtils.openPath(instance.getPath()));
        JButton delete = new JButton(instance.getSource() == GameInstance.Source.EXTERNAL ? "Remove" : "Delete");
        boolean configuredPath = instance.getSource() == GameInstance.Source.EXTERNAL
                && instance == InstanceManager.findByPath(Cogfly.settings.gamePath);
        delete.setEnabled(!configuredPath);
        if (configuredPath)
            delete.setToolTipText("This is the game path set in Settings. Change it there first.");
        delete.addActionListener(_ -> confirmDelete(instance));

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        buttons.add(launch);
        buttons.add(rename);
        buttons.add(open);
        buttons.add(delete);
        card.add(text, BorderLayout.CENTER);
        card.add(buttons, BorderLayout.EAST);
        return card;
    }

    private void confirmDelete(GameInstance instance) {
        List<Profile> using = InstanceManager.getProfilesUsing(instance);
        boolean external = instance.getSource() == GameInstance.Source.EXTERNAL;
        StringBuilder msg = new StringBuilder(external
                ? "Remove this instance from Cogfly? The game files at " + instance.getPath() + " will not be touched."
                : "Delete this instance? This will delete the game files in " + instance.getPath() + ".");
        if (!using.isEmpty()) {
            msg.append("\n\nThese profiles use it and will need another instance before they can launch:");
            for (Profile p : using)
                msg.append("\n• ").append(p.getName());
        }
        int result = JOptionPane.showConfirmDialog(FrameManager.getOrCreate().frame, msg.toString(),
                external ? "Remove Instance" : "Delete Instance", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (result != JOptionPane.YES_OPTION)
            return;
        Cogfly.runAsync(() -> InstanceManager.remove(instance)).whenComplete((_, e) -> SwingUtilities.invokeLater(() -> {
            if (e != null)
                Cogfly.throwNonFatalError(e);
            refreshAll();
        }));
    }

    private void refreshAll() {
        dev.ambershadow.cogfly.elements.profiles.ProfilesScreenElement.queueRefresh();
        reload();
    }
}
