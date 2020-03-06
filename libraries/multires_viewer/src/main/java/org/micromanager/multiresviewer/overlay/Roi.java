package org.micromanager.multiresviewer.overlay;

import java.awt.*;
import java.util.*;
import java.io.*;
import java.awt.image.*;
import java.awt.event.*;
import java.awt.geom.*;

/**
 * A rectangular region of interest and superclass for the other ROI classes.
 *
 * This class implements {@code Iterable<Point>} and can thus be used to iterate
 * over the contained coordinates. Usage example:
 * <pre>
 * Roi roi = ...;
 * for (Point p : roi) {
 *   // process p
 * }
 * </pre>
 */
public class Roi extends Object implements Cloneable, java.io.Serializable {

   public static final int CONSTRUCTING = 0, MOVING = 1, RESIZING = 2, NORMAL = 3, MOVING_HANDLE = 4; // States
   public static final int RECTANGLE = 0, OVAL = 1, POLYGON = 2, FREEROI = 3, TRACED_ROI = 4, LINE = 5,
           POLYLINE = 6, FREELINE = 7, ANGLE = 8, COMPOSITE = 9, POINT = 10; // Types
   public static final int HANDLE_SIZE = 5;
   public static final int NOT_PASTING = -1;

   static final int NO_MODS = 0, ADD_TO_ROI = 1, SUBTRACT_FROM_ROI = 2; // modification states

   int startX, startY, x, y;
   int width, height;
   double startXD, startYD;
   Rectangle2D.Double bounds;
   int activeHandle;
   int state;
   int modState = NO_MODS;
   int cornerDiameter;

   public static Roi previousRoi;
   public static final BasicStroke onePixelWide = new BasicStroke(1);
   protected static int lineWidth = 1;
   protected static Color defaultFillColor;
   private static Vector listeners = new Vector();
   protected static Color ROIColor = Color.yellow;

   protected int type;
   protected int xMax, yMax;
   private int imageID;
   protected int oldX, oldY, oldWidth, oldHeight;
   protected int clipX, clipY, clipWidth, clipHeight;
   protected boolean constrain; // to be square
   protected boolean center;
   protected boolean aspect;
   protected boolean updateFullWindow;
   protected double mag = 1.0;
   protected double asp_bk; //saves aspect ratio if resizing takes roi very small
   protected Color handleColor = Color.white;
   protected Color strokeColor;
   protected Color instanceColor; //obsolete; replaced by	strokeColor
   protected Color fillColor;
   protected BasicStroke stroke;
   protected boolean nonScalable;
   protected boolean overlay;
   protected boolean wideLine;
   protected boolean ignoreClipRect;
   private String name;
   private int position;
   private int channel, slice, frame;
   private Overlay prototypeOverlay;
   private boolean subPixel;
   private boolean activeOverlayRoi;
   private Properties props;
   private boolean isCursor;
   private double xcenter = Double.NaN;
   private double ycenter;

   /**
    * Creates a rectangular ROI.
    */
   public Roi(int x, int y, int width, int height) {
      this(x, y, width, height, 0);
   }

   /**
    * Creates a rectangular ROI using double arguments.
    */
   public Roi(double x, double y, double width, double height) {
      this(x, y, width, height, 0);
   }

   /**
    * Creates a new rounded rectangular ROI.
    */
   public Roi(int x, int y, int width, int height, int cornerDiameter) {
      if (width < 1) {
         width = 1;
      }
      if (height < 1) {
         height = 1;
      }
//      if (width > xMax) {
//         width = xMax;
//      }
//      if (height > yMax) {
//         height = yMax;
//      }
      this.cornerDiameter = cornerDiameter;
      this.x = x;
      this.y = y;
      startX = x;
      startY = y;
      oldX = x;
      oldY = y;
      oldWidth = 0;
      oldHeight = 0;
      this.width = width;
      this.height = height;
      oldWidth = width;
      oldHeight = height;
      clipX = x;
      clipY = y;
      clipWidth = width;
      clipHeight = height;
      state = NORMAL;
      type = RECTANGLE;

      fillColor = defaultFillColor;
   }

