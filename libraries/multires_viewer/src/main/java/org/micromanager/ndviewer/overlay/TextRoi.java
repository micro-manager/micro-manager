package org.micromanager.ndviewer.overlay;

import java.awt.geom.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.StringReader;
import java.util.StringTokenizer;
import java.util.Vector;

/**
 * This class is a rectangular ROI containing text.
 */
public class TextRoi extends Roi {

   public static final int LEFT = 0, CENTER = 1, RIGHT = 2;
   static final int MAX_LINES = 50;

   private static final String line1 = "Enter text, then press";
   private static final String line2 = "ctrl+b to add to overlay";
   private static final String line3 = "or ctrl+d to draw.";
   private static final String line1a = "Enter text...";
   private String[] theText = new String[MAX_LINES];
   private static String name = "SansSerif";
   private static int style = Font.PLAIN;
   private static int size = 18;
   private Font instanceFont;
   private static boolean newFont = true;
   private static boolean antialiasedText = true; // global flag used by text tool
   private static int globalJustification;
   private static Color defaultFillColor;
   private int justification;
   private boolean antialiased = antialiasedText;
   private double previousMag;
   private boolean firstChar = true;
   private boolean firstMouseUp = true;
   private int cline = 0;
   private boolean drawStringMode;
   private double angle;  // degrees
   private static double defaultAngle;
   private static boolean firstTime = true;

   /**
    * Creates a TextRoi.
    */
   public TextRoi(int x, int y, String text) {
      super(x, y, 1, 1);
      init(text, null);
   }

   /**
    * Creates a TextRoi using sub-pixel coordinates.
    */
   public TextRoi(double x, double y, String text) {
      super(x, y, 1.0, 1.0);
      init(text, null);
   }

   /**
    * Creates a TextRoi using the specified location and Font.
    *
    * @see ij.gui.Roi#setStrokeColor
    * @see ij.gui.Roi#setNonScalable
    * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
    */
   public TextRoi(int x, int y, String text, Font font) {
      super(x, y, 1, 1);
      init(text, font);
   }

   /**
    * Creates a TextRoi using the specified sub-pixel location and Font.
    */
   public TextRoi(double x, double y, String text, Font font) {
      super(x, y, 1.0, 1.0);
      init(text, font);
   }

   /**
    * Creates a TextRoi using the specified location, size and Font. public
    * TextRoi(int x, int y, int width, int height, String text, Font font) {
    * super(x, y, width, height); init(text, font); }
    *
    * /** Creates a TextRoi using the specified sub-pixel location, size and
    * Font.
    */
   public TextRoi(double x, double y, double width, double height, String text, Font font) {
      super(x, y, width, height);
      init(text, font);
   }

   private void init(String text, Font font) {
      String[] lines = split(text, "\n");
      int count = Math.min(lines.length, MAX_LINES);
      for (int i = 0; i < count; i++) {
         theText[i] = lines[i];
      }
      if (font == null) {
         font = new Font(name, style, size);
      }
      instanceFont = font;
      firstChar = false;

   }

   static String[] splitLines(String str) {
      Vector v = new Vector();
      try {
         BufferedReader br = new BufferedReader(new StringReader(str));
         String line;
         while (true) {
            line = br.readLine();
            if (line == null) {
               break;
            }
            v.addElement(line);
         }
         br.close();
      } catch (Exception e) {
      }
      String[] lines = new String[v.size()];
      v.copyInto((String[]) lines);
      return lines;
   }

   /**
    * Splits a string into substring using the characters contained in the
    * second argument as the delimiter set.
    */
   public static String[] split(String str, String delim) {
      if (delim.equals("\n")) {
         return splitLines(str);
      }
      StringTokenizer t = new StringTokenizer(str, delim);
      int tokens = t.countTokens();
      String[] strings;
      if (tokens > 0) {
         strings = new String[tokens];
         for (int i = 0; i < tokens; i++) {
            strings[i] = t.nextToken();
         }
      } else {
         strings = new String[0];
      }
      return strings;
   }


