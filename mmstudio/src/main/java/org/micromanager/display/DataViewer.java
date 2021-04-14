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

import java.io.IOException;
import java.util.List;
import org.micromanager.EventPublisher;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;

/**
 * General interface for any user interface displaying image data.
 *
 * Implementers: Normally, you should subclass {@link AbstractDataViewer}
 * rather than directly implementing this interface. That will allow your
 * implementation to continue to work when methods are added to this interface
 * in the future.
 * <p>
 * <b>Warning</b>: Support for custom data viewers is experimental.
 * Future updates may require you to update such code for compatibility.
 *
 * @author Chris Weisiger, Mark A. Tsuchida
 */
public interface DataViewer extends EventPublisher {
   /**
    * Register an object to receive events on the viewer event bus.
    * <p>
    * Objects registered by this method will receive viewer events through
    * their methods bearing a {@code com.google.common.eventbus.Subscribe}
    * annotation. See Guava Event Bus documentation for how this works.
    * <p>
    * Events that can be subscribed to include:
    * <ul>
    * <li>{@link DisplaySettingsChangedEvent} (on an arbitrary thread)
    * <li>{@link DisplayPositionChangedEvent} (on an arbitrary thread)
    * <li>TODO DisplayPositionRenderedEvent (on the EDT)
    * <li>TODO DataViewerClosedEvent (on the EDT)
    * </ul>
    *
    * @param recipient the object to register
    *
    * @see #unregisterForEvents(Object)
    */
   @Override
   void registerForEvents(Object recipient);

   @Override
   void unregisterForEvents(Object recipient);

   /**
    * Set the display settings.
    * <p>
    * This method is a good way to set all of the display settings at once, for
    * example to restore a saved set of settings. For changing just part of the
    * settings (for example, in response to the user setting a UI control), see
    * {@link #compareAndSetDisplaySettings(DisplaySettings, DisplaySettings)}.
    * <p>
    * This method can be called from any thread. There may be a delay before
    * the new settings are actually reflected in the viewer user interface.
    * However, {@link #getDisplaySettings()} will always reflect the most recent
    * settings, even if they have not yet been applied to the UI.
    *
    * @param settings the new display settings
    */
   void setDisplaySettings(DisplaySettings settings);

   /**
    * Get the current display settings.
    * <p>
    * This method can be called from any thread. The return value will be
    * consistent with the most recent call to {@link #setDisplaySettings(DisplaySettings)} or
    * {@code compareAndSetDisplaySettings}.
    *
    * @return the current display settings
    */
   DisplaySettings getDisplaySettings();

   /**
    * Set the display settings only if the current settings match the expected
    * one.
    * <p>
    * This method will allow you to make changes to the display settings in
    * a way that is safe if multiple threads are trying to make changes. For
    * example, the following code will set the zoom. If another thread has
    * makes a change to another part of the display settings at the same time
    * (using the same method), all changes will be correctly reflected.
    * <pre><code>
    * DataViewer viewer = ...;
    * do {
    *    DisplaySettings oldSettings = viewer.getDisplaySettings();
    *    DisplaySettings newSettings = oldSettings.copy().
    *          zoom(2.0).build();
    * } while (!viewer.compareAndSetDisplaySettings(oldSettings, newSettings));
    * </code></pre>
    *
    * @param originalSettings apply the new settings only if the current
    * settings match this
    * @param newSettings the new settings
    * @return whether the new settings were applied
    */
   boolean compareAndSetDisplaySettings(
         DisplaySettings originalSettings, DisplaySettings newSettings);

   /**
    * Retrieve the data provider backing this viewer.
    *
    * This method can be called from any thread.
    *
    * Implementers: This method should be implemented so that it can be called
    * from any thread. Typically, the data provider will be a {@code private
    * final} field in your implementation.
    *
    * @return the data provider backing this viewer
    */
   DataProvider getDataProvider();

   /**
    * Retrieve the datastore backing this viewer.
    *
    * @return the datastore backing this viewer, or {@code null} if this viewer
    * is backed by a data provider that is not a datastore
    * @deprecated use {@link #getDataProvider()} instead
    */
   @Deprecated
   Datastore getDatastore();

   /**
    * Display the images at the specified coordinates in the data provider.
    * <p>
    * The exact interpretation of the position may depend on the viewer
    * implementation: for example, a 3D viewer might ignore the Z slice
    * coordinate passed to this method and instead display a whole volume.
    * If the passed position does not uniquely specify a set of images to
    * display, then the viewer might choose an arbitrary subset of the passed
    * coordinates.
    * <p>
    * This method can be called from any thread. There may be a delay before
    * the new position is actually reflected in the viewer user interface.
    * However, {@link #getDisplayPosition()} will always reflect the most recent
    * position, even if it has not been applied to the UI.
    *
    * @param position the coordinates of the images to display
    * @param forceRedisplay if true, assume the image(s) at the position may
    * have changed even if the position does not differ from the current one
    *
    * @see #compareAndSetDisplaySettings(DisplaySettings, DisplaySettings)
    */
   void setDisplayPosition(Coords position, boolean forceRedisplay);

