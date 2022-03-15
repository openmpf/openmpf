package org.mitre.mpf.wfm.camel.operations.detection.transformation;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;

public class DebugCanvas {
    private static final int CANVAS_WIDTH = 900;
    private static final int CANVAS_HEIGHT = 900;
    private static final int DEFAULT_STROKE_WIDTH = 2;
    private static final int CIRCLE_DIAMETER = 16;

    private static final Map<String, Color> KNOWN_COLORS = new HashMap<>();
    private static final String[] COLOR_NAMES;

    static Color MED_GREEN = new Color(0, 128, 0);
    static Color YELLOW_BROWN = new Color(153, 153, 0);

    static {
        // KNOWN_COLORS.put("black",     Color.black);
        // KNOWN_COLORS.put("white",     Color.white);
        KNOWN_COLORS.put("red",       Color.red);
        KNOWN_COLORS.put("blue",      Color.blue);
        KNOWN_COLORS.put("green",     Color.green);
        // KNOWN_COLORS.put("pink",      Color.pink);
        KNOWN_COLORS.put("cyan",      Color.cyan);
        KNOWN_COLORS.put("purple",    Color.magenta);
        KNOWN_COLORS.put("orange",    Color.orange);
        // KNOWN_COLORS.put("yellow",    Color.yellow);
        // KNOWN_COLORS.put("darkgray",  Color.darkGray);
        KNOWN_COLORS.put("lightgray", Color.lightGray);

        COLOR_NAMES = KNOWN_COLORS.keySet().toArray(new String[0]);
    }

    private Graphics2D g2;
    private BufferedImage bufferedImage;
    private int colorIndex;

    private static DebugCanvas instance = new DebugCanvas();

    private DebugCanvas() {
        bufferedImage = new BufferedImage(CANVAS_WIDTH, CANVAS_HEIGHT, BufferedImage.TYPE_INT_RGB);
        g2 = (Graphics2D) bufferedImage.getGraphics();

        g2.setPaint(Color.WHITE);
        g2.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

        g2.setStroke(new BasicStroke(1));
        g2.setColor(Color.BLACK);
        g2.drawLine(CANVAS_WIDTH / 2, 0, CANVAS_WIDTH / 2, CANVAS_HEIGHT);
        g2.drawLine(0, CANVAS_HEIGHT / 2, CANVAS_WIDTH, CANVAS_HEIGHT / 2);

        g2.translate(CANVAS_WIDTH / 2, CANVAS_HEIGHT / 2); // move all shapes into pos. X and Y quadrant
        g2.scale(0.5, 0.5);

        colorIndex = 0;
    }

    public static void show(String title) {
        JDialog dialog = new JDialog();
        dialog.setTitle(title);
        dialog.setSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        dialog.getContentPane().add(new JLabel(new ImageIcon(instance.bufferedImage)));
        dialog.setModal(true);
        dialog.setVisible(true);
    }

    public static void draw(Shape shape) {
        String colorName = nextColor();
        Color color = KNOWN_COLORS.get(colorName);
        instance.g2.setColor(color);
        instance.g2.draw(shape);
        System.out.println("Color: " + colorName);
    }

    public static void draw(Shape shape, Color color) {
        draw(shape, color, DEFAULT_STROKE_WIDTH);
    }

    public static void draw(Shape shape, Color color, int strokeWidth) {
        instance.g2.setColor(color);
        instance.g2.setStroke(new BasicStroke(strokeWidth));
        instance.g2.draw(shape);
    }

    public static void draw(Point2D pt, Color color) {
        draw(pt.getX(), pt.getY(), color);
    }

    public static void draw(double x, double y, Color color) {
        instance.g2.setColor(color);
        int tmpX = (int) Math.round(x);
        int tmpY = (int) Math.round(y);
        instance.g2.fillOval(tmpX - CIRCLE_DIAMETER/2, tmpY - CIRCLE_DIAMETER/2,
                CIRCLE_DIAMETER, CIRCLE_DIAMETER);
    }

    public static void clear() {
        instance = new DebugCanvas();
    }

    private static String nextColor() {
        String colorName = COLOR_NAMES[instance.colorIndex];
        instance.colorIndex = (instance.colorIndex + 1) % COLOR_NAMES.length;
        return colorName;
    }
}