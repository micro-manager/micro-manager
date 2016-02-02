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
import java.awt.geom.Point2D;
/**
 * XY graph data structure.
 *
 */
public class GraphData {
   private double xVals_[];
   private double yVals_[];
   
   public class Bounds {
      public double xMin;
      public double xMax;
      public double yMin;
      public double yMax;
      
      public Bounds(){
         xMin = 0.0;
         xMax = 0.0;
         yMin = 0.0;
         yMax = 0.0;
      }
      
      double getRangeX(){
         return xMax - xMin;
      }
      
      double getRangeY(){
         return yMax - yMin;
      }

      public String toString() {
         return String.format("[X: %.2f to %.2f; Y: %.2f to %.2f]", xMin, xMax, yMin, yMax);
      }
   }
   
   public GraphData(){
      xVals_ = new double[100];
      yVals_ = new double[100];
      
      for (int i=0; i<xVals_.length; i++){
         xVals_[i] = i;
         //yVals_[i] = 100.0 * Math.sin(2.0 *i*Math.PI / 50.0);
         yVals_[i] = 0.0;
      }
   }
   
   public Bounds getBounds() {
      Bounds b = new Bounds();
      b.xMax = Double.MIN_VALUE;
      b.xMin = Double.MAX_VALUE;
      b.yMax = Double.MIN_VALUE;
      b.yMin = Double.MAX_VALUE;
      
      for (int i = 0; i < xVals_.length; i++) {
         if (xVals_[i] > b.xMax)
            b.xMax = xVals_[i];
         if (xVals_[i] < b.xMin)
            b.xMin = xVals_[i];
      }
      for (int i = 0; i < yVals_.length; i++) {
         if (yVals_[i] > b.yMax)
            b.yMax = yVals_[i];
         if (yVals_[i] < b.yMin)
            b.yMin = yVals_[i];
      }
      return b;
   }
   
   public int getSize() {
      return xVals_.length;
   }
   

   public Point2D.Double getPoint(int index){
      double x = xVals_[index];
      double y;
      if (index < yVals_.length)
         y = yVals_[index];
      else
         y = 0.0;
      return new Point2D.Double(x, y);
   }

   public void setData(double xVals[], double yVals[]) {
      xVals_ = xVals;
      yVals_ = yVals;
      
      // TODO: adjust lengths
   }
   
   public void setData(double yVals[]) {
      yVals_ = yVals;
      xVals_ = new double[yVals.length];
      for (int i=0; i<xVals_.length; i++) {
         xVals_[i] = i;
      }
   }
   
   public void setData(int yIntVals[]) {
      yVals_ = new double[yIntVals.length];
      xVals_ = new double[yIntVals.length];
      for (int i=0; i<yIntVals.length; i++){
         yVals_[i] = yIntVals[i];
         xVals_[i] = i;
      }
   }

   /**
    * Generate a new GraphData that sums values together to produce a new graph
    * with the specified number of bins, ranging from the specified min to max
    * intensities. This is mostly intended for use in drawing histograms.
    */
   public GraphData compress(int numBins, int xMin, int xMax) {
      double[] newYs = new double[numBins];
      for (int i = 0; i < newYs.length; ++i) {
         newYs[i] = 0;
      }
      // This is the "compression ratio", or number of bins in the source
      // data that contribute to a bin in the result data.
      double ratio = ((double) xMax - xMin) / numBins;
      double remainder = 0.0;
      for (int i = 0; i < numBins; ++i) {
         int sourceOffset = (int) (xMin + ratio * i + remainder);
         for (int j = 0; j < ratio + remainder; ++j) {
            if (sourceOffset + j >= yVals_.length) {
               break;
            }
            newYs[i] += yVals_[sourceOffset + j];
         }
         remainder = (ratio + remainder) % 1;
      }
      GraphData result = new GraphData();
      result.setData(newYs);
      return result;
   }

   /**
    * Rescale the Y axis to a logarithmic scale.
    */
   public GraphData logScale() {
      double[] newYs = new double[yVals_.length];
      for (int i = 0; i < newYs.length; ++i) {
         newYs[i] = yVals_[i] > 0 ? 1000 * Math.log(yVals_[i]) : 0;
      }
      GraphData result = new GraphData();
      result.setData(newYs);
      return result;
   }

   @Override
   public String toString() {
      String result = "<GraphData:\n";
      for (int i = 0; i < xVals_.length; ++i) {
         result += String.format("%d: %.2f,%.2f;\n", i, xVals_[i], yVals_[i]);
      }
      return result + ">";
   }
}
