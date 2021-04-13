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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.display.internal.event.DefaultDisplayPositionChangedEvent;
import org.micromanager.display.internal.event.DefaultDisplaySettingsChangedEvent;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.internal.utils.ThreadFactoryFactory;

/**
 * Abstract implementation of the {@link DataViewer} interface.
 *
 * Custom data viewer classes should extend this class, rather than directly
 * implement {@code DataViewer}.
 * <p>
 * <b>Warning</b>: Support for custom data viewers is experimental.
 * Future updates may require you to update such code for compatibility.
 *
* @author Mark A. Tsuchida
 */
public abstract class AbstractDataViewer implements DataViewer {
   private final EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());

   // Guarded by monitor on this
   private DisplaySettings displaySettings_;
   // Guarded by monitor on this
   private Coords displayPosition_ = null;

   // When display settings or display position is changed, we need to post the
   // notification events in the correct order. One way to do that is to post
   // those events from a synchronized context, but that is not ideal because
   // we don't have control over what happens in the event handlers. So instead
   // we use a single-threaded executor to sequence the event posting.
   // TODO XXX Need to shut down
   private final ExecutorService asyncEventPoster_ =
         Executors.newSingleThreadExecutor(ThreadFactoryFactory.
               createThreadFactory("AbstractDataViewer Pool"));

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
    *
    * Implementations should call this method to post required notification
    * events.
    * <p>
    * Some standard viewer events require that they be posted on the Swing/AWT
    * event dispatch thread. Make sure you are on the right thread when posting
    * such events.
    * <p>
    * Viewers are required to post the following events:
    * <ul>
    * <li>{@link DisplaySettingsChangedEvent} (posted by this abstract class)
    * <li>{@link DisplayPositionChangedEvent} (posted by this abstract class)
    * </ul>
    *
    * @param event the event to post
    */
   protected final void postEvent(Object event) {
      eventBus_.post(event);
   }

   /**
    * Implements {@code setDisplaySettings}.
    * <p>
    * {@inheritDoc}
    * <p>
    * Implementations must override {@code handleNewDisplaySettings} in order
    * to respond to new display settings.
    */
   @Override
   public final void setDisplaySettings(final DisplaySettings settings) {
      if (settings == null) {
         throw new NullPointerException("Display settings must not be null");
      }
      synchronized (this) {
         final DisplaySettings oldSettings = displaySettings_;
         if (settings == oldSettings) {
            return;
         }
         displaySettings_ = handleDisplaySettings(settings);
         asyncEventPoster_.submit(() -> {
            postEvent(DefaultDisplaySettingsChangedEvent.create(
                    AbstractDataViewer.this, oldSettings, displaySettings_));
         });
      }
   }

   /**
    * Implements {@code getDisplaySettings}.
    * <p>
    * {@inheritDoc}
    */
   @Override
   public final DisplaySettings getDisplaySettings() {
      synchronized (this) {
         return displaySettings_;
      }
   }

   /**
    * Implements {@code compareAndSetDisplaySettings}.
    * <p>
    * {@inheritDoc}
    * <p>
    * Implementations must override {@code handleNewDisplaySettings} in order
    * to respond to new display settings.
    */
   @Override
   public final boolean compareAndSetDisplaySettings(
         final DisplaySettings oldSettings, final DisplaySettings newSettings)
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
         if (newSettings == displaySettings_) {
            return true;
         }
         displaySettings_ = handleDisplaySettings(newSettings);
         asyncEventPoster_.submit(() -> {
            postEvent(DefaultDisplaySettingsChangedEvent.create(
                    AbstractDataViewer.this, oldSettings, displaySettings_));
         });
         return true;
      }
   }

   /**
    * Arrange to apply new display settings.
    *
    * Override this method to apply new display settings.
    * <p>
    * This method is called in a thread-synchronized context, so you should
    * avoid time-consuming actions. It is safe to call {@code
    * getDisplaySettings} and compare its return value with {@code
    * requestedSettings}. However, calling {@code setDisplaySettings} will
    * result in infinite recursion; if you need to adjust {@code
    * requestedSettings} before accepting it, do so by returning a modified
    * copy from this method. This returned settings is what subsequent calls to
    * {@code getDisplaySettings} will return.
    * <p>
    * Typically, the implementation should record all information needed to
    * make the changes and arrange to apply the changes at a later time
    * (usually on the Swing/AWT event dispatch thread).
    *
    * @param requestedSettings the new display settings requested
    * @return adjusted display settings that will actually apply
    */
   protected abstract DisplaySettings handleDisplaySettings(
         DisplaySettings requestedSettings);

   /**
    * Implements {@code setDisplayPosition}.
    * <p>
    * {@inheritDoc}
    * <p>
    * Implementations must override {@code handleNewDisplayPosition} in order
    * to respond to new display positions.
    */
   @Override
   public final void setDisplayPosition(final Coords position,
         boolean forceRedisplay)
   {
      if (position == null) {
         throw new NullPointerException("Position must not be null");
      }
      synchronized (this) {
         final Coords oldPosition = getDisplayPosition();
         if (!forceRedisplay && position.equals(oldPosition)) {
            return;
         }

         displayPosition_ = handleDisplayPosition(position);

         if (!position.equals(oldPosition)) {
            asyncEventPoster_.submit(() -> {
               postEvent(DefaultDisplayPositionChangedEvent.create(
                       AbstractDataViewer.this, oldPosition, displayPosition_));
            });
         }
      }
   }

   @Override
   public final void setDisplayPosition(Coords position) {
      setDisplayPosition(position, false);
   }

   /**
    * Implements {@code getDisplayPosition}.
    *
    * {@inheritDoc}
    */
   @Override
   public final Coords getDisplayPosition() {
      synchronized (this) {
         return displayPosition_;
      }
   }

   /**
    * Implements {@code compareAndSetDisplayPosition}.
    *
    * {@inheritDoc}
    */
   @Override
   public final boolean compareAndSetDisplayPosition(final Coords oldPosition,
         final Coords newPosition, boolean forceRedisplay)
   {
      if (newPosition == null) {
         throw new NullPointerException("Position must not be null");
      }
      synchronized (this) {
         if (!displayPosition_.equals(oldPosition)) {
            return false;
         }
         if (!forceRedisplay && newPosition.equals(displayPosition_)) {
            return true;
         }

         displayPosition_ = handleDisplayPosition(newPosition);

         if (!newPosition.equals(oldPosition)) {
            asyncEventPoster_.submit(() -> {
               postEvent(DefaultDisplayPositionChangedEvent.create(
                       AbstractDataViewer.this, oldPosition, displayPosition_));
            });
         }
         return true;
      }
   }

   @Override
   public final boolean compareAndSetDisplayPosition(Coords oldPosition,
         Coords newPosition)
   {
      return compareAndSetDisplayPosition(oldPosition, newPosition, false);
   }

   protected abstract Coords handleDisplayPosition(Coords position);

   /**
    * Must be called by implementation to release resources.
    * <p>
    * This is not a "close" method because the {@code DataViewer} interface may
    * apply to viewers that are not windows (e.g. an embeddable component).
    * <p>
    * This method should be called at an appropriate time to release non-memory
    * resources used by {@code AbstractDataViewer}.
    */
   protected void dispose() {
      asyncEventPoster_.shutdown();
   }

   /**
    * Implements {@code setDisplayedImageTo} by calling
    * {@code setDisplayPosition}.
    * <p>
    * {@inheritDoc}
    *
    * @param coords
    * @deprecated user code should call {@code setDisplayPosition}
    */
   @Override
   @Deprecated
   public final void setDisplayedImageTo(Coords coords) {
      setDisplayPosition(coords);
   }

   /**
    * Implements {@code getDatastore} by calling {@code getDataProvider}.
    *
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