   /**
    * Creates a rounded rectangular ROI using double arguments.
    */
   public Roi(double x, double y, double width, double height, int cornerDiameter) {
      this((int) x, (int) y, (int) Math.ceil(width), (int) Math.ceil(height), cornerDiameter);
      bounds = new Rectangle2D.Double(x, y, width, height);
      subPixel = true;
   }

   /**
    * Creates a new rectangular Roi.
    */
   public Roi(Rectangle r) {
      this(r.x, r.y, r.width, r.height);
   }

   /**
    * Set the location of the ROI in image coordinates.
    */
   public void setLocation(int x, int y) {
      this.x = x;
      this.y = y;
      startX = x;
      startY = y;
      oldX = x;
      oldY = y;
      oldWidth = 0;
      oldHeight = 0;
      if (bounds != null) {
         bounds.x = x;
         bounds.y = y;
      }
   }

   /**
    * Set the location of the ROI in image coordinates.
    */
   public void setLocation(double x, double y) {
      setLocation((int) x, (int) y);
      if ((int) x == x && (int) y == y) {
         return;
      }
      if (bounds != null) {
         bounds.x = x;
         bounds.y = y;
      } else {
         bounds = new Rectangle2D.Double(x, y, width, height);
      }
      subPixel = true;
   }

   public int getType() {
      return type;
   }

   public int getState() {
      return state;
   }

   public Polygon getConvexHull() {
      return getPolygon();
   }

   double getFeretBreadth(Shape shape, double angle, double x1, double y1, double x2, double y2) {
      double cx = x1 + (x2 - x1) / 2;
      double cy = y1 + (y2 - y1) / 2;
      AffineTransform at = new AffineTransform();
      at.rotate(angle * Math.PI / 180.0, cx, cy);
      Shape s = at.createTransformedShape(shape);
      Rectangle2D r = s.getBounds2D();
      return Math.min(r.getWidth(), r.getHeight());
      /*
		ShapeRoi roi2 = new ShapeRoi(s);
		Roi[] rois = roi2.getRois();
		if (rois!=null && rois.length>0) {
			Polygon p = rois[0].getPolygon();
			ImageProcessor ip = imp.getProcessor();
			for (int i=0; i<p.npoints-1; i++)
				ip.drawLine(p.xpoints[i], p.ypoints[i], p.xpoints[i+1], p.ypoints[i+1]);
			imp.updateAndDraw();
		}
       */
   }

   /**
    * Return this selection's bounding rectangle.
    */
   public Rectangle getBounds() {
      return new Rectangle(x, y, width, height);
   }

   /**
    * Return this selection's bounding rectangle.
    */
   public Rectangle2D.Double getFloatBounds() {
      if (bounds != null) {
         return new Rectangle2D.Double(bounds.x, bounds.y, bounds.width, bounds.height);
      } else {
         return new Rectangle2D.Double(x, y, width, height);
      }
   }

   /**
    * @deprecated replaced by getBounds()
    */
   public Rectangle getBoundingRect() {
      return getBounds();
   }

   /**
    * Returns the outline of this selection as a Polygon, or null if this is a
    * straight line selection.
    *
    * @see ij.process.ImageProcessor#setRoi
    * @see ij.process.ImageProcessor#drawPolygon
    * @see ij.process.ImageProcessor#fillPolygon
    */
   public Polygon getPolygon() {
      int[] xpoints = new int[4];
      int[] ypoints = new int[4];
      xpoints[0] = x;
      ypoints[0] = y;
      xpoints[1] = x + width;
      ypoints[1] = y;
      xpoints[2] = x + width;
      ypoints[2] = y + height;
      xpoints[3] = x;
      ypoints[3] = y + height;
      return new Polygon(xpoints, ypoints, 4);
   }

   protected int clipRectMargin() {
      return 0;
   }

