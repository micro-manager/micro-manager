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
   private double animationRateFPS_ = 10.0;
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
      scheduler_.shutdown();
      stopAnimation();
      try {
         scheduler_.awaitTermination(1, TimeUnit.HOURS);
      } catch (InterruptedException notUsedByUs) {
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
   public synchronized void setAnimationRateFPS(double fps) {
      if (fps < 0.0) {
         throw new IllegalArgumentException("fps must not be negative");
      }
      animationRateFPS_ = fps;
   }

   public synchronized double getAnimationRateFPS() {
      return animationRateFPS_;
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
      if (animationEnabled_.compareAndSet(true, true)) {
         return;
      }
      startTicks(tickIntervalMs_, tickIntervalMs_);
   }

   /**
    * Stops the display animation.
    */
   public void stopAnimation() {
      if (animationEnabled_.compareAndSet(false, false)) {
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
            synchronized (AnimationController.this) {
               if (didJumpToNewPosition_) {
                  didJumpToNewPosition_ = false;
                  listeners_.fire().animationNewDataPositionExpired();
               }
               sequencer_.setAnimationPosition(position);
               listeners_.fire().animationShouldDisplayDataPosition(position);
            }
         }
      }, 0, TimeUnit.MILLISECONDS);
   }

   public synchronized void newDataPosition(final Coords newPosition) {
      // Mode may have switched since scheduling a snap-back, so cancel it here
      if (snapBackFuture_ != null) {
         snapBackFuture_.cancel(false);
         snapBackFuture_ = null;
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
               synchronized (AnimationController.this) {
                  listeners_.fire().animationNewDataPositionExpired();
               }
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
               }
               else if (newPositionModes_.get(axis).equals(
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
         scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               synchronized (AnimationController.this) {
                  listeners_.fire().animationAcknowledgeDataPosition(newPosition);
                  listeners_.fire().animationWillJumpToNewDataPosition(newDisplayPosition);
                  listeners_.fire().animationShouldDisplayDataPosition(newDisplayPosition);
                  didJumpToNewPosition_ = true;
                  listeners_.fire().animationDidJumpToNewDataPosition(newDisplayPosition);
               }
            }
         }, 0, TimeUnit.MILLISECONDS);
         snapBackFuture_ = scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               synchronized (AnimationController.this) {
                  if (snapBackPosition_ != null) { // Be safe
                     if (didJumpToNewPosition_) {
                        didJumpToNewPosition_ = false;
                        listeners_.fire().animationNewDataPositionExpired();
                     }
                     // Even if we have already moved away from the new data
                     // position by other means (e.g. user click), we still
                     // snap back if we are still in snap-back mode.
                     //if (newPositionMode_ == NewPositionHandlingMode.FLASH_AND_SNAP_BACK) {
                     listeners_.fire().animationShouldDisplayDataPosition(snapBackPosition_);
                     //}
                     snapBackPosition_ = null;
                  }
                  if (animationEnabled_.get()) {
                     startTicks(tickIntervalMs_, tickIntervalMs_);
                  }
                  snapBackFuture_ = null;
               }
            }
         }, newPositionFlashDurationMs_, TimeUnit.MILLISECONDS);

      }
      else if (foundAxisToBeIgnored) {
         scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               synchronized (AnimationController.this) {
                  listeners_.fire().animationAcknowledgeDataPosition(newPosition);
                  if (!newDisplayPosition.equals(oldPosition)) {
                     listeners_.fire().animationWillJumpToNewDataPosition(newDisplayPosition);
                     sequencer_.setAnimationPosition((P) newDisplayPosition);
                     listeners_.fire().animationShouldDisplayDataPosition(newDisplayPosition);
                     didJumpToNewPosition_ = true;
                     listeners_.fire().animationDidJumpToNewDataPosition(newDisplayPosition);
                  }
                  else {
                     listeners_.fire().animationNewDataPositionExpired();
                  }
               }
            }
         }, 0, TimeUnit.MILLISECONDS);
      }
      else { // no axis locked
         scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               synchronized (AnimationController.this) {
                  listeners_.fire().animationWillJumpToNewDataPosition(newPosition);
                  sequencer_.setAnimationPosition((P) newPosition);
                  listeners_.fire().animationShouldDisplayDataPosition(newPosition);
                  didJumpToNewPosition_ = true;
                  listeners_.fire().animationDidJumpToNewDataPosition(newPosition);
               }
            }
         }, 0, TimeUnit.MILLISECONDS);
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
      } catch (ExecutionException | CancellationException e) {
      } catch (InterruptedException notUsedByUs) {
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
            synchronized (AnimationController.this) {
               listeners_.fire().animationShouldDisplayDataPosition(newPosition);
            }
         }
      }, 0, TimeUnit.MILLISECONDS);
   }
}