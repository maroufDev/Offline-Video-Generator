package org.example;

import org.jcodec.api.SequenceEncoder;
import org.jcodec.common.model.Picture;
import javafx.application.Platform;
import javafx.embed.swing.JFXPanel;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import org.jcodec.scale.AWTUtil;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;

public class PhysicsVideoCreator {

    private static final int WIDTH = 1080;
    private static final int HEIGHT = 1920;
    private static final int FPS = 60;
    private static final int DURATION = 5;
    private static final Random random = new Random();

    private static class Ball {
        double x, y, velocityX, velocityY;
        int radius;
        Color color;
        private static final double GRAVITY = 0.7;
        private static final double DAMPING = 0.85;
        private static final double WALL_DAMPING = 0.95;

        public Ball(double x, double y, int radius, Color color) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.color = color;
            this.velocityX = (random.nextDouble() - 0.5) * 10;
            this.velocityY = (random.nextDouble() - 0.5) * 10;
        }

        public void updatePhysics() {
            velocityY += GRAVITY;
            x += velocityX;
            y += velocityY;
        }

        public void handleWallCollisions(int frame) {
            // Bottom collision
            if (y + radius > HEIGHT) {
                y = HEIGHT - radius;
                velocityY *= -DAMPING;
            }

            // Left/Right collisions
            if (x - radius < 0) {
                x = radius;
                velocityX *= -WALL_DAMPING;
            } else if (x + radius > WIDTH) {
                x = WIDTH - radius;
                velocityX *= -WALL_DAMPING;
            }
        }
    }

    public static void main(String[] args) {
        new JFXPanel(); // Initialize
        Platform.runLater(() -> {
            try {
                generateVideo();
            } catch (Exception e) {
                System.err.println("Error generating video: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private static void generateVideo() throws Exception {
        String outputDir = createOutputDirectory();
        File outputFile = new File(outputDir + "/physics_demo.mp4");
        SequenceEncoder encoder = SequenceEncoder.createSequenceEncoder(outputFile, FPS);

        Ball[] balls = createBalls(5);
        Canvas canvas = createCanvas();
        GraphicsContext gc = canvas.getGraphicsContext2D();

        for (int frame = 0; frame < FPS * DURATION; frame++) {
            gc.clearRect(0, 0, WIDTH, HEIGHT);
            drawDynamicBackground(gc, frame);

            updatePhysics(balls, frame);
            handleBallCollisions(balls);
            renderBalls(gc, balls);
            addTextOverlay(gc);

            encoder.encodeNativeFrame(createFrame(canvas));
        }

        encoder.finish();
        System.out.println("Video created: " + outputFile.getAbsolutePath());
    }

    private static String createOutputDirectory() throws IOException {
        String outputDir = System.getProperty("user.home") + "/Desktop/videos";
        Files.createDirectories(Paths.get(outputDir));
        return outputDir;
    }

    private static Ball[] createBalls(int count) {
        Ball[] balls = new Ball[count];
        for (int i = 0; i < balls.length; i++) {
            balls[i] = new Ball(random.nextInt(WIDTH - 200) + 100, random.nextInt(HEIGHT / 2), 30 + random.nextInt(40), Color.rgb(random.nextInt(255), random.nextInt(255), random.nextInt(255), 0.9));
        }
        return balls;
    }

    private static Canvas createCanvas() {
        Canvas canvas = new Canvas(WIDTH, HEIGHT);
        canvas.getGraphicsContext2D().setFill(Color.BLACK);
        return canvas;
    }

    private static void updatePhysics(Ball[] balls, int frame) {
        for (Ball ball : balls) {
            ball.updatePhysics();
            ball.handleWallCollisions(frame);
        }
    }

    private static void handleBallCollisions(Ball[] balls) {
        for (int i = 0; i < balls.length; i++) {
            for (int j = i + 1; j < balls.length; j++) {
                handleCollision(balls[i], balls[j]);
            }
        }
    }

    private static void handleCollision(Ball a, Ball b) {
        double dx = a.x - b.x;
        double dy = a.y - b.y;
        double distance = Math.sqrt(dx * dx + dy * dy);
        double minDistance = a.radius + b.radius;

        if (distance < minDistance) {
            // Normalize collision vector
            double nx = dx / distance;
            double ny = dy / distance;

            // Calculate relative velocity
            double rvx = a.velocityX - b.velocityX;
            double rvy = a.velocityY - b.velocityY;
            double velAlongNormal = rvx * nx + rvy * ny;

            // Only resolve if moving toward each other
            if (velAlongNormal > 0) return;

            // Calculate masses (using area as mass)
            double massA = a.radius * a.radius;
            double massB = b.radius * b.radius;

            // Calculate impulse
            double e = 0.9; // Coefficient of restitution
            double j = -(1 + e) * velAlongNormal;
            j /= (1 / massA + 1 / massB);

            // Apply impulse
            double jx = j * nx;
            double jy = j * ny;

            a.velocityX += jx / massA;
            a.velocityY += jy / massA;
            b.velocityX -= jx / massB;
            b.velocityY -= jy / massB;

            // Position correction
            double overlap = minDistance - distance;
            double correction = overlap * 0.5;
            a.x += correction * nx;
            a.y += correction * ny;
            b.x -= correction * nx;
            b.y -= correction * ny;
        }
    }

    private static void renderBalls(GraphicsContext gc, Ball[] balls) {
        for (Ball ball : balls) {
            // Shadow
            gc.setFill(Color.BLACK.deriveColor(0, 1, 1, 0.3));
            gc.fillOval(ball.x - ball.radius + 8, ball.y + 12, ball.radius * 2, ball.radius * 2);

            // Main ball
            gc.setFill(ball.color);
            gc.fillOval(ball.x - ball.radius, ball.y, ball.radius * 2, ball.radius * 2);
        }
    }

    private static void addTextOverlay(GraphicsContext gc) {
        gc.setFill(Color.WHITE);
        gc.setFont(javafx.scene.text.Font.font("Impact", 60));
        gc.fillText("Physics Demo!", WIDTH / 4.5, 150);
    }

    private static Picture createFrame(Canvas canvas) {
        return AWTUtil.fromBufferedImageRGB(javafx.embed.swing.SwingFXUtils.fromFXImage(canvas.snapshot(null, null), null));
    }

    private static void drawDynamicBackground(GraphicsContext gc, int frame) {
        double time = frame * 0.03;
        Color color1 = Color.hsb(time * 50 % 360, 0.6, 0.8);
        Color color2 = Color.hsb((time * 50 + 180) % 360, 0.6, 0.8);

        gc.setFill(new javafx.scene.paint.LinearGradient(0, 0, 1, 1, true, javafx.scene.paint.CycleMethod.REFLECT, new javafx.scene.paint.Stop(0, color1), new javafx.scene.paint.Stop(1, color2)));
        gc.fillRect(0, 0, WIDTH, HEIGHT);
    }
}
