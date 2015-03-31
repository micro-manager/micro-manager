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

package org.micromanager.display.internal.inspector;

import javax.swing.JPanel;

import org.micromanager.display.DisplayWindow;

/**
 * An InspectorPanel is a single component in the inspector window. It has a
 * title bar that can be clicked to show/hide the contents of the panel.
 */
public class InspectorPanel extends JPanel {
   /**
    * Receive a new DisplayWindow. For panels whose contents change in response
    * to the DisplayWindow that is currently "active", this method must be
    * overridden to update those contents.
    * @param display The newly-active DisplayWindow.
    */
   public void setDisplay(DisplayWindow display) {}
}
