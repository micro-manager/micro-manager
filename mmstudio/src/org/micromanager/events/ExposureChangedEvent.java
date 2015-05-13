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
public class ExposureChangedEvent {
   private final String cameraName_;
   private final double newExposureTime_;

   public ExposureChangedEvent(String cameraName, double newExposureTime) {
      cameraName_ = cameraName;
      newExposureTime_ = newExposureTime;
   }
   public String getCameraName() {
      return cameraName_;
   }
   public double getNewExposureTime() {
      return newExposureTime_;
   }
}
