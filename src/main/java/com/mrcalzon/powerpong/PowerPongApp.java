package com.mrcalzon.powerpong;

import com.mrcalzon.powerpong.GameConfig.GameMode;

import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.awt.CardLayout;
import java.awt.Dimension;

public final class PowerPongApp {
    private static final String MENU = "menu";
    private static final String SETTINGS = "settings";
    private static final String GAME = "game";

    private final JFrame frame = new JFrame("PowerPong");
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel root = new JPanel(cardLayout);
    private final MainMenuPanel menuPanel;
    private final SettingsPanel settingsPanel;
    private GamePanel gamePanel;

    private PowerPongApp() {
        menuPanel = new MainMenuPanel(this::showSettings, frame::dispose);
        settingsPanel = new SettingsPanel(this::startGame, this::showMenu);

        root.add(menuPanel, MENU);
        root.add(settingsPanel, SETTINGS);

        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setContentPane(root);
        frame.setMinimumSize(new Dimension(960, 600));
        frame.setSize(1280, 760);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
        showMenu();
    }

    public static void main(String[] args) {
        System.setProperty("sun.java2d.opengl", "true");
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getCrossPlatformLookAndFeelClassName());
            } catch (Exception ignored) {
                // The custom painting does not depend on a specific look and feel.
            }
            new PowerPongApp();
        });
    }

    private void showSettings(GameMode mode) {
        settingsPanel.prepare(mode);
        cardLayout.show(root, SETTINGS);
    }

    private void startGame(GameConfig config) {
        if (gamePanel != null) {
            gamePanel.shutdown();
            root.remove(gamePanel);
        }
        gamePanel = new GamePanel(config, this::showMenu);
        root.add(gamePanel, GAME);
        cardLayout.show(root, GAME);
        gamePanel.begin();
    }

    private void showMenu() {
        if (gamePanel != null) {
            gamePanel.shutdown();
        }
        cardLayout.show(root, MENU);
    }
}
