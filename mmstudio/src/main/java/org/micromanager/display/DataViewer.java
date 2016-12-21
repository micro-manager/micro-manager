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

import java.util.List;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;

/**
 * This is a basic interface that is implemented by DisplayWindow and may
 * potentially be implemented by other types of displays in the future. Its
 * methods chiefly deal with DisplaySettings, events, and access to data. We
 * may also in the future allow other code that implements this interface to
 * make use of standard display widgets (like the Inspector).
 */
public interface DataViewer {
   /**
    * Update the DisplaySettings for the display. This must post a
    * NewDisplaySettingsEvent on the display's EventBus, and should also cause
    * the display to redraw itself. If you are implementing your own DataViewer
    * then you should be certain to do those actions yourself. For example,
    * if your implementation stores the DisplaySettings under the "settings_"
    * field and has an EventBus under the "bus_" field:
    *
    * <pre><code>
    * {@literal @}Override
    * public void setDisplaySettings(DisplaySettings settings) {
    *    settings_ = settings;
    *    bus_.post(new NewDisplaySettingsEvent(settings_, this));
    *    repaint();
    * }
    * </code></pre>
    *
    * @param settings The new display settings.
    */
   public void setDisplaySettings(DisplaySettings settings);

   /**
    * Retrieve the DisplaySettings for this display.
    * @return The DisplaySettings for this display.
    */
   public DisplaySettings getDisplaySettings();

   /**
    * Register for access to the EventBus that the window uses for propagating
    * events. Note that this is different from the EventBus for the Datastore
    * that this display uses; this EventBus is specifically for events related
    * to the display.
    * @param obj The object that wants to subscribe for events.
    */
   public void registerForEvents(Object obj);

   /**
    * Unregister for events for this display. See documentation for
    * registerForEvents().
    * @param obj The object that wants to no longer be subscribed for events.
    */
   public void unregisterForEvents(Object obj);

   /**
    * Post the provided event object on the display's EventBus, so that objects
    * that have called registerForEvents(), above, can receive it.
    * @param obj The event to post.
    */
   public void postEvent(Object obj);

   /**
    * Retrieve the Datastore backing this display.
    * @return The Datastore backing this display.
    */
   public Datastore getDatastore();

   /**
    * Display the image at the specified coordinates in the Datastore the
    * display is showing.
    * @param coords The coordinates of the image to be displayed.
    */
   public void setDisplayedImageTo(Coords coords);

   /**
    * Retrieve the Images currently being displayed.
    * @return Every image at the currently-displayed image coordinates.
    */
   public List<Image> getDisplayedImages();

   /**
    * Request that the display redraw its current image. You shouldn't need
    * to call this often; most changes that affect the display (for example,
    * changes to the display settings) automatically cause the image to be
    * redrawn.
    */
   public void requestRedraw();

   /**
    * Return true if the DataViewer has been closed and should no longer be
    * used.
    */
   public boolean getIsClosed();

   /**
    * Return the unique name of this display. Typically this will include the
    * display number and the name of the dataset; if no name is available, then
    * "MM image display" will be used instead. For DisplayWindows, this string
    * is displayed in the inspector window, ImageJ's "Windows" menu, and a few
    * other places.
    * @return a string labeling this display.
    */
   public String getName();
}
