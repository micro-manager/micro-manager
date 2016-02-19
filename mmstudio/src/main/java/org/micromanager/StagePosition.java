///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// DESCRIPTION:  Describes a single stage position. The stage can have up to three
//               axes. 
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, March 8, 2007
//
// COPYRIGHT:    University of California, San Francisco, 2007
//               100X Imaging Inc, 2008
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
// CVS:          $Id: StagePosition.java 3828 2010-01-22 21:06:21Z arthur $
//
package org.micromanager;

import org.micromanager.internal.utils.NumberUtils;


public class StagePosition {
   /**
    * For two-axis stages, the X position; for one-axis stages, the only stage
    * position. For example, if using a Z focus drive, its position would be
    * given by the "x" parameter.
    */
   public double x;
   /**
    * The Y position for two-axis stages.
    */
   public double y;
   /**
    * RESERVED: do not use.
    */
   public double z;
   public String stageName;
   public int numAxes;
   
   public StagePosition() {
      stageName = "Undefined";
      x = 0.0;
      y = 0.0;
      z = 0.0;
      numAxes=1;
   }
   
   public static StagePosition newInstance(StagePosition aPos) {
      StagePosition sp = new StagePosition();
      sp.x = aPos.x;
      sp.y = aPos.y;
      sp.z = aPos.z;
      sp.numAxes = aPos.numAxes;
      sp.stageName = aPos.stageName;
      return sp;
   }
   
   public String getVerbose() {
      if (numAxes == 1)
         return stageName + "(" + NumberUtils.doubleToDisplayString(x) + ")";
      else if (numAxes == 2)
         return stageName + "(" + NumberUtils.doubleToDisplayString(x) +
      "," + NumberUtils.doubleToDisplayString(y) + ")";
      else
         return stageName + "(" + NumberUtils.doubleToDisplayString(x) +
      "," + NumberUtils.doubleToDisplayString(y) +
      "," + NumberUtils.doubleToDisplayString(z) + ")";

   }

   /**
    * Compare us against the provided StagePosition and return true only if
    * we are equal in all respects.
    * @param alt Other StagePosition to compare against.
    * @return true if every field in alt equals our corresponding field.
    */
   @Override
   public boolean equals(Object alt) {
      if (!(alt instanceof StagePosition)) {
         return false;
      }
      StagePosition spAlt = (StagePosition) alt;
      return (x == spAlt.x && y == spAlt.y && z == spAlt.z &&
            numAxes == spAlt.numAxes && stageName.equals(spAlt.stageName));
   }
}
