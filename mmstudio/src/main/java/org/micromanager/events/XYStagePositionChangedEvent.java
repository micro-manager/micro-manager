///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Events API
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

package org.micromanager.events;

/**
 * This class signals when any XY stage is moved.
 */
public class XYStagePositionChangedEvent {
   private final String deviceName_;
   private final double xPos_;
   private final double yPos_;

   public XYStagePositionChangedEvent(String deviceName, 
         double xPos, double yPos) {
      deviceName_ = deviceName;
      xPos_ = xPos;
      yPos_ = yPos;
   }
   public double getXPos() {
      return xPos_;
   }
   public double getYPos() {
      return yPos_;
   }
   public String getDeviceName() {
      return deviceName_;
   }
}
