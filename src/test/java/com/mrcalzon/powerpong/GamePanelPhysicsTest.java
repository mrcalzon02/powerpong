package com.mrcalzon.powerpong;

import com.mrcalzon.powerpong.GameConfig.GameMode;
import com.mrcalzon.powerpong.GameConfig.PowerUpType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class GamePanelPhysicsTest {
    private static final Method RESET_MATCH = method(GamePanel.class, "resetMatch");
    private static final Method UPDATE_BALLS = method(GamePanel.class, "updateBalls", double.class);
    private static final Field BALLS = field(GamePanel.class, "balls");
    private static final Field LEFT = field(GamePanel.class, "left");
    private static final Field RIGHT = field(GamePanel.class, "right");

    private final List<GamePanel> panels = new ArrayList<>();

    @BeforeAll
    static void enableHeadlessMode() {
        System.setProperty("java.awt.headless", "true");
    }

    @AfterEach
    void closePanels() {
        for (GamePanel panel : panels) {
            panel.shutdown();
        }
        panels.clear();
    }

    @Test
    void matchInitializationCreatesOneDeterministicMovingBall() throws Exception {
        GamePanel panel = newPanel();
        Object firstBall = activeBall(panel);
        double firstVx = number(firstBall, "vx");
        double firstVy = number(firstBall, "vy");

        RESET_MATCH.invoke(panel);

        Object resetBall = activeBall(panel);
        assertEquals(1, balls(panel).size());
        assertEquals(firstVx, number(resetBall, "vx"), 0.0);
        assertEquals(firstVy, number(resetBall, "vy"), 0.0);
        assertTrue(Double.isFinite(firstVx));
        assertTrue(Double.isFinite(firstVy));
        assertTrue(Math.abs(firstVx) >= staticNumber("BASE_BALL_SPEED") * 0.85);
    }

    @Test
    void ballReflectsFromTopWallWithoutScoring() throws Exception {
        GamePanel panel = newPanel();
        Object ball = activeBall(panel);
        Object left = LEFT.get(panel);
        Object right = RIGHT.get(panel);
        double top = staticNumber("ARENA_TOP");
        double radius = number(ball, "radius");
        int leftLives = integer(left, "lives");
        int rightLives = integer(right, "lives");

        setNumber(ball, "x", staticNumber("WIDTH") * 0.5);
        setNumber(ball, "y", top + radius + 1.0);
        setNumber(ball, "vx", 300.0);
        setNumber(ball, "vy", -600.0);

        updateBalls(panel, 0.01);

        assertEquals(top + radius, number(ball, "y"), 0.000001);
        assertTrue(number(ball, "vy") > 0.0);
        assertEquals(leftLives, integer(left, "lives"));
        assertEquals(rightLives, integer(right, "lives"));
        assertEquals(1, balls(panel).size());
    }

    @Test
    void ballReflectsFromBottomWallWithoutScoring() throws Exception {
        GamePanel panel = newPanel();
        Object ball = activeBall(panel);
        Object left = LEFT.get(panel);
        Object right = RIGHT.get(panel);
        double bottom = staticNumber("ARENA_BOTTOM");
        double radius = number(ball, "radius");
        int leftLives = integer(left, "lives");
        int rightLives = integer(right, "lives");

        setNumber(ball, "x", staticNumber("WIDTH") * 0.5);
        setNumber(ball, "y", bottom - radius - 1.0);
        setNumber(ball, "vx", -300.0);
        setNumber(ball, "vy", 600.0);

        updateBalls(panel, 0.01);

        assertEquals(bottom - radius, number(ball, "y"), 0.000001);
        assertTrue(number(ball, "vy") < 0.0);
        assertEquals(leftLives, integer(left, "lives"));
        assertEquals(rightLives, integer(right, "lives"));
        assertEquals(1, balls(panel).size());
    }

    @Test
    void ballReturnsFromLeftPaddleWithoutEnteringGoal() throws Exception {
        GamePanel panel = newPanel();
        Object ball = activeBall(panel);
        Object paddle = LEFT.get(panel);
        double radius = number(ball, "radius");
        double paddleRight = number(paddle, "x") + number(paddle, "width");
        double paddleCenter = number(paddle, "y") + number(paddle, "height") * 0.5;
        int lives = integer(paddle, "lives");

        setNumber(ball, "x", paddleRight + radius + 2.0);
        setNumber(ball, "y", paddleCenter);
        setNumber(ball, "vx", -600.0);
        setNumber(ball, "vy", 0.0);

        updateBalls(panel, 0.01);

        assertTrue(number(ball, "vx") > 0.0);
        assertTrue(number(ball, "x") > paddleRight);
        assertEquals(lives, integer(paddle, "lives"));
    }

    @Test
    void ballReturnsFromRightPaddleWithoutEnteringGoal() throws Exception {
        GamePanel panel = newPanel();
        Object ball = activeBall(panel);
        Object paddle = RIGHT.get(panel);
        double radius = number(ball, "radius");
        double paddleLeft = number(paddle, "x");
        double paddleCenter = number(paddle, "y") + number(paddle, "height") * 0.5;
        int lives = integer(paddle, "lives");

        setNumber(ball, "x", paddleLeft - radius - 2.0);
        setNumber(ball, "y", paddleCenter);
        setNumber(ball, "vx", 600.0);
        setNumber(ball, "vy", 0.0);

        updateBalls(panel, 0.01);

        assertTrue(number(ball, "vx") < 0.0);
        assertTrue(number(ball, "x") < paddleLeft);
        assertEquals(lives, integer(paddle, "lives"));
    }

    @Test
    void crossingLeftGoalScoresForRightPlayerAndImmediatelyRestoresBall() throws Exception {
        GamePanel panel = newPanel();
        Object missedBall = activeBall(panel);
        Object left = LEFT.get(panel);
        Object right = RIGHT.get(panel);
        double radius = number(missedBall, "radius");
        int leftLives = integer(left, "lives");
        int rightLives = integer(right, "lives");

        setNumber(missedBall, "x", -radius - 1.0);
        setNumber(missedBall, "vx", -300.0);
        updateBalls(panel, 0.0);

        assertEquals(leftLives - 1, integer(left, "lives"));
        assertEquals(rightLives, integer(right, "lives"));
        assertFalse((boolean) field(GamePanel.class, "gameOver").get(panel));
        assertEquals(1, balls(panel).size());
        Object replacement = activeBall(panel);
        assertEquals(staticNumber("WIDTH") * 0.5, number(replacement, "x"), 0.000001);
        assertTrue(number(replacement, "vx") < 0.0);
    }

    @Test
    void crossingRightGoalScoresForLeftPlayerAndImmediatelyRestoresBall() throws Exception {
        GamePanel panel = newPanel();
        Object missedBall = activeBall(panel);
        Object left = LEFT.get(panel);
        Object right = RIGHT.get(panel);
        double radius = number(missedBall, "radius");
        int leftLives = integer(left, "lives");
        int rightLives = integer(right, "lives");

        setNumber(missedBall, "x", staticNumber("WIDTH") + radius + 1.0);
        setNumber(missedBall, "vx", 300.0);
        updateBalls(panel, 0.0);

        assertEquals(leftLives, integer(left, "lives"));
        assertEquals(rightLives - 1, integer(right, "lives"));
        assertFalse((boolean) field(GamePanel.class, "gameOver").get(panel));
        assertEquals(1, balls(panel).size());
        Object replacement = activeBall(panel);
        assertEquals(staticNumber("WIDTH") * 0.5, number(replacement, "x"), 0.000001);
        assertTrue(number(replacement, "vx") > 0.0);
    }

    @Test
    void activeMatchRecoversIfBallCollectionIsUnexpectedlyEmpty() throws Exception {
        GamePanel panel = newPanel();
        Object left = LEFT.get(panel);
        Object right = RIGHT.get(panel);
        int leftLives = integer(left, "lives");
        int rightLives = integer(right, "lives");

        balls(panel).clear();
        assertEquals(0, balls(panel).size());

        updateBalls(panel, 0.0);

        assertEquals(1, balls(panel).size());
        assertNotNull(activeBall(panel));
        assertEquals(leftLives, integer(left, "lives"));
        assertEquals(rightLives, integer(right, "lives"));
    }

    private GamePanel newPanel() throws Exception {
        GamePanel panel = new GamePanel(
                new GameConfig(GameMode.ONE_PLAYER, EnumSet.noneOf(PowerUpType.class), false),
                () -> { });
        panels.add(panel);
        RESET_MATCH.invoke(panel);
        assertEquals(1, balls(panel).size());
        return panel;
    }

    private static void updateBalls(GamePanel panel, double seconds) throws Exception {
        UPDATE_BALLS.invoke(panel, seconds);
    }

    private static Object activeBall(GamePanel panel) throws Exception {
        List<Object> balls = balls(panel);
        assertFalse(balls.isEmpty());
        return balls.get(0);
    }

    @SuppressWarnings("unchecked")
    private static List<Object> balls(GamePanel panel) throws Exception {
        return (List<Object>) BALLS.get(panel);
    }

    private static double staticNumber(String name) throws Exception {
        return field(GamePanel.class, name).getDouble(null);
    }

    private static double number(Object target, String name) throws Exception {
        return field(target.getClass(), name).getDouble(target);
    }

    private static int integer(Object target, String name) throws Exception {
        return field(target.getClass(), name).getInt(target);
    }

    private static void setNumber(Object target, String name, double value) throws Exception {
        field(target.getClass(), name).setDouble(target, value);
    }

    private static Field field(Class<?> type, String name) {
        try {
            Field field = type.getDeclaredField(name);
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }

    private static Method method(Class<?> type, String name, Class<?>... parameterTypes) {
        try {
            Method method = type.getDeclaredMethod(name, parameterTypes);
            method.setAccessible(true);
            return method;
        } catch (ReflectiveOperationException exception) {
            throw new ExceptionInInitializerError(exception);
        }
    }
}
