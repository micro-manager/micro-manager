// Copyright (C) 2016-2017 Open Imaging, Inc.
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

package org.micromanager.display.internal.animate;

import com.google.common.util.concurrent.AtomicDouble;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.commons.lang3.event.EventListenerSupport;
import org.micromanager.data.Coords;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.internal.utils.performance.PerformanceMonitor;

/**
 * Coordinates and moderates animation of displayed data position.
 *
 * <p>This class is responsible for timing and coordination, but not for
 * determining the sequence of images to animate.</p>
 *
 * <p>All events resulting in a position change ("scrolling" in user terms) pass
 * through this object, including: playback animation, new incoming images,
 * manual scrolling by the user, and events from interlinked display windows.</p>
 *
 * <p>The data position type {@code P} is parameterized to support possible future
 * extensions such as animation in dilated physical time for non-uniformly
 * spaced time lapse datasets.</p>
 *
 * @param <P> the type used to describe a data position to display
 * @author Mark A. Tsuchida
 */
public final class AnimationController<P> {
   /**
    * Indicates how to handle a new position request.
    */
   public enum NewPositionHandlingMode {
      IGNORE,
      JUMP_TO_AND_STAY,
      FLASH_AND_SNAP_BACK,
   }

   /**
    * Listeners are notified on an otherwise nonblocking thread owned by the
    * animation controller (never the EDT).
    *
    * @param <P> For now only Coords, but parameterized to support future
    *            extensions
    */
   public interface Listener<P> {
      void animationShouldDisplayDataPosition(P position);

      /**
       * Update range but don't display.
       *
       * @param position Coord
       */

      void animationAcknowledgeDataPosition(P position);

      void animationWillJumpToNewDataPosition(P position);

      void animationDidJumpToNewDataPosition(P position);

      /**
       * After a certain amount of time, a newly displayed image is no longer considered new.
       * This function signifies that the image is no longer "new".
       */
      void animationNewDataPositionExpired();
      // TODO Need to also notify of animation start/stop
   }

   private final EventListenerSupport<Listener> listeners_ =
         new EventListenerSupport<>(Listener.class, Listener.class.getClassLoader());

   private final AnimationStateDelegate<P> sequencer_;

   private int tickIntervalMs_ = 100;
   private AtomicDouble animationRateFPS_ = new AtomicDouble(10.0);
   private final Map<String, NewPositionHandlingMode> newPositionModes_ =
         new HashMap<>();
   private int newPositionFlashDurationMs_ = 500;

   private final AtomicBoolean animationEnabled_ = new AtomicBoolean(false);

   private final ScheduledExecutorService scheduler_ =
         Executors.newSingleThreadScheduledExecutor(ThreadFactoryFactory
               .createThreadFactory("AnimationController"));

   private ScheduledFuture<?> scheduledTickFuture_;
   private ScheduledFuture<?> newDataPositionExpiredFuture_;
   private long lastTickNs_;

   private ScheduledFuture<?> snapBackFuture_;
   private P snapBackPosition_;

   // Track pending display task to enable coalescence at high frame rates
   private ScheduledFuture<?> pendingDisplayFuture_;

   // Rate limiting for display updates to prevent overwhelming the display pipeline
   // Even at 250 FPS camera speed, display can only update at ~60 FPS
   private static final long MIN_DISPLAY_INTERVAL_NS = 16_000_000L; // 16ms = ~60 FPS
   private long lastDisplayFiredNs_ = 0;

   private boolean didJumpToNewPosition_;

   private PerformanceMonitor perfMon_;

   public static <P> AnimationController create(AnimationStateDelegate<P> sequencer) {
      return new AnimationController(sequencer);
   }

   private AnimationController(AnimationStateDelegate<P> sequencer) {
      sequencer_ = sequencer;
   }

   /**
    * Adds a listener to be notified of animation events.
    *
    * @param listener Implementation of the Listener class to be added.
    */
   public synchronized void addListener(Listener listener) {
      listeners_.addListener(listener, true);
   }

