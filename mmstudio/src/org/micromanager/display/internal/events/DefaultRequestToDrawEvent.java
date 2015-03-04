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

package org.micromanager.display.internal.events;

import org.micromanager.data.Coords;
import org.micromanager.display.RequestToDrawEvent;

public class DefaultRequestToDrawEvent implements RequestToDrawEvent {
   private Coords coords_;
   
   public DefaultRequestToDrawEvent() {
   }

   public DefaultRequestToDrawEvent(Coords coords) {
      coords_ = coords;
   }

   public Coords getCoords() {
      return coords_;
   }
}
