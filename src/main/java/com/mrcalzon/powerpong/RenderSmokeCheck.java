package com.mrcalzon.powerpong;

import javax.swing.SwingUtilities;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.EnumSet;
import java.util.concurrent.atomic.AtomicReference;

/** Headless CI check that exercises the actual Swing gameplay renderer. */
public final class RenderSmokeCheck {
    private RenderSmokeCheck() {
    }

    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        AtomicReference<Throwable> failure = new AtomicReference<>();

        SwingUtilities.invokeAndWait(() -> {
            GamePanel panel = null;
            try {
                GameConfig config = new GameConfig(
                        GameConfig.GameMode.ONE_PLAYER,
                        EnumSet.allOf(GameConfig.PowerUpType.class),
                        false);
                panel = new GamePanel(config, () -> { });
                panel.setSize(1280, 720);
                panel.begin();

                BufferedImage frame = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = frame.createGraphics();
                try {
                    panel.paint(graphics);
                } finally {
                    graphics.dispose();
                }

                int centerBallSamples = 0;
                int centerX = frame.getWidth() / 2;
                int centerY = (78 + 680) / 2;
                for (int y = centerY - 22; y <= centerY + 22; y++) {
                    for (int x = centerX - 22; x <= centerX + 22; x++) {
                        int pixel = frame.getRGB(x, y);
                        int red = (pixel >>> 16) & 0xFF;
                        int green = (pixel >>> 8) & 0xFF;
                        int blue = pixel & 0xFF;
                        if (red + green + blue > 650) {
                            centerBallSamples++;
                        }
                    }
                }
                if (centerBallSamples < 20) {
                    throw new IllegalStateException("No visible ball was rendered at match start");
                }

                int illuminatedSamples = 0;
                for (int y = 0; y < frame.getHeight(); y += 8) {
                    for (int x = 0; x < frame.getWidth(); x += 8) {
                        int pixel = frame.getRGB(x, y);
                        int red = (pixel >>> 16) & 0xFF;
                        int green = (pixel >>> 8) & 0xFF;
                        int blue = pixel & 0xFF;
                        if (red + green + blue > 45) {
                            illuminatedSamples++;
                        }
                    }
                }
                if (illuminatedSamples < 250) {
                    throw new IllegalStateException("Gameplay renderer produced an unexpectedly blank frame");
                }
            } catch (Throwable throwable) {
                failure.set(throwable);
            } finally {
                if (panel != null) {
                    panel.shutdown();
                }
            }
        });

        if (failure.get() != null) {
            throw new IllegalStateException("PowerPong render smoke check failed", failure.get());
        }
        System.out.println("PowerPong gameplay render smoke check passed");
    }
}
