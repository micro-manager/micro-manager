// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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

package org.micromanager.display.inspector;

/**
 *
 */
public interface InspectorPanelListener {
   /**
    * Must be called before changing minimum, preferred, and/or maximum height
    * of the panel.
    * <p>
    * This method should be called when the panel height is changed due to the
    * panel content changing, but not when the user resizes the panel manually.
    *
    * @param controller the controller of the inspector panel whose height is
    *                   about to change
    */
   void inspectorPanelWillChangeHeight(InspectorPanelController controller);

   /**
    * Must be called after changing minimum, preferred, and/or maximum height
    * of the panel.
    * <p>
    * This method should be called when the panel height is changed due to the
    * panel content changing, but not when the user resizes the panel manually.
    *
    * @param controller the controller of the inspector panel whose height has
    *                   changed
    */
   void inspectorPanelDidChangeHeight(InspectorPanelController controller);

   /**
    * Must be called when the title of the inspector panel has changed.
    *
    * @param controller
    */
   void inspectorPanelDidChangeTitle(InspectorPanelController controller);
}