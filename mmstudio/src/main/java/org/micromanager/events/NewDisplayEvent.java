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
 * This event is posted whenever a new display window is created for *any*
 * Datastore. Register for this event using the MMStudio.registerForEvents()
 * method (i.e. not the equivalent Datastore or DisplayWindow methods). Note
 * that the DisplayWindow will not be visible on-screen until at least one
 * image is in the Datastore that the DisplayWindow represents. If you want
 * to be notified when a DisplayWindow is about to be drawn, then you should
 * listen for the DisplayAboutToShowEvent.
 */
public interface NewDisplayEvent {
   /**
    * Provides access to the newly-created DisplayWindow.
    * @return the new DisplayWindow.
    */
   public DisplayWindow getDisplay();
}
