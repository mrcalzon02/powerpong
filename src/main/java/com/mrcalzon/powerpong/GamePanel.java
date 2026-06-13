package com.mrcalzon.powerpong;

import com.mrcalzon.powerpong.AudioEngine.Sound;
import com.mrcalzon.powerpong.GameConfig.GameMode;
import com.mrcalzon.powerpong.GameConfig.PowerUpType;

import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.ActionEvent;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

final class GamePanel extends JPanel {
    private static final int WIDTH = 1280;
    private static final int HEIGHT = 720;
    private static final double ARENA_TOP = 78;
    private static final double ARENA_BOTTOM = 680;
    private static final double LEFT_PADDLE_X = 58;
    private static final double RIGHT_PADDLE_X = 1200;
    private static final double PADDLE_WIDTH = 22;
    private static final double PADDLE_HEIGHT = 116;
    private static final double BALL_RADIUS = 9;
    private static final double BASE_BALL_SPEED = 255;
    private static final double MAX_BALL_SPEED = 860;
    private static final double BASE_PADDLE_SPEED = 245;
    private static final double MAX_PADDLE_SPEED = 455;
    private static final double MAX_BOUNCE_ANGLE = Math.toRadians(63);
    private static final int MAX_BALLS = 7;

    private final GameConfig config;
    private final Runnable onExit;
    private final Timer timer;
    private final Random random = new Random();
    private final Paddle left = new Paddle(Side.LEFT, LEFT_PADDLE_X);
    private final Paddle right = new Paddle(Side.RIGHT, RIGHT_PADDLE_X);
    private final List<Ball> balls = new ArrayList<>();
    private final List<Pickup> pickups = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Star> stars = new ArrayList<>();
    private final AudioEngine audio;

    private boolean p1Up;
    private boolean p1Down;
    private boolean p2Up;
    private boolean p2Down;
    private boolean paused;
    private boolean gameOver;
    private boolean shuttingDown;
    private long lastNanos;
    private double gameTime;
    private double visualTime;
    private double nextPowerUpAt;
    private double serveAt;
    private Side serveToward = Side.RIGHT;
    private String winnerText = "";
    private String statusText = "";
    private Side statusOwner;
    private double statusUntil;

    GamePanel(GameConfig config, Runnable onExit) {
        this.config = config;
        this.onExit = onExit;
        this.audio = new AudioEngine(config.soundEnabled());
        this.timer = new Timer(8, this::tick);
        this.timer.setCoalesce(true);
        setBackground(UiTheme.BLACK);
        setFocusable(true);
        createStars();
        installKeyBindings();
    }

    void begin() {
        resetMatch();
        lastNanos = System.nanoTime();
        timer.start();
        requestFocusInWindow();
    }

    void shutdown() {
        if (shuttingDown) {
            return;
        }
        shuttingDown = true;
        timer.stop();
        audio.close();
    }

    private void resetMatch() {
        shuttingDown = false;
        paused = false;
        gameOver = false;
        winnerText = "";
        statusText = "";
        gameTime = 0;
        visualTime = 0;
        balls.clear();
        pickups.clear();
        particles.clear();
        left.reset();
        right.reset();
        nextPowerUpAt = 6.0 + random.nextDouble() * 3.0;
        serveAt = 0.8;
        serveToward = random.nextBoolean() ? Side.LEFT : Side.RIGHT;
    }

    private void createStars() {
        Random seeded = new Random(0xB4115EEDL);
        for (int i = 0; i < 210; i++) {
            stars.add(new Star(seeded.nextDouble() * WIDTH, seeded.nextDouble() * HEIGHT,
                    0.55 + seeded.nextDouble() * 1.8, seeded.nextDouble() * Math.PI * 2.0,
                    0.2 + seeded.nextDouble() * 0.8));
        }
    }

    private void tick(ActionEvent ignored) {
        long now = System.nanoTime();
        double dt = Math.min(0.033, Math.max(0.0, (now - lastNanos) / 1_000_000_000.0));
        lastNanos = now;
        visualTime += dt;
        if (!paused && !gameOver) {
            update(dt);
        } else {
            updateParticles(dt * 0.35);
        }
        repaint();
    }

    private void update(double dt) {
        gameTime += dt;
        updatePaddles(dt);
        updateBalls(dt);
        updatePickups();
        updateParticles(dt);

        if (!config.enabledPowerUps().isEmpty() && gameTime >= nextPowerUpAt) {
            spawnPowerUp();
            nextPowerUpAt = gameTime + 6.5 + random.nextDouble() * 5.5;
        }

        if (balls.isEmpty() && !gameOver && gameTime >= serveAt) {
            spawnServe(serveToward);
        }
    }

