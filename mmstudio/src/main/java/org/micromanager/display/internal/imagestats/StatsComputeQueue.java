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

import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionService;
import java.util.concurrent.ExecutorCompletionService;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.internal.utils.performance.PerformanceMonitor;

/**
 * Facade to manage background, rate-limited image stats computation.
 * @author Mark A. Tsuchida
 */
public final class StatsComputeQueue {
   private final ImageStatsProcessor processor_ = ImageStatsProcessor.create();
   private final ThrottledProcessQueue<ImageStatsRequest, ImagesAndStats>
         processQueue_ = ThrottledProcessQueue.create(processor_);
   private final BoundedOverridingPriorityQueue<ImageStatsRequest>
         bypassQueue_ = BoundedOverridingPriorityQueue.create();

   private final ExecutorService multiWaitExecutor_;

   private ImagesAndStats storedStats_;

   private long processIntervalNs_ = 0;

   private boolean shutdown_;


   public static StatsComputeQueue create() {
      return new StatsComputeQueue();
   }

   private StatsComputeQueue() {
      multiWaitExecutor_ = new ThreadPoolExecutor(0, Integer.MAX_VALUE,
            60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
            ThreadFactoryFactory.createThreadFactory("StatsComputeQueue"));
   }

   public void setPerformanceMonitor(PerformanceMonitor perfMon) {
      processor_.setPerformanceMonitor(perfMon);
   }

   public synchronized void start() {
      if (shutdown_) {
         throw new IllegalStateException();
      }
      processQueue_.start(processIntervalNs_);
   }

   public synchronized void stop() throws InterruptedException {
      if (shutdown_) {
         return;
      }
      processQueue_.stop();
   }

   public synchronized void shutdown() throws InterruptedException {
      multiWaitExecutor_.shutdown();
      processQueue_.stop();
      bypassQueue_.clear();
      shutdown_ = true;
   }

   public void submitRequest(ImageStatsRequest request)
   {
      // We must not acquire the monitor on this here; that would deadlock with
      // any thread waiting to retrieve a result.
      // We need not check shutdown_ here, because it is harmless to submit to
      // a shut-down process queue and bypass queue.
      // If submitRequest() is called from multiple threads at the same time,
      // we could end up with items out of order (which may not be an issue),
      // but that is not an intended use of this class.
      processQueue_.submitRequest(request);
      bypassQueue_.submit(request);
   }

   public synchronized ImagesAndStats waitAndRetrieveResult()
         throws InterruptedException
   {
      if (shutdown_) {
         throw new IllegalStateException();
      }
      if (processIntervalNs_ == 0) { // No bypass
         return processQueue_.waitAndRetrieveResult();
      }

      // Processing in interval mode

      ImageStatsRequest nextRequest = null;
      if (storedStats_ == null) {
         // Always wait until the first ever stats become available.
         storedStats_ = processQueue_.waitAndRetrieveResult();
         nextRequest = bypassQueue_.retrieveIfAvailable();
      }
      else {
         // We generally retrieve the next bypassed request and combine it with
         // the last-computed stats, refreshing the latter when possible.
         // However, we also need to ensure that stats for the last-submitted
         // request eventually gets returned (so that the stats don't remain
         // stale when we go quiescent). So we need to wait on both queues at
         // the same time if neither new stats nor a bypassed request is
         // immediately available.
         ImagesAndStats newStats = null;
         for (;;) {
            newStats = processQueue_.retrieveResultIfAvailable();
            nextRequest = bypassQueue_.retrieveIfAvailable();
            if (newStats != null || nextRequest != null) {
               break;
            }
            waitForStatsOrBypass();
         }

         if (newStats != null) {
            storedStats_ = newStats;
         }
      }

      return combineStatsWithRequest(storedStats_, nextRequest);
   }

   public synchronized void setProcessIntervalNs(long intervalNs)
         throws InterruptedException
   {
      if (shutdown_) {
         return;
      }

      if (intervalNs == processIntervalNs_) {
         return;
      }

      processIntervalNs_ = Math.min(0, intervalNs);

      processQueue_.stop();
      processQueue_.start(processIntervalNs_);

      // We need to ensure that the last-submitted request is always eventually
      // processed. If there were any unretrieved items when we entered this
      // method, they are still in the bypass queue, so we re-submit them.
      List<ImageStatsRequest> requests = bypassQueue_.drain();
      Collections.reverse(requests); // Resubmit in high-to-low priority order
      for (ImageStatsRequest request : requests) {
         submitRequest(request);
      }
   }

   private void waitForStatsOrBypass() throws InterruptedException {
      CompletionService<Void> completion =
            new ExecutorCompletionService<Void>(multiWaitExecutor_);
      Future<Void> waitForStats = completion.submit(new Callable<Void>() {
         @Override
         public Void call() throws InterruptedException {
            processQueue_.awaitResult();
            return null;
         }
      });
      Future<Void> waitForBypass = completion.submit(new Callable<Void>() {
         @Override
         public Void call() throws InterruptedException {
            bypassQueue_.await();
            return null;
         }
      });
      completion.take();
      waitForStats.cancel(true);
      waitForBypass.cancel(true);
   }

   private ImagesAndStats combineStatsWithRequest(ImagesAndStats stats,
         ImageStatsRequest request)
   {
      if (request == null || stats.getRequest() == request) {
         return stats;
      }
      return stats.copyForRequest(request);
   }
}