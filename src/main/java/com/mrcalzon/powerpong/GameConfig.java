package com.mrcalzon.powerpong;

import java.util.EnumSet;
import java.util.Objects;

/** Immutable settings chosen before a match begins. */
public record GameConfig(GameMode mode, EnumSet<PowerUpType> enabledPowerUps, boolean soundEnabled) {
    public GameConfig {
        Objects.requireNonNull(mode, "mode");
        enabledPowerUps = enabledPowerUps == null
                ? EnumSet.noneOf(PowerUpType.class)
                : EnumSet.copyOf(enabledPowerUps);
    }

    @Override
    public EnumSet<PowerUpType> enabledPowerUps() {
        return EnumSet.copyOf(enabledPowerUps);
    }

    public enum GameMode {
        ONE_PLAYER("1 VS AI"),
        TWO_PLAYER("2 PLAYER");

        private final String displayName;

        GameMode(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    public enum PowerUpType {
        POWER_SHOT("POWER SHOT", "Charges the next return with extreme speed."),
        BALL_CONTROL("BALL CONTROL", "Movement bends your outgoing ball for eight seconds."),
        MULTI_BALL("MULTI BALL", "Splits the active ball into a three-ball attack."),
        EXTRA_LIFE("LIFE", "Restores one life, up to a maximum of five."),
        FREEZE_BALL("FREEZE BALL", "Your next return holds the ball briefly before release.");

        private final String displayName;
        private final String description;

        PowerUpType(String displayName, String description) {
            this.displayName = displayName;
            this.description = description;
        }

        public String displayName() {
            return displayName;
        }

        public String description() {
            return description;
        }
    }
}