    private void updatePaddles(double dt) {
        double maxSpeed = currentPaddleSpeed();
        double leftInput = (p1Down ? 1.0 : 0.0) - (p1Up ? 1.0 : 0.0);
        left.velocity = leftInput * maxSpeed;

        if (config.mode() == GameMode.TWO_PLAYER) {
            double rightInput = (p2Down ? 1.0 : 0.0) - (p2Up ? 1.0 : 0.0);
            right.velocity = rightInput * maxSpeed;
        } else {
            updateAi(maxSpeed);
        }

        if (gameTime < left.frozenUntil) {
            left.velocity = 0;
        }
        if (gameTime < right.frozenUntil) {
            right.velocity = 0;
        }

        left.y = GameMath.clamp(left.y + left.velocity * dt, ARENA_TOP, ARENA_BOTTOM - PADDLE_HEIGHT);
        right.y = GameMath.clamp(right.y + right.velocity * dt, ARENA_TOP, ARENA_BOTTOM - PADDLE_HEIGHT);
    }

    private void updateAi(double maxSpeed) {
        Ball threat = balls.stream()
                .filter(ball -> ball.attachedTo != Side.RIGHT)
                .min(Comparator.comparingDouble(ball -> {
                    if (ball.vx > 0.0) {
                        return Math.max(0.0, (right.x - ball.x) / ball.vx);
                    }
                    return 20.0 + Math.abs(right.x - ball.x) / Math.max(1.0, Math.abs(ball.vx));
                }))
                .orElse(null);

        double target = (ARENA_TOP + ARENA_BOTTOM) * 0.5;
        if (threat != null) {
            double seconds = threat.vx > 1.0 ? Math.max(0.0, (right.x - threat.x) / threat.vx) : 0.45;
            target = GameMath.reflectedY(threat.y, threat.vy, seconds,
                    ARENA_TOP + BALL_RADIUS, ARENA_BOTTOM - BALL_RADIUS);
            target += Math.sin(gameTime * 1.47) * (15.0 + Math.min(24.0, threat.speed() * 0.025));
        }

        double delta = target - right.centerY();
        double aiLimit = maxSpeed * 0.82;
        right.velocity = GameMath.clamp(delta * 4.1, -aiLimit, aiLimit);
        if (Math.abs(delta) < 5.0) {
            right.velocity *= 0.2;
        }
    }

    private void updateBalls(double dt) {
        List<Ball> additions = new ArrayList<>();
        Iterator<Ball> iterator = balls.iterator();
        while (iterator.hasNext()) {
            Ball ball = iterator.next();

            if (ball.attachedTo != null) {
                updateAttachedBall(ball);
                continue;
            }

            applyBallControl(ball, dt);
            accelerateBall(ball, dt);

            double previousX = ball.x;
            ball.x += ball.vx * dt;
            ball.y += ball.vy * dt;

            if (ball.y - ball.radius <= ARENA_TOP && ball.vy < 0) {
                ball.y = ARENA_TOP + ball.radius;
                ball.vy = Math.abs(ball.vy);
                audio.play(Sound.WALL);
                burst(ball.x, ball.y, UiTheme.BLUE, 5, 80);
            } else if (ball.y + ball.radius >= ARENA_BOTTOM && ball.vy > 0) {
                ball.y = ARENA_BOTTOM - ball.radius;
                ball.vy = -Math.abs(ball.vy);
                audio.play(Sound.WALL);
                burst(ball.x, ball.y, UiTheme.BLUE, 5, 80);
            }

            if (ball.vx < 0 && previousX - ball.radius >= left.x + left.width
                    && ball.x - ball.radius <= left.x + left.width
                    && overlapsPaddle(ball, left)) {
                returnFromPaddle(ball, left);
            } else if (ball.vx > 0 && previousX + ball.radius <= right.x
                    && ball.x + ball.radius >= right.x
                    && overlapsPaddle(ball, right)) {
                returnFromPaddle(ball, right);
            }

            if (ball.x + ball.radius < 0) {
                iterator.remove();
                loseLife(left, Side.LEFT, ball.y);
                continue;
            }
            if (ball.x - ball.radius > WIDTH) {
                iterator.remove();
                loseLife(right, Side.RIGHT, ball.y);
                continue;
            }

            for (Pickup pickup : pickups) {
                if (!pickup.consumed && ball.lastHit != null && distanceSquared(ball.x, ball.y, pickup.x, pickup.y) <= square(ball.radius + pickup.radius)) {
                    pickup.consumed = true;
                    applyPowerUp(pickup.type, ball.lastHit, ball, additions);
                }
            }
        }

        if (!additions.isEmpty()) {
            int capacity = Math.max(0, MAX_BALLS - balls.size());
            balls.addAll(additions.subList(0, Math.min(capacity, additions.size())));
        }

        if (balls.isEmpty() && !gameOver && serveAt < gameTime) {
            serveAt = gameTime + 0.9;
        }
    }

