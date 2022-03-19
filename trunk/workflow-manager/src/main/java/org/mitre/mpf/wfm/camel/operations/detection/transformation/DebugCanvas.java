/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2021 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2021 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

package org.mitre.mpf.wfm.camel.operations.detection.transformation;

import org.apache.commons.lang3.StringUtils;
import org.mitre.mpf.wfm.data.entities.transients.Detection;

import javax.swing.*;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.Optional;

public class DebugCanvas {
    private static final int CANVAS_WIDTH = 900;
    private static final int CANVAS_HEIGHT = 900;
    private static final int DEFAULT_STROKE_WIDTH = 2;
    private static final int CIRCLE_DIAMETER = 16;

    private Graphics2D g2;
    private BufferedImage bufferedImage;

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
    }

    public static void show(String title) {
        JDialog dialog = new JDialog();
        dialog.setTitle(title);
        dialog.setSize(CANVAS_WIDTH, CANVAS_HEIGHT);
        dialog.getContentPane().add(new JLabel(new ImageIcon(instance.bufferedImage)));
        dialog.setModal(true);
        dialog.setVisible(true);
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

    public static void draw(Detection detection, Color origColor, Color transformedColor) {


        Rectangle2D.Double detectionRect = new Rectangle2D.Double(detection.getX(), detection.getY(),
                detection.getWidth(), detection.getHeight());

        AffineTransform detectionTransform = DetectionTransformationProcessor.getInPlaceTransform(detection);
        Shape detectionShape = detectionTransform.createTransformedShape(detectionRect);

        if (origColor != null) {
            draw(detectionRect, origColor);
            draw(detectionRect.getX(), detection.getY(), origColor);
        }

        draw(detectionShape, transformedColor);
        draw(detection.getX(), detection.getY(), transformedColor);
    }
}