   public void draw(Graphics g) {
      Color color = strokeColor != null ? strokeColor : ROIColor;
      if (fillColor != null) {
         color = fillColor;
      }

      g.setColor(color);
      mag = 1.0;
      int sw = (int) (width * mag);
      int sh = (int) (height * mag);
      int sx1 = (x);
      int sy1 = (y);
      if (subPixelResolution() && bounds != null) {
         sw = (int) (bounds.width * mag);
         sh = (int) (bounds.height * mag);
         sx1 = (int) (bounds.x);
         sy1 = (int) (bounds.y);
      }
      int sx2 = sx1 + sw / 2;
      int sy2 = sy1 + sh / 2;
      int sx3 = sx1 + sw;
      int sy3 = sy1 + sh;
      Graphics2D g2d = (Graphics2D) g;
      if (stroke != null) {
         g2d.setStroke(stroke);
      }
      if (cornerDiameter > 0) {
         int sArcSize = (int) Math.round(cornerDiameter * mag);
         g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
         if (fillColor != null) {
            g.fillRoundRect(sx1, sy1, sw, sh, sArcSize, sArcSize);
         } else {
            g.drawRoundRect(sx1, sy1, sw, sh, sArcSize, sArcSize);
         }
      } else if (fillColor != null) {
         if (!overlay) {
            g.setColor(Color.cyan);
            g.drawRect(sx1, sy1, sw, sh);
         } else if (!(this instanceof TextRoi)) {
            g.fillRect(sx1, sy1, sw, sh);
         } else {
            g.drawRect(sx1, sy1, sw, sh);
         }
      } else {
         g.drawRect(sx1, sy1, sw, sh);
      }

   }

   public void drawOverlay(Graphics g) {
      overlay = true;
      draw(g);
      overlay = false;
   }

   /**
    * Sets the default (global) color used for ROI outlines.
    *
    * @see #getColor()
    * @see #setStrokeColor(Color)
    */
   public static void setColor(Color c) {
      ROIColor = c;
   }

   /**
    * Returns the default (global) color used for drawing ROI outlines.
    *
    * @see #setColor(Color)
    * @see #getStrokeColor()
    */
   public static Color getColor() {
      return ROIColor;
   }

   void drawPreviousRoi(Graphics g) {
      if (previousRoi != null && previousRoi != this && previousRoi.modState != NO_MODS) {
         if (type != POINT && previousRoi.getType() == POINT && previousRoi.modState != SUBTRACT_FROM_ROI) {
            return;
         }
         previousRoi.draw(g);
      }
   }

   void drawHandle(Graphics g, int x, int y) {
      double size = (width * height) * mag * mag;
      if (type == LINE) {
         size = Math.sqrt(width * width + height * height);
         size *= size * mag * mag;
      }
      if (size > 4000.0) {
         g.setColor(Color.black);
         g.fillRect(x, y, 5, 5);
         g.setColor(handleColor);
         g.fillRect(x + 1, y + 1, 3, 3);
      } else if (size > 1000.0) {
         g.setColor(Color.black);
         g.fillRect(x + 1, y + 1, 4, 4);
         g.setColor(handleColor);
         g.fillRect(x + 2, y + 2, 2, 2);
      } else {
         g.setColor(Color.black);
         g.fillRect(x + 1, y + 1, 3, 3);
         g.setColor(handleColor);
         g.fillRect(x + 2, y + 2, 1, 1);
      }
   }

   public boolean contains(int x, int y) {
      Rectangle r = new Rectangle(this.x, this.y, width, height);
      boolean contains = r.contains(x, y);
      if (cornerDiameter == 0 || contains == false) {
         return contains;
      }
      RoundRectangle2D rr = new RoundRectangle2D.Float(this.x, this.y, width, height, cornerDiameter, cornerDiameter);
      return rr.contains(x, y);
   }

   /**
    * Sets the color used by this ROI to draw its outline. This color, if not
    * null, overrides the global color set by the static setColor() method.
    *
    * @see #getStrokeColor
    * @see #setStrokeWidth
    * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
    */
   public void setStrokeColor(Color c) {
      strokeColor = c;
   }

