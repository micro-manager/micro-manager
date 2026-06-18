package org.micromanager.stitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Test;

/**
 * Unit tests for {@link ParallelTileWriter}, the produce-in-parallel / consume-serially
 * pipeline extracted from the Stitch export. These run with a fake producer/consumer (no
 * NDTiff, no TileBlender) so the concurrency guarantees can be checked deterministically.
 */
public class ParallelTileWriterTest {

   /** Every item is produced and consumed exactly once; consumer is single-threaded. */
   @Test
   public void consumesEveryItemOnceSingleThreaded() throws Exception {
      final int total = 1000;
      final Set<Integer> produced = Collections.synchronizedSet(new HashSet<>());
      final Set<Integer> consumed = new HashSet<>(); // consumer is serial, so no sync needed
      final AtomicReference<Thread> consumerThread = new AtomicReference<>(null);
      final AtomicBoolean consumerConcurrent = new AtomicBoolean(false);
      final AtomicInteger consumerInFlight = new AtomicInteger(0);

      int count = ParallelTileWriter.run(total, 8,
            idx -> {
               // produce returns a small holder carrying the item index
               assertTrue("produced duplicate index " + idx, produced.add(idx));
               return Integer.valueOf(idx);
            },
            (item, consumedCount) -> {
               // Verify the consumer is only ever called from one thread, never concurrently.
               consumerThread.compareAndSet(null, Thread.currentThread());
               if (consumerThread.get() != Thread.currentThread()) {
                  fail("consumer called from a second thread");
               }
               if (consumerInFlight.incrementAndGet() != 1) {
                  consumerConcurrent.set(true);
               }
               consumed.add(item);
               consumerInFlight.decrementAndGet();
            },
            null, null);

      assertEquals("all items consumed", total, count);
      assertEquals("each produced exactly once", total, produced.size());
      assertEquals("each consumed exactly once", total, consumed.size());
      assertFalse("consumer must never run concurrently", consumerConcurrent.get());
   }

   /** A producer exception is propagated and the run does not deadlock. */
   @Test(timeout = 10000)
   public void producerErrorPropagatesWithoutDeadlock() throws Exception {
      final int total = 500;
      try {
         ParallelTileWriter.run(total, 4,
               idx -> {
                  if (idx == 123) {
                     throw new IllegalStateException("boom at 123");
                  }
                  return Integer.valueOf(idx);
               },
               (item, consumedCount) -> {
                  // small sleep so the producer error happens while the consumer is busy
                  Thread.sleep(0, 100);
               },
               null, null);
         fail("expected the producer exception to propagate");
      } catch (Exception e) {
         // The pipeline wraps the original cause.
         assertNotNull(e.getCause());
         assertTrue(e.getCause() instanceof IllegalStateException);
         assertEquals("boom at 123", e.getCause().getMessage());
      }
   }

   /** A consumer exception is propagated and the run does not deadlock. */
   @Test(timeout = 10000)
   public void consumerErrorPropagatesWithoutDeadlock() throws Exception {
      final int total = 500;
      try {
         ParallelTileWriter.run(total, 4,
               idx -> Integer.valueOf(idx),
               (item, consumedCount) -> {
                  if (consumedCount == 50) {
                     throw new IllegalStateException("consumer boom");
                  }
               },
               null, null);
         fail("expected the consumer exception to propagate");
      } catch (Exception e) {
         assertNotNull(e.getCause());
         assertTrue(e.getCause() instanceof IllegalStateException);
         assertEquals("consumer boom", e.getCause().getMessage());
      }
   }

   /** A producer Error (not just Exception) aborts the run and is reported, not swallowed. */
   @Test(timeout = 10000)
   public void producerErrorThrowablePropagates() throws Exception {
      final int total = 500;
      try {
         ParallelTileWriter.run(total, 4,
               idx -> {
                  if (idx == 77) {
                     throw new OutOfMemoryError("simulated OOM");
                  }
                  return Integer.valueOf(idx);
               },
               (item, consumedCount) -> Thread.sleep(0, 100),
               null, null);
         fail("expected the producer Error to propagate, not complete silently");
      } catch (Exception e) {
         assertNotNull(e.getCause());
         assertTrue("cause should be the Error", e.getCause() instanceof OutOfMemoryError);
      }
   }

   /** Cancellation stops the pipeline promptly without consuming everything, no deadlock. */
   @Test(timeout = 10000)
   public void cancelStopsCleanly() throws Exception {
      final int total = 100000;
      final AtomicBoolean cancel = new AtomicBoolean(false);

      int count = ParallelTileWriter.run(total, 4,
            idx -> Integer.valueOf(idx),
            (item, consumedCount) -> {
               // Request cancellation partway through.
               if (consumedCount == 25) {
                  cancel.set(true);
               }
            },
            cancel::get, null);

      // We stopped early: not everything was consumed.
      assertTrue("should have consumed at least up to the cancel point", count >= 25);
      assertTrue("cancel should stop well before the end", count < total);
   }

   /** Single worker still works (degenerate parallelism) and progress is reported 0..100. */
   @Test
   public void singleWorkerAndProgress() throws Exception {
      final int total = 200;
      final AtomicInteger lastProgress = new AtomicInteger(-1);
      final AtomicBoolean monotonic = new AtomicBoolean(true);

      int count = ParallelTileWriter.run(total, 1,
            idx -> Integer.valueOf(idx),
            (item, consumedCount) -> { },
            null,
            pct -> {
               if (pct < lastProgress.get()) {
                  monotonic.set(false);
               }
               lastProgress.set(pct);
            });

      assertEquals(total, count);
      assertEquals("final progress is 100", 100, lastProgress.get());
      assertTrue("progress is monotonic non-decreasing", monotonic.get());
   }

   /** Zero items is a no-op. */
   @Test
   public void zeroItemsIsNoOp() throws Exception {
      int count = ParallelTileWriter.run(0, 4,
            idx -> {
               throw new AssertionError("producer should not run");
            },
            (item, consumedCount) -> fail("consumer should not run"),
            null, null);
      assertEquals(0, count);
   }
}
