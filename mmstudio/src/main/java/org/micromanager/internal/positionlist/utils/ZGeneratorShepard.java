///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn
//              Nick Anthony, 2018  Moved from AcquireMultipleRegions plugin.
//
// COPYRIGHT:    University of California, San Francisco, 2014
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

package org.micromanager.internal.positionlist.utils;


import java.util.HashMap;
import java.util.Map;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.StagePosition;

/**
 * Allows construction of Z positions by interpolation using Shepard Interpolation.
 */
class ZGeneratorShepard implements ZGenerator {
   Map<String, ShepardInterpolator> interpolators_;

   /**
    * Constructor that creates a ZGeneratorShepard using the
    * PositionList provided.
    *
    * @param positionList PositionList containing the positions to be used for
    *                     interpolation.
    */
   public ZGeneratorShepard(PositionList positionList) {
      //use default exponent of 2
      createInterpolator(positionList, 2.0);
   }

   public ZGeneratorShepard(PositionList positionList, double exponent) {
      createInterpolator(positionList, exponent);
   }

   private void createInterpolator(PositionList positionList, double exp) {
      int nPositions;
      double[] x;
      double[] y;

      //initialize arrays
      nPositions = positionList.getNumberOfPositions();
      x = new double[nPositions];
      y = new double[nPositions];

      MultiStagePosition msp;
      interpolators_ = new HashMap<String, ShepardInterpolator>(5);
      //Loop over all positions and extract X and Y values
      for (int p = 0; p < nPositions; p++) {
         msp = positionList.getPosition(p);
         x[p] = msp.getX();
         y[p] = msp.getY();
      }

      //now repeat for each single axis stage and create an interpolator for each one
      msp = positionList.getPosition(0);
      double[] z; //positions to be passed to interpolator
      StagePosition sp;
      for (int a = 0; a < msp.size(); a++) {
         z = new double[nPositions];
         sp = msp.get(a); //get an axis
         if (sp.is1DStagePosition()) {
            for (int p = 0; p < nPositions; p++) {
               z[p] = positionList.getPosition(p).get(a).get1DPosition();
            }
            interpolators_.put(sp.getStageDeviceLabel(),
                  new ShepardInterpolator(x, y, z, exp)); //store the interpolator for this axis
         }
      }
   }

   /**
    * Returns the Z position for the given X and Y coordinates
    * using Shepard Interpolation.
    *
    * @param x X coordinate
    * @param y Y coordinate
    * @return Z coordinate
    */
   @Override
   public double getZ(double x, double y, String zDevice) {
      ShepardInterpolator interpolator;
      interpolator = interpolators_.get(zDevice);
      return interpolator.interpolate(x, y);
   }

   @Override
   public String getDescription() {
      return ZGenerator.Type.SHEPINTERPOLATE.toString();
   }

   private static class ShepardInterpolator {
      private double[] x_;
      private double[] y_;
      private double[] z_;
      public double exponent_;

      /*
       * @param xin x position list
       * @param yin y position list
       * @param zin z position list
       * @param exp radial weighting exponent
       */
      public ShepardInterpolator(double[] xin, double[] yin, double[] zin, double exp) {
         this.exponent_ = exp;
         if (xin.length != yin.length || xin.length != zin.length) {
            throw new IllegalArgumentException();
         }
         this.x_ = xin;
         this.y_ = yin;
         this.z_ = zin;
      }

      public double interpolate(double xi, double yi) {
         double weight;
         double numerator;
         double denominator;
         double epsilon = 0.001;
         double d;
         int i;
         numerator = 0;
         denominator = 0;
         for (i = 0; i < x_.length; i++) {
            //calculate weight
            d = distance(x_[i], xi, y_[i], yi);
            if (d < epsilon) {
               //if we're on top of a point, return it's z coordinate,
               //otherwise d = 0, weight = infinity, and we return a NaN
               return z_[i];
            }
            weight = Math.pow(d, -exponent_);
            numerator += z_[i] * weight;
            denominator += weight;
         }
         return numerator / denominator;
      }

      private double distance(double x1, double x2, double y1, double y2) {
         return Math.pow((Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2)), 0.5);
      }
   }
}
