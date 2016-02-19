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

package org.micromanager.display.internal;

import org.micromanager.display.DisplayWindow;

/**
 * This event signifies that a display has been destroyed via its
 * forceClosed() method.
 */
public class DisplayDestroyedEvent implements org.micromanager.display.DisplayDestroyedEvent {
   private final DisplayWindow display_;

   public DisplayDestroyedEvent(DisplayWindow display) {
      display_ = display;
   }

   @Override
   public DisplayWindow getDisplay() {
      return display_;
   }
}
