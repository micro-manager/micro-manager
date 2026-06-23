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

import org.micromanager.PositionList;


/**
 * Generates a Z position from XY coordinates.
 */
public interface ZGenerator {
   enum Type {
      SHEPINTERPOLATE("Weighted Interpolation"),
      AVERAGE("Average");
      public final String description_;

      Type(String description) {
         description_ = description;
      }

      @Override
      public String toString() {
         return description_;
      }
   }

   /**
    * Creates a ZGenerator of the requested type from a list of points.
    *
    * @param type interpolation method to use
    * @param points positions (with XY and one or more 1D Z stages) to
    *               interpolate between
    * @return a ZGenerator instance
    */
   static ZGenerator create(ZGenerator.Type type, PositionList points) {
      switch (type) {
         case SHEPINTERPOLATE:
            return new ZGeneratorShepard(points);
         case AVERAGE:
         default:
            return new ZGeneratorAverage(points);
      }
   }

   public abstract double getZ(double x, double y, String zDevice);

   public abstract String getDescription();
}