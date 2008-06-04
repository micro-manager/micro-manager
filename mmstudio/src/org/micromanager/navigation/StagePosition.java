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
package org.micromanager.navigation;

import java.text.DecimalFormat;

public class StagePosition {
   public double x;
   public double y;
   public double z;
   public String stageName;
   public int numAxes;
   private static DecimalFormat fmt = new DecimalFormat("#0.00");
   
   public StagePosition() {
      stageName = new String("Undefined");
      x = 0.0;
      y = 0.0;
      z = 0.0;
      numAxes=1;
   }
   
   public String getVerbose() {
      if (numAxes == 1)
         return new String(stageName + "(" + fmt.format(x) + ")");
      else if (numAxes == 2)
         return new String(stageName + "(" + fmt.format(x) + "," + fmt.format(y) + ")");
      else
         return new String(stageName + "(" + fmt.format(x) + "," + fmt.format(y) + "," + fmt.format(z) + ")");

   }
}
