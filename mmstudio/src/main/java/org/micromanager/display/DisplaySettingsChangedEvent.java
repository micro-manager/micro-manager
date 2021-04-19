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

package org.micromanager.display;

import org.micromanager.MMEvent;

/**
 * Event used internally by a data viewer to respond to requests to update
 * the display settings.
 *
 * All compliant {@code DataViewer} implementations post an instance of this
 * event when told to change the display settings (this is usually handled by
 * {@code AbstractDataViewer}). Implementations then subscribe to this event,
 * and in their handler(s) arrange to apply the new settings.
 *
 * {@code DataViewer.setDisplaySettings} and other methods that can result in
 * new display settings can be called on any thread. This event is posted from
 * any such calling thread. Thus, handlers for this event must be prepared to
 * be called on any thread, including the Swing/AWT event dispatch thread
 * (EDT).
 *
 * It is guaranteed that only a single instance of this event will ever be
 * posted concurrently for a given data viewer, and that such posting will
 * happen in the correct order.
 *
 * Although applying display settings almost always requires interaction with
 * UI components, handlers for this event must not perform any action
 * synchronously on the EDT (such as by calling {@code
 * SwingUtilities.invokeAndWait}). Instead, actions on the EDT should be
 * deferred until a later time (such as by calling {@code
 * SwingUtilities.invokeLater}).
 *
 * This event may be posted at a relatively high frequency (up to 60 times per
 * second under normal conditions, though there is no guaranteed upper limit),
 * so well-designed handlers should defer time-consuming operations to a later
 * time.
 *
 * It is okay for objects not directly related to the data viewer to subscribe
 * to this event, but they must obey the same rules regarding threading and
 * performance.
 *
 * The default implementation of this event posts on the DataViewer event bus.
 * Register using {@link DataViewer#registerForEvents(Object)}.
 *
 * @author Chris Weisiger and Mark A. Tsuchida
 */
public interface DisplaySettingsChangedEvent extends MMEvent {

   /**
    * Get the new display settings.
    * @return the new display settings
    */
   DisplaySettings getDisplaySettings();

   /**
    * Get the display settings before the change being handled.
    *
    * Comparing this with the return value of {@code getDisplaySettings} can
    * reveal what exactly is to be changed.
    *
    * @return the previous display settings
    */
   DisplaySettings getPreviousDisplaySettings();

   /**
    * Get the data viewer.
    * @return the data viewer for which the new display settings is to be
    * applied
    */
   DataViewer getDataViewer();

   /**
    * Old name for {@code getDataViewer}.
    * @return the data viewer for which the new display settings is to be
    * applied
    * @deprecated use {@code getDataViewer} instead
    */
   @Deprecated
   DataViewer getDisplay();
}