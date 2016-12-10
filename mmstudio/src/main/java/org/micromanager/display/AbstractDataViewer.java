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

import com.google.common.eventbus.EventBus;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.display.internal.DefaultNewDisplaySettingsEvent;

/**
 * Abstract implementation of the {@link DataViewer} interface.
 *
 * Custom data viewer classes should extend this class, rather than directly
 * implement {@code DataViewer}.
 *
 * <b>Warning</b>: Support for custom data viewers should be considered
 * experimental. Future updates may require you to update such code for
 * compatibility.
 *
* @author Mark A. Tsuchida
 */
public abstract class AbstractDataViewer implements DataViewer {
   private final EventBus eventBus_ = new EventBus();

   // Guarded by monitor on this
   private DisplaySettings displaySettings_;
   // Guarded by monitor on this
   private Coords displayPosition_ = new DefaultCoords.Builder().build();

   /**
    * Construct the abstract viewer implementation.
    *
    * @param initialDisplaySettings initial display settings
    */
   protected AbstractDataViewer(DisplaySettings initialDisplaySettings) {
      if (initialDisplaySettings == null) {
         throw new NullPointerException("Display settings must not be null");
      }
      displaySettings_ = initialDisplaySettings;
   }

   @Override
   public final void registerForEvents(Object recipient) {
      eventBus_.register(recipient);
   }

   @Override
   public final void unregisterForEvents(Object recipient) {
      eventBus_.unregister(recipient);
   }

   /**
    * Post an event on the viewer event bus.
    * <p>
    * Some standard viewer events require that they be posted on the Swing/AWT
    * event dispatch thread. Make sure you are on the right thread when posting
    * such events.
    * <p>
    * Viewers are required to post the following events:
    * <ul>
    * <li>{@link NewDisplaySettingsEvent} (posted by this abstract class)
    * <li>{@link NewDataPositionRenderedEvent} (on the EDT)
    * <li>{@link DisplayDidCloseEvent} (on the EDT)
    * </ul>
    *
    * @param event the event to post
    */
   protected final void postEvent(Object event) {
      eventBus_.post(event);
   }

   /** Implements {@code setDisplaySettings}.
    *
    * {@inheritDoc}
    * <p>
    * You cannot override this method. To apply the newly-set settings to your
    * display, subscribe to {@link NewDisplaySettingsEvent}.
    *
    * @param settings
    */
   @Override
   public final void setDisplaySettings(DisplaySettings settings) {
      if (settings == null) {
         throw new NullPointerException("Display settings must not be null");
      }
      synchronized (this) {
         DisplaySettings oldSettings = displaySettings_;
         displaySettings_ = settings;
         postEvent(DefaultNewDisplaySettingsEvent.create(this,
               oldSettings, settings));
      }
   }

   @Override
   public final DisplaySettings getDisplaySettings() {
      synchronized (this) {
         return displaySettings_;
      }
   }

   @Override
   public final boolean compareAndSetDisplaySettings(
         DisplaySettings oldSettings, DisplaySettings newSettings)
   {
      if (newSettings == null) {
         throw new NullPointerException("Display settings must not be null");
      }
      synchronized (this) {
         if (oldSettings != displaySettings_) {
            // We could compare the contents, but it's probably not worth the
            // effort; spurious failures should not affect proper usage
            return false;
         }
         displaySettings_ = newSettings;
         postEvent(DefaultNewDisplaySettingsEvent.create(this,
               oldSettings, newSettings));
         return true;
      }
   }

   /**
    * Implements {@code setDisplayPosition}.
    *
    * {@inheritDoc}
    * <p>
    * You should override this method, but make sure to call {@code super}
    * (this ensures that {@code getDisplayPosition} and
    * {@code compareAndSetDispalyPosition} will work).
    * <p>
    * You should not perform time-consuming updates in your override. Instead,
    * you should merely arrange for the display to update on an appropriate
    * thread (usually the Swing/AWT event dispatch thread) at a later time.
    */
   @Override
   public void setDisplayPosition(Coords position) {
      if (position == null) {
         throw new NullPointerException("Position must not be null");
      }
      synchronized (this) {
         displayPosition_ = position;
      }
   }

   /**
    * Implements {@code getDisplayPosition}.
    *
    * {@inheritDoc}
    * <p>
    * You can override this method (usually should not be necessary), but make
    * sure to call {@code super} to get the position (this ensures that {@code
    * setDisplayPosition} and {@code compareAndSetDisplayPosition} will work).
    * <p>
    * You should not wait for other threads to fetch data in your override.
    */
   @Override
   public Coords getDisplayPosition() {
      synchronized (this) {
         return displayPosition_;
      }
   }

   /**
    * Implements {@code compareAndSetDisplayPosition}.
    *
    * {@inheritDoc}
    * <p>
    * This implementation calls {@code getDisplayPosition} and {@code
    * setDisplayPosition} with the monitor on this acquired.
    */
   @Override
   public final boolean compareAndSetDisplayPosition(Coords oldPosition,
         Coords newPosition)
   {
      if (newPosition == null) {
         throw new NullPointerException("Position must not be null");
      }
      synchronized (this) {
         if (!getDisplayPosition().equals(oldPosition)) {
            return false;
         }
         setDisplayPosition(newPosition);
         return true;
      }
   }

   /**
    * Implements {@code setDisplayedImageTo} by calling
    * {@code setDisplayPosition).
    * @deprecated user code should call {@code setDisplayPosition}
    */
   @Override
   @Deprecated
   public final void setDisplayedImageTo(Coords coords) {
      setDisplayPosition(coords);
   }

   /**
    * Implements {@code getDatastore} by calling {@code getDataProvider}.
    * @deprecated user code should call {@code getDataProvider}
    */
   @Override
   @Deprecated
   public final Datastore getDatastore() {
      DataProvider provider = getDataProvider();
      if (provider instanceof Datastore) {
         return (Datastore) provider;
      }
      return null;
   }
}