   Font getScaledFont() {
      if (nonScalable) {
         return instanceFont;
      } else {
         if (instanceFont == null) {
            instanceFont = new Font(name, style, size);
         }
         double mag = 1;
         return instanceFont.deriveFont((float) (instanceFont.getSize() * mag));
      }
   }


   /**
    * Draws the text on the screen, clipped to the ROI.
    */
   public void draw(Graphics g) {

      Color c = getStrokeColor();
      setStrokeColor(getColor());
      super.draw(g); // draw the rectangle
      setStrokeColor(c);
      double mag = 1;
      int sx = (int) (getXBase());
      int sy = (int) (getYBase());
      int swidth = (int) ((bounds != null ? bounds.width : width) * mag);
      int sheight = (int) ((bounds != null ? bounds.height : height) * mag);
      Rectangle r = null;
      if (angle != 0.0) {
         drawText(g);
      } else {
         r = g.getClipBounds();
         g.setClip(sx, sy, swidth, sheight);
         drawText(g);
         if (r != null) {
            g.setClip(r.x, r.y, r.width, r.height);
         }
      }
   }

   public void drawOverlay(Graphics g) {
      drawText(g);
   }

   void drawText(Graphics g) {
      g.setColor(strokeColor != null ? strokeColor : ROIColor);
      double mag = 1;
      int xi = (int) Math.round(getXBase());
      int yi = (int) Math.round(getYBase());
      double widthd = bounds != null ? bounds.width : width;
      double heightd = bounds != null ? bounds.height : height;
      int widthi = (int) Math.round(widthd);
      int heighti = (int) Math.round(heightd);
      int sx = (int) (nonScalable ? xi : (getXBase()));
      int sy = (int) (nonScalable ? yi : (getYBase()));
      int sw = nonScalable ? widthi : (int) (1 * widthd);
      int sh = nonScalable ? heighti : (int) (1 * heightd);
      Font font = getScaledFont();
      FontMetrics metrics = g.getFontMetrics(font);
      int fontHeight = metrics.getHeight();
      int descent = metrics.getDescent();
      g.setFont(font);
      Graphics2D g2d = (Graphics2D) g;
      AffineTransform at = null;
      if (angle != 0.0) {
         at = g2d.getTransform();
         double cx = sx, cy = sy;
         double theta = Math.toRadians(angle);
//         if (drawStringMode) {
//            cx = screenX(x);
//            cy = screenY(y + height - descent);
//         }
         g2d.rotate(-theta, cx, cy);
      }
      int i = 0;
      if (fillColor != null) {
//         updateBounds(g);
         Color c = g.getColor();
         int alpha = fillColor.getAlpha();
         g.setColor(fillColor);
         g.fillRect(sx, sy, sw, sh);
         g.setColor(c);
      }
      int y2 = y;
      while (i < MAX_LINES && theText[i] != null) {
         switch (justification) {
            case LEFT:
               if (drawStringMode) {
                  g.drawString(theText[i], (x), (y2 + height - descent));
                  y2 += fontHeight / mag;
               } else {
                  g.drawString(theText[i], sx, sy + fontHeight - descent);
               }
               break;
            case CENTER:
               int tw = metrics.stringWidth(theText[i]);
               g.drawString(theText[i], sx + (sw - tw) / 2, sy + fontHeight - descent);
               break;
            case RIGHT:
               tw = metrics.stringWidth(theText[i]);
               g.drawString(theText[i], sx + sw - tw, sy + fontHeight - descent);
               break;
         }
         i++;
         sy += fontHeight;
      }
      if (at != null) // restore transformation matrix used to rotate text
      {
         g2d.setTransform(at);
      }
   }

   /**
    * Returns the name of the global (default) font.
    */
   public static String getFont() {
      return name;
   }

