///////////////////////////////////////////////////////////////////////////////
//FILE:          GraphPanel.java
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
package org.micromanager.graph;
import java.awt.*;
import javax.swing.*;
import java.awt.geom.*;
import java.text.*;

/**
 * XY graph view. 
 */
public class GraphPanel extends JPanel {
   private static final long serialVersionUID = -1280955888510181945L;
   private GraphData data_;
   private GraphData.Bounds bounds_;
   
   private float xMargin_   = 50;
   private float yMargin_   = 50;
   boolean textVisible_ = true;
   boolean gridVisible_ = true;
   private int TEXT_SIZE = 10;
   private float TEXT_OFFSET_X = 40;
   private float TEXT_OFFSET_Y = 20;
   
   float cursorLoPos_;
   float cursorHiPos_;
   
   public GraphPanel() {
      data_ = new GraphData();
      setAutoBounds();
      cursorLoPos_ = (float)bounds_.xMin;
      cursorHiPos_ = (float)bounds_.xMax;
   }
   
   public void setData(GraphData d) {
      data_ = d;
   }
   
   public void setAutoBounds(){
      bounds_ = data_.getBounds();
      AdjustCursors();
   }
   
   public void setMargins(float x, float y) {
      xMargin_ = x;
      yMargin_ = y;
   }
   
   public void setCursors(double low, double high) {
      cursorLoPos_ = (float)low;
      cursorHiPos_ = (float)high;     
   }
   
   public void setTextVisible(boolean state) {
      textVisible_ = state;
   }
   public void setGridVisible(boolean state) {
      gridVisible_ = state;
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
   
   private void AdjustCursors() {
      cursorLoPos_ = Math.max(cursorLoPos_, (float) bounds_.xMin);
      cursorHiPos_ = Math.min(cursorHiPos_, (float) bounds_.xMax);      
   }
   
   /**
    * Draw graph traces.
    * @param g
    * @param box
    */
   private void drawGraph(Graphics2D g, Rectangle box) {
       if (data_.getSize() < 2)
         return;
      
      // set scaling
      float xUnit = 1.0f;
      float yUnit = 1.0f;
      
      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 0.0) {
         return; // invalid range data
      }
      
      xUnit = (float) (box.width / bounds_.getRangeX());
      yUnit = (float) (box.height / bounds_.getRangeY());
      
      GeneralPath trace = new GeneralPath(GeneralPath.WIND_EVEN_ODD, data_.getSize());
      Point2D.Float pt = getDevicePoint(data_.getPoint(0), box, xUnit, yUnit);
      trace.moveTo(pt.x, pt.y);
      
      for (int i=1; i<data_.getSize(); i++){
         pt = getDevicePoint(data_.getPoint(i), box, xUnit, yUnit);
         trace.lineTo(pt.x, pt.y);
      }
      
      g.draw(trace);
   }
   
   public void drawCursor(Graphics2D g, Rectangle box, float xPos) {
      // set scaling
      float xUnit = 1.0f;
      float yUnit = 1.0f;
      
      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 0.0) {
         System.out.println("Out of range " + bounds_.getRangeX() + ", " + bounds_.getRangeY());
         return; // invalid range data
      }
      
      xUnit = (float) (box.width / bounds_.getRangeX());
      yUnit = (float) (box.height / bounds_.getRangeY());

      Point2D.Float ptPosBottom = new Point2D.Float(xPos, (float)bounds_.yMax);
      Point2D.Float ptDevBottom = getDevicePoint(ptPosBottom, box, xUnit, yUnit);
      Point2D.Float ptPosTop = new Point2D.Float(xPos, (float)bounds_.yMin);
      Point2D.Float ptDevTop = getDevicePoint(ptPosTop, box, xUnit, yUnit);
      
      Color oldColor = g.getColor();
      Stroke oldStroke = g.getStroke();
      g.setColor(Color.black);

