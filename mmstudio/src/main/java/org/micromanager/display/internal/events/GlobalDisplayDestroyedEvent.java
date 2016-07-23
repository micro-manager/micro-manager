///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Internal display events
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

import org.micromanager.display.DisplayWindow;

/**
 * This event signifies that a display has been closed (that its
 * forceClosed() method was called). It is distinct from the
 * DisplayDestroyedEvent in org.micromanager.displays in that it is posted to
 * the global event bus as a system-wide notification. It is not expected that
 * any third-party code will need to know about this.
 */
public class GlobalDisplayDestroyedEvent {
   private DisplayWindow display_;

   public GlobalDisplayDestroyedEvent(DisplayWindow display) {
      display_ = display;
   }

   public DisplayWindow getDisplay() {
      return display_;
   }
}
