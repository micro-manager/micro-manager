package org.micromanager.profile.internal;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public class ProfileSaverTest {
   private static ScheduledThreadPoolExecutor executor() {
      return new ScheduledThreadPoolExecutor(1, runnable -> {
         Thread thread = new Thread(runnable);
         thread.setDaemon(true);
         return thread;
      });
   }

   private static void await(CountDownLatch latch) {
      try {
         Assert.assertTrue(latch.await(5, TimeUnit.SECONDS));
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new AssertionError(e);
      }
   }

   @Test
   public void stopWaitsForOldWriteAndPreservesLatestState() throws Exception {
      ScheduledThreadPoolExecutor executor = executor();
      ScheduledThreadPoolExecutor closer = executor();
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicInteger state = new AtomicInteger(1);
      AtomicInteger saved = new AtomicInteger();
      AtomicInteger calls = new AtomicInteger();
      AtomicBoolean interrupted = new AtomicBoolean();
      ProfileSaver saver = new ProfileSaver(() -> {
         int snapshot = state.get();
         if (calls.incrementAndGet() == 1) {
            started.countDown();
            await(release);
         }
         saved.set(snapshot);
      }, null, executor);
      try {
         saver.setSaveIntervalSeconds(1);
         saver.onEvent(UserProfileChangedEvent.create());
         await(started);
         state.set(2);
         saver.onEvent(UserProfileChangedEvent.create());
         CountDownLatch stopping = new CountDownLatch(1);
         Future<?> stopped = closer.submit(() -> {
            Thread.currentThread().interrupt();
            stopping.countDown();
            try {
               saver.stop();
               interrupted.set(Thread.currentThread().isInterrupted());
            } catch (InterruptedException e) {
               throw new AssertionError("Interrupt skipped the final save", e);
            } finally {
               Thread.interrupted();
            }
         });
         await(stopping);
         try {
            stopped.get(200, TimeUnit.MILLISECONDS);
            Assert.fail("Close returned before the running save finished");
         } catch (TimeoutException expected) {
            // A close must wait for the old snapshot before writing the final one.
         }
         release.countDown();
         stopped.get(5, TimeUnit.SECONDS);
         executor.shutdown();
         Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
         Assert.assertEquals(2, saved.get());
         Assert.assertTrue(interrupted.get());
         int finishedCalls = calls.get();
         saver.onEvent(UserProfileChangedEvent.create());
         saver.syncToDisk();
         saver.stop();
         Assert.assertEquals(finishedCalls, calls.get());
      } finally {
         release.countDown();
         executor.shutdownNow();
         closer.shutdownNow();
      }
   }

   @Test
   public void stoppingUnmodifiedProfileDoesNotWriteOrReschedule() throws Exception {
      ScheduledThreadPoolExecutor executor = executor();
      AtomicInteger calls = new AtomicInteger();
      try {
         ProfileSaver saver = new ProfileSaver(calls::incrementAndGet, null, executor);
         saver.stop();
         saver.onEvent(UserProfileChangedEvent.create());
         saver.syncToDisk();
         Assert.assertEquals(0, calls.get());
         Assert.assertTrue(executor.getQueue().isEmpty());
      } finally {
         executor.shutdownNow();
      }
   }

   @Test
   public void runtimeFailureAllowsCloseRetry() throws Exception {
      ScheduledThreadPoolExecutor executor = executor();
      AtomicInteger calls = new AtomicInteger();
      try {
         ProfileSaver saver = new ProfileSaver(() -> {
            if (calls.incrementAndGet() == 1) {
               throw new IllegalStateException("Write failed before completing");
            }
         }, null, executor);
         saver.onEvent(UserProfileChangedEvent.create());
         try {
            saver.stop();
            Assert.fail("Expected write failure");
         } catch (IllegalStateException expected) {
            Assert.assertEquals("Write failed before completing", expected.getMessage());
         }
         saver.stop();
         saver.stop();
         Assert.assertEquals(2, calls.get());
      } finally {
         executor.shutdownNow();
      }
   }

   @Test
   public void finalIoFailureIsReportedOnceAndStopRemainsTerminal() throws Exception {
      ScheduledThreadPoolExecutor executor = executor();
      AtomicInteger errors = new AtomicInteger();
      try {
         ProfileSaver saver = new ProfileSaver(() -> {
            throw new IOException("Disk full");
         }, error -> {
            Assert.assertEquals("Disk full", error.getMessage());
            errors.incrementAndGet();
         }, executor);
         saver.onEvent(UserProfileChangedEvent.create());
         saver.stop();
         saver.stop();
         saver.onEvent(UserProfileChangedEvent.create());
         saver.syncToDisk();
         Assert.assertEquals(1, errors.get());
      } finally {
         executor.shutdownNow();
      }
   }
}
