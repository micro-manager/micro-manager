package org.micromanager.patternoverlay;

import ij.gui.ImageCanvas;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
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
         Image image, ImageCanvas canvas, String mode, int size, String color,
         boolean shouldDrawSize) {
      // Don't allow shrinking to invisibility.
      size = (int) Math.max(1, size);
      int canvasWidth = canvas.getWidth();
      int canvasHeight = canvas.getHeight();
      int centerX = canvasWidth / 2;
      int centerY = canvasHeight / 2;
      g.setColor(colors_.get(color));

      // Record the size of what we draw so we can indicate it.
      // These values are in fractions of the image size (e.g. 1.0 = full width
      // or height of entire image), though we don't account for zoom until it
      // is time to draw the text.
      ArrayList<Double> displayedSizes = new ArrayList<Double>();
      // Formatted string for the size display.
      String sizeText = "";
      if (mode.equals(CROSSHAIR)) {
         int width = size * canvasWidth / 100 / 2;
         int height = size * canvasHeight / 100 / 2;
         g.drawLine(centerX, centerY, centerX - width, centerY - height);
         g.drawLine(centerX, centerY, centerX + width, centerY - height);
         g.drawLine(centerX, centerY, centerX - width, centerY + height);
         g.drawLine(centerX, centerY, centerX + width, centerY + height);
         displayedSizes.add(width * 2.0);
         displayedSizes.add(height * 2.0);
         sizeText = "Crosshair dimensions: %.1f%s x %.1f%s";
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
         displayedSizes.add(panelWidth);
         displayedSizes.add(panelHeight);
         sizeText = "Grid cell dimensions: %.1f%s x %.1f%s";
      }
      else if (mode.equals(CIRCLE) || mode.equals(TARGET)) {
         // Default to CIRCLE mode.
         double[] sizes = new double[] {size};
         sizeText = "Circle diameter: %.1f%s";
         if (mode.equals(TARGET)) {
            // Add some extra, smaller circles.
            sizes = new double[] {size, size * .666, size * .333};
            sizeText = "Circle diameters: %.1f%s, %.1f%s, %.1f%s";
         }
         int minDim = (int) Math.min(canvasWidth, canvasHeight);
         for (double fac : sizes) {
            int radius = (int) (minDim / 2 * fac / 100);
            displayedSizes.add(radius * 2.0);
            g.drawOval(centerX - radius, centerY - radius,
                  radius * 2, radius * 2);
         }
      }

      if (shouldDrawSize) {
         // Convert to pixels or to microns if possible.
         String units = "px";
         Double pixelSize = image.getMetadata().getPixelSizeUm();
         if (pixelSize != null) {
            for (int i = 0; i < displayedSizes.size(); ++i) {
               displayedSizes.set(i, displayedSizes.get(i) * pixelSize);
            }
            units = "\u00b5m";
         }
         // Apply zoom and set up our formatting arguments.
         double zoom = display.getDisplaySettings().getMagnification();
         Object[] args = new Object[displayedSizes.size() * 2];
         for (int i = 0; i < displayedSizes.size(); ++i) {
            args[i * 2] = displayedSizes.get(i) / zoom;
            args[i * 2 + 1] = units;
         }
         sizeText = String.format(sizeText, args);
         g.setFont(new Font("Arial", Font.PLAIN, 12));
         g.drawString(sizeText, 5, canvasHeight - 5);
      }
   }
}
