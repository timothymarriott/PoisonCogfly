package dev.ambershadow.cogfly.elements.instances;

import com.google.gson.JsonObject;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.instance.Downloader;
import dev.ambershadow.cogfly.instance.GameInstance;
import dev.ambershadow.cogfly.instance.InstanceManager;
import dev.ambershadow.cogfly.instance.VersionCatalog;
import dev.ambershadow.cogfly.util.FileUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.Map;
import java.util.function.Consumer;

/** Pick a game version (or enter a manifest by hand), then watch it download. */
public class DownloadDialog extends JDialog {

    private static final String SELECT = "select";
    private static final String PROGRESS = "progress";

    private final Consumer<GameInstance> onFinished;
    private final CardLayout cards = new CardLayout();
    private final JPanel root = new JPanel(cards);

    private final JRadioButton known = new JRadioButton("Known version", true);
    private final JRadioButton manual = new JRadioButton("Manual (for versions not in the list)");
    private final JComboBox<VersionCatalog.Entry> versions = new JComboBox<>();
    private final JTextField manifestField = new JTextField(20);
    private final JComboBox<String> branchBox = new JComboBox<>(new String[]{"Public", "PublicBeta"});
    private final JTextField labelField = new JTextField(20);

    private final JLabel status = new JLabel(" ");
    private final JProgressBar bar = new JProgressBar(0, 1000);
    private final JLabel qrLabel = new JLabel();
    private final JPanel promptPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
    private final JLabel promptLabel = new JLabel();
    private final JPasswordField promptField = new JPasswordField(16);
    private final JButton cancel = new JButton("Cancel");

    private Downloader.Job job;
    private GameInstance target;
    private boolean finished;

