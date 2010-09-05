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
package org.micromanager.graph;


import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.geom.Line2D;
import java.awt.geom.Point2D;
import java.awt.geom.QuadCurve2D;


import org.micromanager.utils.ReportingUtils;

/**
 * Histogram view. 
 */
public class HistogramPanel extends GraphPanel {
   private static final long serialVersionUID = -1789623844214721902L;
   // default histogram bins
   private int xMin_ = 0;
   private int xMax_ = 255;
   
   public HistogramPanel() {
      super();
   }
   
   private void updateBounds(){
//      GraphData.Bounds bounds = getGraphBounds();
//      DecimalFormat fmtDec = new DecimalFormat("#0.00");
//      DecimalFormat fmtInt = new DecimalFormat("#0");
//      fldXMin.setText(fmtInt.format(bounds.xMin));
//      fldXMax.setText(fmtInt.format(bounds.xMax));
//      fldYMin.setText(fmtDec.format(bounds.yMin));
//      fldYMax.setText(fmtDec.format(bounds.yMax));
   }
   
   /**
    * Auto-scales Y axis.
    *
    */
   public void setAutoScale() {
      setAutoBounds();
      updateBounds();
   }
   public void setDataSource(GraphData data){
      setData(data);
      refresh();
   }

   @Override
   /*
    * Draws a dashed vertical line at minimum and maximum pixel value
    * position.  Not sure if this is useful
    */
   public void drawCursor(Graphics2D g, Rectangle box, float xPos) {
      // set scaling
      float xUnit = 1.0f;
      float yUnit = 1.0f;

      // correct if Y range is zero
      if (bounds_.getRangeY() == 0.0) {
         if (bounds_.yMax > 0.0)
            bounds_.yMin = 0.0;
         else if (bounds_.yMax < 0.0) {
            bounds_.yMax = 0.0;
         }
      }

      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 0.0) {
         ReportingUtils.logMessage("Out of range " + bounds_.getRangeX() + ", " + bounds_.getRangeY());
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


   @Override
   /*
    * Draws a line showing the mapping between pixel values and display
    * intensity on the screen.  Gamma (curved appearance) is fudged and not
    * correct. Gamma is expressed as a number between 0 and 100 (slider position)
    */
   public void drawMapping(Graphics2D g, Rectangle box, float xStart, float xEnd, double gamma) {
         // set scaling
      float xUnit = 1.0f;
      float yUnit = 1.0f;

      // correct if Y range is zero
      if (bounds_.getRangeY() == 0.0) {
         if (bounds_.yMax > 0.0)
            bounds_.yMin = 0.0;
         else if (bounds_.yMax < 0.0) {
            bounds_.yMax = 0.0;
         }
      }

      if (bounds_.getRangeX() <= 0.0 || bounds_.getRangeY() <= 0.0) {
         ReportingUtils.logMessage("Out of range " + bounds_.getRangeX() + ", " + bounds_.getRangeY());
         return; // invalid range data
      }

      xUnit = (float) (box.width / bounds_.getRangeX());
      yUnit = (float) (box.height / bounds_.getRangeY());

      Point2D.Float ptPosBottom = new Point2D.Float(xStart, (float)bounds_.yMin);
      Point2D.Float ptDevBottom = getDevicePoint(ptPosBottom, box, xUnit, yUnit);
      Point2D.Float ptPosTop = new Point2D.Float(xEnd, (float)bounds_.yMax);
      Point2D.Float ptDevTop = getDevicePoint(ptPosTop, box, xUnit, yUnit);
      Point2D.Float ptPosGamma = new Point2D.Float(
              (float)(gamma_ / 100.0) * (xEnd - xStart) + xStart,
              (float)( (1 - (gamma_ / 100.0)) * (bounds_.yMax - bounds_.yMin) +
              bounds_.yMin) );
      Point2D.Float ptDevGamma = getDevicePoint(ptPosGamma, box, xUnit, yUnit);

      Color oldColor = g.getColor();
      Stroke oldStroke = g.getStroke();
      g.setColor(Color.black);

      float dash1[] = {3.0f};
      BasicStroke solid = new BasicStroke(1.0f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER);
      g.setStroke(solid);
      g.draw(new QuadCurve2D.Float(ptDevBottom.x, ptDevBottom.y, ptDevGamma.x,
              ptDevGamma.y, ptDevTop.x, ptDevTop.y));
      g.setColor(oldColor);
      g.setStroke(oldStroke);
   }

   public void refresh() {
      GraphData.Bounds bounds = getGraphBounds();
//      if (fldXMin.getText().length() > 0 && fldYMin.getText().length() > 0 && 
//          fldXMax.getText().length() > 0 && fldYMax.getText().length() > 0 )
//      {      
//         bounds.xMin = Double.parseDouble(fldXMin.getText());
//         bounds.xMax = Double.parseDouble(fldXMax.getText());
//         bounds.yMin = Double.parseDouble(fldYMin.getText());
//         bounds.yMax = Double.parseDouble(fldYMax.getText());
//      }
      bounds.xMin = xMin_;
      bounds.xMax = xMax_;
      setBounds(bounds);
      repaint();
   }
   
}