   /**
    * Returns the the color used to draw the ROI outline or null if the default
    * color is being used.
    *
    * @see #setStrokeColor(Color)
    */
   public Color getStrokeColor() {
      return strokeColor;
   }

   /**
    * Sets the fill color used to display this ROI, or set to null to display it
    * transparently.
    *
    * @see #getFillColor
    * @see #setStrokeColor
    */
   public void setFillColor(Color color) {
      fillColor = color;
   }

   /**
    * Returns the fill color used to display this ROI, or null if it is
    * displayed transparently.
    *
    * @see #setFillColor
    * @see #getStrokeColor
    */
   public Color getFillColor() {
      return fillColor;
   }

   public static void setDefaultFillColor(Color color) {
      defaultFillColor = color;
   }

   public static Color getDefaultFillColor() {
      return defaultFillColor;
   }

   /**
    * Copy the attributes (outline color, fill color, outline width) of	'roi2'
    * to the this selection.
    */
   public void copyAttributes(Roi roi2) {
      this.strokeColor = roi2.strokeColor;
      this.fillColor = roi2.fillColor;
      this.stroke = roi2.stroke;
   }

   /**
    * Set 'nonScalable' true to have TextRois in a display list drawn at a fixed
    * location and size.
    */
   public void setNonScalable(boolean nonScalable) {
      this.nonScalable = nonScalable;
   }

   /**
    * Sets the width of the line used to draw this ROI. Set the width to 0.0 and
    * the ROI will be drawn using a a 1 pixel stroke width regardless of the
    * magnification.
    *
    * @see #setStrokeColor(Color)
    * @see ij.ImagePlus#setOverlay(ij.gui.Overlay)
    */
   public void setStrokeWidth(float width) {
      if (width < 0f) {
         width = 0f;
      }
      if (width == 0) {
         stroke = null;
      } else if (wideLine) {
         this.stroke = new BasicStroke(width, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL);
      } else {
         this.stroke = new BasicStroke(width);
      }
      if (width > 1f) {
         fillColor = null;
      }
   }

   /**
    * This is a version of setStrokeWidth() that accepts a double argument.
    */
   public void setStrokeWidth(double width) {
      setStrokeWidth((float) width);
   }

   /**
    * Returns the lineWidth.
    */
   public float getStrokeWidth() {
      return stroke != null ? stroke.getLineWidth() : 0f;
   }

   /**
    * Sets the Stroke used to draw this ROI.
    */
   public void setStroke(BasicStroke stroke) {
      this.stroke = stroke;
   }

   /**
    * Returns the Stroke used to draw this ROI, or null if no Stroke is used.
    */
   public BasicStroke getStroke() {
      return stroke;
   }

   /**
    * Returns the name of this ROI, or null.
    */
   public String getName() {
      return name;
   }

   /**
    * Sets the name of this ROI.
    */
   public void setName(String name) {
      this.name = name;
   }

   /**
    * Sets the rounded rectangle corner diameter (pixels).
    */
   public void setCornerDiameter(int cornerDiameter) {
      if (cornerDiameter < 0) {
         cornerDiameter = 0;
      }
      this.cornerDiameter = cornerDiameter;
   }

   /**
    * Returns the rounded rectangle corner diameter (pixels).
    */
   public int getCornerDiameter() {
      return cornerDiameter;
   }

   /**
    * Obsolete; replaced by setCornerDiameter().
    */
   public void setRoundRectArcSize(int cornerDiameter) {
      setCornerDiameter(cornerDiameter);
   }

   /**
    * Obsolete; replaced by getCornerDiameter().
    */
   public int getRoundRectArcSize() {
      return cornerDiameter;
   }

   /**
    * Sets the stack position (image number) of this ROI. In an overlay, this
    * ROI is only displayed when the stack is at the specified position. Set to
    * zero to have the ROI displayed on all images in the stack.
    *
    * @see ij.gui.Overlay
    */
   public void setPosition(int n) {
      if (n < 0) {
         n = 0;
      }
      position = n;
      channel = slice = frame = 0;
   }

