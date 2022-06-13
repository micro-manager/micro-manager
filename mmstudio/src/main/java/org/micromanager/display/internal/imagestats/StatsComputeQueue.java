// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.display.internal.imagestats;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.apache.commons.lang3.event.EventListenerSupport;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.internal.utils.performance.PerformanceMonitor;

/**
 * Facade to manage background, rate-limited image stats computation.
 *
 * @author Mark A. Tsuchida
 */
public final class StatsComputeQueue {
   public interface Listener {
      // Returns min nanoseconds between calls
      long imageStatsReady(ImagesAndStats result);
   }

   // Guarded by monitor on this
   private final EventListenerSupport<Listener> listeners_ =
         new EventListenerSupport<>(Listener.class, Listener.class.getClassLoader());

   // Only accessed from compute executor thread
   private final ImageStatsProcessor processor_ = ImageStatsProcessor.create();

   private final ExecutorService computeExecutor_ =
         Executors.newSingleThreadExecutor(ThreadFactoryFactory
               .createThreadFactory("Stats Compute Queue Compute"));

   private final ExecutorService bypassExecutor_ =
         Executors.newSingleThreadExecutor(ThreadFactoryFactory
               .createThreadFactory("Stats Compute Queue Bypass"));

   private final ExecutorService resultExecutor_ =
         Executors.newSingleThreadExecutor(ThreadFactoryFactory
               .createThreadFactory("Stats Compute Queue Result"));

   // Outstanding compute tasks by priority
   // Guarded by monitor on this
   private final List<Future<?>> computeFutures_ =
         new ArrayList<Future<?>>();

   // Outstanding bypassed data by priority
   // Guarded by monitor on this
   private final List<Future<?>> bypassFutures_ =
         new ArrayList<Future<?>>();

   // Outstanding results by priority. We keep 2 slots per priority, to provide
   // some buffering (otherwise we'll always be as slow as downstream)
   // Guarded by monitor on this
   private final List<Deque<Future<?>>> resultFutures_ =
         new ArrayList<Deque<Future<?>>>();
   private static final int RESULT_BUFFER_SIZE = 2;

   // Serial number for each request received
   private long nextRequestSequenceNumber_ = 0;
   private long lastResultSequenceNumber_ = -1;

   // The "stale" stats used in lieu of real stats when update interval > 0.
   // By priority.
   // Guarded by monitor on this
   private final List<ImagesAndStats> storedStats_ =
         new ArrayList<ImagesAndStats>();

   // Guarded by monitor on this
   private long updateIntervalNs_ = 0;

   // Guarded by monitor on this
   private long nextStatsReadyCallAllowedNs_ = 0;

   private PerformanceMonitor perfMon_;


   public static StatsComputeQueue create() {
      return new StatsComputeQueue();
   }

   private StatsComputeQueue() {
   }

   public void setPerformanceMonitor(PerformanceMonitor perfMon) {
      processor_.setPerformanceMonitor(perfMon);
      perfMon_ = perfMon;
   }

   public synchronized void addListener(Listener listener) {
      listeners_.addListener(listener, true);
   }

   public synchronized void removeListener(Listener listener) {
      listeners_.removeListener(listener);
   }

   public synchronized void shutdown() throws InterruptedException {
      processor_.shutdown();
      computeExecutor_.shutdown();
      bypassExecutor_.shutdown();
      resultExecutor_.shutdown();
      perfMon_ = null;
   }

   public synchronized void submitRequest(ImageStatsRequest request) {
      long sequenceNumber = nextRequestSequenceNumber_++;
      long nowNs = System.nanoTime();
      int priority = request.getNumberOfImages();

      if (updateIntervalNs_ < Long.MAX_VALUE) {
         final long waitTargetNs = updateIntervalNs_ == Long.MAX_VALUE
               ? Long.MAX_VALUE :
                     nowNs + updateIntervalNs_ - nowNs % Math.max(1, updateIntervalNs_);

         submitCompute(sequenceNumber, priority, request, waitTargetNs);
         if (perfMon_ != null) {
            perfMon_.sampleTimeInterval("Compute submitted");
         }
      }
      if (updateIntervalNs_ > 0) {
         submitBypass(sequenceNumber, priority, request);
         if (perfMon_ != null) {
            perfMon_.sampleTimeInterval("Bypass submitted");
         }
      }
   }

