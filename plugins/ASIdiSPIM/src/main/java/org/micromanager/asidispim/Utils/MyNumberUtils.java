///////////////////////////////////////////////////////////////////////////////
//FILE:          MyNumberUtils.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim.utils;

import org.apache.commons.math3.util.Precision;


/**
 * @author Jon
 */
public class MyNumberUtils {
   
   public MyNumberUtils() {
   }
   
   
   /**
    * Does "equality" test on floats using commons-math3 library
    * and epsilon of 10*maxUlps
    * (before r14313 used locally-defined epsilon of 1e-12)
    * @param f1
    * @param f2
    * @return
    */
   public static boolean floatsEqual(float f1, float f2) {
      return Precision.equals(f1, f2, 10);
   }
   
   /**
    * "rounds up" to nearest increment of 0.25, e.g. 0 goes to 0 but 0.01 goes to 0.25
    * @param f
    * @return
    */
   public static float ceilToQuarterMs(float f) {
      return (float) (Math.ceil(f*4)/4);
   }
   
   /**
    * "rounds up" to nearest increment of 0.25
    * @param f
    * @return
    */
   public static float roundToQuarterMs(float f) {
      return ((float) Math.round(f*4))/4;
   }
   
   
}
