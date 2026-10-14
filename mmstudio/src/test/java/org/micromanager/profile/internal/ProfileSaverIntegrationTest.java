package org.micromanager.profile.internal;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Test;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;

public class ProfileSaverIntegrationTest {
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
   public void closeSavesLatestRealProfileSnapshotToFile() throws Exception {
      File file = Files.createTempFile("profile-saver", ".json").toFile();
      DefaultUserProfile profile = DefaultUserProfile.create(null,
            UUID.randomUUID(), PropertyMaps.emptyPropertyMap());
      ScheduledThreadPoolExecutor executor = executor();
      ScheduledThreadPoolExecutor closer = executor();
      CountDownLatch firstSnapshot = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicInteger writes = new AtomicInteger();
      ProfileSaver saver = ProfileSaver.create(profile, () -> {
         PropertyMap snapshot = profile.toPropertyMap();
         if (writes.incrementAndGet() == 1) {
            firstSnapshot.countDown();
            await(release);
         }
         snapshot.saveJSON(file, true, false);
      }, error -> {
         throw new AssertionError(error);
      }, executor);
      profile.setSaver(saver);
      boolean closed = false;
      try {
         saver.setSaveIntervalSeconds(1);
         profile.getSettings(getClass()).putInteger("value", 1);
         await(firstSnapshot);
         profile.getSettings(getClass()).putInteger("value", 2);
         Future<?> close = closer.submit(() -> {
            try {
               profile.close();
            } catch (InterruptedException e) {
               throw new AssertionError(e);
            }
         });
         try {
            close.get(200, TimeUnit.MILLISECONDS);
            closed = true;
            Assert.fail("Profile close returned while the old snapshot was still writing");
         } catch (TimeoutException expected) {
            // The close must wait for the first write, then write the latest state.
         }
         release.countDown();
         close.get(5, TimeUnit.SECONDS);
         closed = true;
         executor.shutdown();
         Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
         Assert.assertEquals(2, PropertyMaps.loadJSON(file)
               .getPropertyMap(getClass().getCanonicalName(), PropertyMaps.emptyPropertyMap())
               .getInteger("value", -1));
         profile.getSettings(getClass()).putInteger("value", 3);
         Assert.assertTrue(executor.getQueue().isEmpty());
         Assert.assertEquals(2, writes.get());
      } finally {
         release.countDown();
         executor.shutdownNow();
         closer.shutdownNow();
         if (!closed) {
            UserProfileMigratorImpl.unregisterForEvents(profile);
         }
         Files.deleteIfExists(file.toPath());
      }
   }

   @Test
   public void profileMutationCannotDeadlockFinalSnapshot() throws Exception {
      DefaultUserProfile profile = DefaultUserProfile.create(null,
            UUID.randomUUID(), PropertyMaps.emptyPropertyMap());
      ScheduledThreadPoolExecutor executor = executor();
      ScheduledThreadPoolExecutor workers = executor();
      CountDownLatch profileHeld = new CountDownLatch(1);
      CountDownLatch saving = new CountDownLatch(1);
      ProfileSaver saver = ProfileSaver.create(profile, () -> {
         saving.countDown();
         profile.toPropertyMap();
      }, null, executor);
      profile.setSaver(saver);
      boolean closed = false;
      try {
         profile.getSettings(getClass()).putInteger("value", 1);
         Future<?> mutation = workers.submit(() -> {
            synchronized (profile) {
               profileHeld.countDown();
               await(saving);
               profile.getSettings(getClass()).putInteger("value", 2);
            }
         });
         await(profileHeld);
         ScheduledThreadPoolExecutor closer = executor();
         try {
            Future<?> close = closer.submit(() -> {
               try {
                  profile.close();
               } catch (InterruptedException e) {
                  throw new AssertionError(e);
               }
            });
            mutation.get(5, TimeUnit.SECONDS);
            close.get(5, TimeUnit.SECONDS);
            closed = true;
         } finally {
            closer.shutdownNow();
         }
      } finally {
         saving.countDown();
         executor.shutdownNow();
         workers.shutdownNow();
         if (!closed) {
            UserProfileMigratorImpl.unregisterForEvents(profile);
         }
      }
   }

   @Test
   public void errorListenerCanAcquireLifecycleMonitorDuringClose() throws Exception {
      DefaultUserProfile profile = DefaultUserProfile.create(null,
            UUID.randomUUID(), PropertyMaps.emptyPropertyMap());
      ScheduledThreadPoolExecutor executor = executor();
      ScheduledThreadPoolExecutor closer = executor();
      CountDownLatch writing = new CountDownLatch(1);
      CountDownLatch closing = new CountDownLatch(1);
      CountDownLatch listenerFinished = new CountDownLatch(1);
      AtomicInteger writes = new AtomicInteger();
      AtomicReference<ProfileSaver> reference = new AtomicReference<>();
      ProfileSaver saver = ProfileSaver.create(profile, () -> {
         profile.toPropertyMap();
         if (writes.incrementAndGet() == 1) {
            writing.countDown();
            await(closing);
            throw new IOException("Injected autosave failure");
         }
      }, error -> {
         // setCurrentUserProfile and shutdown use this lifecycle monitor.
         synchronized (UserProfileAdmin.class) {
            try {
               reference.get().stop();
            } catch (InterruptedException e) {
               throw new AssertionError(e);
            }
            listenerFinished.countDown();
         }
      }, executor);
      reference.set(saver);
      profile.setSaver(saver);
      boolean closed = false;
      try {
         saver.setSaveIntervalSeconds(1);
         profile.getSettings(getClass()).putInteger("value", 1);
         await(writing);
         Future<?> close = closer.submit(() -> {
            synchronized (UserProfileAdmin.class) {
               closing.countDown();
               try {
                  profile.close();
               } catch (InterruptedException e) {
                  throw new AssertionError(e);
               }
            }
         });
         close.get(5, TimeUnit.SECONDS);
         closed = true;
         await(listenerFinished);
         Assert.assertEquals(2, writes.get());
      } finally {
         closing.countDown();
         executor.shutdownNow();
         closer.shutdownNow();
         if (!closed) {
            UserProfileMigratorImpl.unregisterForEvents(profile);
         }
      }
   }
}
