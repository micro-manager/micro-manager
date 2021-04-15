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
import org.apache.commons.lang3.event.EventListenerSupport;
import org.micromanager.data.Coords;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.internal.utils.performance.PerformanceMonitor;

/**
 * Coordinates and moderates animation of displayed data position.
 *
 * This class is responsible for timing and coordination, but not for
 * determining the sequence of images to animate.
 *
 * All events resulting in a position change ("scrolling" in user terms) pass
 * through this object, including: playback animation, new incoming images,
 * manual scrolling by the user, and events from interlinked display windows.
 *
 * The data position type {@code P} is parameterized to support possible future
 * extensions such as animation in dilated physical time for non-uniformly
 * spaced time lapse datasets.
 *
 * @param <P> the type used to describe a data position to display
 * @author Mark A. Tsuchida
 */
public final class AnimationController<P> {
   public enum NewPositionHandlingMode {
      IGNORE,
      JUMP_TO_AND_STAY,
      FLASH_AND_SNAP_BACK,
   }

   /**
    * Listeners are notified on an otherwise nonblocking thread owned by the
    * animation controller (never the EDT).
    * @param <P> For now only Coords, but parameterized to support future 
    *            extensions
   */
   public interface Listener<P> {
      void animationShouldDisplayDataPosition(P position);     
      /**
       * Update range but don't display
       * @param position Coord 
       */
      void animationAcknowledgeDataPosition(P position); 
      void animationWillJumpToNewDataPosition(P position);
      void animationDidJumpToNewDataPosition(P position);
      /**
       * What could this signify? Docs can be useful!
       */
      void animationNewDataPositionExpired();
      // TODO Need to also notify of animation start/stop
   }

   private final EventListenerSupport<Listener> listeners_ =
         new EventListenerSupport<> (Listener.class, Listener.class.getClassLoader());

   private final AnimationStateDelegate<P> sequencer_;

   private int tickIntervalMs_ = 100;
   private double animationRateFPS_ = 10.0;
   private NewPositionHandlingMode newPositionMode_ =
         NewPositionHandlingMode.JUMP_TO_AND_STAY;
   private final Map<String, NewPositionHandlingMode> newPositionModes_ = 
           new HashMap<>();
   private int newPositionFlashDurationMs_ = 500;

   private boolean animationEnabled_ = false;

   private final ScheduledExecutorService scheduler_ =
         Executors.newSingleThreadScheduledExecutor(ThreadFactoryFactory.
               createThreadFactory("AnimationController"));

   private ScheduledFuture<?> scheduledTickFuture_;
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

   public synchronized void addListener(Listener listener) {
      listeners_.addListener(listener, true);
   }

   public synchronized void removeListener(Listener listener) {
      listeners_.removeListener(listener);
   }

   public void setPerformanceMonitor(PerformanceMonitor perfMon) {
      perfMon_ = perfMon;
   }

   /**
    * Permanently cease all animation and scheduled events.
    */
   public synchronized void shutdown() {
      scheduler_.shutdown();
      stopAnimation();
      try {
         scheduler_.awaitTermination(1, TimeUnit.HOURS);
      }
      catch (InterruptedException notUsedByUs) {
         Thread.currentThread().interrupt();
      }
   }

   public synchronized void setTickIntervalMs(int intervalMs) {
      if (intervalMs <= 0) {
         throw new IllegalArgumentException("interval must be positive");
      }
      if (intervalMs == tickIntervalMs_) {
         return;
      }
      if (animationEnabled_) {
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

   public synchronized void setAnimationRateFPS(double fps) {
      if (fps < 0.0) {
         throw new IllegalArgumentException("fps must not be negative");
      }
      animationRateFPS_ = fps;
   }

   public synchronized double getAnimationRateFPS() {
      return animationRateFPS_;
   }

   public synchronized void setNewPositionHandlingMode(String axis, 
           NewPositionHandlingMode mode) {
      if (mode == null) {
         throw new NullPointerException("mode must not be null");
      }
      newPositionModes_.put(axis, mode);
   }
   
   public synchronized void setNewPositionHandlingMode(NewPositionHandlingMode mode) {
      if (mode == null) {
         throw new NullPointerException("mode must not be null");
      }
      newPositionMode_ = mode;
   }
   
   public synchronized NewPositionHandlingMode getNewPositionHandlingMode(
              String axis) {
      return newPositionModes_.get(axis);
   }

   public synchronized NewPositionHandlingMode getNewPositionHandlingMode() {
      return newPositionMode_;
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

   public synchronized void startAnimation() {
      if (animationEnabled_) {
         return;
      }
      startTicks(tickIntervalMs_, tickIntervalMs_);
      animationEnabled_ = true;
   }

   public synchronized void stopAnimation() {
      if (!animationEnabled_) {
         return;
      }
      if (isTicksScheduled()) {
         stopTicks();
      }
      animationEnabled_ = false;
   }

   public synchronized boolean isAnimating() {
      return animationEnabled_;
   }

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
      ScheduledFuture<?> newDataPositionExpiredFuture = null;
      if (didJumpToNewPosition_) {
         didJumpToNewPosition_ = false;
         newDataPositionExpiredFuture = scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               synchronized (AnimationController.this) {
                  if (Thread.interrupted()) {
                     return; // Canceled
                  }
                  listeners_.fire().animationNewDataPositionExpired();
               }
            }
         }, 0, TimeUnit.MILLISECONDS);
      }