   /**
    * Returns the stack position (image number) of this ROI, or zero if the ROI
    * is not associated with a particular stack image.
    *
    * @see ij.gui.Overlay
    */
   public int getPosition() {
      return position;
   }

   /**
    * Sets the hyperstack position of this ROI. In an overlay, this ROI is only
    * displayed when the hyperstack is at the specified position.
    *
    * @see ij.gui.Overlay
    */
   public void setPosition(int channel, int slice, int frame) {
      if (channel < 0) {
         channel = 0;
      }
      this.channel = channel;
      if (slice < 0) {
         slice = 0;
      }
      this.slice = slice;
      if (frame < 0) {
         frame = 0;
      }
      this.frame = frame;
      position = 0;
   }

   /**
    * Returns the channel position of this ROI, or zero if this ROI is not
    * associated with a particular channel.
    */
   public final int getCPosition() {
      return channel;
   }

   /**
    * Returns the slice position of this ROI, or zero if this ROI is not
    * associated with a particular slice.
    */
   public final int getZPosition() {
      return slice;
   }

   /**
    * Returns the frame position of this ROI, or zero if this ROI is not
    * associated with a particular frame.
    */
   public final int getTPosition() {
      return frame;
   }

   // Used by the FileSaver and RoiEncoder to save overlay settings. */
   public void setPrototypeOverlay(Overlay overlay) {
      prototypeOverlay = new Overlay();
      prototypeOverlay.drawLabels(overlay.getDrawLabels());
      prototypeOverlay.drawNames(overlay.getDrawNames());
      prototypeOverlay.drawBackgrounds(overlay.getDrawBackgrounds());
      prototypeOverlay.setLabelColor(overlay.getLabelColor());
      prototypeOverlay.setLabelFont(overlay.getLabelFont());
   }

   // Used by the FileOpener and RoiDecoder to restore overlay settings. */
   public Overlay getPrototypeOverlay() {
      if (prototypeOverlay != null) {
         return prototypeOverlay;
      } else {
         return new Overlay();
      }
   }

   /**
    * Returns 'true' if this is an area selection.
    */
   public boolean isArea() {
      return (type >= RECTANGLE && type <= TRACED_ROI) || type == COMPOSITE;
   }

   /**
    * Returns 'true' if this is a line selection.
    */
   public boolean isLine() {
      return type >= LINE && type <= FREELINE;
   }

   /**
    * Returns 'true' if this is an ROI primarily used from drawing (e.g.,
    * TextRoi or Arrow).
    */
   public boolean isDrawingTool() {
      //return cornerDiameter>0;
      return false;
   }

   /**
    * Convenience method that converts Roi type to a human-readable form.
    */
   public String getTypeAsString() {
      String s = "";
      switch (type) {
         case POLYGON:
            s = "Polygon";
            break;
         case FREEROI:
            s = "Freehand";
            break;
         case TRACED_ROI:
            s = "Traced";
            break;
         case POLYLINE:
            s = "Polyline";
            break;
         case FREELINE:
            s = "Freeline";
            break;
         case ANGLE:
            s = "Angle";
            break;
         case LINE:
            s = "Straight Line";
            break;
         case OVAL:
            s = "Oval";
            break;
         case COMPOSITE:
            s = "Composite";
            break;
         case POINT:
            s = "Point";
            break;
         default:
            if (this instanceof TextRoi) {
               s = "Text";

            } else {
               s = "Rectangle";
            }
            break;
      }
      return s;
   }

   /**
    * Returns true if this is a slection that supports sub-pixel resolution.
    */
   public boolean subPixelResolution() {
      return subPixel;
   }

   /**
    * Returns true if this is a PolygonRoi that supports sub-pixel resolution
    * and polygons are drawn on zoomed images offset down and to the right by
    * 0.5 pixels..
    */
   public boolean getDrawOffset() {
      return false;
   }

   public void setDrawOffset(boolean drawOffset) {
   }

   public void setIgnoreClipRect(boolean ignoreClipRect) {
      this.ignoreClipRect = ignoreClipRect;
   }

