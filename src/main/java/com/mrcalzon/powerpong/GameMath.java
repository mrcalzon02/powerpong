package com.mrcalzon.powerpong;

final class GameMath {
    private GameMath() {
    }

    static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    /** Projects a Y coordinate through repeated reflections between two horizontal walls. */
    static double reflectedY(double y, double velocityY, double time, double minY, double maxY) {
        if (maxY <= minY) {
            return minY;
        }
        double span = maxY - minY;
        double raw = y + velocityY * time - minY;
        double period = span * 2.0;
        double folded = raw % period;
        if (folded < 0) {
            folded += period;
        }
        return minY + (folded <= span ? folded : period - folded);
    }

    static double length(double x, double y) {
        return Math.sqrt(x * x + y * y);
    }
}
