package dev.ambershadow.cogfly.elements.settings;

import dev.ambershadow.cogfly.Cogfly;
import dev.ambershadow.cogfly.elements.SettingsDialog;
import dev.ambershadow.cogfly.instance.Downloader;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.*;
import java.io.IOException;

public class SteamAccountElement extends SettingsElement {

    public SteamAccountElement(SettingsDialog parent) {
        JLabel label = new JLabel("Steam Username");
        label.setToolTipText("Used to download game versions. Leave empty to sign in with a QR code; Cogfly fills this in after you do.");
        JTextField field = new JTextField(Cogfly.settings.steamUsername, 12);
        field.getDocument().addDocumentListener(new DocumentListener() {
            private void changed() {
                parent.update(s -> s.steamUsername = field.getText().trim());
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                changed();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                changed();
            }
        });
        JButton signOut = new JButton("Forget Login");
        signOut.setToolTipText("Deletes the saved Steam login. You'll be asked to sign in again on your next download.");
        signOut.addActionListener(_ -> {
            try {
                Downloader.logout();
                JOptionPane.showMessageDialog(this, "Saved Steam login removed.", "Steam", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, e.getMessage(), "Steam", JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        right.add(field);
        right.add(signOut);
        add(label, right);
    }
}
