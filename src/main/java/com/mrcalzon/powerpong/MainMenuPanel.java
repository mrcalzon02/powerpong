package com.mrcalzon.powerpong;

import com.mrcalzon.powerpong.GameConfig.GameMode;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

final class MainMenuPanel extends UiTheme.StarfieldPanel {
    MainMenuPanel(Consumer<GameMode> onModeSelected, Runnable onExit) {
        super(170);
        setLayout(new BorderLayout());

        JLabel title = new JLabel("POWERPONG", SwingConstants.CENTER);
        title.setFont(UiTheme.TITLE);
        title.setForeground(UiTheme.CYAN);

        JLabel subtitle = new JLabel("VECTOR COMBAT // PADDLE SYSTEM ONLINE", SwingConstants.CENTER);
        subtitle.setFont(UiTheme.SMALL.deriveFont(14f));
        subtitle.setForeground(UiTheme.MUTED);

        UiTheme.GlowButton ai = button("1 VS AI", e -> onModeSelected.accept(GameMode.ONE_PLAYER));
        UiTheme.GlowButton twoPlayer = button("2 PLAYER", e -> onModeSelected.accept(GameMode.TWO_PLAYER));
        UiTheme.GlowButton exit = button("EXIT", e -> onExit.run());

        var stack = UiTheme.translucentPanel();
        stack.setLayout(new BoxLayout(stack, BoxLayout.Y_AXIS));
        stack.setPreferredSize(new Dimension(430, 430));

        title.setAlignmentX(CENTER_ALIGNMENT);
        subtitle.setAlignmentX(CENTER_ALIGNMENT);
        ai.setAlignmentX(CENTER_ALIGNMENT);
        twoPlayer.setAlignmentX(CENTER_ALIGNMENT);
        exit.setAlignmentX(CENTER_ALIGNMENT);

        stack.add(title);
        stack.add(Box.createVerticalStrut(7));
        stack.add(subtitle);
        stack.add(Box.createVerticalStrut(45));
        stack.add(ai);
        stack.add(Box.createVerticalStrut(14));
        stack.add(twoPlayer);
        stack.add(Box.createVerticalStrut(14));
        stack.add(exit);

        var center = new javax.swing.JPanel();
        center.setOpaque(false);
        center.setLayout(new java.awt.GridBagLayout());
        center.add(stack);
        add(center, BorderLayout.CENTER);

        JLabel footer = new JLabel("P-1  W / S     //     P-2  UP / DOWN     //     SPACE  PAUSE", SwingConstants.CENTER);
        footer.setForeground(new Color(126, 191, 220));
        footer.setFont(UiTheme.SMALL);
        footer.setBorder(javax.swing.BorderFactory.createEmptyBorder(0, 0, 22, 0));
        add(footer, BorderLayout.SOUTH);
    }

    private static UiTheme.GlowButton button(String text, ActionListener listener) {
        UiTheme.GlowButton button = new UiTheme.GlowButton(text);
        button.addActionListener(listener);
        return button;
    }
}
