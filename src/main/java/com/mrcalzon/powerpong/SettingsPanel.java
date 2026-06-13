package com.mrcalzon.powerpong;

import com.mrcalzon.powerpong.GameConfig.GameMode;
import com.mrcalzon.powerpong.GameConfig.PowerUpType;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridBagLayout;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.function.Consumer;

final class SettingsPanel extends UiTheme.StarfieldPanel {
    private final JLabel modeLabel = new JLabel("", SwingConstants.CENTER);
    private final JCheckBox masterPowerUps = UiTheme.themedCheckBox("ENABLE RANDOM POWER-UPS", true);
    private final JCheckBox soundEnabled = UiTheme.themedCheckBox("ENABLE LWJGL OPENAL AUDIO", true);
    private final Map<PowerUpType, JCheckBox> powerUpBoxes = new EnumMap<>(PowerUpType.class);
    private GameMode selectedMode = GameMode.ONE_PLAYER;

    SettingsPanel(Consumer<GameConfig> onStart, Runnable onBack) {
        super(150);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("PRE-GAME CONFIGURATION", SwingConstants.CENTER);
        title.setFont(UiTheme.HEADING.deriveFont(31f));
        title.setForeground(UiTheme.CYAN);

        modeLabel.setFont(UiTheme.SMALL.deriveFont(15f));
        modeLabel.setForeground(UiTheme.MUTED);

        JPanel options = new JPanel();
        options.setOpaque(false);
        options.setLayout(new BoxLayout(options, BoxLayout.Y_AXIS));
        masterPowerUps.setAlignmentX(LEFT_ALIGNMENT);
        soundEnabled.setAlignmentX(LEFT_ALIGNMENT);
        options.add(masterPowerUps);
        options.add(Box.createVerticalStrut(12));

        for (PowerUpType type : PowerUpType.values()) {
            JCheckBox box = UiTheme.themedCheckBox(type.displayName() + "  //  " + type.description(), true);
            box.setFont(UiTheme.BODY.deriveFont(14f));
            box.setAlignmentX(LEFT_ALIGNMENT);
            powerUpBoxes.put(type, box);
            options.add(box);
            options.add(Box.createVerticalStrut(8));
        }

        options.add(Box.createVerticalStrut(12));
        options.add(soundEnabled);

        masterPowerUps.addActionListener(e -> powerUpBoxes.values().forEach(box -> box.setEnabled(masterPowerUps.isSelected())));

        UiTheme.GlowButton start = new UiTheme.GlowButton("LAUNCH MATCH");
        start.addActionListener(e -> onStart.accept(buildConfig()));
        UiTheme.GlowButton back = new UiTheme.GlowButton("BACK");
        back.addActionListener(e -> onBack.run());

        JPanel buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.add(start);
        buttons.add(Box.createHorizontalStrut(10));
        buttons.add(back);

        JPanel card = UiTheme.translucentPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        card.setPreferredSize(new Dimension(980, 580));
        title.setAlignmentX(CENTER_ALIGNMENT);
        modeLabel.setAlignmentX(CENTER_ALIGNMENT);
        options.setAlignmentX(CENTER_ALIGNMENT);
        buttons.setAlignmentX(CENTER_ALIGNMENT);
        card.add(title);
        card.add(Box.createVerticalStrut(7));
        card.add(modeLabel);
        card.add(Box.createVerticalStrut(28));
        card.add(options);
        card.add(Box.createVerticalGlue());
        card.add(buttons);

        JPanel center = new JPanel(new GridBagLayout());
        center.setOpaque(false);
        center.setBorder(BorderFactory.createEmptyBorder(24, 24, 24, 24));
        center.add(card);
        add(center, BorderLayout.CENTER);

        JLabel note = new JLabel("Power-ups are claimed by whichever paddle last touched the collecting ball.", SwingConstants.CENTER);
        note.setForeground(new Color(126, 191, 220));
        note.setFont(UiTheme.SMALL);
        note.setBorder(BorderFactory.createEmptyBorder(0, 0, 18, 0));
        add(note, BorderLayout.SOUTH);

        prepare(GameMode.ONE_PLAYER);
    }

    void prepare(GameMode mode) {
        selectedMode = mode;
        modeLabel.setText("MATCH TYPE  //  " + mode.displayName());
    }

    private GameConfig buildConfig() {
        EnumSet<PowerUpType> enabled = EnumSet.noneOf(PowerUpType.class);
        if (masterPowerUps.isSelected()) {
            powerUpBoxes.forEach((type, box) -> {
                if (box.isSelected()) {
                    enabled.add(type);
                }
            });
        }
        return new GameConfig(selectedMode, enabled, soundEnabled.isSelected());
    }
}
