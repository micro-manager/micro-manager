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

package org.micromanager.events.internal;

import org.micromanager.events.ShutterEvent;

/**
 * This event posts when the shutter opens or closes.
 *
 * <p>This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.</p>
 */
public final class DefaultShutterEvent implements ShutterEvent {
   private final boolean isOn_;

   /**
    * Event signalling that the shutter changed its state.
    *
    * @param isOn true if the shutter is now open, false otherwise.
    */
   public DefaultShutterEvent(boolean isOn) {
      isOn_ = isOn;
   }

   /**
    * Returns the new state of the shutter.
    *
    * @return true if the shutter is open, false if it is closed.
    */
   @Override
   public boolean getShutter() {
      return isOn_;
   }
}
