///////////////////////////////////////////////////////////////////////////////
//FILE:          StagePosition.java
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
package org.micromanager.api;

import org.micromanager.utils.NumberUtils;


public class StagePosition {
   /**
    * NOTE: despite what the name fields in this class may suggest, every 
    * StagePosition in practice has only 1 or 2 axes, and thus only uses either
    * the x, or the x and y fields. That is, the "x" field means "z" when
    * using a 1-axis positioner! 
    */
   public double x;
   public double y;
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
}