    private void updateAttachedBall(Ball ball) {
        Paddle paddle = paddle(ball.attachedTo);
        ball.x = ball.attachedTo == Side.LEFT
                ? paddle.x + paddle.width + ball.radius + 2
                : paddle.x - ball.radius - 2;
        ball.y = GameMath.clamp(paddle.centerY() + ball.attachOffset,
                ARENA_TOP + ball.radius, ARENA_BOTTOM - ball.radius);

        if (gameTime >= ball.releaseAt) {
            Side side = ball.attachedTo;
            double direction = side == Side.LEFT ? 1.0 : -1.0;
            ball.attachedTo = null;
            ball.vx = Math.cos(ball.releaseAngle) * ball.releaseSpeed * direction;
            ball.vy = Math.sin(ball.releaseAngle) * ball.releaseSpeed + paddle.velocity * 0.14;
            normalizeBall(ball, Math.min(MAX_BALL_SPEED * 1.18, Math.max(BASE_BALL_SPEED, ball.releaseSpeed)));
            audio.play(Sound.FREEZE);
            burst(ball.x, ball.y, UiTheme.CYAN, 18, 190);
        }
    }

    private void applyBallControl(Ball ball, double dt) {
        if (ball.lastHit == Side.LEFT && ball.vx > 0 && gameTime < left.ballControlUntil) {
            ball.vy += left.velocity * 0.95 * dt;
        } else if (ball.lastHit == Side.RIGHT && ball.vx < 0 && gameTime < right.ballControlUntil) {
            ball.vy += right.velocity * 0.95 * dt;
        }
        double speed = ball.speed();
        if (speed > 0) {
            double minimumHorizontal = speed * 0.28;
            if (Math.abs(ball.vx) < minimumHorizontal) {
                ball.vx = Math.copySign(minimumHorizontal, ball.vx == 0 ? (ball.lastHit == Side.RIGHT ? -1 : 1) : ball.vx);
                normalizeBall(ball, speed);
            }
        }
    }

    private void accelerateBall(Ball ball, double dt) {
        double speed = ball.speed();
        if (speed <= 0) {
            return;
        }
        double accelerated = Math.min(MAX_BALL_SPEED, speed * Math.exp(0.043 * dt));
        normalizeBall(ball, accelerated);
    }

    private void returnFromPaddle(Ball ball, Paddle paddle) {
        double impact = GameMath.clamp((ball.y - paddle.centerY()) / (paddle.height * 0.5), -1.0, 1.0);
        double angle = impact * MAX_BOUNCE_ANGLE;
        double speed = Math.min(MAX_BALL_SPEED, Math.max(BASE_BALL_SPEED, ball.speed() * 1.055));
        boolean powered = paddle.powerShotCharges > 0;
        if (powered) {
            paddle.powerShotCharges--;
            speed = Math.min(MAX_BALL_SPEED * 1.22, speed * 1.52);
            ball.powerTrailUntil = gameTime + 1.15;
            audio.play(Sound.POWER_SHOT);
        } else {
            audio.play(Sound.PADDLE);
        }

        ball.lastHit = paddle.side;
        ball.x = paddle.side == Side.LEFT
                ? paddle.x + paddle.width + ball.radius + 1
                : paddle.x - ball.radius - 1;

        if (paddle.freezeCharges > 0) {
            paddle.freezeCharges--;
            ball.attachedTo = paddle.side;
            ball.attachOffset = GameMath.clamp(ball.y - paddle.centerY(), -paddle.height * 0.42, paddle.height * 0.42);
            ball.releaseAt = gameTime + 0.78;
            ball.releaseSpeed = speed;
            ball.releaseAngle = angle;
            audio.play(Sound.FREEZE);
            status(paddle.side, "TEMPORAL HOLD", 1.2);
        } else {
            double direction = paddle.side == Side.LEFT ? 1.0 : -1.0;
            ball.vx = Math.cos(angle) * speed * direction;
            ball.vy = Math.sin(angle) * speed + paddle.velocity * 0.16;
            normalizeBall(ball, speed);
        }

        burst(ball.x, ball.y, powered ? new Color(178, 93, 255) : UiTheme.CYAN, powered ? 20 : 9, powered ? 230 : 130);
    }

    private boolean overlapsPaddle(Ball ball, Paddle paddle) {
        return ball.y + ball.radius >= paddle.y && ball.y - ball.radius <= paddle.y + paddle.height;
    }

    private void loseLife(Paddle loser, Side missedSide, double y) {
        loser.lives--;
        serveToward = missedSide;
        serveAt = gameTime + 0.95;
        audio.play(Sound.SCORE);
        burst(missedSide == Side.LEFT ? 8 : WIDTH - 8, y, new Color(255, 76, 132), 28, 260);
        status(missedSide, "LIFE LOST", 1.1);
        if (loser.lives <= 0) {
            gameOver = true;
            Side winner = missedSide.opposite();
            winnerText = labelFor(winner) + " WINS";
            balls.clear();
        }
    }

    private void updatePickups() {
        pickups.removeIf(pickup -> pickup.consumed || gameTime - pickup.spawnedAt > 11.5);
    }

