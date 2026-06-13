# PowerPong

PowerPong is a Java 17 Swing arcade game built around deliberately readable movement, steadily escalating speed, local two-player controls, and player-owned power-ups. The visual direction is a black starfield, electric-blue arena geometry, glowing projected trajectories, pulsing energy balls, and clearly labeled paddles.

LWJGL is used for generated OpenAL sound effects. The game remains playable if an audio device is unavailable; it automatically falls back to silent mode instead of failing to launch.

## Current gameplay

- **1 VS AI** and **2 PLAYER** modes from the main menu.
- A pre-game configuration screen with a master power-up switch and individual power-up toggles.
- Slow opening ball and paddle movement that accelerates throughout the match.
- Three starting lives per side. Life pickups can raise the total to five.
- A faint glowing projected travel line with top/bottom wall reflection previews.
- Pulsing balls, paddle glow, collision particles, telemetry, and power-up status readouts.
- Power-ups belong to the paddle that last touched the collecting ball.

### Power-ups

| Power-up | Effect |
|---|---|
| Power Shot | Charges the owner's next return with extreme speed and a bright trail. |
| Ball Control | For eight seconds, the owner's paddle movement bends outgoing balls. |
| Multi Ball | Splits the collecting ball into a three-ball attack. |
| Life | Restores one life, up to five. |
| Freeze Ball | Charges the owner's next return to hold at the paddle briefly before release. |

## Controls

| Action | Player 1 | Player 2 |
|---|---|---|
| Move up | `W` | `Up Arrow` |
| Move down | `S` | `Down Arrow` |
| Pause / resume | `Space` or `P` | `Space` or `P` |
| Return to menu | `Escape` | `Escape` |
| Restart after match | `Enter` or `R` | `Enter` or `R` |

The AI controls the right paddle in 1 VS AI mode.

## Requirements

- Java 17 or newer.
- Maven 3.9+ to build locally.

No external art, sound, or data files are required. Graphics and audio tones are generated in code.

## Build a self-contained JAR

```bash
mvn clean package
```

The result is:

```text
target/powerpong.jar
```

Maven automatically selects the LWJGL native library for the current operating system and packages it into the shaded executable JAR.

Run it with:

```bash
java -jar target/powerpong.jar
```

On a desktop with Java `.jar` file association enabled, `powerpong.jar` can also be launched directly by double-clicking it. The GitHub Actions workflow builds separate standalone JAR artifacts for Windows x64, Linux x64, macOS Intel, and macOS Apple Silicon.

## Project layout

```text
src/main/java/com/mrcalzon/powerpong/
  PowerPongApp.java    Swing application shell and screen navigation
  MainMenuPanel.java   Mode selection
  SettingsPanel.java   Pre-game configuration
  GamePanel.java       Match simulation, rendering, AI, power-ups, and controls
  AudioEngine.java     Optional LWJGL OpenAL generated audio
  GameConfig.java      Immutable match settings and enums
  GameMath.java        Shared collision/projection math
  UiTheme.java         Neon UI components and starfield panels
```

## Design notes

The game simulates in a fixed 1280×720 logical playfield and scales uniformly to the window. This keeps collision behavior, paddle proportions, and the trajectory preview consistent at different window sizes. Keyboard input uses Swing key bindings rather than a focus-sensitive key listener, making local play more reliable after menus or window resizing.