   /**
    * Removes the Listener from the list of Listenered to be notified of animation events.
    *
    * @param listener Listener to be removed.
    */
   public synchronized void removeListener(Listener listener) {
      listeners_.removeListener(listener);
   }

   public void setPerformanceMonitor(PerformanceMonitor perfMon) {
      perfMon_ = perfMon;
   }

   /**
    * Permanently cease all animation and scheduled events.
    */
   public void shutdown() {
      stopAnimation();

      // Cancel all pending scheduled tasks
      synchronized (this) {
         if (scheduledTickFuture_ != null) {
            scheduledTickFuture_.cancel(true);
            scheduledTickFuture_ = null;
         }
         if (newDataPositionExpiredFuture_ != null) {
            newDataPositionExpiredFuture_.cancel(true);
            newDataPositionExpiredFuture_ = null;
         }
         if (snapBackFuture_ != null) {
            snapBackFuture_.cancel(true);
            snapBackFuture_ = null;
         }
         if (pendingDisplayFuture_ != null) {
            pendingDisplayFuture_.cancel(true);
            pendingDisplayFuture_ = null;
         }
      }

      scheduler_.shutdown();
      try {
         // Wait up to 5 seconds for graceful shutdown
         if (!scheduler_.awaitTermination(5, TimeUnit.SECONDS)) {
            // Force shutdown if graceful shutdown times out
            ReportingUtils.logMessage(
                    "AnimationController scheduler did not terminate gracefully, forcing shutdown");
            scheduler_.shutdownNow();
            // Wait a bit more for forced shutdown
            if (!scheduler_.awaitTermination(2, TimeUnit.SECONDS)) {
               ReportingUtils.logError(
                       "AnimationController scheduler did not terminate after forced shutdown");
            }
         }
      } catch (InterruptedException notUsedByUs) {
         scheduler_.shutdownNow();
         Thread.currentThread().interrupt();
      }
      ReportingUtils.logDebugMessage("Scheduler in AnimationController was shut down");
      perfMon_ = null;
   }

   /**
    * Sets the desired interval between automatically updating the displayed image.
    * Such as - for instance - while playing back time lapse movies.
    *
    * @param intervalMs Interval between displaying images.
    */
   public synchronized void setTickIntervalMs(int intervalMs) {
      if (intervalMs <= 0) {
         throw new IllegalArgumentException("interval must be positive");
      }
      if (intervalMs == tickIntervalMs_) {
         return;
      }
      if (animationEnabled_.get()) {
         if (isTicksScheduled()) {
            // Adjust the current interval to match new interval, if possible
            long remainingMs =
                  scheduledTickFuture_.getDelay(TimeUnit.MILLISECONDS);
            long newRemainingMs = Math.max(0,
                  remainingMs + intervalMs - tickIntervalMs_);

            stopTicks();
            startTicks(newRemainingMs, intervalMs);
         }
      }
      tickIntervalMs_ = intervalMs;
   }

   public synchronized int getTickIntervalMs() {
      return tickIntervalMs_;
   }

   /**
    * Sets the animation rate in frames per second.
    *
    * @param fps Rate in frames per second.
    */
   public void setAnimationRateFPS(double fps) {
      if (fps < 0.0) {
         throw new IllegalArgumentException("fps must not be negative");
      }
      animationRateFPS_.set(fps);
   }

   public synchronized double getAnimationRateFPS() {
      return animationRateFPS_.get();
   }

   /**
    * Sets the mode to be used when displaying a new Position.
    *
    * @param axis Axis to which this new mode applies.
    * @param mode Mode to be used.
    */
   public synchronized void setNewPositionHandlingMode(String axis, NewPositionHandlingMode mode) {
      if (mode == null) {
         throw new NullPointerException("mode must not be null");
      }
      newPositionModes_.put(axis, mode);
   }

   public synchronized NewPositionHandlingMode getNewPositionHandlingMode(String axis) {
      return newPositionModes_.get(axis);
   }

