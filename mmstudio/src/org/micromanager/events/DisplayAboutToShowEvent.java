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

import org.micromanager.display.DisplayWindow;

/**
 * This event is posted immediately prior to a new DisplayWindow drawing
 * itself to the screen for the first time. This will happen as soon as there
 * is at least one image in the Datastore for the DisplayWindow. If you want
 * to be notified of when a DisplayWindow is created, instead, then you should
 * listen for the NewDisplayEvent.
 */
public interface DisplayAboutToShowEvent {
   /**
    * Provides access to the DisplayWindow that is about to be drawn.
    * @return the DisplayWindow.
    */
   public DisplayWindow getDisplay();
}
