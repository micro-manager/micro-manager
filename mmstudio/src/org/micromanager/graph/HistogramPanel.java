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

/**
 * Histogram view. 
 */
public class HistogramPanel extends GraphPanel {
  
   // default histogram bins
   private int xMin_ = 0;
   private int xMax_ = 255;
   
   public HistogramPanel() {
      super();
   }
   
   private void updateBounds(){
      GraphData.Bounds bounds = getGraphBounds();
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
