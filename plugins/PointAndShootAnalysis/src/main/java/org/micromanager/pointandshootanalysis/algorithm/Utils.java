 ///////////////////////////////////////////////////////////////////////////////
 //FILE:          
 //PROJECT:       
 //-----------------------------------------------------------------------------
 //
 // AUTHOR:       Nico Stuurman
 //
 // COPYRIGHT:    University of California, San Francisco 2015
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

package org.micromanager.pointandshootanalysis.algorithm;


import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.Overlay;
import ij.gui.Roi;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Color;
import java.awt.Polygon;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.List;

/**
 *
 * @author nico
 * 
 * Copied from spotIntensityAnalysis plugin
 * 
 */
public class Utils {
   private static CircleMask circleMask_;
   
   public static ImagePlus Average(ImagePlus inPlus) {
      ImageStack stack = inPlus.getImageStack();
      // TODO: return error when the stack does not contain shortProcessors
      ImageProcessor ip = stack.getProcessor(1);
      final int width = ip.getWidth();
      final int height = ip.getHeight();
      final int dimension = width * height;
      
      long[] summedPixels = new long[dimension];
      
      for (int n = 1; n <= stack.getSize(); n++) {
         ip = stack.getProcessor(n);
         short[] pixels = (short[]) ip.getPixels();
         for (int i = 0; i < dimension; i++) {
            summedPixels[i] += pixels[i] & 0xffff;
         }
      }
      
      short[] averagedPixels = new short[dimension];
      for (int i = 0; i < dimension; i++) {
            averagedPixels[i] = (short) (summedPixels[i] / stack.getSize());
         }

      ImageProcessor outProc = new ShortProcessor(width, height);
      outProc.setPixels(averagedPixels);
      ImagePlus outPlus = new ImagePlus("Average", outProc);  
      outPlus.copyScale(inPlus);
      
      return outPlus;
   }
   
   public static Overlay GetSpotOverlay (Polygon spots, int radius, 
           Color symbolColor) {
      Overlay ov = new Overlay();
      int diameter = 2 * radius;
      for (int i = 0; i < spots.npoints; i++) {
         int x = spots.xpoints[i];
         int y = spots.ypoints[i];
         Roi roi = new Roi(x - radius, y - radius, 
                 diameter, diameter, diameter);
         roi.setStrokeColor(symbolColor);
         ov.add(roi);
      }
      return ov;
   }
   
   /**
    * Sum the intensities around the given pixel in a circle with given radius
    * @param ip
    * @param x
    * @param y
    * @param radius
    * @return Summed Intensity
    */
   public static float GetIntensity(FloatProcessor ip, int x, int y, int radius) {
      float results = 0.0f;
      if (circleMask_ == null || circleMask_.getRadius() != radius)
         circleMask_ = new CircleMask(radius);
      //circleMask_.print();
      
      // use symmetry 
      for (int i = 0; i <= radius; i++) {
         for (int j = 0; j <= radius; j++) {
            if (circleMask_.getMask()[i][j]) {
               if (i == 0 && j == 0) {
                  results += ip.getf(x, y);
               } else if (i == 0) {
                  results += ip.getf(x, y + j);
                  results += ip.getf(x, y - j);
               } else if (j == 0) {
                  results += ip.getf(x + i, y);
                  results += ip.getf(x - i, y);
               } else {
                  results += ip.getf(x - i, y - j);
                  results += ip.getf(x - i, y + j);
                  results += ip.getf(x + i, y - j);
                  results += ip.getf(x + i, y + j);
               }
            }
         }
      }
      return results ;
   }
   
   /**
    * Simple Utility to convert an ImageJ Polygon into a list of x/y points
    * @param points ImageJ Polygon
    * @return List of Point2D.Doubles
    */
   public static List<Point2D.Double> PolygonToList(Polygon points) {
      List<Point2D.Double> output = new ArrayList<Point2D.Double>();
      for (int i = 0; i < points.npoints; i++) {
         output.add(new Point2D.Double(points.xpoints[i], points.ypoints[i]));
      }
      return output;
   }
   
}

