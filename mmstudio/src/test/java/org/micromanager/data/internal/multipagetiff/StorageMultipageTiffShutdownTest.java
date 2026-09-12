package org.micromanager.data.internal.multipagetiff;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.internal.utils.ThreadFactoryFactory;

/** Tests that finishing and closing cannot abandon queued TIFF writes. */
public class StorageMultipageTiffShutdownTest {
   @Rule
   public TemporaryFolder temporaryFolder = new TemporaryFolder();

   @Test
   public void metadataFailureStillDrainsWritesAndPreservesInterrupt() throws Exception {
      DefaultDatastore store = new DefaultDatastore(null);
      StorageMultipageTiff storage = new StorageMultipageTiff(null, store,
            temporaryFolder.getRoot().toPath().resolve("data").toString(), true, false, false);
      ThreadPoolExecutor executor = installExecutor(storage);
      CountDownLatch writing = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicBoolean written = new AtomicBoolean();
      AtomicBoolean interrupted = new AtomicBoolean();
      executor.execute(() -> {
         writing.countDown();
         await(release);
         written.set(true);
      });
      Thread finishing = new Thread(() -> {
         // No OME metadata exists: finished() takes its IOException path.
         storage.finished();
         interrupted.set(Thread.currentThread().isInterrupted());
      });
      try {
         Assert.assertTrue(writing.await(5, TimeUnit.SECONDS));
         finishing.start();
         awaitShutdown(executor);
         finishing.interrupt();
         finishing.join(100);
         Assert.assertTrue("An interrupt must not abandon pending writes", finishing.isAlive());
         release.countDown();
         finishing.join(5000);
         Assert.assertFalse(finishing.isAlive());
         Assert.assertTrue(written.get());
         Assert.assertTrue(interrupted.get());
         Assert.assertTrue(executor.isTerminated());
      } finally {
         release.countDown();
         finishing.join(5000);
         storage.close();
         store.close();
      }
   }

   @Test
   public void closeWaitsForAlreadyShutDownExecutor() throws Exception {
      DefaultDatastore store = new DefaultDatastore(null);
      StorageMultipageTiff storage = new StorageMultipageTiff(null, store,
            temporaryFolder.getRoot().toPath().resolve("data").toString(), true, false, false);
      ThreadPoolExecutor executor = installExecutor(storage);
      CountDownLatch writing = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch closing = new CountDownLatch(1);
      AtomicBoolean written = new AtomicBoolean();
      executor.execute(() -> {
         writing.countDown();
         await(release);
         written.set(true);
      });
      executor.shutdown();
      Thread closer = new Thread(() -> {
         closing.countDown();
         storage.close();
      });
      try {
         Assert.assertTrue(writing.await(5, TimeUnit.SECONDS));
         closer.start();
         Assert.assertTrue(closing.await(5, TimeUnit.SECONDS));
         closer.join(100);
         Assert.assertTrue("close must wait for accepted writes", closer.isAlive());
         release.countDown();
         closer.join(5000);
         Assert.assertFalse(closer.isAlive());
         Assert.assertTrue(written.get());
         Assert.assertTrue(executor.isTerminated());
      } finally {
         release.countDown();
         closer.join(5000);
         storage.close();
         store.close();
      }
   }

   private static ThreadPoolExecutor installExecutor(StorageMultipageTiff storage)
         throws Exception {
      ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            ThreadFactoryFactory.createNonDaemonThreadFactory("TiffShutdownTest"));
      Field field = StorageMultipageTiff.class.getDeclaredField("writingExecutor_");
      field.setAccessible(true);
      field.set(storage, executor);
      return executor;
   }

   private static void awaitShutdown(ThreadPoolExecutor executor) throws InterruptedException {
      long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
      while (!executor.isShutdown() && System.nanoTime() < deadline) {
         Thread.sleep(1);
      }
      Assert.assertTrue("finishing must shut down even after a metadata error",
            executor.isShutdown());
   }

   private static void await(CountDownLatch latch) {
      try {
         if (!latch.await(10, TimeUnit.SECONDS)) {
            throw new AssertionError("Test did not release pending write");
         }
      } catch (InterruptedException e) {
         Thread.currentThread().interrupt();
         throw new AssertionError(e);
      }
   }
}