   /**
    * Sets the Flash Duration for the new position in ms.
    *
    * @param durationMs Duration in milli-seconds
    */
   public synchronized void setNewPositionFlashDurationMs(int durationMs) {
      if (durationMs <= 0) {
         throw new IllegalArgumentException("duration must be positive");
      }
      newPositionFlashDurationMs_ = durationMs;
   }

   public synchronized int getNewPositionFlashDurationMs() {
      return newPositionFlashDurationMs_;
   }

   /**
    * Starts the display animation.
    */
   public synchronized void startAnimation() {
      if (!animationEnabled_.compareAndSet(false, true)) {
         return;
      }
      startTicks(tickIntervalMs_, tickIntervalMs_);
   }

   /**
    * Stops the display animation.
    */
   public void stopAnimation() {
      if (!animationEnabled_.compareAndSet(true, false)) {
         return;
      }
      if (isTicksScheduled()) {
         stopTicks();
      }
   }

   public boolean isAnimating() {
      return animationEnabled_.get();
   }

   /**
    * Forces the display to the requested display position.
    *
    * @param position Position that should be displayed.
    */
   public synchronized void forceDataPosition(final P position) {
      // Always call listeners from scheduler thread
      scheduler_.schedule(new Runnable() {
         @Override
         public void run() {
            // Capture state while holding lock, then fire listeners without lock
            boolean shouldFireExpired;
            synchronized (AnimationController.this) {
               shouldFireExpired = didJumpToNewPosition_;
               if (didJumpToNewPosition_) {
                  didJumpToNewPosition_ = false;
               }
               sequencer_.setAnimationPosition(position);
            }
            // Fire listeners without holding lock to avoid deadlock
            if (shouldFireExpired) {
               listeners_.fire().animationNewDataPositionExpired();
            }
            listeners_.fire().animationShouldDisplayDataPosition(position);
         }
      }, 0, TimeUnit.MILLISECONDS);
   }

