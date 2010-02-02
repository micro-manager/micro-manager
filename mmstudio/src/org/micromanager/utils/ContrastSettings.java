///////////////////////////////////////////////////////////////////////////////
//FILE:          ContrastSettings.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, January 20, 2006
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
package org.micromanager.utils;

/**
 * Contrast & brightness settings for the main panel
 * TODO: this data structure is almost the same as metadata.DisplaySettings and they
 *       should be merged.
 */
public class ContrastSettings {
   public double min;
   public double max;
   
   public ContrastSettings() {
      min = 0.0;
      max = 0.0;
   }
   public ContrastSettings(double min, double max) {
      this.min = min;
      this.max = max;
   }
   
   public double getRange() {
      return max - min;
   }
   public void Set(double min, double max) {
	      this.min = min;
	      this.max = max;
   }
}
