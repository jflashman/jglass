package com.jglass.firmware;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import javax.imageio.ImageIO;

/** Generates the deterministic 640x360 JGlass XE24 boot animation frames. */
public final class BootAnimationGenerator {
    private static final int WIDTH = 640;
    private static final int HEIGHT = 360;
    private static final int FRAME_COUNT = 48;
    private static final int FPS = 24;
    private static final Color CYAN = new Color(0, 255, 255);
    private static final Color YELLOW = new Color(255, 255, 0);

    private BootAnimationGenerator() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("Output directory is required");
        File root = new File(args[0]);
        File frames = new File(root, "part0");
        if (!frames.isDirectory() && !frames.mkdirs()) throw new IllegalStateException("Could not create frame directory");
        Files.write(new File(root, "desc.txt").toPath(),
                (WIDTH + " " + HEIGHT + " " + FPS + "\np 0 0 part0\n").getBytes(StandardCharsets.US_ASCII));
        for (int frame = 0; frame < FRAME_COUNT; frame++) {
            BufferedImage image = draw(frame / (double) FRAME_COUNT);
            ImageIO.write(image, "png", new File(frames, String.format("jglass_%04d.png", frame)));
        }
    }

    private static BufferedImage draw(double progress) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLACK);
        graphics.fillRect(0, 0, WIDTH, HEIGHT);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);

        double centerX = WIDTH / 2.0;
        double centerY = 143.0;
        double scale = 0.86;
        stroke(graphics, CYAN, 2.0f);
        circle(graphics, centerX, centerY, 100 * scale);
        circle(graphics, centerX, centerY, 25.322 * scale);

        segmented(graphics, centerX, centerY, 82 * scale, 4.0f, 360 * wave(progress, 1.0), CYAN,
                new double[] {101, 38, 151, 25, 198, 54, 270, 31, 311, 13, 340, 40});
        arc(graphics, centerX, centerY, 69 * scale, -45, 315, -360 * wave(progress, 2.0), YELLOW, 1.5f);
        segmented(graphics, centerX, centerY, 55 * scale, 5.0f, -360 * wave(progress, 1.5), CYAN,
                new double[] {239, 143, 72, 72});
        segmented(graphics, centerX, centerY, 43 * scale, 1.2f, 360 * wave(progress, 1.5), CYAN,
                new double[] {214, 112, 34, 112});
        arc(graphics, centerX, centerY, 16 * scale, -45, 318, 360 * wave(progress, 3.0), YELLOW, 2.0f);
        stroke(graphics, CYAN, 1.0f);
        circle(graphics, centerX, centerY, 5.55 * scale);

        graphics.setColor(Color.WHITE);
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 39));
        String brand = "JGLASS";
        int brandWidth = graphics.getFontMetrics().stringWidth(brand);
        graphics.drawString(brand, (WIDTH - brandWidth) / 2, 294);
        graphics.setColor(new Color(100, 100, 100));
        graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
        String subtitle = "PERSONAL ASSISTANT";
        int subtitleWidth = graphics.getFontMetrics().stringWidth(subtitle);
        graphics.drawString(subtitle, (WIDTH - subtitleWidth) / 2, 321);
        graphics.dispose();
        return image;
    }

    private static double wave(double progress, double cycles) {
        return 0.5 - 0.5 * Math.cos(progress * Math.PI * 2.0 * cycles);
    }

    private static void circle(Graphics2D graphics, double centerX, double centerY, double radius) {
        graphics.draw(new Ellipse2D.Double(centerX - radius, centerY - radius, radius * 2, radius * 2));
    }

    private static void segmented(Graphics2D graphics, double centerX, double centerY, double radius,
            float width, double rotation, Color color, double[] arcs) {
        stroke(graphics, color, width);
        Graphics2D rotated = (Graphics2D) graphics.create();
        rotated.rotate(Math.toRadians(rotation), centerX, centerY);
        for (int index = 0; index < arcs.length; index += 2) {
            rotated.draw(new Arc2D.Double(centerX - radius, centerY - radius, radius * 2, radius * 2,
                    -arcs[index], -arcs[index + 1], Arc2D.OPEN));
        }
        rotated.dispose();
    }

    private static void arc(Graphics2D graphics, double centerX, double centerY, double radius,
            double start, double sweep, double rotation, Color color, float width) {
        stroke(graphics, color, width);
        Graphics2D rotated = (Graphics2D) graphics.create();
        rotated.rotate(Math.toRadians(rotation), centerX, centerY);
        rotated.draw(new Arc2D.Double(centerX - radius, centerY - radius, radius * 2, radius * 2,
                -start, -sweep, Arc2D.OPEN));
        rotated.dispose();
    }

    private static void stroke(Graphics2D graphics, Color color, float width) {
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER));
    }
}
