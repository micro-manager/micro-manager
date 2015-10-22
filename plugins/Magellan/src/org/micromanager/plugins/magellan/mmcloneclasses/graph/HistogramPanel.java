///////////////////////////////////////////////////////////////////////////////
//FILE:          HistogramPanel.java
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
package org.micromanager.plugins.magellan.mmcloneclasses.graph;


import java.awt.*;
import java.awt.event.*;
import java.awt.geom.GeneralPath;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.Point2D.Float;
import java.util.ArrayList;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.micromanager.plugins.magellan.misc.JavaUtils;

/**
 * Histogram view. 
 */
public class HistogramPanel extends JPanel implements FocusListener, KeyListener {
   
   private static final int LUT_HANDLE_SIZE = 10;
   private static final Color HIGHLIGHT_COLOR = Color.blue;
   private static final int TOP_HANDLE_OFFSET = JavaUtils.isMac() ? 7 : 3;
   private static final int BOTTOM_HANDLE_OFFSET = JavaUtils.isMac() ? 3 : 3;
   private static final int PIXELS_PER_HANDLE_DIGIT = 6;
   
   private static final long serialVersionUID = -1789623844214721902L;
   // default histogram bins
   private int xMin_ = 0;
   private int xMax_ = 255;
   private int currentHandle_;
   //click location: 1 is low cursor text, 2 is high cursor text
   private int clickLocation_ = 0;
   private ArrayList<CursorListener> cursorListeners_;
   private Float ptDevBottom_;
   private Float ptDevTop_;
   private Float ptDevTopUnclippedX_;
   private boolean contrastMinEditable_ = false, contrastMaxEditable_ = false;
   private String newContrast_ = "";
   private String cursorTextLow_ = "",cursorTextHigh_ = "";   
   
   private GraphData data_;
   protected GraphData.Bounds bounds_;
   
   private float xMargin_   = 50;
   private float yMargin_   = 50;
   
   float cursorLoPos_;
   float cursorHiPos_;
   double gamma_;
   
   private boolean fillTrace_ = false;
   private Color traceColor_ = Color.black;
   

   
   public HistogramPanel() {
      super();
      addFocusListener(this);
      addKeyListener(this);
      data_ = new GraphData();
      setAutoBounds();
      cursorLoPos_ = (float) bounds_.xMin;
      cursorHiPos_ = (float) bounds_.xMax;
      gamma_ = 1.0;
      this.setFocusable(true);
      cursorListeners_ = new ArrayList<CursorListener>();
      setupMouseListeners();
   }

   private GeneralPath generateGammaCurvePath(Float ptDevTop, Float ptDevBottom, double gamma) {
      double xn, yn;
      int X;
      int Y;
      int w = (int) (ptDevTop.x - ptDevBottom.x) + 1;
      int h = (int) (ptDevBottom.y - ptDevTop.y);
      GeneralPath path = new GeneralPath();
      path.moveTo(ptDevBottom.x, ptDevBottom.y);
      for (int x = 0; x < w; x += 3) {
         xn = (double) x / (double) w;
         yn = Math.pow(xn, gamma);
         X = x + (int) ptDevBottom.x;
         Y = (int) ((1 - yn) * h + ptDevTop.y);
         path.lineTo(X, Y);
      }
      path.lineTo(ptDevTop.x, ptDevTop.y);
      return path;
   }
      
   /**
    * Auto-scales Y axis.
    *
    */
   public void setAutoScale() {
      setAutoBounds();
   }
   
   public void setDataSource(GraphData data){
      setData(data);
      refresh();
   }

   /*
    * Draws a dashed vertical line at minimum and maximum pixel value
    * position.
    */
   public void drawCursor(Graphics2D g, Rectangle box, float xPos) {
      // correct if Y range is zero
      if (bounds_.getRangeY() == 0.0) {
         if (bounds_.yMax > 0.0)
            bounds_.yMin = 0.0;
         else if (bounds_.yMax < 0.0) {
            bounds_.yMax = 0.0;
         }
      }

      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 0.0) {
         return; // invalid range data
      }

      // set scaling
      float xUnit = (float) (box.width / bounds_.getRangeX());
      float yUnit = (float) (box.height / bounds_.getRangeY());

