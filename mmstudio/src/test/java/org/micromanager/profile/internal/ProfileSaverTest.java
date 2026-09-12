package org.micromanager.profile.internal;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Assert;
import org.junit.Test;

public class ProfileSaverTest {
   @Test
   public void stopWaitsForRunningSaveAndWritesLatestState() throws Exception {
      ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
      ScheduledThreadPoolExecutor closer = new ScheduledThreadPoolExecutor(1);
      CountDownLatch started = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicInteger calls = new AtomicInteger();
      AtomicInteger state = new AtomicInteger(1);
      AtomicInteger saved = new AtomicInteger();
      ProfileSaver saver = new ProfileSaver(() -> {
         int value = state.get();
         if (calls.incrementAndGet() == 1) {
            started.countDown();
            try {
               Assert.assertTrue(release.await(5, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               throw new AssertionError(e);
            }
         }
         saved.set(value);
      }, executor);
      try {
         saver.setSaveIntervalSeconds(1);
         saver.onEvent(UserProfileChangedEvent.create());
         Assert.assertTrue(started.await(5, TimeUnit.SECONDS));
         state.set(2);
         saver.onEvent(UserProfileChangedEvent.create());
         CountDownLatch stopping = new CountDownLatch(1);
         Future<?> stopped = closer.submit(() -> {
            stopping.countDown();
            try {
               saver.stop();
            } catch (InterruptedException e) {
               Thread.currentThread().interrupt();
               throw new AssertionError(e);
            }
         });
         Assert.assertTrue(stopping.await(5, TimeUnit.SECONDS));
         Assert.assertFalse(stopped.isDone());
         release.countDown();
         stopped.get(5, TimeUnit.SECONDS);
         Assert.assertEquals(2, saved.get());
         int callsAtStop = calls.get();
         saver.onEvent(UserProfileChangedEvent.create());
         saver.syncToDisk();
         saver.stop();
         executor.shutdown();
         Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
         Assert.assertEquals(callsAtStop, calls.get());
      } finally {
         release.countDown();
         executor.shutdownNow();
         closer.shutdownNow();
      }
   }

   @Test
   public void stoppingUnmodifiedProfileDoesNotWrite() throws Exception {
      ScheduledThreadPoolExecutor executor = new ScheduledThreadPoolExecutor(1);
      AtomicInteger calls = new AtomicInteger();
      try {
         ProfileSaver saver = new ProfileSaver(calls::incrementAndGet, executor);
         saver.stop();
         saver.onEvent(UserProfileChangedEvent.create());
         saver.syncToDisk();
         Assert.assertEquals(0, calls.get());
         Assert.assertTrue(executor.getQueue().isEmpty());
      } finally {
         executor.shutdownNow();
      }
   }
}