    public DownloadDialog(Frame owner, Consumer<GameInstance> onFinished) {
        super(owner, "Download Game Version", true);
        this.onFinished = onFinished;
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                requestClose();
            }
        });

        root.add(buildSelectCard(), SELECT);
        root.add(buildProgressCard(), PROGRESS);
        setContentPane(root);
        pack();
        setMinimumSize(new Dimension(460, 0));
        setLocationRelativeTo(owner);
    }

    // ---- selection ----

    private JPanel buildSelectCard() {
        for (VersionCatalog.Entry entry : VersionCatalog.forCurrentPlatform())
            versions.addItem(entry);
        versions.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value, int index, boolean selected, boolean focus) {
                super.getListCellRendererComponent(list, value, index, selected, focus);
                if (value instanceof VersionCatalog.Entry e) {
                    setText(e.version() + (e.isBeta() ? "  (beta)" : "") + (isInstalled(e) ? "  — installed" : ""));
                }
                return this;
            }
        });

        ButtonGroup group = new ButtonGroup();
        group.add(known);
        group.add(manual);
        known.addActionListener(_ -> updateMode());
        manual.addActionListener(_ -> updateMode());
        manifestField.setToolTipText("The depot manifest ID of the build, e.g. from SteamDB.");

        JPanel form = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.WEST;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridwidth = 2;
        c.gridy = 0;
        form.add(known, c);
        c.gridy = 1;
        form.add(versions, c);
        c.gridy = 2;
        form.add(manual, c);
        c.gridwidth = 1;
        c.gridy = 3;
        form.add(new JLabel("Manifest ID"), c);
        c.gridx = 1;
        form.add(manifestField, c);
        c.gridx = 0;
        c.gridy = 4;
        form.add(new JLabel("Branch"), c);
        c.gridx = 1;
        form.add(branchBox, c);
        c.gridx = 0;
        c.gridy = 5;
        form.add(new JLabel("Label (optional)"), c);
        c.gridx = 1;
        form.add(labelField, c);

        String username = Cogfly.settings.steamUsername;
        JLabel account = new JLabel(username == null || username.isBlank()
                ? "You'll sign in to Steam with a QR code. Your account must own Silksong."
                : "Signing in to Steam as " + username + " (change this in Settings).");
        account.setEnabled(false);
        c.gridx = 0;
        c.gridy = 6;
        c.gridwidth = 2;
        form.add(account, c);

        JButton start = new JButton("Download");
        start.addActionListener(_ -> start());
        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        south.add(start);

        JPanel card = new JPanel(new BorderLayout());
        card.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        card.add(form, BorderLayout.CENTER);
        card.add(south, BorderLayout.SOUTH);
        updateMode();
        return card;
    }

    private void updateMode() {
        boolean isKnown = known.isSelected();
        versions.setEnabled(isKnown);
        manifestField.setEnabled(!isKnown);
        branchBox.setEnabled(!isKnown);
    }

    private static boolean isInstalled(VersionCatalog.Entry entry) {
        return InstanceManager.getAll().stream().anyMatch(i -> entry.version().equals(i.getVersion()));
    }

    // ---- progress ----

    private JPanel buildProgressCard() {
        status.setAlignmentX(Component.CENTER_ALIGNMENT);
        bar.setStringPainted(true);
        qrLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        qrLabel.setHorizontalAlignment(SwingConstants.CENTER);
        promptPanel.add(promptLabel);
        promptPanel.add(promptField);
        JButton submit = new JButton("Submit");
        promptPanel.add(submit);
        promptPanel.setVisible(false);
        submit.addActionListener(_ -> submitPrompt());
        promptField.addActionListener(_ -> submitPrompt());
        cancel.addActionListener(_ -> requestClose());

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        card.add(status);
        card.add(Box.createVerticalStrut(8));
        card.add(qrLabel);
        card.add(Box.createVerticalStrut(8));
        card.add(promptPanel);
        card.add(Box.createVerticalStrut(8));
        card.add(bar);
        card.add(Box.createVerticalStrut(10));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.CENTER));
        south.add(cancel);
        card.add(south);
        return card;
    }

    private void start() {
        String platform;
        String branch;
        long manifest;
        String version;
        if (known.isSelected()) {
            VersionCatalog.Entry entry = (VersionCatalog.Entry) versions.getSelectedItem();
            if (entry == null)
                return;
            if (isInstalled(entry)) {
                JOptionPane.showMessageDialog(this, "You already have version " + entry.version() + " installed.",
                        "Already installed", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            platform = entry.platform();
            branch = entry.branch();
            manifest = entry.manifest();
            version = entry.version();
        } else {
            try {
                manifest = Long.parseUnsignedLong(manifestField.getText().trim());
            } catch (NumberFormatException e) {
                JOptionPane.showMessageDialog(this, "Enter the manifest ID as a number.", "Invalid manifest", JOptionPane.WARNING_MESSAGE);
                return;
            }
            platform = VersionCatalog.currentPlatform();
            branch = (String) branchBox.getSelectedItem();
            // we'll read the real version out of the game once it's downloaded
            version = null;
        }

        target = InstanceManager.createDownloaded();
        final String knownVersion = version;
        final long manifestId = manifest;
        final String branchName = branch;
        try {
            job = Downloader.download(platform, branch, manifest, target.getPath(), new Downloader.Listener() {
                @Override
                public void onQr(String url) {
                    SwingUtilities.invokeLater(() -> showQr(url));
                }

                @Override
                public void onPrompt(String kind, JsonObject data) {
                    SwingUtilities.invokeLater(() -> showPrompt(kind, data));
                }

                @Override
                public void onConfirm(String message) {
                    SwingUtilities.invokeLater(() -> status.setText(message));
                }

                @Override
                public void onProgress(long downloaded, long total) {
                    SwingUtilities.invokeLater(() -> showProgress(downloaded, total));
                }

                @Override
                public void onLoggedIn(String username) {
                    SwingUtilities.invokeLater(() -> loggedIn(username));
                }
            });
        } catch (IOException e) {
            failed(e);
            return;
        }
        status.setText("Connecting to Steam...");
        bar.setIndeterminate(true);
        cards.show(root, PROGRESS);
        pack();
        setLocationRelativeTo(getOwner());
        job.future.whenComplete((_, e) -> SwingUtilities.invokeLater(() -> {
            if (finished)
                return;
            if (e != null) {
                failed(e);
                return;
            }
            complete(knownVersion, manifestId, branchName);
        }));
    }

    private void showQr(String url) {
        status.setText("Scan this with the Steam mobile app to sign in");
        promptPanel.setVisible(false);
        qrLabel.setIcon(new ImageIcon(renderQr(url, 6)));
        pack();
    }

    private void showPrompt(String kind, JsonObject data) {
        qrLabel.setIcon(null);
        boolean retry = data.has("retry") && data.get("retry").getAsBoolean();
        String prefix = retry ? "That code was wrong. " : "";
        switch (kind) {
            case "password" -> promptLabel.setText("Steam password for " + data.get("username").getAsString() + ":");
            case "guard_device" -> promptLabel.setText(prefix + "Steam Guard code from your authenticator app:");
            default -> promptLabel.setText(prefix + "Steam Guard code sent to your email:");
        }
        status.setText("Sign in to Steam");
        promptField.setText("");
        promptPanel.setVisible(true);
        pack();
        promptField.requestFocusInWindow();
    }

    private void submitPrompt() {
        String value = new String(promptField.getPassword());
        if (value.isBlank() || job == null)
            return;
        promptPanel.setVisible(false);
        promptField.setText("");
        status.setText("Signing in...");
        job.respond(value);
        pack();
    }

    private void loggedIn(String username) {
        qrLabel.setIcon(null);
        promptPanel.setVisible(false);
        status.setText("Signed in. Preparing download...");
        if (username != null && !username.isBlank() && (Cogfly.settings.steamUsername == null || Cogfly.settings.steamUsername.isBlank())) {
            Cogfly.settings.steamUsername = username;
            Cogfly.settings.save();
        }
        pack();
    }

    private void showProgress(long downloaded, long total) {
        bar.setIndeterminate(false);
        bar.setValue((int) (downloaded * 1000 / Math.max(1, total)));
        double mb = 1024.0 * 1024.0;
        bar.setString(String.format("%.1f%%", downloaded * 100.0 / Math.max(1, total)));
        status.setText(String.format("Downloading... %.0f / %.0f MB", downloaded / mb, total / mb));
    }

    private void complete(String knownVersion, long manifest, String branch) {
        status.setText("Finishing up...");
        bar.setIndeterminate(true);
        Cogfly.runAsync(() -> {
            String version = knownVersion != null ? knownVersion : Downloader.identify(target.getPath());
            target.setVersion(version);
            target.setBuild(manifest, branch);
            target.setLabel(labelField.getText());
            InstanceManager.completeDownload(target);
        }).whenComplete((_, e) -> SwingUtilities.invokeLater(() -> {
            if (e != null) {
                failed(e);
                return;
            }
            finished = true;
            dispose();
            onFinished.accept(target);
        }));
    }

    private void failed(Throwable e) {
        if (finished)
            return;
        finished = true;
        Throwable cause = e.getCause() != null && e instanceof java.util.concurrent.CompletionException ? e.getCause() : e;
        Cogfly.logger.error("Download failed", cause);
        cleanup();
        dispose();
        JOptionPane.showMessageDialog(getOwner(), "The download failed:\n" + cause.getMessage(), "Download failed", JOptionPane.ERROR_MESSAGE);
    }

    private void requestClose() {
        if (job == null || finished) {
            finished = true;
            cleanup();
            dispose();
            return;
        }
        int result = JOptionPane.showConfirmDialog(this, "Cancel this download? Files downloaded so far will be deleted.",
                "Cancel download", JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.YES_OPTION || finished)
            return;
        finished = true;
        job.cancel();
        cleanup();
        dispose();
    }

    /** Removes the partial download; only ever called for an instance that was never registered. */
    private void cleanup() {
        if (target == null || InstanceManager.getById(target.getId()) != null)
            return;
        GameInstance partial = target;
        Cogfly.runAsync(() -> FileUtils.deleteFolder(partial.getDir()))
                .exceptionally(e -> {
                    Cogfly.logger.warn("Failed to remove partial download", e);
                    return null;
                });
    }

    private static BufferedImage renderQr(String text, int scale) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0,
                    Map.of(EncodeHintType.MARGIN, 2));
            BufferedImage image = new BufferedImage(matrix.getWidth() * scale, matrix.getHeight() * scale, BufferedImage.TYPE_INT_RGB);
            for (int y = 0; y < image.getHeight(); y++)
                for (int x = 0; x < image.getWidth(); x++)
                    image.setRGB(x, y, matrix.get(x / scale, y / scale) ? 0x000000 : 0xFFFFFF);
            return image;
        } catch (WriterException e) {
            throw new RuntimeException(e);
        }
    }
}
