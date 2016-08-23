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
import javax.swing.JPopupMenu;

/**
 * An InspectorPanel is a single component in the inspector window. 
 */
public abstract class InspectorPanel extends JPanel {
   /**
    * Provide a menu used for miscellaneous controls. This menu will be
    * attached to a "gear button" visible in the title bar, when this
    * panel is currently open. May be null, in which case no gear button will
    * be shown. This method is called when the panel is first added to the
    * inspector to determine if the button should be used; then when the
    * button is clicked, the method is called again, in case you need to
    * re-generate your menu based on context (e.g. changes in display
    * settings).
    * @return GearMenu
    */
   public JPopupMenu getGearMenu() {
      return null;
   }

   /**
    * Set the Inspector instance. This is only needed if your class needs to
    * be able to invoke the methods exposed in the Inspector interface.
    * @param inspector An implementation of the Inspector interface.
    */
   public void setInspector(Inspector inspector) {}

   /**
    * Return whether or not this InspectorPanel can meaningfully interact with
    * the provided DataViewer. If this method returns false, then this
    * InspectorPanel will be hidden until a different DataViewer is activated
    * in the Inspector window.
    * @param viewer DataViewer that the Inspector window is tracking.
    * @return true if this InspectorPanel can work with the DataViewer.
    */
   public boolean getIsValid(DataViewer viewer) {
      return true;
   }

   /**
    * Receive a new DataViewer. For panels whose contents change in response
    * to the DataViewer that is currently "active", this method must be
    * overridden to update those contents. This method will only ever be
    * called if getIsValid() returns true for the provided viewer.
    * @param viewer The newly-active DataViewer. This may be null, in which
    *        case no DataViewer is available.
    */
   public void setDataViewer(DataViewer viewer) {}

   /**
    * Provides sizing rules for when the Inspector window is resized.
    * @return Whether or not this panel should be allowed to grow vertically to
    * take up extra space when the Inspector window is resized.
    */
   public boolean getGrowsVertically() {
      return true;
   }

   /**
    * Release resources and unregister for events, because the Inspector that
    * this panel is a part of is about to be destroyed. For example, if your
    * InspectorPanel is registered to a DisplayWindow's event system, then it
    * needs to unregister in this method.
    */
   public abstract void cleanup();
}
