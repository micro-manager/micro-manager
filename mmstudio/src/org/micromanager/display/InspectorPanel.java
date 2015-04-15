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

package org.micromanager.display;

import javax.swing.JPanel;

import org.micromanager.display.DisplayWindow;

/**
 * An InspectorPanel is a single component in the inspector window. 
 */
public abstract class InspectorPanel extends JPanel {
   /**
    * Set the Inspector instance. This is only needed if your class needs to
    * be able to invoke the methods exposed in the Inspector interface.
    * @param inspector An implementation of the Inspector interface.
    */
   public void setInspector(Inspector inspector) {}

   /**
    * Receive a new DisplayWindow. For panels whose contents change in response
    * to the DisplayWindow that is currently "active", this method must be
    * overridden to update those contents.
    * @param display The newly-active DisplayWindow. This may be null, in which
    *        case no DisplayWindow is available.
    */
   public abstract void setDisplay(DisplayWindow display);

   /**
    * Release resources and unregister for events, because the Inspector that
    * this panel is a part of is about to be destroyed. For example, if your
    * InspectorPanel is registered to a DisplayWindow's event system, then it
    * needs to unregister in this method.
    */
   public abstract void cleanup();
}
