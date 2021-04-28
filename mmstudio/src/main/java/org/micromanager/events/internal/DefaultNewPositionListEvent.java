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

import org.micromanager.PositionList;
import org.micromanager.events.NewPositionListEvent;

/**
 * This event posts when the application's Stage Position List changes
 * (positions added, removed, or moved).
 *
 * This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.
 */
public final class DefaultNewPositionListEvent implements NewPositionListEvent {
   private PositionList newList_;

   public DefaultNewPositionListEvent(PositionList newList) {
      newList_ = newList;
   }

   /**
    * Returns the new stage position list.
    * @return PositionList that is modified, usually the application's Stage
    * Position List.
    */
   @Override
   public PositionList getPositionList() {
      return newList_;
   }
}