      float dash1[] = {3.0f};
      BasicStroke dashed = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 3.0f, dash1, 0.0f);
      g.setStroke(dashed);
      g.draw(new Line2D.Float(ptDevBottom, ptDevTop));      
      g.setColor(oldColor);
      g.setStroke(oldStroke);
   }
   
   public Point2D.Float getDevicePoint(Point2D.Float pt, Rectangle box, float xUnit, float yUnit){
      Point2D.Float ptDev = new Point2D.Float((float)(pt.x - bounds_.xMin)*xUnit + box.x, box.height - (float)(pt.y - bounds_.yMin)*yUnit + box.y);
      // clip the drawing region
      ptDev.x = Math.max(Math.min(ptDev.x, (float)box.x + box.width), (float)box.x);
      ptDev.y = Math.max(Math.min(ptDev.y, (float)box.y + box.height), (float)box.y);
      return ptDev;
   }
   
   /**
    * Draw grid on the graph box with tick lines and numbers.
    * @param g
    * @param box
    */
   private void drawGrid(Graphics2D g, Rectangle box) {
      if (data_.getSize() < 2) {
         System.out.println("Invalid size " + data_.getSize());
         return;
      }
      
      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 0.0)
      {
         System.out.println("Out of range " + bounds_.getRangeX() + ", " + bounds_.getRangeY());
         return; // invalid range data
      }
      
      int tickCountX = 5;
      int tickCountY = 5;
      
      int tickSizeX = box.width / tickCountX;
      int tickSizeY = box.height / tickCountY;
      
      Color oldColor = g.getColor();
      Stroke oldStroke = g.getStroke();
      g.setColor(Color.black);
      g.setStroke(new BasicStroke(1));
      g.draw(box);
      
      if (textVisible_) {
         // create font
         Font fnt = new Font("Arial", Font.PLAIN, TEXT_SIZE);
         Font oldFont = g.getFont();
         g.setFont(fnt);
         DecimalFormat fmt = new DecimalFormat("#0.00");
         g.drawString(fmt.format(bounds_.xMin), box.x, box.y + box.height + TEXT_OFFSET_Y);
         g.drawString(fmt.format(bounds_.xMax).toString(), box.x + box.width, box.y + box.height + TEXT_OFFSET_Y);
         g.drawString(fmt.format(bounds_.yMin).toString(), box.x - TEXT_OFFSET_X, box.y+box.height);
         g.drawString(fmt.format(bounds_.yMax).toString(), box.x - TEXT_OFFSET_X, box.y);
         g.setFont(oldFont);
      }
      
      if (gridVisible_) {
         for (int i=1; i<tickCountX; i++){
            int x = box.x + tickSizeX * i;
            g.draw(new Line2D.Float(x, box.y + box.height, x, box.y));
         }
         for (int i=1; i<tickCountX; i++){
            int y = box.y + tickSizeY * i;
            g.draw(new Line2D.Float(box.x, y, box.x + box.width, y));
         }
      }
      g.setColor(oldColor);
      g.setStroke(oldStroke);
   }
   
   public void paintComponent(Graphics g) {
      
      super.paintComponent(g); // JPanel draws background
      Graphics2D  g2d = (Graphics2D) g;
      
      // get drawing rectangle
      Rectangle box = getBounds();
 
      box.x = (int)xMargin_;
      box.y = (int)yMargin_;
      box.height -= 2*yMargin_;
      box.width -= 2*xMargin_;
      
      // save current settings
      Color oldColor = g2d.getColor();      
      Paint oldPaint = g2d.getPaint();
      Stroke oldStroke = g2d.getStroke();

      g2d.setPaint(Color.black);
      g2d.setStroke(new BasicStroke((float)2));
      
      drawGraph(g2d, box);
      drawGrid(g2d, box);
      drawCursor(g2d, box, cursorLoPos_);
      drawCursor(g2d, box, cursorHiPos_);
           
      // restore settings
      g2d.setPaint(oldPaint);
      g2d.setStroke(oldStroke);
      g2d.setColor(oldColor);
   }
   
}
