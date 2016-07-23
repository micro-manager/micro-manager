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
 * This class signals when the exposure time for a given camera has changed.
 */
public class SLMExposureChangedEvent {
   private final String deviceName_;
   private final double newExposureTime_;

   public SLMExposureChangedEvent(String deviceName, double newExposureTime) {
      deviceName_ = deviceName;
      newExposureTime_ = newExposureTime;
   }
   public String getDeviceName() {
      return deviceName_;
   }
   public double getNewExposureTime() {
      return newExposureTime_;
   }
}
