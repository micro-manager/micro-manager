package org.micromanager.patternoverlay;

import ij.gui.ImageCanvas;

import java.awt.Color;
import java.awt.Graphics;
import java.util.HashMap;

import org.micromanager.data.Image;
import org.micromanager.display.DisplayWindow;

/**
 * This class handles drawing the different overlay options.
 */
public class OverlayOptions {
   public static final String CROSSHAIR = "Crosshair";
   public static final String GRID = "Grid";
   public static final String CIRCLE = "Circle";
   public static final String TARGET = "Target";
   // Ordered list for arranging the GUI.
   public static final String[] OPTIONS = new String[] {
      CROSSHAIR, GRID, CIRCLE, TARGET
   };

   public static final String[] COLOR_NAMES = new String[] {
      "Red", "Magenta", "Yellow", "Green", "Blue", "Cyan", "Black", "White"
   };
   private static final HashMap<String, Color> colors_;
   static {
      colors_ = new HashMap<String, Color>();
      colors_.put(COLOR_NAMES[0], Color.red);
      colors_.put(COLOR_NAMES[1], Color.magenta);
      colors_.put(COLOR_NAMES[2], Color.yellow);
      colors_.put(COLOR_NAMES[3], Color.green);
      colors_.put(COLOR_NAMES[4], Color.blue);
      colors_.put(COLOR_NAMES[5], Color.cyan);
      colors_.put(COLOR_NAMES[6], Color.black);
      colors_.put(COLOR_NAMES[7], Color.white);
   }

   public static final void drawOverlay(Graphics g, DisplayWindow display,
         Image image, ImageCanvas canvas, String mode, int size, String color) {
      // Don't allow shrinking to invisibility.
      size = (int) Math.max(1, size);
      int canvasWidth = canvas.getWidth();
      int canvasHeight = canvas.getHeight();
      int centerX = canvasWidth / 2;
      int centerY = canvasHeight / 2;
      g.setColor(colors_.get(color));

      if (mode.equals(CROSSHAIR)) {
         int width = size * canvasWidth / 100 / 2;
         int height = size * canvasHeight / 100 / 2;
         g.drawLine(centerX, centerY, centerX - width, centerY - height);
         g.drawLine(centerX, centerY, centerX + width, centerY - height);
         g.drawLine(centerX, centerY, centerX - width, centerY + height);
         g.drawLine(centerX, centerY, centerX + width, centerY + height);
      }
      else if (mode.equals(GRID)) {
         int numPanels = size / 12 + 2;
         double panelWidth = ((double) canvasWidth) / numPanels;
         double panelHeight = ((double) canvasHeight) / numPanels;
         for (int i = 0; i < numPanels; ++i) {
            g.drawLine((int) (i * panelWidth), 0,
                  (int) (i * panelWidth), canvasHeight);
         }
         for (int j = 0; j < numPanels; ++j) {
            g.drawLine(0, (int) (j * panelHeight),
                  canvasWidth, (int) (j * panelHeight));
         }
      }
      else if (mode.equals(CIRCLE) || mode.equals(TARGET)) {
         // Default to CIRCLE mode.
         double[] sizes = new double[] {size};
         if (mode.equals(TARGET)) {
            // Add some extra, smaller circles.
            sizes = new double[] {size, size * .666, size * .333};
         }
         int minDim = (int) Math.min(canvasWidth, canvasHeight);
         for (double fac : sizes) {
            int radius = (int) (minDim / 2 * fac / 100);
            g.drawOval(centerX - radius, centerY - radius,
                  radius * 2, radius * 2);
         }
      }
   }
}