    private void spawnPowerUp() {
        if (pickups.size() >= 2) {
            return;
        }
        EnumSet<PowerUpType> enabled = config.enabledPowerUps();
        if (enabled.isEmpty()) {
            return;
        }
        List<PowerUpType> options = new ArrayList<>(enabled);
        PowerUpType type = options.get(random.nextInt(options.size()));
        double x = WIDTH * (0.31 + random.nextDouble() * 0.38);
        double y = ARENA_TOP + 70 + random.nextDouble() * (ARENA_BOTTOM - ARENA_TOP - 140);
        pickups.add(new Pickup(type, x, y, gameTime));
    }

    private void applyPowerUp(PowerUpType type, Side owner, Ball source, List<Ball> additions) {
        Paddle paddle = paddle(owner);
        switch (type) {
            case POWER_SHOT -> paddle.powerShotCharges = Math.min(3, paddle.powerShotCharges + 1);
            case BALL_CONTROL -> paddle.ballControlUntil = Math.max(paddle.ballControlUntil, gameTime) + 8.0;
            case MULTI_BALL -> splitBall(source, additions);
            case EXTRA_LIFE -> paddle.lives = Math.min(5, paddle.lives + 1);
            case FREEZE_BALL -> paddle.freezeCharges = Math.min(3, paddle.freezeCharges + 1);
        }
        audio.play(Sound.POWER_UP);
        status(owner, type.displayName(), 1.65);
        burst(source.x, source.y, powerUpColor(type), 24, 210);
    }

    private void splitBall(Ball source, List<Ball> additions) {
        double speed = Math.max(BASE_BALL_SPEED, source.speed() * 0.92);
        for (double offset : new double[]{Math.toRadians(-20), Math.toRadians(20)}) {
            Ball clone = source.copy();
            double cos = Math.cos(offset);
            double sin = Math.sin(offset);
            double vx = source.vx * cos - source.vy * sin;
            double vy = source.vx * sin + source.vy * cos;
            double length = GameMath.length(vx, vy);
            clone.vx = vx / Math.max(1.0, length) * speed;
            clone.vy = vy / Math.max(1.0, length) * speed;
            clone.x += Math.copySign(5.0, clone.vx);
            clone.y += Math.copySign(4.0, clone.vy);
            clone.attachedTo = null;
            additions.add(clone);
        }
    }

    private void spawnServe(Side toward) {
        double angle = Math.toRadians(-24 + random.nextDouble() * 48);
        double direction = toward == Side.RIGHT ? 1.0 : -1.0;
        Ball ball = new Ball(WIDTH * 0.5, (ARENA_TOP + ARENA_BOTTOM) * 0.5,
                Math.cos(angle) * BASE_BALL_SPEED * direction,
                Math.sin(angle) * BASE_BALL_SPEED,
                BALL_RADIUS);
        ball.lastHit = toward.opposite();
        balls.add(ball);
        serveAt = Double.POSITIVE_INFINITY;
    }

    private double currentPaddleSpeed() {
        return Math.min(MAX_PADDLE_SPEED, BASE_PADDLE_SPEED + gameTime * 4.2);
    }

    private void normalizeBall(Ball ball, double targetSpeed) {
        double length = ball.speed();
        if (length <= 0.0001) {
            ball.vx = targetSpeed;
            ball.vy = 0;
            return;
        }
        ball.vx = ball.vx / length * targetSpeed;
        ball.vy = ball.vy / length * targetSpeed;
    }

    private Paddle paddle(Side side) {
        return side == Side.LEFT ? left : right;
    }

    private String labelFor(Side side) {
        if (side == Side.LEFT) {
            return "P-1";
        }
        return config.mode() == GameMode.ONE_PLAYER ? "AI" : "P-2";
    }

    private void status(Side owner, String message, double duration) {
        statusOwner = owner;
        statusText = labelFor(owner) + " // " + message;
        statusUntil = gameTime + duration;
    }

