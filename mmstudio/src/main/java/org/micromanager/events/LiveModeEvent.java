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
 * This class signals that live mode has been turned on or off.
 *
 * The default implementation of this event is posted on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public interface LiveModeEvent {
   /**
    * Informs the caller if live mode is on or off.
    * @return True if live mode has been turned on, false if it has been turned
    *         off.
    */
   boolean isOn();

   /**
    * @return true if live mode has been turned on, false if it has been turned
    *            off.
    * @deprecated use {@link #isOn()} instead.
    */
   @Deprecated
   default boolean getIsOn() {
      return isOn();
   }
}
