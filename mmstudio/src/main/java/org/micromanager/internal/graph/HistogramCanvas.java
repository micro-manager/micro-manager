///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// CVS:          $Id$
//
package org.micromanager.internal.graph;


import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Double;
import java.util.ArrayList;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.micromanager.internal.utils.JavaUtils;

/**
 * Histogram view.
 */
public class HistogramCanvas extends JPanel implements FocusListener, KeyListener {

   private static final int LUT_HANDLE_SIZE = 10;
   private static final Color HIGHLIGHT_COLOR = Color.blue;
   private static final int TOP_HANDLE_OFFSET = JavaUtils.isMac() ? 7 : 3;
   private static final int BOTTOM_HANDLE_OFFSET = JavaUtils.isMac() ? 3 : 3;
   private static final int PIXELS_PER_HANDLE_DIGIT = 6;

   private static final long serialVersionUID = -1789623844214721902L;
   private int currentHandle_;
   //click location: 1 is low cursor text, 2 is high cursor text
   private int clickLocation_ = 0;
   private int curComponent_ = 0;
   private ArrayList<CursorListener> cursorListeners_;
   private Double ptDevBottom_;
   private Double ptDevTop_;
   private Double ptDevTopUnclippedX_;
   private boolean contrastMinEditable_ = false, contrastMaxEditable_ = false;
   private String newContrast_ = "";
   private String cursorTextLow_ = "",cursorTextHigh_ = "";
   private String overlayText_ = null;

   private GraphData[] graphs_ = new GraphData[0];

   private double xMargin_   = 50;
   private double yMargin_   = 50;

   private double xDataMax_ = 1.0;

   double[] cursorLowPositions_;
   double[] cursorHighPositions_;
   double highlightPosition_ = -1;
   double gamma_;

   private boolean fillTrace_ = false;
   private Color[] traceColors_ = new Color[] {Color.black};

   private boolean isLogScale_ = false;

   public HistogramCanvas() {
      addFocusListener(this);
      addKeyListener(this);
      graphs_ = new GraphData[] { new GraphData() };
      cursorLowPositions_ = new double[] { 0.0 };
      cursorHighPositions_ = new double[] { 1.0 };
      gamma_ = 1.0;
      this.setFocusable(true);
      cursorListeners_ = new ArrayList<CursorListener>();
      setupMouseListeners();
   }

   private GeneralPath generateGammaCurvePath(Double ptDevTop, Double ptDevBottom, double gamma) {
      double xn, yn;
      int X;
      int Y;
      int w = (int) (ptDevTop.x - ptDevBottom.x) + 1;
      int h = (int) (ptDevBottom.y - ptDevTop.y);
      GeneralPath path = new GeneralPath();
      path.moveTo(ptDevBottom.x, ptDevBottom.y);
      for (int x = 0; x < w; x += 3) {
         xn = x / (double) w;
         yn = Math.pow(xn, gamma);
         X = x + (int) ptDevBottom.x;
         Y = (int) ((1 - yn) * h + ptDevTop.y);
         path.lineTo(X, Y);
      }
      path.lineTo(ptDevTop.x, ptDevTop.y);
      return path;
   }

