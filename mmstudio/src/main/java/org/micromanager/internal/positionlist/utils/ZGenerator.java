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

   public abstract double getZ(double x, double y, String zDevice);

   public abstract String getDescription();
}