   /**
    * Display the images at the specified coordinates in the data provider.
    * <p>
    * See {@link #setDisplayPosition(Coords, boolean)} for details. This method
    * does not force a redisplay if {@code position} is the same as the current
    * display position.
    *
    * @param position the coordinates of the images to display
    */
   void setDisplayPosition(Coords position);

   /**
    * Get the coordinates for the currently displayed images.
    * <p>
    * This method can be called from any thread. The return value will be
    * consistent with the most recent call to {@link #setDisplayPosition(Coords)} or
    * {@code compareAndSetDisplayPosition}.
    *
    * @return the current coordinates displayed
    */
   Coords getDisplayPosition();

   /**
    * Set the display position only if the current position is the expected one.
    * <p>
    * This method will allow you to set the display position based on the
    * current position in a way that is safe if multiple threads are trying to
    * make changes. For  example, the following code will scroll to the next
    * channel. If the display position is being changed by another thread
    * (perhaps the time points are being animated, or new time points are being
    * added), all changes will be correctly reflected.
    * <pre><code>
    * DataViewer viewer = ...;
    * do {
    *    Coords oldPos = viewer.getDisplayPosition();
    *    Coords newPos = oldPos.copy().
    *          channel((oldPos.getChannel() + 1) % nChannels).build();
    * } while (!viewer.compareAndSetDisplayPosition(oldPos, newPos));
    * </code></pre>
    *
    * @param originalPosition apply the new position only if the current
    * position matches this one
    * @param newPosition the new display position
    * @param forceRedisplay if true, assume the image(s) at the position may
    * have changed even if the position does not differ from the current one
    * @return whether the new position was applied
    */
   boolean compareAndSetDisplayPosition(Coords originalPosition,
         Coords newPosition, boolean forceRedisplay);

   /**
    * Set the display position only if the current position is the expected one.
    * <p>
    * See {@link #compareAndSetDisplayPosition(Coords, Coords, boolean)} for
    * details.
    * <p>
    * This method will not force a redisplay if {@code newPosition} is the same
    * as the current display position.
    *
    * @param originalPosition
    * @param newPosition
    * @return
    */
   boolean compareAndSetDisplayPosition(Coords originalPosition,
         Coords newPosition);

   /**
    * Obsolete equivalent of {@link #setDisplayPosition(Coords)}.
    *
    * @param position the coordinates of the images to display
    * @deprecated use {@link #setDisplayPosition(Coords)} instead
    */
   @Deprecated
   void setDisplayedImageTo(Coords position);

   /**
    * Retrieve the Images currently being displayed.
    * <p>
    * This method can be called from any thread.
    *
    * @return Every image at the currently-displayed image coordinates.
    * @throws java.io.IOException
    */
   List<Image> getDisplayedImages() throws IOException;

   /**
    * Return true if this viewer is visible.
    *
    * It is recommended that this method be called on the Swing/AWT event
    * dispatch thread. If you call it from another thread, make sure that your
    * thread is not obstructing the event dispatch thread (i.e. the event
    * dispatch thread is not waiting for your thread to finish something).
    *
    * @return whether the viewer is visible on screen
    */
   boolean isVisible();

   /**
    * Return true if this viewer has been closed.
    *
    * It is recommended that this method be called on the Swing/AWT event
    * dispatch thread. If you call it from another thread, make sure that your
    * thread is not obstructing the event dispatch thread (i.e. the event
    * dispatch thread is not waiting for your thread to finish something).
    *
    * @return whether the viewer has been closed
    */
   boolean isClosed();

   /**
    * Return the name of this viewer.
    *
    * Typically this will include the display number and the name of the
    * dataset.
    *
    * Implementers: An effort should be made to return a unique name (e.g. by
    * adding a suffix) so that the user can distinguish between viewers that
    * would otherwise have the same name (for example, because they are
    * attached to the same dataset).
    *
    * @return a string labeling this display
    */
   String getName();
   
   /**
    * Listeners will be notified of important events of the DataViewer
    * 
    * @param listener - that will be notified
    * @param priority - determines the order in which listeners will be called.
    * the lower the number, the earlier the listener will be called.  If the 
    * priority matches a previously added listener, the previously added listener
    * will be called first
    */
   void addListener(DataViewerListener listener, int priority);
   
   /**
    * No longer notify this listener
    * @param listener - that will no longer be notified
    */
   void removeListener(DataViewerListener listener);
   
}