   /**
    * Returns the global (default) font size.
    */
   public static int getSize() {
      return size;
   }

   /**
    * Returns the global (default) font style.
    */
   public static int getStyle() {
      return style;
   }

   /**
    * Set the current (instance) font.
    */
   public void setCurrentFont(Font font) {
      instanceFont = font;
   }

   /**
    * Returns the current (instance) font.
    */
   public Font getCurrentFont() {
      return instanceFont;
   }

   /**
    * Returns the state of global 'antialiasedText' variable, which is used by
    * the "Fonts" widget.
    */
   public static boolean isAntialiased() {
      return antialiasedText;
   }

   /**
    * Sets the 'antialiased' instance variable.
    */
   public void setAntialiased(boolean antialiased) {
      this.antialiased = antialiased;
      if (angle > 0.0) {
         this.antialiased = true;
      }
   }

   /**
    * Returns the state of the 'antialiased' instance variable.
    */
   public boolean getAntialiased() {
      return antialiased;
   }

   /**
    * Sets the 'justification' instance variable (must be LEFT, CENTER or RIGHT)
    */
   public static void setGlobalJustification(int justification) {
      if (justification < 0 || justification > RIGHT) {
         justification = LEFT;
      }
      globalJustification = justification;

   }

   /**
    * Returns the global (default) justification (LEFT, CENTER or RIGHT).
    */
   public static int getGlobalJustification() {
      return globalJustification;
   }

   /**
    * Sets the 'justification' instance variable (must be LEFT, CENTER or RIGHT)
    */
   public void setJustification(int justification) {
      if (justification < 0 || justification > RIGHT) {
         justification = LEFT;
      }
      this.justification = justification;
   }

   /**
    * Returns the value of the 'justification' instance variable (LEFT, CENTER
    * or RIGHT).
    */
   public int getJustification() {
      return justification;
   }

   /**
    * Sets the global font face, size and style that will be used by TextROIs
    * interactively created using the text tool.
    */
   public static void setFont(String fontName, int fontSize, int fontStyle) {
      setFont(fontName, fontSize, fontStyle, true);
   }

   /**
    * Sets the font face, size, style and antialiasing mode that will be used by
    * TextROIs interactively created using the text tool.
    */
   public static void setFont(String fontName, int fontSize, int fontStyle, boolean antialiased) {
      name = fontName;
      size = fontSize;
      style = fontStyle;
      globalJustification = LEFT;
      antialiasedText = antialiased;
      newFont = true;

   }

   /**
    * Sets the default fill (background) color.
    */
   public static void setDefaultFillColor(Color fillColor) {
      defaultFillColor = fillColor;
   }

   /**
    * Sets the default angle.
    */
   public static void setDefaultAngle(double angle) {
      defaultAngle = angle;
   }

   double stringWidth(String s, FontMetrics metrics, Graphics g) {
      java.awt.geom.Rectangle2D r = metrics.getStringBounds(s, g);
      return r.getWidth();
   }

   private String text() {
      String text = "";
      for (int i = 0; i < MAX_LINES; i++) {
         if (theText[i] == null) {
            break;
         }
         text += theText[i];
         if (theText[i + 1] != null) {
            text += "\\n";
         }
      }
      return text;
   }

   public String getText() {
      String text = "";
      for (int i = 0; i < MAX_LINES; i++) {
         if (theText[i] == null) {
            break;
         }
         text += theText[i] + "\n";
      }
      return text;
   }

   public boolean isDrawingTool() {
      return true;
   }

   public double getAngle() {
      return angle;
   }

   public void setAngle(double angle) {
      this.angle = angle;
      if (angle != 0.0) {
         setAntialiased(true);
      }
   }

   public boolean getDrawStringMode() {
      return drawStringMode;
   }

   public void setDrawStringMode(boolean drawStringMode) {
      this.drawStringMode = drawStringMode;
   }

}
