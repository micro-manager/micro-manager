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

import javax.swing.JPopupMenu;
import org.apache.commons.lang3.event.EventListenerSupport;
import org.micromanager.display.DataViewer;

/**
 * A panel containing a section of the inspector window.
 *
 * In addition to implementing this abstract class, inspector panels must
 * call the appropriate methods of {@code Inspector} to notify the latter of
 * certain changes.
 */
public abstract class AbstractInspectorPanelController
      implements InspectorPanelController
{
   private final EventListenerSupport<InspectorPanelListener> listeners_ =
         new EventListenerSupport<InspectorPanelListener>(
               InspectorPanelListener.class, InspectorPanelListener.class.getClassLoader());

   @Override
   public final void addInspectorPanelListener(
         InspectorPanelListener listener)
   {
      listeners_.addListener(listener, true);
   }

   @Override
   public final void removeInspectorPanelListener(
         InspectorPanelListener listener)
   {
      listeners_.removeListener(listener);
   }

   protected void fireInspectorPanelWillChangeHeight() {
      listeners_.fire().inspectorPanelWillChangeHeight(this);
   }

   protected void fireInspectorPanelDidChangeHeight()
   {
      listeners_.fire().inspectorPanelDidChangeHeight(this);
   }

   protected void fireInspectorPanelDidChangeTitle() {
      listeners_.fire().inspectorPanelDidChangeTitle(this);
   }

   @Override
   public abstract String getTitle();

   /**
    * Provide a menu used for miscellaneous controls. This menu will be
    * attached to a "gear button" visible in the title bar, when this
    * panel is currently open. May be null, in which case no gear button will
    * be shown. This method is called when the panel is first added to the
    * inspector to determine if the button should be used; then when the
    * button is clicked, the method is called again, in case you need to
    * re-generate your menu based on context (e.g. changes in display
    * settings).
    * <p>
    * It is permissible to always return the same JPopupMenu instance, as long
    * as you ensure that it is updated each time this method is called.
    * <p>
    * This method is guaranteed to be called on the Swing/AWT event dispatch
    * thread.
    *
    * @return the gear popup menu, or null if the panel does not use a gear
    * menu
    */
   @Override
   public JPopupMenu getGearMenu() {
      return null;
   }

   /**
    * Attach to a data viewer.
    *
    * @param viewer a {@code DataViewer} (never null)
    */
   @Override
   public abstract void attachDataViewer(DataViewer viewer);

   /**
    * Detach from any currently attached data viewer. This method should not throw an exception if a viewer is not yet attached.
    */
   @Override
   public abstract void detachDataViewer();

   /**
    * Indicate whether the panel can be resized vertically by the user.
    *
    * Note that this is independent of whether the panel resizes itself
    * depending on its content.
    *
    * @return true if the panel can be resized vertically; false if the panel
    * has a fixed height
    */
   @Override
   public abstract boolean isVerticallyResizableByUser();

}