    private void updateParticles(double dt) {
        Iterator<Particle> iterator = particles.iterator();
        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.life -= dt;
            if (particle.life <= 0) {
                iterator.remove();
                continue;
            }
            particle.x += particle.vx * dt;
            particle.y += particle.vy * dt;
            particle.vx *= Math.pow(0.12, dt);
            particle.vy *= Math.pow(0.12, dt);
        }
    }

    private void burst(double x, double y, Color color, int count, double speed) {
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double magnitude = speed * (0.25 + random.nextDouble() * 0.75);
            particles.add(new Particle(x, y, Math.cos(angle) * magnitude, Math.sin(angle) * magnitude,
                    0.28 + random.nextDouble() * 0.42, color, 1.0 + random.nextDouble() * 2.5));
        }
    }

    private void installKeyBindings() {
        bindHold("pressed W", "p1-up-on", () -> p1Up = true);
        bindHold("released W", "p1-up-off", () -> p1Up = false);
        bindHold("pressed S", "p1-down-on", () -> p1Down = true);
        bindHold("released S", "p1-down-off", () -> p1Down = false);
        bindHold("pressed UP", "p2-up-on", () -> p2Up = true);
        bindHold("released UP", "p2-up-off", () -> p2Up = false);
        bindHold("pressed DOWN", "p2-down-on", () -> p2Down = true);
        bindHold("released DOWN", "p2-down-off", () -> p2Down = false);
        bindPress("pressed SPACE", "pause", () -> {
            if (!gameOver) {
                paused = !paused;
            }
        });
        bindPress("pressed P", "pause-p", () -> {
            if (!gameOver) {
                paused = !paused;
            }
        });
        bindPress("pressed ESCAPE", "exit", onExit);
        bindPress("pressed ENTER", "restart", () -> {
            if (gameOver) {
                resetMatch();
            }
        });
        bindPress("pressed R", "restart-r", () -> {
            if (gameOver) {
                resetMatch();
            }
        });
    }

    private void bindHold(String keystroke, String name, Runnable action) {
        InputMap input = getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap actions = getActionMap();
        input.put(KeyStroke.getKeyStroke(keystroke), name);
        actions.put(name, new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.run();
            }
        });
    }

    private void bindPress(String keystroke, String name, Runnable action) {
        bindHold(keystroke, name, action);
    }

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D outer = (Graphics2D) graphics.create();
        outer.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        outer.setColor(UiTheme.BLACK);
        outer.fillRect(0, 0, getWidth(), getHeight());

        double scale = Math.min(getWidth() / (double) WIDTH, getHeight() / (double) HEIGHT);
        double offsetX = (getWidth() - WIDTH * scale) * 0.5;
        double offsetY = (getHeight() - HEIGHT * scale) * 0.5;
        outer.translate(offsetX, offsetY);
        outer.scale(scale, scale);

        drawBackground(outer);
        drawArena(outer);
        for (Ball ball : balls) {
            drawTrajectory(outer, ball);
        }
        drawPickups(outer);
        drawParticles(outer);
        drawPaddle(outer, left, "P-1", new Color(52, 222, 255));
        drawPaddle(outer, right, config.mode() == GameMode.ONE_PLAYER ? "AI" : "P-2", new Color(66, 132, 255));
        for (Ball ball : balls) {
            drawBall(outer, ball);
        }
        drawHud(outer);
        drawOverlay(outer);
        outer.dispose();
    }

    private void drawBackground(Graphics2D g) {
        g.setPaint(new GradientPaint(0, 0, new Color(1, 3, 10), 0, HEIGHT, new Color(3, 14, 31)));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        for (Star star : stars) {
            double y = (star.y + visualTime * 4.0 * star.depth) % HEIGHT;
            double pulse = 0.45 + 0.45 * Math.sin(visualTime * (0.7 + star.depth) + star.phase);
            int alpha = (int) GameMath.clamp(55 + pulse * 165, 40, 220);
            g.setColor(new Color(132, 214, 255, alpha));
            double size = star.size * (0.65 + star.depth * 0.55);
            g.fill(new Ellipse2D.Double(star.x, y, size, size));
        }

        g.setColor(new Color(27, 106, 171, 20));
        g.setStroke(new BasicStroke(1f));
        for (int x = 0; x <= WIDTH; x += 40) {
            g.drawLine(x, (int) ARENA_TOP, x, (int) ARENA_BOTTOM);
        }
        for (int y = (int) ARENA_TOP; y <= ARENA_BOTTOM; y += 40) {
            g.drawLine(0, y, WIDTH, y);
        }
    }

    private void drawArena(Graphics2D g) {
        glowLine(g, 0, ARENA_TOP, WIDTH, ARENA_TOP, UiTheme.BLUE);
        glowLine(g, 0, ARENA_BOTTOM, WIDTH, ARENA_BOTTOM, UiTheme.BLUE);

        g.setStroke(new BasicStroke(2f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 0, new float[]{10, 12}, 0));
        g.setColor(new Color(70, 199, 255, 95));
        g.drawLine(WIDTH / 2, (int) ARENA_TOP + 14, WIDTH / 2, (int) ARENA_BOTTOM - 14);

        g.setStroke(new BasicStroke(1.2f));
        g.setColor(new Color(67, 179, 235, 60));
        g.draw(new Rectangle2D.Double(14, ARENA_TOP + 14, WIDTH - 28, ARENA_BOTTOM - ARENA_TOP - 28));
    }

    private void glowLine(Graphics2D g, double x1, double y1, double x2, double y2, Color color) {
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 28));
        g.setStroke(new BasicStroke(9f));
        g.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 160));
        g.setStroke(new BasicStroke(1.6f));
        g.drawLine((int) x1, (int) y1, (int) x2, (int) y2);
    }

    private void drawTrajectory(Graphics2D g, Ball ball) {
        Velocity projected = projectedVelocity(ball);
        if (Math.abs(projected.vx) < 0.001) {
            return;
        }

        Path2D path = new Path2D.Double();
        double x = ball.x;
        double y = ball.y;
        double vx = projected.vx;
        double vy = projected.vy;
        double targetX = vx > 0 ? right.x : left.x + left.width;
        path.moveTo(x, y);

        for (int i = 0; i < 5; i++) {
            double tx = (targetX - x) / vx;
            if (tx <= 0) {
                break;
            }
            double wallY = vy > 0 ? ARENA_BOTTOM - ball.radius : ARENA_TOP + ball.radius;
            double ty = Math.abs(vy) < 0.001 ? Double.POSITIVE_INFINITY : (wallY - y) / vy;
            if (ty > 0 && ty < tx) {
                x += vx * ty;
                y = wallY;
                path.lineTo(x, y);
                vy = -vy;
            } else {
                x = targetX;
                y += vy * tx;
                path.lineTo(x, y);
                break;
            }
        }

        g.setStroke(new BasicStroke(8f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(62, 220, 255, 13));
        g.draw(path);
        g.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{6, 11}, 0));
        g.setColor(new Color(102, 225, 255, 34));
        g.draw(path);
        g.setStroke(new BasicStroke(1f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 0, new float[]{4, 12}, 0));
        g.setColor(new Color(190, 246, 255, 92));
        g.draw(path);
    }

    private Velocity projectedVelocity(Ball ball) {
        if (ball.attachedTo != null) {
            double direction = ball.attachedTo == Side.LEFT ? 1 : -1;
            return new Velocity(Math.cos(ball.releaseAngle) * ball.releaseSpeed * direction,
                    Math.sin(ball.releaseAngle) * ball.releaseSpeed);
        }
        return new Velocity(ball.vx, ball.vy);
    }

    private void drawPaddle(Graphics2D g, Paddle paddle, String label, Color color) {
        double pulse = 0.5 + 0.5 * Math.sin(visualTime * 3.2 + (paddle.side == Side.LEFT ? 0 : 1.4));
        for (int i = 4; i >= 1; i--) {
            int alpha = (int) (11 + pulse * 7);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha));
            double spread = i * 4.0;
            g.fill(new RoundRectangle2D.Double(paddle.x - spread, paddle.y - spread,
                    paddle.width + spread * 2, paddle.height + spread * 2, 10 + spread, 10 + spread));
        }
        g.setPaint(new GradientPaint((float) paddle.x, (float) paddle.y, Color.WHITE,
                (float) (paddle.x + paddle.width), (float) paddle.y, color));
        g.fill(new RoundRectangle2D.Double(paddle.x, paddle.y, paddle.width, paddle.height, 8, 8));
        g.setColor(new Color(220, 251, 255));
        g.setStroke(new BasicStroke(1.1f));
        g.draw(new RoundRectangle2D.Double(paddle.x, paddle.y, paddle.width, paddle.height, 8, 8));

        g.setFont(UiTheme.SMALL.deriveFont(Font.BOLD, 15f));
        FontMetrics fm = g.getFontMetrics();
        int x = (int) (paddle.centerX() - fm.stringWidth(label) * 0.5);
        int y = (int) paddle.y - 12;
        g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 70));
        g.drawString(label, x + 2, y + 2);
        g.setColor(UiTheme.PALE);
        g.drawString(label, x, y);
    }

    private void drawBall(Graphics2D g, Ball ball) {
        double pulse = 0.5 + 0.5 * Math.sin(visualTime * 7.0 + ball.phase);
        Color glow = gameTime < ball.powerTrailUntil ? new Color(190, 78, 255) : UiTheme.CYAN;

        if (gameTime < ball.powerTrailUntil && ball.attachedTo == null) {
            double speed = Math.max(1.0, ball.speed());
            double nx = ball.vx / speed;
            double ny = ball.vy / speed;
            for (int i = 1; i <= 8; i++) {
                double trailX = ball.x - nx * i * 8;
                double trailY = ball.y - ny * i * 8;
                int alpha = Math.max(5, 70 - i * 8);
                g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), alpha));
                double r = ball.radius * (1.0 - i * 0.05);
                g.fill(new Ellipse2D.Double(trailX - r, trailY - r, r * 2, r * 2));
            }
        }

        for (int i = 4; i >= 1; i--) {
            double r = ball.radius + i * (3.0 + pulse * 1.2);
            int alpha = 8 + i * 5;
            g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), alpha));
            g.fill(new Ellipse2D.Double(ball.x - r, ball.y - r, r * 2, r * 2));
        }

        double core = ball.radius + pulse * 1.5;
        g.setColor(new Color(207, 250, 255));
        g.fill(new Ellipse2D.Double(ball.x - core, ball.y - core, core * 2, core * 2));
        g.setColor(Color.WHITE);
        g.fill(new Ellipse2D.Double(ball.x - core * 0.42, ball.y - core * 0.42, core * 0.84, core * 0.84));

        if (ball.attachedTo != null) {
            g.setStroke(new BasicStroke(2f));
            g.setColor(new Color(125, 236, 255, 150));
            double ring = core + 8 + pulse * 4;
            g.draw(new Ellipse2D.Double(ball.x - ring, ball.y - ring, ring * 2, ring * 2));
        }
    }

    private void drawPickups(Graphics2D g) {
        for (Pickup pickup : pickups) {
            double pulse = 0.5 + 0.5 * Math.sin(visualTime * 4.2 + pickup.phase);
            double radius = pickup.radius + pulse * 2.5;
            Color color = powerUpColor(pickup.type);
            Path2D hex = hexagon(pickup.x, pickup.y, radius);

            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 22));
            g.setStroke(new BasicStroke(10f));
            g.draw(hex);
            g.setColor(new Color(4, 18, 38, 225));
            g.fill(hex);
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 210));
            g.setStroke(new BasicStroke(2f));
            g.draw(hex);

            String abbreviation = switch (pickup.type) {
                case POWER_SHOT -> "PS";
                case BALL_CONTROL -> "BC";
                case MULTI_BALL -> "MB";
                case EXTRA_LIFE -> "+1";
                case FREEZE_BALL -> "FR";
            };
            g.setFont(UiTheme.SMALL.deriveFont(Font.BOLD, 13f));
            FontMetrics fm = g.getFontMetrics();
            g.setColor(UiTheme.PALE);
            g.drawString(abbreviation, (float) (pickup.x - fm.stringWidth(abbreviation) * 0.5), (float) (pickup.y + fm.getAscent() * 0.35));
        }
    }

    private Path2D hexagon(double x, double y, double radius) {
        Path2D path = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double angle = Math.PI / 6 + i * Math.PI / 3;
            double px = x + Math.cos(angle) * radius;
            double py = y + Math.sin(angle) * radius;
            if (i == 0) {
                path.moveTo(px, py);
            } else {
                path.lineTo(px, py);
            }
        }
        path.closePath();
        return path;
    }

    private Color powerUpColor(PowerUpType type) {
        return switch (type) {
            case POWER_SHOT -> new Color(198, 78, 255);
            case BALL_CONTROL -> new Color(42, 232, 214);
            case MULTI_BALL -> new Color(80, 155, 255);
            case EXTRA_LIFE -> new Color(88, 255, 158);
            case FREEZE_BALL -> new Color(151, 229, 255);
        };
    }

    private void drawParticles(Graphics2D g) {
        for (Particle particle : particles) {
            double alphaFraction = GameMath.clamp(particle.life / 0.7, 0, 1);
            int alpha = (int) (alphaFraction * 190);
            g.setColor(new Color(particle.color.getRed(), particle.color.getGreen(), particle.color.getBlue(), alpha));
            g.fill(new Ellipse2D.Double(particle.x - particle.size * 0.5, particle.y - particle.size * 0.5,
                    particle.size, particle.size));
        }
    }

    private void drawHud(Graphics2D g) {
        g.setFont(UiTheme.SMALL.deriveFont(Font.BOLD, 14f));
        g.setColor(UiTheme.PALE);
        g.drawString("P-1  LIVES " + Math.max(0, left.lives), 32, 34);

        String rightName = config.mode() == GameMode.ONE_PLAYER ? "AI" : "P-2";
        String rightLives = rightName + "  LIVES " + Math.max(0, right.lives);
        FontMetrics fm = g.getFontMetrics();
        g.drawString(rightLives, WIDTH - 32 - fm.stringWidth(rightLives), 34);

        String telemetry = String.format("BALL %03d  //  PADDLE %03d  //  %s",
                Math.round(averageBallSpeed()), Math.round(currentPaddleSpeed()),
                config.enabledPowerUps().isEmpty() ? "POWER-UPS OFF" : "POWER-UPS ONLINE");
        g.setColor(UiTheme.MUTED);
        g.drawString(telemetry, WIDTH / 2 - g.getFontMetrics().stringWidth(telemetry) / 2, 34);

        drawEffects(g, left, 32, 58, false);
        drawEffects(g, right, WIDTH - 32, 58, true);

        if (gameTime < statusUntil && !statusText.isBlank()) {
            Color color = statusOwner == Side.LEFT ? UiTheme.CYAN : new Color(94, 148, 255);
            g.setFont(UiTheme.HEADING.deriveFont(Font.BOLD, 18f));
            fm = g.getFontMetrics();
            int x = WIDTH / 2 - fm.stringWidth(statusText) / 2;
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 45));
            g.drawString(statusText, x + 2, 68 + 2);
            g.setColor(color);
            g.drawString(statusText, x, 68);
        }

        g.setFont(UiTheme.SMALL.deriveFont(12f));
        g.setColor(new Color(110, 170, 200));
        String controls = config.mode() == GameMode.ONE_PLAYER
                ? "W/S MOVE    SPACE PAUSE    ESC MENU"
                : "P-1 W/S    P-2 UP/DOWN    SPACE PAUSE    ESC MENU";
        g.drawString(controls, WIDTH / 2 - g.getFontMetrics().stringWidth(controls) / 2, HEIGHT - 14);
    }

    private void drawEffects(Graphics2D g, Paddle paddle, int x, int y, boolean alignRight) {
        List<String> effects = new ArrayList<>();
        if (paddle.powerShotCharges > 0) {
            effects.add("POWER x" + paddle.powerShotCharges);
        }
        if (paddle.freezeCharges > 0) {
            effects.add("FREEZE x" + paddle.freezeCharges);
        }
        if (gameTime < paddle.ballControlUntil) {
            effects.add("CONTROL " + (int) Math.ceil(paddle.ballControlUntil - gameTime) + "s");
        }
        String text = String.join("  //  ", effects);
        if (text.isEmpty()) {
            return;
        }
        g.setColor(new Color(91, 207, 244));
        int drawX = alignRight ? x - g.getFontMetrics().stringWidth(text) : x;
        g.drawString(text, drawX, y);
    }

    private double averageBallSpeed() {
        return balls.stream().mapToDouble(Ball::speed).average().orElse(BASE_BALL_SPEED);
    }

    private void drawOverlay(Graphics2D g) {
        if (!paused && !gameOver) {
            if (balls.isEmpty()) {
                double remaining = Math.max(0, serveAt - gameTime);
                int count = Math.max(1, (int) Math.ceil(remaining));
                drawCenteredOverlay(g, "SERVE " + count, "VECTOR LOCK ACQUIRING");
            }
            return;
        }
        g.setComposite(AlphaComposite.SrcOver.derive(0.78f));
        g.setColor(new Color(1, 5, 14));
        g.fillRect(0, 0, WIDTH, HEIGHT);
        g.setComposite(AlphaComposite.SrcOver);
        if (gameOver) {
            drawCenteredOverlay(g, winnerText, "ENTER / R  RESTART     ESC  MENU");
        } else {
            drawCenteredOverlay(g, "PAUSED", "SPACE / P  RESUME     ESC  MENU");
        }
    }

    private void drawCenteredOverlay(Graphics2D g, String title, String subtitle) {
        g.setFont(UiTheme.TITLE.deriveFont(Font.BOLD, 46f));
        FontMetrics fm = g.getFontMetrics();
        int titleX = WIDTH / 2 - fm.stringWidth(title) / 2;
        int titleY = HEIGHT / 2 - 6;
        g.setColor(new Color(61, 214, 255, 50));
        g.drawString(title, titleX + 4, titleY + 4);
        g.setColor(UiTheme.CYAN);
        g.drawString(title, titleX, titleY);

        g.setFont(UiTheme.SMALL.deriveFont(14f));
        fm = g.getFontMetrics();
        g.setColor(UiTheme.MUTED);
        g.drawString(subtitle, WIDTH / 2 - fm.stringWidth(subtitle) / 2, titleY + 42);
    }

    private static double distanceSquared(double x1, double y1, double x2, double y2) {
        return square(x2 - x1) + square(y2 - y1);
    }

    private static double square(double value) {
        return value * value;
    }

    private enum Side {
        LEFT,
        RIGHT;

        Side opposite() {
            return this == LEFT ? RIGHT : LEFT;
        }
    }

    private static final class Paddle {
        final Side side;
        final double x;
        final double width = PADDLE_WIDTH;
        final double height = PADDLE_HEIGHT;
        double y;
        double velocity;
        int lives;
        int powerShotCharges;
        int freezeCharges;
        double ballControlUntil;
        double frozenUntil;

        Paddle(Side side, double x) {
            this.side = side;
            this.x = x;
            reset();
        }

        void reset() {
            y = (ARENA_TOP + ARENA_BOTTOM - height) * 0.5;
            velocity = 0;
            lives = 3;
            powerShotCharges = 0;
            freezeCharges = 0;
            ballControlUntil = 0;
            frozenUntil = 0;
        }

        double centerX() {
            return x + width * 0.5;
        }

        double centerY() {
            return y + height * 0.5;
        }
    }

    private static final class Ball {
        double x;
        double y;
        double vx;
        double vy;
        final double radius;
        final double phase = Math.random() * Math.PI * 2.0;
        Side lastHit;
        Side attachedTo;
        double attachOffset;
        double releaseAt;
        double releaseSpeed;
        double releaseAngle;
        double powerTrailUntil;

        Ball(double x, double y, double vx, double vy, double radius) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.radius = radius;
        }

        double speed() {
            return GameMath.length(vx, vy);
        }

        Ball copy() {
            Ball copy = new Ball(x, y, vx, vy, radius);
            copy.lastHit = lastHit;
            copy.powerTrailUntil = powerTrailUntil;
            return copy;
        }
    }

    private static final class Pickup {
        final PowerUpType type;
        final double x;
        final double y;
        final double radius = 22;
        final double spawnedAt;
        final double phase = Math.random() * Math.PI * 2.0;
        boolean consumed;

        Pickup(PowerUpType type, double x, double y, double spawnedAt) {
            this.type = type;
            this.x = x;
            this.y = y;
            this.spawnedAt = spawnedAt;
        }
    }

    private static final class Particle {
        double x;
        double y;
        double vx;
        double vy;
        double life;
        final Color color;
        final double size;

        Particle(double x, double y, double vx, double vy, double life, Color color, double size) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.life = life;
            this.color = color;
            this.size = size;
        }
    }

    private record Star(double x, double y, double size, double phase, double depth) {
    }

    private record Velocity(double vx, double vy) {
    }
}