   private void submitCompute(final long sequenceNumber, final int priority,
                              final ImageStatsRequest request, final long waitTargetNs) {
      while (computeFutures_.size() <= priority) {
         computeFutures_.add(null);
      }
      for (int p = priority; p >= 0; --p) {
         if (computeFutures_.get(p) != null) {
            computeFutures_.get(p).cancel(true);
         }
      }
      computeFutures_.set(priority, computeExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            // Interruptible wait for the next 'tick'
            long nowNs = System.nanoTime();
            if (waitTargetNs > nowNs) {
               long waitNs = waitTargetNs - nowNs;
               try {
                  if (perfMon_ != null) {
                     perfMon_.sample("Compute pre-delay (ms)", waitNs / 1000000);
                  }
                  Thread.sleep(waitNs / 1000000L, (int) (waitNs % 1000000L));
               } catch (InterruptedException cancel) {
                  if (perfMon_ != null) {
                     perfMon_.sampleTimeInterval("Compute pre-delay interrupted");
                  }
                  return;
               }
            }

            final ImagesAndStats result;
            try {
               result = processor_.process(sequenceNumber, request, false);
            } catch (InterruptedException shouldNotHappen) {
               Thread.currentThread().interrupt();
               if (perfMon_ != null) {
                  perfMon_.sampleTimeInterval("Compute interrupted (!)");
               }
               return;
            }
            if (perfMon_ != null) {
               perfMon_.sampleTimeInterval("Compute submitting result");
            }
            synchronized (StatsComputeQueue.this) {
               submitResult(sequenceNumber, priority, result);

               while (storedStats_.size() <= priority) {
                  storedStats_.add(null);
               }
               for (int p = priority; p >= 0; --p) {
                  storedStats_.set(p, null);
               }
               storedStats_.set(priority, result);
            }
         }
      }));
   }

   private void submitBypass(final long sequenceNumber, final int priority,
                             final ImageStatsRequest request) {
      while (bypassFutures_.size() <= priority) {
         bypassFutures_.add(null);
      }
      for (int p = priority; p >= 0; --p) {
         if (bypassFutures_.get(p) != null) {
            bypassFutures_.get(p).cancel(false);
         }
      }
      bypassFutures_.set(priority, bypassExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            ImagesAndStats storedStats = null;
            synchronized (StatsComputeQueue.this) {
               for (int p = storedStats_.size() - 1; p >= 0; --p) {
                  storedStats = storedStats_.get(p);
                  if (storedStats != null) {
                     break;
                  }
               }
            }
            final ImagesAndStats result;
            if (storedStats == null) {
               result = ImagesAndStats.create(-1, request);
            } else {
               result = storedStats.copyForRequest(request);
            }

            if (perfMon_ != null) {
               perfMon_.sampleTimeInterval("Compute submitting bypass result");
            }
            submitResult(sequenceNumber, priority, result);
         }
      }));
   }

   private synchronized void submitResult(long sequenceNumber,
                                          final int priority, final ImagesAndStats result) {
      if (sequenceNumber < lastResultSequenceNumber_ && result.isRealStats()) {
         // Prevent late-arriving stats from causing animation to retrogress.
         // If this result contains new stats, it will still be applied to the
         // stored stats to be applied to subsequent bypassed requests.
         if (perfMon_ != null) {
            perfMon_.sampleTimeInterval("Compute result discarded (retro seq nr)");
         }
         return;
      } else if (sequenceNumber <= lastResultSequenceNumber_ && !result.isRealStats()) {
         // In the event that the bypasses images arrive after
         // the computed stats for the same request, discard.
         if (perfMon_ != null) {
            perfMon_.sampleTimeInterval("Compute bypass discarded (retro seq nr)");
         }
         return;
      } else {
         lastResultSequenceNumber_ = sequenceNumber;
      }

      while (resultFutures_.size() <= priority) {
         resultFutures_.add(new ArrayDeque<Future<?>>(RESULT_BUFFER_SIZE));
      }
      for (int p = priority; p >= 0; --p) {
         Deque<Future<?>> buffer = resultFutures_.get(p);
         while (buffer.peekFirst() != null && buffer.peekFirst().isDone()) {
            buffer.removeFirst();
         }
         if (p == priority && perfMon_ != null) {
            perfMon_.sample("Compute result queue size at priority " + priority,
                  resultFutures_.get(priority).size());
         }
         if (buffer.size() == RESULT_BUFFER_SIZE) { // full, discard oldest
            buffer.removeFirst().cancel(false);
         }
      }
      resultFutures_.get(priority).addLast(resultExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            long waitNs;
            synchronized (StatsComputeQueue.this) {
               waitNs = nextStatsReadyCallAllowedNs_ - System.nanoTime();
            }
            if (perfMon_ != null) {
               perfMon_.sample("Compute result pre-wait (ms)", Math.max(0, waitNs / 1000000));
            }
            try {
               if (waitNs > 0) {
                  Thread.sleep(waitNs / 1000000, (int) (waitNs % 1000000));
               }
            } catch (InterruptedException unexpected) {
            }

            synchronized (StatsComputeQueue.this) {
               long intervalNs = listeners_.fire().imageStatsReady(result);
               nextStatsReadyCallAllowedNs_ = System.nanoTime() + intervalNs;
            }
         }
      }));
   }

   public synchronized void setProcessIntervalNs(long intervalNs) {
      updateIntervalNs_ = Math.max(0, intervalNs);
   }

   public synchronized long getProcessIntervalNs() {
      return updateIntervalNs_;
   }
}