      final Coords oldPosition = (Coords) sequencer_.getAnimationPosition();
           
      Coords.CoordsBuilder newDisplayPositionBuilder = newPosition.copyBuilder();
      Coords.CoordsBuilder snapBackPositionBuilder = newPosition.copyBuilder();
      Boolean foundAxisToBeIgnored = false;
      Boolean foundFlashBackAxis = false;
      if (oldPosition != null) {
         for (String axis : newPositionModes_.keySet()) {
         // for (String axis : newPosition.getAxes()) {
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
         if (newDataPositionExpiredFuture != null) {
            newDataPositionExpiredFuture.cancel(true);
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
                  if (animationEnabled_) {
                     startTicks(tickIntervalMs_, tickIntervalMs_);
                  }
                  snapBackFuture_ = null;
               }
            }
         }, newPositionFlashDurationMs_, TimeUnit.MILLISECONDS);

      } else if (foundAxisToBeIgnored) {
         scheduler_.schedule(new Runnable() {
            @Override
            public void run() {
               synchronized (AnimationController.this) {
                  listeners_.fire().animationAcknowledgeDataPosition(newPosition);
                  if (newDisplayPosition != oldPosition) {
                     listeners_.fire().animationWillJumpToNewDataPosition(newDisplayPosition);
                     sequencer_.setAnimationPosition((P) newDisplayPosition);
                     listeners_.fire().animationShouldDisplayDataPosition(newDisplayPosition);
                     didJumpToNewPosition_ = true;
                     listeners_.fire().animationDidJumpToNewDataPosition(newDisplayPosition);
                  }
               }
            }
         }, 0, TimeUnit.MILLISECONDS);
      } else { // no axis locked
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

      /*
      
      switch (newPositionMode_) {
         case IGNORE: // aka red locked
            scheduler_.schedule(new Runnable() {
               @Override
               public void run() {
                  synchronized (AnimationController.this) {
                     listeners_.fire().animationAcknowledgeDataPosition(newPosition);
                  }
               }
            }, 0, TimeUnit.MILLISECONDS);
            break;

         case JUMP_TO_AND_STAY: // aka unlocked
            // This mode does not make sense when animating, so fall through
            // to flash-and-snap-back mode.
            if (!isAnimating()) {
               if (newDataPositionExpiredFuture != null) {
                  newDataPositionExpiredFuture.cancel(true);
               }
               scheduler_.schedule(new Runnable() {
                  @Override
                  public void run() {
                     synchronized (AnimationController.this) {
                        listeners_.fire().animationWillJumpToNewDataPosition(newPosition);
                        sequencer_.setAnimationPosition( (P) newPosition);
                        listeners_.fire().animationShouldDisplayDataPosition(newPosition);
                        didJumpToNewPosition_ = true;
                        listeners_.fire().animationDidJumpToNewDataPosition(newPosition);
                     }
                  }
               }, 0, TimeUnit.MILLISECONDS);
               snapBackFuture_ = scheduler_.schedule(new Runnable() {
                  @Override
                  public void run() {
                     if (didJumpToNewPosition_) {
                        didJumpToNewPosition_ = false;
                        listeners_.fire().animationNewDataPositionExpired();
                     }
                  }
               }, newPositionFlashDurationMs_, TimeUnit.MILLISECONDS);
               break;
            }
            // Conditional fallthrough            // Conditional fallthrough            // Conditional fallthrough            // Conditional fallthrough

         case FLASH_AND_SNAP_BACK: // aka black locked
            stopTicks();
            if (newDataPositionExpiredFuture != null) {
               newDataPositionExpiredFuture.cancel(true);
            }
            scheduler_.schedule(new Runnable() {
               @Override
               public void run() {
                  synchronized (AnimationController.this) {
                     snapBackPosition_ = sequencer_.getAnimationPosition();
                     listeners_.fire().animationWillJumpToNewDataPosition(newPosition);
                     listeners_.fire().animationShouldDisplayDataPosition(newPosition);
                     didJumpToNewPosition_ = true;
                     listeners_.fire().animationDidJumpToNewDataPosition(newPosition);
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
                        if (newPositionMode_ == NewPositionHandlingMode.FLASH_AND_SNAP_BACK) {
                           listeners_.fire().animationShouldDisplayDataPosition(snapBackPosition_);
                        }
                        snapBackPosition_ = null;
                     }
                     if (animationEnabled_) {
                        startTicks(tickIntervalMs_, tickIntervalMs_);
                     }
                     snapBackFuture_ = null;
                  }
               }
            }, newPositionFlashDurationMs_, TimeUnit.MILLISECONDS);
            break;

         default: // Should be impossible
            throw new AssertionError(newPositionMode_);
      }
      */
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
      }
      catch (ExecutionException | CancellationException e) {
      }
      catch (InterruptedException notUsedByUs) {
         Thread.currentThread().interrupt();
      }
      scheduledTickFuture_ = null;
   }

   private synchronized boolean isTicksScheduled() {
      return (scheduledTickFuture_ != null);
   }

   private synchronized void handleTimerTick() {
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