   /**
    * Converts a float array to an int array using truncation.
    */
   public static int[] toInt(float[] arr) {
      return toInt(arr, null, arr.length);
   }

   public static int[] toInt(float[] arr, int[] arr2, int size) {
      int n = arr.length;
      if (size > n) {
         size = n;
      }
      int[] temp = arr2;
      if (temp == null || temp.length < n) {
         temp = new int[n];
      }
      for (int i = 0; i < size; i++) {
         temp[i] = (int) arr[i];
      }
      //temp[i] = (int)Math.floor(arr[i]+0.5);
      return temp;
   }

   /**
    * Converts a float array to an int array using rounding.
    */
   public static int[] toIntR(float[] arr) {
      int n = arr.length;
      int[] temp = new int[n];
      for (int i = 0; i < n; i++) {
         temp[i] = (int) Math.floor(arr[i] + 0.5);
      }
      return temp;
   }

   /**
    * Converts an int array to a float array.
    */
   public static float[] toFloat(int[] arr) {
      int n = arr.length;
      float[] temp = new float[n];
      for (int i = 0; i < n; i++) {
         temp[i] = arr[i];
      }
      return temp;
   }

   public void setProperty(String key, String value) {
      if (key == null) {
         return;
      }
      if (props == null) {
         props = new Properties();
      }
      if (value == null || value.length() == 0) {
         props.remove(key);
      } else {
         props.setProperty(key, value);
      }
   }

   public String getProperty(String property) {
      if (props == null) {
         return null;
      } else {
         return props.getProperty(property);
      }
   }

   public String getProperties() {
      if (props == null) {
         return null;
      }
      Vector v = new Vector();
      for (Enumeration en = props.keys(); en.hasMoreElements();) {
         v.addElement(en.nextElement());
      }
      String[] keys = new String[v.size()];
      for (int i = 0; i < keys.length; i++) {
         keys[i] = (String) v.elementAt(i);
      }
      Arrays.sort(keys);
      StringBuffer sb = new StringBuffer();
      for (int i = 0; i < keys.length; i++) {
         sb.append(keys[i]);
         sb.append(": ");
         sb.append(props.get(keys[i]));
         sb.append("\n");
      }
      return sb.toString();
   }

   public int getPropertyCount() {
      if (props == null) {
         return 0;
      } else {
         return props.size();
      }
   }

   public String toString() {
      return ("Roi[" + getTypeAsString() + ", x=" + x + ", y=" + y + ", width=" + width + ", height=" + height + "]");
   }

   /**
    * Deprecated
    */
   public void temporarilyHide() {
   }

   public double getXBase() {
      if (bounds != null) {
         return bounds.x;
      } else {
         return x;
      }
   }

   public double getYBase() {
      if (bounds != null) {
         return bounds.y;
      } else {
         return y;
      }
   }

   public double getFloatWidth() {
      if (bounds != null) {
         return bounds.width;
      } else {
         return width;
      }
   }

   public double getFloatHeight() {
      if (bounds != null) {
         return bounds.height;
      } else {
         return height;
      }
   }

   /**
    * Overridden by PolygonRoi (angle between first two points), TextRoi (text
    * angle) and Line (line angle).
    */
   public double getAngle() {
      return 0.0;
   }

   public void enableSubPixelResolution() {
      bounds = new Rectangle2D.Double(getXBase(), getYBase(), getFloatWidth(), getFloatHeight());
      subPixel = true;
   }

   public void setIsCursor(boolean isCursor) {
      this.isCursor = isCursor;
   }

   public boolean isCursor() {
      return isCursor;
   }

   public String getDebugInfo() {
      return "";
   }

   public void setRotationCenter(double x, double y) {
      xcenter = x;
      ycenter = y;
   }

   /**
    * Returns a hashcode for this Roi that typically changes if it is moved,
    * even though it is still the same object.
    */
   public int getHashCode() {
      return hashCode() ^ (new Double(getXBase()).hashCode())
              ^ Integer.rotateRight(new Double(getYBase()).hashCode(), 16);
   }

}
