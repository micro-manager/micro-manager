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


public class ContrastSettings {
   public int min;
   public int max;
   public double gamma;
   
   public ContrastSettings() {
      min = 0;
      max = 0;
      gamma = 1.0;
   }
   public ContrastSettings(int min, int max) {
      this.min = min;
      this.max = max;
      this.gamma = 1.0;
   }
   
   public ContrastSettings(int min, int max, double gamma) {
      this.min = min;
      this.max = max;
      this.gamma = gamma;      
   }
   
   public double getRange() {
      return max - min;
   }
}
