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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

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

   // Listeners are notified on an otherwise nonblocking thread owned by the
   // animation controller (never the EDT).
   public interface Listener<P> {
      void animationShouldDisplayDataPosition(P position);
      void animationAcknowledgeDataPosition(P position); // Update range but don't display
      void animationWillJumpToNewDataPosition(P position);
      void animationDidJumpToNewDataPosition(P position);
      void animationWillSnapBackFromNewDataPosition();
      void animationDidSnapBackFromNewDataPosition();
      // TODO Need to also notify of animation start/stop
   }

   private final List<Listener> listeners_ = new ArrayList<Listener>();

   private final AnimationStateDelegate<P> sequencer_;

   private int tickIntervalMs_ = 17;
   private double animationRateFPS_ = 30.0;
   private NewPositionHandlingMode newPositionMode_ =
         NewPositionHandlingMode.JUMP_TO_AND_STAY;
   private int newPositionFlashDurationMs_ = 500;

   private boolean animationEnabled_ = false;

   private final ScheduledExecutorService scheduler_ =
         Executors.newSingleThreadScheduledExecutor(ThreadFactoryFactory.
               createThreadFactory("AnimationController"));

   private ScheduledFuture<?> scheduledTickFuture_;
   private long lastTickNs_;

   private ScheduledFuture<?> snapBackFuture_;
   private P snapBackPosition_;

   public static <P> AnimationController create(AnimationStateDelegate<P> sequencer) {
      return new AnimationController(sequencer);
   }

   private AnimationController(AnimationStateDelegate<P> sequencer) {
      sequencer_ = sequencer;
   }

   public synchronized void addListener(Listener listener) {
      listeners_.add(listener);
   }

   public synchronized boolean removeListener(Listener listener) {
      return listeners_.remove(listener);
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

   public synchronized void setNewPositionHandlingMode(NewPositionHandlingMode mode) {
      if (mode == null) {
         throw new NullPointerException("mode must not be null");
      }
      newPositionMode_ = mode;
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
               for (Listener<P> l : listeners_) {
                  l.animationShouldDisplayDataPosition(position);
               }
            }
         }
      }, 0, TimeUnit.MILLISECONDS);
   }

   public synchronized void newDataPosition(final P position) {
      switch (newPositionMode_) {
         case IGNORE:
            scheduler_.schedule(new Runnable() {
               @Override
               public void run() {
                  synchronized (AnimationController.this) {
                     for (Listener<P> l : listeners_) {
                        l.animationAcknowledgeDataPosition(position);
                     }
                  }
               }
            }, 0, TimeUnit.MILLISECONDS);
            break;

         case JUMP_TO_AND_STAY:
            stopAnimation();
            scheduler_.schedule(new Runnable() {
               @Override
               public void run() {
                  synchronized (AnimationController.this) {
                     for (Listener<P> l : listeners_) {
                        l.animationWillJumpToNewDataPosition(position);
                        l.animationShouldDisplayDataPosition(position);
                        l.animationDidJumpToNewDataPosition(position);
                     }
                  }
               }
            }, 0, TimeUnit.MILLISECONDS);
            break;

         case FLASH_AND_SNAP_BACK:
            stopTicks();
            if (snapBackFuture_ != null) {
               snapBackFuture_.cancel(false);
               snapBackFuture_ = null;
            }
            if (snapBackPosition_ == null) {
               snapBackPosition_ = sequencer_.getAnimationPosition();
            }
            scheduler_.schedule(new Runnable() {
               @Override
               public void run() {
                  synchronized (AnimationController.this) {
                     for (Listener<P> l : listeners_) {
                        l.animationWillJumpToNewDataPosition(position);
                        l.animationShouldDisplayDataPosition(position);
                        l.animationDidJumpToNewDataPosition(position);
                     }
                  }
               }
            }, 0, TimeUnit.MILLISECONDS);
            snapBackFuture_ = scheduler_.schedule(new Runnable() {
               @Override
               public void run() {
                  synchronized (AnimationController.this) {
                     if (snapBackPosition_ != null) { // Be safe
                        for (Listener<P> l : listeners_) {
                           l.animationWillSnapBackFromNewDataPosition();
                           l.animationShouldDisplayDataPosition(snapBackPosition_);
                           l.animationDidSnapBackFromNewDataPosition();
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
            throw new NotImplementedException();
      }
   }

   private synchronized void startTicks(long delayMs, long intervalMs) {
      stopTicks(); // Be defensive
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
      catch (ExecutionException e) {
      }
      catch (CancellationException e) {
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

      // If the elapsed time is much shorter than expected, it means that we
      // are experiencing a pile-up of ticks. In that case, skip this one.
      // (Note that if the elapsed time is unexpectedly _long_, we have every
      // reason to process this tick and let subsequent ones be skipped if
      // necessary.)
      if (elapsedNs < (tickIntervalMs_ * 1000000L) / 2L) {
         return;
      }

      double elapsedMs = ((double) elapsedNs) / 1000000.0;
      double framesToAdvance = getAnimationRateFPS() * elapsedMs / 1000.0;
      final P newPosition = sequencer_.advanceAnimationPosition(framesToAdvance);
      scheduler_.schedule(new Runnable() {
         @Override
         public void run() {
            synchronized (AnimationController.this) {
               for (Listener<P> l : listeners_) {
                  l.animationShouldDisplayDataPosition(newPosition);
               }
            }
         }
      }, 0, TimeUnit.MILLISECONDS);
   }
}