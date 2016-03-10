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

package org.micromanager.asidispim.Utils;

import org.apache.commons.math3.util.Precision;


/**
 * @author Jon
 */
public class MyNumberUtils {
   
   public MyNumberUtils() {
   }
   
   
   /**
    * Does "equality" test on floats using commons-math3 library
    * and epsilon of 100*maxUlps
    * (before r14315 used locally-defined epsilon of 1e-12, 
    *   then in changed to 10*maxUlps, then to 100*maxUlps in r15867
    * @param f1
    * @param f2
    * @return
    */
   public static boolean floatsEqual(float f1, float f2) {
      return Precision.equals(f1, f2, 100);
   }
   
   public static float roundFloatToPlace(float f, int place) {
      if (place < 0) throw new IllegalArgumentException();
      if (place > 9) throw new IllegalArgumentException();
      long factor = (long) Math.pow(10, place);
      return ((float) Math.round(f * factor)) / factor;
   }
   
   /**
    * "rounds up" to nearest increment of 0.25, e.g. 0.0 goes to 0.0 but 0.01 goes to 0.25
    * @param f
    * @return
    */
   public static float ceilToQuarterMs(float f) {
      return (float) (Math.ceil(f*4)/4);
   }
   
   /**
    * rounds to nearest increment of 0.25
    * @param f
    * @return
    */
   public static float roundToQuarterMs(float f) {
      return ((float) Math.round(f*4))/4;
   }
   
   /**
    * Tests whether a float is outside the range set by two others.  Don't need to know
    *   which of the two range-specifying numbers is minimum and which is maximum.
    * @param num
    * @param end1
    * @param end2
    * @return
    */
   public static boolean outsideRange(float num, float end1, float end2) {
      return (num > Math.max(end1, end2) || num < Math.min(end1, end2));
   }
   
   /**
    * Tests whether a float is outside the range set by two others.  Don't need to know
    *   which of the two range-specifying numbers is minimum and which is maximum.
    * @param num
    * @param end1
    * @param end2
    * @return
    */
   public static boolean outsideRange(double num, double end1, double end2) {
      return (num > Math.max(end1, end2) || num < Math.min(end1, end2));
   }
   
   /**
    * Tests whether a float is outside the range set by two others.  Don't need to know
    *   which of the two range-specifying numbers is minimum and which is maximum.
    * @param num
    * @param end1
    * @param end2
    * @return
    */
   public static boolean outsideRange(int num, int end1, int end2) {
      return (num > Math.max(end1, end2) || num < Math.min(end1, end2));
   }
   
   
}