   /**
    * Sets the new position of the display.
    *
    * @param newPosition The new position to be displayed.
    */
   public synchronized void newDataPosition(final Coords newPosition) {
      // Mode may have switched since scheduling a snap-back, so cancel it here
      if (snapBackFuture_ != null) {
         snapBackFuture_.cancel(false);
         snapBackFuture_ = null;
      }

      // Cancel any pending display task to prevent queue backlog at high frame rates
      if (pendingDisplayFuture_ != null && !pendingDisplayFuture_.isDone()) {
         pendingDisplayFuture_.cancel(false);
         pendingDisplayFuture_ = null;
      }
      if (didJumpToNewPosition_) {
         didJumpToNewPosition_ = false;
         if (newDataPositionExpiredFuture_ != null) {
            newDataPositionExpiredFuture_.cancel(false);
         }
         newDataPositionExpiredFuture_ = scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               if (Thread.interrupted()) {
                  return; // Canceled
               }
               // Fire listener without holding lock to avoid deadlock
               listeners_.fire().animationNewDataPositionExpired();
            }
         }, 1000, TimeUnit.MILLISECONDS);
      }

      final Coords oldPosition = (Coords) sequencer_.getAnimationPosition();

      Coords.CoordsBuilder newDisplayPositionBuilder = newPosition.copyBuilder();
      Coords.CoordsBuilder snapBackPositionBuilder = newPosition.copyBuilder();
      boolean foundAxisToBeIgnored = false;
      boolean foundFlashBackAxis = false;
      if (oldPosition != null) {
         for (String axis : newPositionModes_.keySet()) {
            if (oldPosition.getIndex(axis) != newPosition.getIndex(axis)) {
               if (newPositionModes_.get(axis).equals(
                     NewPositionHandlingMode.IGNORE)) {
                  newDisplayPositionBuilder.index(axis, oldPosition.getIndex(axis));
                  snapBackPositionBuilder.index(axis, oldPosition.getIndex(axis));
                  foundAxisToBeIgnored = true;
               } else if (newPositionModes_.get(axis).equals(
                     NewPositionHandlingMode.FLASH_AND_SNAP_BACK)) {
                  snapBackPositionBuilder.index(axis, oldPosition.getIndex(axis));
                  foundFlashBackAxis = true;
               }
            }
         }
      }
      final Coords newDisplayPosition = newDisplayPositionBuilder.build();
      final Coords snapBackPosition = snapBackPositionBuilder.build();

      if (foundFlashBackAxis) {
         stopTicks();
         if (newDataPositionExpiredFuture_ != null) {
            newDataPositionExpiredFuture_.cancel(true);
         }
         if (snapBackFuture_ != null) {
            snapBackFuture_.cancel(false);
            snapBackFuture_ = null;
         }
         snapBackPosition_ = (P) snapBackPosition;

         // Calculate delay to rate-limit display updates to ~60 FPS
         long now = System.nanoTime();
         long timeSinceLastFire = now - lastDisplayFiredNs_;
         long delayNs = Math.max(0, MIN_DISPLAY_INTERVAL_NS - timeSinceLastFire);
         long delayMs = delayNs / 1_000_000L;

         pendingDisplayFuture_ = scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               // Clear pending future at start of execution
               synchronized (AnimationController.this) {
                  pendingDisplayFuture_ = null;
                  didJumpToNewPosition_ = true;
                  lastDisplayFiredNs_ = System.nanoTime();
               }
               // Fire listeners without holding lock to avoid deadlock
               listeners_.fire().animationAcknowledgeDataPosition(newPosition);
               listeners_.fire().animationWillJumpToNewDataPosition(newDisplayPosition);
               listeners_.fire().animationShouldDisplayDataPosition(newDisplayPosition);
               listeners_.fire().animationDidJumpToNewDataPosition(newDisplayPosition);
            }
         }, delayMs, TimeUnit.MILLISECONDS);
         snapBackFuture_ = scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               // Capture state while holding lock
               boolean shouldFireExpired;
               P capturedSnapBackPosition;
               synchronized (AnimationController.this) {
                  capturedSnapBackPosition = snapBackPosition_;
                  shouldFireExpired = false;
                  if (snapBackPosition_ != null) { // Be safe
                     if (didJumpToNewPosition_) {
                        didJumpToNewPosition_ = false;
                        shouldFireExpired = true;
                     }
                     snapBackPosition_ = null;
                  }
               }
               // Fire listeners without holding lock to avoid deadlock
               if (capturedSnapBackPosition != null) {
                  if (shouldFireExpired) {
                     listeners_.fire().animationNewDataPositionExpired();
                  }
                  // Even if we have already moved away from the new data
                  // position by other means (e.g. user click), we still
                  // snap back if we are still in snap-back mode.
                  //if (newPositionMode_ == NewPositionHandlingMode.FLASH_AND_SNAP_BACK) {
                  listeners_.fire().animationShouldDisplayDataPosition(capturedSnapBackPosition);
                  //}
               }
               // Restart ticks and clear future if animation enabled
               synchronized (AnimationController.this) {
                  if (animationEnabled_.get()) {
                     startTicks(tickIntervalMs_, tickIntervalMs_);
                  }
                  snapBackFuture_ = null;
               }
            }
         }, newPositionFlashDurationMs_, TimeUnit.MILLISECONDS);

      } else if (foundAxisToBeIgnored) {
         // Calculate delay to rate-limit display updates to ~60 FPS
         long now = System.nanoTime();
         long timeSinceLastFire = now - lastDisplayFiredNs_;
         long delayNs = Math.max(0, MIN_DISPLAY_INTERVAL_NS - timeSinceLastFire);
         long delayMs = delayNs / 1_000_000L;

         pendingDisplayFuture_ = scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               // Clear pending future and update state while holding lock
               boolean positionChanged = !newDisplayPosition.equals(oldPosition);
               synchronized (AnimationController.this) {
                  pendingDisplayFuture_ = null;
                  if (positionChanged) {
                     sequencer_.setAnimationPosition((P) newDisplayPosition);
                     didJumpToNewPosition_ = true;
                     lastDisplayFiredNs_ = System.nanoTime();
                  }
               }
               // Fire listeners without holding lock to avoid deadlock
               listeners_.fire().animationAcknowledgeDataPosition(newPosition);
               if (positionChanged) {
                  listeners_.fire().animationWillJumpToNewDataPosition(newDisplayPosition);
                  listeners_.fire().animationShouldDisplayDataPosition(newDisplayPosition);
                  listeners_.fire().animationDidJumpToNewDataPosition(newDisplayPosition);
               } else {
                  listeners_.fire().animationNewDataPositionExpired();
               }
            }
         }, delayMs, TimeUnit.MILLISECONDS);
      } else { // no axis locked
         // Calculate delay to rate-limit display updates to ~60 FPS
         long now = System.nanoTime();
         long timeSinceLastFire = now - lastDisplayFiredNs_;
         long delayNs = Math.max(0, MIN_DISPLAY_INTERVAL_NS - timeSinceLastFire);
         long delayMs = delayNs / 1_000_000L;

         pendingDisplayFuture_ = scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               // Clear pending future and update state while holding lock
               synchronized (AnimationController.this) {
                  pendingDisplayFuture_ = null;
                  sequencer_.setAnimationPosition((P) newPosition);
                  didJumpToNewPosition_ = true;
                  lastDisplayFiredNs_ = System.nanoTime();
               }
               // Fire listeners without holding lock to avoid deadlock
               listeners_.fire().animationWillJumpToNewDataPosition(newPosition);
               listeners_.fire().animationShouldDisplayDataPosition(newPosition);
               listeners_.fire().animationDidJumpToNewDataPosition(newPosition);
            }
         }, delayMs, TimeUnit.MILLISECONDS);
      }
   }

   private synchronized void startTicks(long delayMs, long intervalMs) {
      stopTicks(); // Be defensive
      lastTickNs_ = System.nanoTime();
      scheduledTickFuture_ = scheduler_.scheduleAtFixedRate(new Runnable() {
         @Override
         public void run() {
            handleTimerTick();
         }
      }, delayMs, intervalMs, TimeUnit.MILLISECONDS);
   }

   private synchronized void stopTicks() {
      if (scheduledTickFuture_ == null) {
         return;
      }
      scheduledTickFuture_.cancel(false);
      // Wait for cancellation
      try {
         scheduledTickFuture_.get();
      } catch (ExecutionException e) {
         ReportingUtils.logError(e);
      } catch (CancellationException e) {
         // nothing to do, we were waiting for this cancellation;
         ReportingUtils.logDebugMessage(
               "scheduledTickFuture task cancelled in animationController");
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
      }
      scheduledTickFuture_ = null;
   }

   private synchronized boolean isTicksScheduled() {
      return (scheduledTickFuture_ != null);
   }

   private synchronized void handleTimerTick() {
      if (scheduler_.isShutdown()) {
         return;
      }
      long thisTickNs = System.nanoTime();
      long elapsedNs = thisTickNs - lastTickNs_;
      lastTickNs_ = thisTickNs;
      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("Animation actual tick");
         perfMon_.sample("Animation tick interval setpoint (ms)", tickIntervalMs_);
      }

      double elapsedMs = ((double) elapsedNs) / 1000000.0;
      double framesToAdvance = getAnimationRateFPS() * elapsedMs / 1000.0;
      if (perfMon_ != null) {
         perfMon_.sample("Animation frames to advance at tick", framesToAdvance);
      }
      final P newPosition = sequencer_.advanceAnimationPosition(framesToAdvance);
      if (newPosition == null) { // No advancement after rounding
         return;
      }
      scheduler_.schedule(new Runnable() {
         @Override
         public void run() {
            if (perfMon_ != null) {
               perfMon_.sampleTimeInterval("Animation position for tick (run)");
            }
            // Fire listener without holding lock to avoid deadlock
            listeners_.fire().animationShouldDisplayDataPosition(newPosition);
         }
      }, 0, TimeUnit.MILLISECONDS);
   }
}