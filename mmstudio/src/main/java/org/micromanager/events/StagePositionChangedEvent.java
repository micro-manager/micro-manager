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
 * This class signals when a single-axis drive has moved.
 *
 * The default implementation of this event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public interface StagePositionChangedEvent extends MMEvent {


   /**
    * @return The new (current) position of the stage
    */
   double getPos();

   /**
    * @return Name of the stage that moved
    */
   String getDeviceName();

}
