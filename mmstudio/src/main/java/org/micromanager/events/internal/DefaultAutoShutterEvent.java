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

import org.micromanager.events.AutoShutterEvent;


/**
 *  This event is posted on the Studio event bus, so subscribe it using
 *  {@link org.micromanager.events.EventManager}.
 */
public final class DefaultAutoShutterEvent implements AutoShutterEvent {
   private boolean isAutoOn_;
   public DefaultAutoShutterEvent(boolean isOn) {
      isAutoOn_ = isOn;
   }

   @Override
   public boolean getAutoShutter() {
      return isAutoOn_;
   }
}
