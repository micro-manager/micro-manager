// Copyright (C) 2016-7 Open Imaging, Inc.
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

/**
 * Run compute tasks in the background, skipping based on the rate of output
 * consumption and/or limiting processing to once per time interval.
 *
 * This is basically a single-threaded background work queue, but has special
 * priority semantics designed specifically for the purpose of computing image
 * statistics (histogram, etc.) for display.
 *
 * Items submitted to the queue have an associated priority. The priority is
 * a small, nonnegative integer, and greater values are considered higher
 * priority. Lower priorities increase the chance of the item being skipped
 * when the queue or its consumer is not catching up with incoming requests.
 *
 * Submission of work items (requests; input data) to the queue is always non-
 * blocking. If requests are submitted at a higher rate than processing, a new
 * request will replace any older, pending requests of equal or lower priority.
 *
 * Processing occurs in one of two modes, specified by a zero vs nonzero
 * interval setting. When the processing interval is zero, processing proceeds
 * as frequently as possible, except when an item with the highest priority
 * so far encountered has been processed, in which case processing pauses until
 * that result has been retrieved by the consumer. This ensures that higher-
 * priority items selectively pass through the queue when the consumer cannot
 * keep up with all items. It also means that a stale result will be obtained
 * if the consumer is much slower than the processing, but the delay is at most
 * comparable to the consumption rate, so should not be a major issue.
 *
 * The second mode of operation is specified by a positive interval setting. In
 * this mode, processing of an item occurs at most once per given interval, and
 * is otherwise only limited by the time taken by the processing. The rate of
 * result consumption does not affect processing rate, and prioritization only
 * occurs at the request submission stage (see above). This mode is intended
 * for use when it is desirable to limit CPU consumption by the processing.
 *
 * The actual processor may perform whatever computation it desires, but must
 * preserve the priority of the request and assign the same priority to the
 * result item. Behavior is undefined if the processor emits results whose
 * priority differs from the corresponding requests.
 *
 * @param <I> the type of requests, i.e. input to the processor
 * @param <O> the type of results, i.e. output from the processor
 *
 * @author Mark A. Tsuchida
 */
public final class ThrottledProcessQueue<
      I extends BoundedPriorityElement,
      O extends BoundedPriorityElement>
{
   /**
    * Processing to be performed on data items.
    *
    * @param <I> the type of input (request)
    * @param <O> the type of output (result)
    */
   public static interface Processor<I, O> {
      /**
       * Process a request and return a result.
       *
       * @param request the request object
       * @return the result, whose priority must match that of {@code request}
       * @throws java.lang.InterruptedException if an interrupt is detected on
       * the current thread while processing
       */
      public O process(I request) throws InterruptedException;
   }

   private final BoundedOverridingPriorityQueue<I> requestQueue_ =
         BoundedOverridingPriorityQueue.create();
   private final BoundedOverridingPriorityQueue<O> resultQueue_ =
         BoundedOverridingPriorityQueue.create();
   private final Processor<I, O> processor_;
   private Thread computeThread_; // Non-null when started; null when stopped

   /**
    * Create a new throttled processing queue.
    * @param <In> the type of processing input items (requests)
    * @param <Out> the type of processing output items (results)
    * @param processor the processing to perform on items
    * @return the new processing queue
    */
   public static <In extends BoundedPriorityElement, Out extends BoundedPriorityElement>
       ThrottledProcessQueue<In, Out> create(Processor<In, Out> processor)
   {
      return new ThrottledProcessQueue<In, Out>(processor);
   }

   private ThrottledProcessQueue(Processor<I, O> processor) {
      processor_ = processor;
   }

   /**
    * Submit a request to the processing queue.
    * @param request the input item
    */
   public void submitRequest(I request) {
      requestQueue_.submit(request);
   }

   public void awaitResult() throws InterruptedException {
      resultQueue_.await();
   }

   public O retrieveResultIfAvailable() {
      return resultQueue_.retrieveIfAvailable();
   }

   /**
    * Retrieve a result item from the processing queue, blocking until
    * available.
    * @return the output item
    * @throws InterruptedException if the current thread is interrupted while
    * blocking
    */
   public O waitAndRetrieveResult() throws InterruptedException {
      return resultQueue_.waitAndRetrieve();
   }

   /**
    * Activate the processing queue.
    * @param intervalNs the processing interval in nanoseconds (see the class
    * documentation for details)
    */
   public synchronized void start(final long intervalNs) {
      if (computeThread_ != null) {
         return;
      }
      if (intervalNs == 0) {
         computeThread_ = new Thread("Display Image Processor") {
            @Override
            public void run() {
               asRetrieved();
            }
         };
      }
      else if (intervalNs > 0) {
         computeThread_ = new Thread("Display Image Processor") {
            @Override
            public void run() {
               atTimeInterval(intervalNs);
            }
         };
      }
      if (computeThread_ != null) {
         computeThread_.start();
      }
   }

   /**
    * Deactivate the processing queue.
    * Any pending results are cleared before returning from this method. Note
    * that the queue continues to accept request submissions even when stopped.
    * @throws java.lang.InterruptedException if the current thread is
    * interrupted while waiting for processing to finish.
    */
   public synchronized void stop() throws InterruptedException {
      if (computeThread_ == null) {
         return;
      }
      computeThread_.interrupt();
      computeThread_.join();
      resultQueue_.clear();
   }

   private void asRetrieved() {
      try {
         for (;;) {
            I request = requestQueue_.waitAndRetrieve();
            O result = processor_.process(request);
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
            resultQueue_.submitWaitingToReplaceHighestEverPriority(result);
         }
      }
      catch (InterruptedException exitRequested) {
      }
   }

   @SuppressWarnings("SleepWhileInLoop") // We sleep for throttling
   private void atTimeInterval(long intervalNs) {
      long nextTime_ = 0;
      try {
         for (;;) {
            long waitNs = nextTime_ - System.nanoTime();
            if (waitNs > 0) {
               Thread.sleep(waitNs / 1000000, (int) (waitNs % 1000000));
            }
            nextTime_ = System.nanoTime() + intervalNs;

            I request = requestQueue_.waitAndRetrieve();
            O result = processor_.process(request);
            if (Thread.interrupted()) {
               throw new InterruptedException();
            }
            resultQueue_.submit(result);
         }
      }
      catch (InterruptedException exitRequested) {
      }
   }
}