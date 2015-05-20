///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal.events;

/**
 * This class handles notifications of the current incoming image rate (data 
 * rate) and displayed image rate.
 */
public class FPSEvent {
   private double dataFPS_;
   private double displayFPS_;

   public FPSEvent(double dataFPS, double displayFPS) {
      dataFPS_ = dataFPS;
      displayFPS_ = displayFPS;
   }

   public double getDataFPS() {
      return dataFPS_;
   }

   public double getDisplayFPS() {
      return displayFPS_;
   }
}
