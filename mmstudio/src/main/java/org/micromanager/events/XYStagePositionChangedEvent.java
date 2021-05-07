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

import org.micromanager.MMEvent;

/**
 * This class signals when any XY stage changes position.
 *
 * The default implementation of this event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public interface XYStagePositionChangedEvent extends MMEvent {


   /**
    * Name of the (XYStage) device that change position
    * @return Name of the (XYStage) device that changed position
    */
   String getDeviceName();

   /**
    * @return New X position of the stage in microns
    */
   double getXPos();

   /**
    * @return New Y position of the stage in microns
    */
   double getYPos();

}