   /*
    * Draws a dashed vertical line at minimum and maximum pixel value
    * position.
    */
   public void drawCursor(Graphics2D g, Rectangle box, double xPos,
         Color color, int offset) {
      double xUnit = box.width / xDataMax_;
      double yUnit = box.height;

      Point2D.Double ptPosBottom = new Point2D.Double(xPos, 1.0);
      Point2D.Double ptDevBottom = getDevicePoint(ptPosBottom, box, xUnit, yUnit);
      Point2D.Double ptPosTop = new Point2D.Double(xPos, 0.0);
      Point2D.Double ptDevTop = getDevicePoint(ptPosTop, box, xUnit, yUnit);

      Color oldColor = g.getColor();
      Stroke oldStroke = g.getStroke();
      g.setColor(color);

      BasicStroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 3.0f, new float[] {3.0f}, offset);
      g.setStroke(dashed);
      g.draw(new Line2D.Double(ptDevBottom, ptDevTop));
      g.setColor(oldColor);
      g.setStroke(oldStroke);
   }


   /*
    * Draws a line showing the mapping between pixel values and display
    * intensity on the screen.
    */
   public void drawMapping(Graphics2D g, Rectangle box,
         double xStart, double xEnd, double gamma) {
      double xUnit = box.width / xDataMax_;
      double yUnit = box.height;

      Point2D.Double ptPosBottom = new Point2D.Double(xStart, 0.0);
      ptDevBottom_ = getDevicePoint(ptPosBottom, box, xUnit, yUnit);
      Point2D.Double ptPosTop = new Point2D.Double(xEnd, 1.0);
      ptDevTop_ = getDevicePoint(ptPosTop, box, xUnit, yUnit);
      ptDevTopUnclippedX_ = getDevicePointUnclippedXMax(
            ptPosTop, box, xUnit, yUnit);

      GeneralPath path = generateGammaCurvePath(
            ptDevTopUnclippedX_, ptDevBottom_, gamma);

      Color oldColor = g.getColor();
      Stroke oldStroke = g.getStroke();
      g.setColor(new Color(120, 120, 120));

      BasicStroke solid = new BasicStroke(2.0f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER);
      g.setStroke(solid);
      g.draw(path);
      g.setColor(oldColor);
      g.setStroke(oldStroke);

      drawLUTHandles(g, (int) ptDevBottom_.x, (int) ptDevBottom_.y,
              (int) ptDevTop_.x, (int) ptDevTop_.y);
   }

   private void drawLUTHandles(Graphics2D g, int xmin, int ymin, 
         int xmax, int ymax) {
      drawTopHandle(g, xmax, ymax);
      drawBottomHandle(g, xmin, ymin);
   }

   private void drawTopHandle(Graphics2D g, int x, int y) {
      drawTriangle(g, x, y, true);

      g.setFont(new java.awt.Font("Lucida Grande", 0, 11));
      String text = cursorTextHigh_;
      if (contrastMaxEditable_ && newContrast_.length() != 0) {
          text = newContrast_;
      }

      int width = PIXELS_PER_HANDLE_DIGIT * text.length() + TOP_HANDLE_OFFSET;
      int textOffset = x - width;
      if (textOffset < 0) {
         // Put the text on the other side of the handle.
         textOffset = x + LUT_HANDLE_SIZE;
      }
      if (contrastMaxEditable_) {
         g.setColor(HIGHLIGHT_COLOR);
         g.fillRect(x - width, y - LUT_HANDLE_SIZE, width, LUT_HANDLE_SIZE);
         g.setColor(UIManager.getColor("Panel.background"));
         g.drawString(text, textOffset, y - 1);
      } else {
         g.setColor(UIManager.getColor("Panel.background"));
         g.fillRect(x - width, y - LUT_HANDLE_SIZE, width, LUT_HANDLE_SIZE);
         g.setColor(Color.black);
         g.drawString(text, textOffset, y - 1);
      }
   }

   private void drawBottomHandle(Graphics2D g, int x, int y) {
      drawTriangle(g, x, y, false);
      g.setFont(new java.awt.Font("Lucida Grande", 0, 11));
      String text = cursorTextLow_;
      if (contrastMinEditable_ && newContrast_.length() != 0) {
         text = newContrast_;
      }
      int width = PIXELS_PER_HANDLE_DIGIT * text.length() + 7;
      // Intensity string normally goes on the right side of the handle, but
      // should go on the left if that will cause it to overlap with the X
      // scale indicator from the graph.
      int textOffset = x + BOTTOM_HANDLE_OFFSET;
      int textWidth = PIXELS_PER_HANDLE_DIGIT * text.length();
      if (textOffset + textWidth > getBox().width - 50) { // fiddle factor
         // Put the text on the other side of the handle and away from the
         // X bounds.
         textOffset = x - PIXELS_PER_HANDLE_DIGIT *
               (text.length() + Integer.toString((int) xDataMax_).length());
      }
      if (contrastMinEditable_) {
         g.setColor(HIGHLIGHT_COLOR);
         g.fillRect(x, y, width, LUT_HANDLE_SIZE + 1);
         g.setColor(UIManager.getColor("Panel.background"));
         g.drawString(newContrast_.length() != 0 ? newContrast_ : text,
               textOffset, y + 10);
      } else {
         g.setColor(UIManager.getColor("Panel.background"));
         g.fillRect(x, y, width, LUT_HANDLE_SIZE + 1);
         g.setColor(Color.black);
         g.drawString(text, textOffset, y + 10);
      }
   }

   void drawTriangle(Graphics2D g, int x, int y, boolean top) {
      int s = LUT_HANDLE_SIZE;
      if (top) {
         s = -s;
      }
      int[] xs = {x, x - s, x};
      int[] ys = {y, y + s, y + s};
      Stroke oldStroke = g.getStroke();
      //draw outline
      g.setStroke(new BasicStroke(1));
      g.setColor(traceColors_[curComponent_]);
      g.fillPolygon(xs, ys, 3);
      //fill center
      g.setColor(Color.black);
      g.drawPolygon(xs, ys, 3);
      g.setStroke(oldStroke);
   }

   @Override
   public void focusGained(FocusEvent e) {}

   @Override
   public void focusLost(FocusEvent e) {
      applyContrastValues();
   }

   @Override
   public void keyTyped(KeyEvent e) {
      try {
         int i = Integer.parseInt("" + e.getKeyChar());
         if (contrastMaxEditable_ || contrastMinEditable_) {
            newContrast_ += i;
            repaint();
         }
      } catch (Exception ex) {} // not a digit
   }

   @Override
   public void keyPressed(KeyEvent e) {
      if (e.getKeyCode() == KeyEvent.VK_ENTER) {
         applyContrastValues();
      } else if (e.getKeyCode() == KeyEvent.VK_LEFT) {
         if (contrastMaxEditable_ && newContrast_.length() == 0) {
            setContrastMax(Integer.parseInt(cursorTextHigh_) - 1);
         } else if (contrastMinEditable_ && newContrast_.length() == 0) {
             setContrastMin(Integer.parseInt(cursorTextLow_) - 1);
         }
      } else if (e.getKeyCode() == KeyEvent.VK_RIGHT) {
         if (contrastMaxEditable_ && newContrast_.length() == 0) {
            setContrastMax(Integer.parseInt(cursorTextHigh_) + 1);
         } else if (contrastMinEditable_ && newContrast_.length() == 0) {
            setContrastMin(Integer.parseInt(cursorTextLow_) + 1);
         }
      }
      repaint();
   }

   @Override
   public void keyReleased(KeyEvent e) {}

   public interface CursorListener {
      public void contrastMinInput(int min);
      public void contrastMaxInput(int max);
      public void onLeftCursor(double pos);
      public void onRightCursor(double pos);
      public void onGammaCurve(double gamma);
   }

   public void addCursorListener(CursorListener cursorListener) {
      cursorListeners_.add(cursorListener);
   }

   public void removeCursorListeners(CursorListener cursorListener) {
      cursorListeners_.remove(cursorListener);
   }

   public CursorListener[] getCursorListeners() {
      return (CursorListener[]) cursorListeners_.toArray();
   }

   private void notifyCursorLeft(double pos) {
      for (CursorListener cursorListener:cursorListeners_) {
         cursorListener.onLeftCursor(pos);
      }
   }

   private void notifyCursorRight(double pos) {
      for (CursorListener cursorListener:cursorListeners_) {
         cursorListener.onRightCursor(pos);
      }
   }

   private void notifyGammaMouse(double gamma) {
      for (CursorListener cursorListener:cursorListeners_) {
         cursorListener.onGammaCurve(gamma);
      }
   }

   private void setContrastMax(int max) {
      for (CursorListener cursorListener : cursorListeners_) {
         cursorListener.contrastMaxInput(max);
      }
   }

   private void setContrastMin(int min) {
      for (CursorListener cursorListener : cursorListeners_) {
         cursorListener.contrastMinInput(min);
      }
   }

   private void applyContrastValues() {
      //apply the values
      if (!newContrast_.equals("")) {
         if (contrastMaxEditable_) {
            setContrastMax(Integer.parseInt(newContrast_));
         } else if (contrastMinEditable_) {
            setContrastMin(Integer.parseInt(newContrast_));
         }
      }
      contrastMaxEditable_ = false;
      contrastMinEditable_ = false;
      newContrast_ = "";

      repaint();
   }

   private void setupMouseListeners() {
      final JPanel panelRef = this;
      addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            panelRef.requestFocus();
            int x = e.getX();
            int y = e.getY();
            getClickBand(x, y);
            if (currentHandle_ != 0) {
               Point2D.Double pt = getPositionPoint(x,y);
               if (currentHandle_ == 1) {
                  notifyCursorLeft(pt.x);
               }
               else if (currentHandle_ == 2) {
                  notifyCursorRight(pt.x);
               }
               else if (currentHandle_ == 3) {
                  double gamma = getGammaFromMousePosition(x,y);
                  if (Math.abs((gamma_ - gamma)/gamma_) < 0.2) {
                     notifyGammaMouse(getGammaFromMousePosition(x, y));
                     currentHandle_ = 4;
                  }
               }
            }
         }
         @Override
         public void mouseReleased(MouseEvent e) {
            currentHandle_ = 0;
         }

         @Override
         public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() >= 2 && clickLocation_ == 1) {
               applyContrastValues();
               contrastMinEditable_ = true;
               contrastMaxEditable_ = false;
            } else if (e.getClickCount() >= 2 && clickLocation_ == 2) {
               applyContrastValues();
               contrastMaxEditable_ = true;
               contrastMinEditable_ = false;
            } else if (clickLocation_ == 0) {
               applyContrastValues();
            }
            repaint();
         }
      });

      addMouseMotionListener(new MouseMotionAdapter() {
         @Override
         public void mouseDragged(MouseEvent e) {
            if (currentHandle_ == 0)
               return;
            Point2D.Double pt = getPositionPoint(e.getX(),e.getY());
            if (currentHandle_ == 1)
               notifyCursorLeft(pt.x);
            if (currentHandle_ == 2)
               notifyCursorRight(pt.x);
            if (currentHandle_ == 4)
               notifyGammaMouse(getGammaFromMousePosition(e.getX(), e.getY()));
         }
      });
   }

      private double getGammaFromMousePosition(int x, int y) {
         if ((ptDevTopUnclippedX_ == null) || (ptDevBottom_ == null)) {
            return 1.0;
         }
         double width = ptDevTopUnclippedX_.x - ptDevBottom_.x;
         double height = ptDevBottom_.y - ptDevTopUnclippedX_.y;
         double xn = (x - ptDevBottom_.x) / width;
         double yn = (ptDevBottom_.y - y) / height;
         double gammaClick;
         if (xn > 0.01 && xn < 0.99 && yn > 0.01 && yn < 0.99) {
            gammaClick = Math.log(yn) / Math.log(xn);
         }
         else if (xn >= .99 || yn <= .01) {
            // Mouse off the right/bottom end of the histogram.
            gammaClick = 100;
         }
         else {
            // Mouse off the left/top end of the histogram.
            gammaClick = 0.0;
         }
         // Clamp gamma to "sane" values so that the gamma curve doesn't become
         // impossible to click on.
         return Math.max(.05, Math.min(50, gammaClick));
      }

   static int clipVal(int v, int min, int max) {
      return Math.max(min, Math.min(v, max));
   }

   private double getHistogramTopNumbersWidth() {
      return (PIXELS_PER_HANDLE_DIGIT * cursorTextHigh_.length() + TOP_HANDLE_OFFSET);
   }

   private double getHistogramBottomNumbersWidth() {
      return (PIXELS_PER_HANDLE_DIGIT * cursorTextLow_.length() + BOTTOM_HANDLE_OFFSET);
   }

   private void getClickBand(int x, int y) {
      Rectangle box = getBox();
      int ymin = box.y + box.height;
      int ymax = box.y;
      double xUnit = box.width / xDataMax_;
      double deviceCursorLoX = getDevicePoint(
            new Point2D.Double(cursorLowPositions_[curComponent_], 0), box,
            xUnit, 1.0).x;
      double deviceCursorHiX = getDevicePoint(
            new Point2D.Double(cursorHighPositions_[curComponent_], 0), box,
            xUnit, 1.0).x;
      clickLocation_ = 0;
      if (y < ymin + 10 && y >= ymin) {
         if (x > deviceCursorLoX && 
               x < deviceCursorLoX + getHistogramBottomNumbersWidth()) {
            clickLocation_ = 1;
            currentHandle_ = 0; //low cursor text field
            return;
         }
         currentHandle_ = 1; // Low cursor margin
      } else if (y <= ymax && y > ymax - 10) {
         if (x < deviceCursorHiX && 
               x > deviceCursorHiX - getHistogramTopNumbersWidth()) {
            clickLocation_ = 2;
            currentHandle_ = 0; //hi cursor text field
            return;
         }
         currentHandle_ = 2; // High cursor margin
      }  else if ((x > deviceCursorLoX - 5) && (x < deviceCursorLoX + 5)) {
         currentHandle_ = 1; // Low cursor vertical line
      }  else if ((x > deviceCursorHiX - 5) && (x < deviceCursorHiX + 5)) {
         currentHandle_ = 2; // High cursor vertical line
      } else if (y > ymax && y < ymin) {
         currentHandle_ = 3; // Gamma curve margin
      } else {
         currentHandle_ = 0;
      }
   }

   public void setData(int component, GraphData d) {
      extendArraysIfNeeded(component);
      graphs_[component] = d;
   }

   public void setGamma(double gamma) {
      gamma_ = gamma;
   }

   public void setTraceStyle(boolean fillTrace, int component, Color color) {
      fillTrace_ = fillTrace;
      extendArraysIfNeeded(component);
      traceColors_[component] = color;
   }

   public void setCurComponent(int component) {
      curComponent_ = component;
      extendArraysIfNeeded(component);
   }

   /**
    * Ensure that our various per-component arrays are long enough to
    * include the specified component. Make a new array and copy the values
    * over.
    */
   private void extendArraysIfNeeded(int component) {
      if (cursorLowPositions_.length <= component) {
         double[] newLows = new double[component + 1];
         double[] newHighs = new double[component + 1];
         for (int i = 0; i < cursorLowPositions_.length; ++i) {
            newLows[i] = cursorLowPositions_[i];
            newHighs[i] = cursorHighPositions_[i];
         }
         cursorLowPositions_ = newLows;
         cursorHighPositions_ = newHighs;
      }

      if (traceColors_.length <= component) {
         Color[] newColors = new Color[component + 1];
         for (int i = 0; i < traceColors_.length; ++i) {
            newColors[i] = traceColors_[i];
         }
         traceColors_ = newColors;
      }

      if (graphs_.length <= component) {
         GraphData[] newDatas = new GraphData[component + 1];
         for (int i = 0; i < graphs_.length; ++i) {
            newDatas[i] = graphs_[i];
         }
         graphs_ = newDatas;
      }
   }

   public void setMargins(double x, double y) {
      xMargin_ = x;
      yMargin_ = y;
   }

   public void setCursorText(String low, String high) {
      cursorTextLow_ = low;
      cursorTextHigh_ = high;
   }

   /**
    * Set text for drawing on top of everything.
    */
   public void setOverlayText(String newText) {
      overlayText_ = newText;
   }

   public void setCursors(int component, double low, double high, double gamma) {
      extendArraysIfNeeded(component);
      cursorLowPositions_[component] = low;
      cursorHighPositions_[component] = high;
      gamma_ = gamma;
   }

   public void setHighlight(double pos) {
      highlightPosition_ = pos;
      repaint();
   }

   public void setXDataMax(double xMax) {
      xDataMax_ = xMax;
   }

   public void setLogScale(boolean isLogScale) {
      isLogScale_ = isLogScale;
   }

   /**
    * Draw graph traces.
    * @param g
    * @param box
    */
   protected void drawGraph(Graphics2D g, int component, Rectangle box) {
      GraphData data = graphs_[component];
      if (data.getSize() < 2) {
         return;
      }

      // Make black background, only for the first component.
      if (component == 0) {
         Color oldC = g.getColor();
         g.setColor(Color.black);
         g.fillRect(box.x, box.y, box.width, box.height);
         g.setColor(oldC);
      }

      Color oldColor = g.getColor();
      g.setColor(traceColors_[component]);

      // Determine scaling. Multiple bins in our source data may be applied
      // to a single pixel in the output; compress the data as needed by
      // summing bins together.
      data = data.compress(Math.min(data.getSize(), box.width), 0,
            (int) xDataMax_);
      if (isLogScale_) {
         data = data.logScale();
      }

      // Now the X range of the compressed data corresponds to xDataMax_
      // of the original data
      final GraphData.Bounds dataBounds = data.getBounds();
      if (dataBounds.getRangeY() < 0.5) {
         // We have an array of all zeros, i.e. nothing to draw, so don't try.
         return;
      }
      double xUnit = box.width / dataBounds.getRangeX();
      double yUnit = box.height / dataBounds.getRangeY();

      GeneralPath trace = new GeneralPath(
            GeneralPath.WIND_EVEN_ODD, data.getSize() + 1);
      // we need to start and end at y=0 to avoid strange display issues
      Point2D.Double pt0 = getDevicePoint(
            new Point2D.Double(0.0, 0.0), box, xUnit, yUnit);
      trace.moveTo(pt0.x, pt0.y);
      Point2D.Double pt1 = getDevicePoint(
            new Point2D.Double(1.0, 0.0), box, xUnit, yUnit);
      double halfWidth = (pt1.x - pt0.x) / 2;

      for (int i = 0; i < data.getSize(); i++) {
         Point2D.Double pt = getDevicePoint(
               data.getPoint(i), box, xUnit, yUnit);
         if (pt.x + halfWidth > box.x + box.width) {
            // Out of bounds.
            break;
         }
         trace.lineTo(pt.x - halfWidth, pt.y);
         trace.lineTo(pt.x + halfWidth, pt.y);
      }

      if (fillTrace_) {
         pt0 = getDevicePoint(
               new Point2D.Double(
                  data.getPoint(data.getSize() - 1).getX(), 0.0),
               box, xUnit, yUnit);
         trace.lineTo(pt0.x, pt0.y);
         g.fill(trace);
      }
      else {
         g.draw(trace);
      }

      g.setColor(oldColor);
   }

   public Point2D.Double getDevicePointUnclippedXMax(
         Point2D.Double pt, Rectangle box, double xUnit, double yUnit) {
      Point2D.Double ptDev = new Point2D.Double(
            pt.x * xUnit + box.x,
            box.height - pt.y * yUnit + box.y);
      // clip the drawing region
      ptDev.x = Math.max(ptDev.x, box.x);
      ptDev.y = Math.max(Math.min(ptDev.y, box.y + box.height),
            box.y);
      return ptDev;
   }

   public Point2D.Double getDevicePoint(Point2D.Double pt, Rectangle box,
         double xUnit, double yUnit){
      Point2D.Double ptDev = new Point2D.Double(
            pt.x * xUnit + box.x,
            box.height - pt.y * yUnit + box.y);
      // clip the drawing region
      ptDev.x = Math.max(Math.min(ptDev.x, box.x + box.width),
            box.x);
      ptDev.y = Math.max(Math.min(ptDev.y, box.y + box.height),
            box.y);
      return ptDev;
   }

   public Point2D.Double getPositionPoint(int x, int y) {
      Rectangle box = getBox();
      double yRange = graphs_[curComponent_].getBounds().getRangeY();
      Point2D.Double posPt = new Point2D.Double(
              ((((double) x - box.x) / box.width) * xDataMax_),
              (((((double) box.y + box.height) - y) / box.height) * yRange));
      return posPt;
   }

   public Rectangle getBox() {
      Rectangle box = getBounds();
      box.x = (int) xMargin_;
      box.y = (int) yMargin_;
      box.height -= 2 * yMargin_;
      box.width -= 2 * xMargin_;
      return box;
   }

   @Override
   public void paintComponent(Graphics g) {
      g.setColor(Color.red);
      super.paintComponent(g); // JPanel draws background
      Graphics2D g2d = (Graphics2D) g;

      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
            RenderingHints.VALUE_ANTIALIAS_ON);

      // get drawing rectangle
      Rectangle box = getBox();

      // save current settings
      Color oldColor = g2d.getColor();
      Paint oldPaint = g2d.getPaint();
      Stroke oldStroke = g2d.getStroke();

      g2d.setPaint(Color.black);
      if (graphs_.length == 1) {
         g2d.setStroke(new BasicStroke(2.0f));
      }
      else {
         g2d.setStroke(new BasicStroke(1.0f));
      }

      for (int i = 0; i < graphs_.length; ++i) {
         drawGraph(g2d, i, box);
         drawCursor(g2d, box, cursorLowPositions_[i],
               traceColors_[i], i);
         drawCursor(g2d, box, cursorHighPositions_[i],
               traceColors_[i], i);
      }
      if (highlightPosition_ > 0) {
         drawCursor(g2d, box, highlightPosition_, Color.YELLOW, 0);
      }
      drawMapping(g2d, box, cursorLowPositions_[curComponent_],
            cursorHighPositions_[curComponent_], gamma_);

      g.setColor(Color.GRAY);
      if (isLogScale_) {
         g.drawString("LOG", box.x + box.width - 30, box.y + 15);
      }

      if (overlayText_ != null) {
         g.drawString(overlayText_, box.x + 25, box.y + box.height / 2);
      }

      // restore settings
      g2d.setPaint(oldPaint);
      g2d.setStroke(oldStroke);
      g2d.setColor(oldColor);
   }
}

