package com.mrcalzon.powerpong;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JPanel;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.Random;

final class UiTheme {
    static final Color BLACK = new Color(2, 5, 13);
    static final Color DEEP_BLUE = new Color(4, 15, 35);
    static final Color CYAN = new Color(62, 220, 255);
    static final Color BLUE = new Color(32, 122, 255);
    static final Color PALE = new Color(196, 241, 255);
    static final Color MUTED = new Color(100, 156, 185);
    static final Color PANEL = new Color(5, 18, 37, 220);

    static final Font TITLE = new Font(Font.SANS_SERIF, Font.BOLD, 58);
    static final Font HEADING = new Font(Font.SANS_SERIF, Font.BOLD, 25);
    static final Font BODY = new Font(Font.SANS_SERIF, Font.PLAIN, 16);
    static final Font SMALL = new Font(Font.MONOSPACED, Font.BOLD, 13);

    private UiTheme() {
    }

    static JCheckBox themedCheckBox(String text, boolean selected) {
        JCheckBox box = new JCheckBox(text, selected);
        box.setOpaque(false);
        box.setForeground(PALE);
        box.setFocusPainted(false);
        box.setFont(BODY);
        box.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return box;
    }

    static JPanel translucentPanel() {
        JPanel panel = new JPanel();
        panel.setOpaque(true);
        panel.setBackground(PANEL);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(CYAN.getRed(), CYAN.getGreen(), CYAN.getBlue(), 110), 1),
                BorderFactory.createEmptyBorder(22, 28, 22, 28)
        ));
        return panel;
    }

    static final class GlowButton extends JButton {
        private boolean hover;

        GlowButton(String text) {
            super(text);
            setFont(HEADING.deriveFont(18f));
            setForeground(PALE);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setBorderPainted(false);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setPreferredSize(new Dimension(260, 52));
            addChangeListener(e -> {
                boolean next = getModel().isRollover();
                if (next != hover) {
                    hover = next;
                    repaint();
                }
            });
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            int w = getWidth();
            int h = getHeight();
            RoundRectangle2D shape = new RoundRectangle2D.Double(2, 2, w - 4, h - 4, 10, 10);

            if (hover || getModel().isPressed()) {
                g.setColor(new Color(24, 128, 210, getModel().isPressed() ? 155 : 110));
                g.fill(shape);
            } else {
                g.setColor(new Color(6, 25, 49, 210));
                g.fill(shape);
            }

            g.setStroke(new BasicStroke(hover ? 2.2f : 1.2f));
            g.setColor(hover ? CYAN : new Color(48, 132, 196));
            g.draw(shape);

            FontMetrics fm = g.getFontMetrics(getFont());
            int x = (w - fm.stringWidth(getText())) / 2;
            int y = (h - fm.getHeight()) / 2 + fm.getAscent();
            g.setFont(getFont());
            g.setColor(isEnabled() ? getForeground() : MUTED);
            g.drawString(getText(), x, y);
            g.dispose();
        }
    }

    static class StarfieldPanel extends JPanel {
        private final Star[] stars;
        private final Timer repaintTimer;
        private final long startedAt = System.nanoTime();

        StarfieldPanel(int count) {
            setOpaque(true);
            Random random = new Random(0x50A7B00BL + count);
            stars = new Star[count];
            for (int i = 0; i < count; i++) {
                stars[i] = new Star(random.nextDouble(), random.nextDouble(), 0.5 + random.nextDouble() * 1.8,
                        random.nextDouble() * Math.PI * 2.0);
            }
            repaintTimer = new Timer(40, e -> repaint());
        }

        @Override
        public void addNotify() {
            super.addNotify();
            repaintTimer.start();
        }

        @Override
        public void removeNotify() {
            repaintTimer.stop();
            super.removeNotify();
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setPaint(new GradientPaint(0, 0, BLACK, 0, Math.max(1, getHeight()), DEEP_BLUE));
            g.fillRect(0, 0, getWidth(), getHeight());

            double seconds = (System.nanoTime() - startedAt) / 1_000_000_000.0;
            g.setComposite(AlphaComposite.SrcOver);
            for (Star star : stars) {
                double twinkle = 0.42 + 0.38 * Math.sin(seconds * 1.25 + star.phase);
                int alpha = (int) GameMath.clamp(80 + twinkle * 130, 45, 220);
                g.setColor(new Color(125, 211, 255, alpha));
                int x = (int) (star.x * getWidth());
                int y = (int) ((star.y + seconds * 0.004 * star.size) % 1.0 * getHeight());
                int size = Math.max(1, (int) Math.round(star.size));
                g.fillOval(x, y, size, size);
            }

            g.setStroke(new BasicStroke(1f));
            g.setColor(new Color(25, 110, 180, 24));
            int spacing = 48;
            for (int x = 0; x < getWidth(); x += spacing) {
                g.drawLine(x, 0, x, getHeight());
            }
            for (int y = 0; y < getHeight(); y += spacing) {
                g.drawLine(0, y, getWidth(), y);
            }
            g.dispose();
        }

        private record Star(double x, double y, double size, double phase) {
        }
    }
}
