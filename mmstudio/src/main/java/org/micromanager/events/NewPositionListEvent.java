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

import org.micromanager.PositionList;

/**
 * This event posts when the application's Stage Position List changes
 * (positions added, removed, or moved).
 *
 * <p>The default implementation of this event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.</p>
 */
public interface NewPositionListEvent {

   /**
    * Returns the new stage position list.
    *
    * @return PositionList that is modified, usually the application's Stage
    *     Position List.
    */
   PositionList getPositionList();
}