      Point2D.Float ptPosBottom = new Point2D.Float(xPos, (float)bounds_.yMax);
      Point2D.Float ptDevBottom = getDevicePoint(ptPosBottom, box, xUnit, yUnit);
      Point2D.Float ptPosTop = new Point2D.Float(xPos, (float)bounds_.yMin);
      Point2D.Float ptDevTop = getDevicePoint(ptPosTop, box, xUnit, yUnit);

      Color oldColor = g.getColor();
      Stroke oldStroke = g.getStroke();
      g.setColor(new Color(120,120,120));

      float dash1[] = {3.0f};
      BasicStroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT,
            BasicStroke.JOIN_MITER, 3.0f, dash1, 0.0f);
      g.setStroke(dashed);
      g.draw(new Line2D.Float(ptDevBottom, ptDevTop));
      g.setColor(oldColor);
      g.setStroke(oldStroke);
   }


   /*
    * Draws a line showing the mapping between pixel values and display
    * intensity on the screen. 
    */
   public void drawMapping(Graphics2D g, Rectangle box, 
         float xStart, float xEnd, double gamma) {
      // correct if Y range is zero
      if (bounds_.getRangeY() == 0.0) {
         if (bounds_.yMax > 0.0)
            bounds_.yMin = 0.0;
         else if (bounds_.yMax < 0.0) {
            bounds_.yMax = 0.0;
         }
      }

      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 1.e-10) {
         return; // invalid range data
      }

      // set scaling
      float xUnit = (float) (box.width / bounds_.getRangeX());
      float yUnit = (float) (box.height / bounds_.getRangeY());

      Point2D.Float ptPosBottom = new Point2D.Float(xStart, 
            (float) bounds_.yMin);
      ptDevBottom_ = getDevicePoint(ptPosBottom, box, xUnit, yUnit);
      Point2D.Float ptPosTop = new Point2D.Float(xEnd, (float) bounds_.yMax);
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
      if (contrastMaxEditable_) {
         g.setColor(HIGHLIGHT_COLOR);
         g.fillRect(x - width, y - LUT_HANDLE_SIZE, width, LUT_HANDLE_SIZE);
         g.setColor(UIManager.getColor("Panel.background"));
         g.drawString(text, x - width, y - 1);
      } else {
         g.setColor(UIManager.getColor("Panel.background"));
         g.fillRect(x - width, y - LUT_HANDLE_SIZE, width, LUT_HANDLE_SIZE);
         g.setColor(Color.black);
         g.drawString(text, x - width, y - 1);
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
      if (contrastMinEditable_) {
         g.setColor(HIGHLIGHT_COLOR);
         g.fillRect(x, y, width, LUT_HANDLE_SIZE + 1);
         g.setColor(UIManager.getColor("Panel.background"));
         g.drawString(newContrast_.length() != 0 ? newContrast_ : text, 
               x + BOTTOM_HANDLE_OFFSET, y + 10);
      } else {
         g.setColor(UIManager.getColor("Panel.background"));
         g.fillRect(x, y, width, LUT_HANDLE_SIZE + 1);
         g.setColor(Color.black);
         g.drawString(text, x + BOTTOM_HANDLE_OFFSET, y + 10);
      }
   }

   static void drawTriangle(Graphics2D g, int x, int y, boolean top) {
      int s = LUT_HANDLE_SIZE;
      if (top) {
         s = -s;
      }
      int[] xs = {x, x - s, x};
      int[] ys = {y, y + s, y + s};
      Stroke oldStroke = g.getStroke();
      //draw outline
      g.setStroke(new BasicStroke(1));
      g.setColor(top ? Color.white : Color.black);
      g.fillPolygon(xs, ys, 3);
      //fill center
      g.setColor(Color.black);
      g.drawPolygon(xs, ys, 3);
      g.setStroke(oldStroke);
   }

   public void refresh() {
      GraphData.Bounds bounds = getGraphBounds();
      bounds.xMin = xMin_;
      bounds.xMax = xMax_;
      setBounds(bounds);
      repaint();
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
            getClickBand(x,y);
            if (currentHandle_ != 0) {
               Point2D.Float pt = getPositionPoint(x,y);
               if (currentHandle_ == 1)
                  notifyCursorLeft(pt.x);
               if (currentHandle_ == 2)
                  notifyCursorRight(pt.x);
               if (currentHandle_ == 3) {
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
            Point2D.Float pt = getPositionPoint(e.getX(),e.getY());
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
            return 0;
         }
         double width = ptDevTopUnclippedX_.x - ptDevBottom_.x;
         double height = ptDevBottom_.y - ptDevTopUnclippedX_.y;
         double xn = (x - ptDevBottom_.x) / width;
         double yn = (ptDevBottom_.y - y) / height;
         double gammaClick;
         if (xn > 0.05 && xn < 0.95 && yn > 0.05 && yn < 0.95) {
            gammaClick = Math.log(yn) / Math.log(xn);
         } else {
            gammaClick = 0;
         }
         return gammaClick;
      }

   static int clipVal(int v, int min, int max) {
      return Math.max(min, Math.min(v, max));
   }

   private float getHistogramTopNumbersWidth() {
      return (float) (PIXELS_PER_HANDLE_DIGIT * cursorTextHigh_.length() + TOP_HANDLE_OFFSET);
   }
   
   private float getHistogramBottomNumbersWidth() {
      return (float) (PIXELS_PER_HANDLE_DIGIT * cursorTextLow_.length() + BOTTOM_HANDLE_OFFSET);
   }

   private void getClickBand(int x, int y) {
      Rectangle box = getBox();
      int ymin = box.y + box.height;
      int ymax = box.y;
      float xUnit = (float) (box.width / bounds_.getRangeX());
      float deviceCursorLoX = getDevicePoint(
            new Point2D.Float(cursorLoPos_, 0), box, xUnit, (float) 1.0).x;
      float deviceCursorHiX = getDevicePoint(
            new Point2D.Float(cursorHiPos_, 0), box, xUnit, (float) 1.0).x;
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

   public void setData(GraphData d) {
      data_ = d;
   }

   public void setGamma(double gamma) {
      gamma_ = gamma;
   }

   public void setTraceStyle(boolean fillTrace, Color color) {
      fillTrace_ = fillTrace;
      traceColor_ = color;
   }
 
   public final void setAutoBounds(){
      bounds_ = data_.getBounds();
      AdjustCursors();
   }
   
   public void setMargins(float x, float y) {
      xMargin_ = x;
      yMargin_ = y;
   }
   
   public void setCursorText(String low, String high) {
      cursorTextLow_ = low;
      cursorTextHigh_ = high;
   }
   
   public void setCursors(double low, double high, double gamma) {
      cursorLoPos_ = (float)low;
      cursorHiPos_ = (float)high;
      gamma_ = gamma;
   }
   
   public GraphData.Bounds getGraphBounds() {
      return bounds_;
   }
   
   public void setBounds(double xMin, double xMax, double yMin, double yMax){
      bounds_.xMin = xMin;
      bounds_.xMax = xMax;
      bounds_.yMin = yMin;
      bounds_.yMax = yMax;
      AdjustCursors();
   }
   
   public void setBounds(GraphData.Bounds b){
      bounds_ = b;
      AdjustCursors();
   }

   // Note: doesn't adjust cursorHiPos_, so that the contrast line can have an
   // endpoint past the end of the histogram.
   private void AdjustCursors() {
      cursorLoPos_ = Math.max(cursorLoPos_, (float) bounds_.xMin);
   }
   
   /**
    * Draw graph traces.
    * @param g
    * @param box
    */
   protected void drawGraph(Graphics2D g, Rectangle box) {
      if (data_.getSize() < 2)
         return;

      //Make black background
      Color oldC = g.getColor();
      g.setColor(Color.black);
      g.fillRect(box.x, box.y, box.width, box.height);
      g.setColor(oldC);

      Color oldColor = g.getColor();
      g.setColor(traceColor_);
            
      // correct if Y range is zero
      if (bounds_.getRangeY() == 0.0) {
         if (bounds_.yMax > 0.0)
            bounds_.yMin = 0.0;
         else if (bounds_.yMax < 0.0) {
            bounds_.yMax = 0.0;
         }
      }

      // bounds can have strange values (i.e. 1e-42).  Avoid artefacts:
      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 1.e-10) {
         return; // invalid range data
      }
          
      // set scaling
      float xUnit = (float) (box.width / bounds_.getRangeX());
      float yUnit = (float) (box.height / bounds_.getRangeY());
      
      GeneralPath trace = new GeneralPath(
            GeneralPath.WIND_EVEN_ODD, data_.getSize() + 1);
      // we need to start and end at y=0 to avoid strange display issues
      Point2D.Float pt0 = getDevicePoint(
            new Point2D.Float(0.0f, 0.0f), box, xUnit, yUnit);
      trace.moveTo(pt0.x, pt0.y);
      Point2D.Float pt1 = getDevicePoint(
            new Point2D.Float(1.0f, 0.0f), box, xUnit, yUnit);
      float halfWidth = (pt1.x - pt0.x) / 2;
      for (int i = 0; i < data_.getSize(); i++) {
         Point2D.Float pt = getDevicePoint(
               data_.getPoint(i), box, xUnit, yUnit);
         trace.lineTo(pt.x - halfWidth, pt.y);
         trace.lineTo(pt.x + halfWidth, pt.y);
      }
      pt0 = getDevicePoint(
            new Point2D.Float((float) data_.getPoint(data_.getSize() - 1).getX(), 0.0f), 
            box, xUnit, yUnit);
      trace.lineTo(pt0.x, pt0.y);

      if (fillTrace_)
         g.fill(trace);
      else
         g.draw(trace);

      g.setColor(oldColor);
   }

   public Point2D.Float getDevicePointUnclippedXMax(
         Point2D.Float pt, Rectangle box, float xUnit, float yUnit) {
      Point2D.Float ptDev = new Point2D.Float(
            (float) (pt.x - bounds_.xMin) * xUnit + box.x, box.height - (float) (pt.y - bounds_.yMin) * yUnit + box.y);
      // clip the drawing region
      ptDev.x = Math.max(ptDev.x, (float) box.x);
      ptDev.y = Math.max(Math.min(ptDev.y, (float) box.y + box.height), 
            (float) box.y);
      return ptDev;
   }
   
   public Point2D.Float getDevicePoint(Point2D.Float pt, Rectangle box, 
         float xUnit, float yUnit){
      Point2D.Float ptDev = new Point2D.Float(
            (float) (pt.x - bounds_.xMin) * xUnit + box.x, 
            box.height - (float) (pt.y - bounds_.yMin) * yUnit + box.y);
      // clip the drawing region
      ptDev.x = Math.max(Math.min(ptDev.x, (float) box.x + box.width), 
            (float) box.x);
      ptDev.y = Math.max(Math.min(ptDev.y, (float) box.y + box.height), 
            (float) box.y);
      return ptDev;
   }

   public Point2D.Float getPositionPoint(int x, int y) {
      Rectangle box = getBox();
      Point2D.Float posPt = new Point2D.Float(
              (float) (((x - box.x) / (float) box.width) * (bounds_.xMax - bounds_.xMin)),
              (float) ((((box.y + box.height) - y) / (float) box.height) * (bounds_.yMax - bounds_.yMin)));
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
      Graphics2D  g2d = (Graphics2D) g;

      g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, 
            RenderingHints.VALUE_ANTIALIAS_ON);

      // get drawing rectangle
      Rectangle box = getBox();
       
      // save current settings
      Color oldColor = g2d.getColor();      
      Paint oldPaint = g2d.getPaint();
      Stroke oldStroke = g2d.getStroke();

      g2d.setPaint(Color.black);
      g2d.setStroke(new BasicStroke(2.0f));

      drawGraph(g2d, box);
      drawCursor(g2d, box, cursorLoPos_);
      drawCursor(g2d, box, cursorHiPos_);
      drawMapping(g2d, box, cursorLoPos_, cursorHiPos_, gamma_);
    
           
      // restore settings
      g2d.setPaint(oldPaint);
      g2d.setStroke(oldStroke);
      g2d.setColor(oldColor);
   }
}

