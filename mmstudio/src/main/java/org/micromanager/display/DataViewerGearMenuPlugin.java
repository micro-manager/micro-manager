///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    Regents of the University of California, 2026
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

import org.micromanager.MMPlugin;

/**
 * Adds an item to the gear menu of a viewer that is a {@link DataViewer} but not
 * a {@link DisplayWindow}, such as the TiledDataViewer.
 *
 * <p>This is the counterpart of {@link DisplayGearMenuPlugin}, which takes a
 * {@code DisplayWindow} and so cannot be used by viewers that do not implement
 * that interface. Implementations declare which viewers they support through
 * {@link #isApplicableToDataViewer}, in the same way
 * {@link org.micromanager.display.inspector.InspectorPanelPlugin} does, and
 * typically cast the viewer to their own API type inside
 * {@link #onPluginSelected}.
 */
public interface DataViewerGearMenuPlugin extends MMPlugin {
   /**
    * Indicate which sub-menu of the gear menu this plugin should appear in. If
    * that sub-menu does not exist, it will be created. If an empty string is
    * returned, then the plugin will be inserted directly into the gear menu,
    * instead of into a sub-menu.
    *
    * @return Sub-menu of the gear menu hosting this entry, or empty string.
    */
   String getSubMenu();

   /**
    * Indicate whether this plugin can operate on the given viewer.
    *
    * <p>Only applicable plugins are shown in that viewer's gear menu, so an
    * implementation may safely cast the viewer to its expected type in
    * {@link #onPluginSelected}.
    *
    * @param viewer the viewer whose gear menu is being built
    * @return true if this plugin should appear in that viewer's gear menu
    */
   boolean isApplicableToDataViewer(DataViewer viewer);

   /**
    * This method will be called when the plugin is selected from the gear menu.
    *
    * @param viewer The viewer whose gear menu was interacted with. This is one
    *               for which {@link #isApplicableToDataViewer} returned true.
    */
   void onPluginSelected(DataViewer viewer);
}
