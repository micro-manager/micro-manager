package org.micromanager.stitch;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;

/**
 * Runs a "produce in parallel, consume serially" pipeline: N worker threads each produce
 * items (the CPU-bound work) and hand them to a bounded queue that a single consumer thread
 * (the caller) drains in queue order.
 *
 * <p>This was extracted from the Stitch export so the concurrency can be unit-tested in
 * isolation from NDTiff and {@code TileBlender}. In Stitch the producer is the per-output-tile
 * compositing and the consumer is the NDTiff write; the consumer MUST stay single-threaded
 * because NDTiff's inline pyramid build (overwritePixels on shared low-res tiles) is not
 * thread-safe.</p>
 *
 * <h2>Guarantees</h2>
 * <ul>
 *   <li>Every item index {@code [0, total)} is produced exactly once (atomic work counter),
 *       unless the run is cancelled or a producer throws first.</li>
 *   <li>The consumer is called from one thread only (the thread that calls {@link #run}),
 *       in the order items finish producing (which need not match item index order).</li>
 *   <li>No deadlock: producers poll a shared abort flag with a timed queue offer, and the
 *       consumer's finally-block sets abort + drains the queue before joining, so a producer
 *       blocked on a full queue can always complete.</li>
 *   <li>The first producer/consumer exception aborts the run and is rethrown from
 *       {@link #run} (wrapped). Items already produced before the failure may still be
 *       consumed.</li>
 * </ul>
 *
 * <p>Not reusable: construct one per run.</p>
 *
 * @param <T> the produced item type (in Stitch: a small holder of pixels + axes/tags)
 */
final class ParallelTileWriter<T> {

   /** Produces item {@code index} on a worker thread. Must be thread-safe. */
   interface Producer<T> {
      T produce(int index) throws Exception;
   }

   /** Consumes one produced item on the single consumer thread. */
   interface Consumer<T> {
      void consume(T item, int consumedCount) throws Exception;
   }

   // How long a blocked producer waits on a full queue before re-checking the abort flag.
   // Small enough that cancel/error react promptly; large enough to avoid busy-spinning.
   private static final long OFFER_POLL_MS = 200;

   private ParallelTileWriter() {
   }

   /**
    * Run the pipeline to completion (or until cancelled / a failure occurs).
    *
    * @param total      number of items to produce/consume
    * @param numWorkers number of producer threads (>= 1)
    * @param producer   produces item i on a worker thread (CPU-bound work)
    * @param consumer   consumes each produced item on the calling thread, serially
    * @param cancelled  polled by the consumer between items; when true, stop at a boundary
    * @param progress   optional 0-100 callback after each consumed item (may be null)
    * @return number of items consumed
    * @throws Exception if a producer or consumer threw, or the thread was interrupted
    */
   static <T> int run(int total, int numWorkers,
                      Producer<T> producer, Consumer<T> consumer,
                      BooleanSupplier cancelled, IntConsumer progress) throws Exception {
      if (numWorkers < 1) {
         numWorkers = 1;
      }
      if (total <= 0) {
         return 0;
      }

      final BlockingQueue<Object> queue = new ArrayBlockingQueue<>(numWorkers * 2);
      // Sentinel signalling end-of-stream to the consumer. Distinct identity, never produced.
      final Object poison = new Object();
      final AtomicInteger nextIndex = new AtomicInteger(0);
      final AtomicInteger activeWorkers = new AtomicInteger(numWorkers);
      final AtomicReference<Exception> failure = new AtomicReference<>(null);
      final AtomicBoolean abort = new AtomicBoolean(false);
      final int totalItems = total;

      Runnable workerBody = () -> {
         try {
            int idx;
            while ((idx = nextIndex.getAndIncrement()) < totalItems) {
               if (abort.get() || failure.get() != null) {
                  break;
               }
               T item = producer.produce(idx);
               // Offer with a timeout so the worker re-checks `abort` rather than blocking
               // forever on a full queue if the consumer has stopped.
               while (!abort.get()
                     && !queue.offer(item, OFFER_POLL_MS, TimeUnit.MILLISECONDS)) {
                  // retry until enqueued or aborting
               }
            }
         } catch (Exception e) {
            failure.compareAndSet(null, e);
         } finally {
            // The last worker to finish signals the consumer to stop.
            if (activeWorkers.decrementAndGet() == 0) {
               try {
                  while (!abort.get()
                        && !queue.offer(poison, OFFER_POLL_MS, TimeUnit.MILLISECONDS)) {
                     // retry until delivered or aborting
                  }
               } catch (InterruptedException ie) {
                  Thread.currentThread().interrupt();
               }
            }
         }
      };

      List<Thread> workers = new ArrayList<>(numWorkers);
      for (int i = 0; i < numWorkers; i++) {
         Thread th = new Thread(workerBody, "Stitch-composite-" + i);
         workers.add(th);
         th.start();
      }

      int consumed = 0;
      try {
         while (true) {
            if (cancelled != null && cancelled.getAsBoolean()) {
               abort.set(true);
               break;
            }
            Object item = queue.take();
            if (item == poison) {
               break;
            }
            @SuppressWarnings("unchecked")
            T typed = (T) item;
            consumer.consume(typed, consumed);
            consumed++;
            if (progress != null) {
               progress.accept(consumed * 100 / totalItems);
            }
         }
      } catch (Exception e) {
         failure.compareAndSet(null, e);
      } finally {
         // Stop workers and drain so any worker blocked offering can complete, then join.
         abort.set(true);
         queue.clear();
         for (Thread th : workers) {
            th.join();
         }
      }
      if (failure.get() != null) {
         throw new Exception("ParallelTileWriter: pipeline failed", failure.get());
      }
      return consumed;
   }
}
