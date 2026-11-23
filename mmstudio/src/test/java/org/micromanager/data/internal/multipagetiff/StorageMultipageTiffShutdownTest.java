package org.micromanager.data.internal.multipagetiff;

import java.lang.reflect.Field;
import java.nio.channels.FileChannel;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
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
         executor.shutdown();
         Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
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
         executor.shutdown();
         Assert.assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
         storage.close();
         store.close();
      }
   }

   @Test
   public void queuedImagesSurviveFreezeCloseAndReopen() throws Exception {
      String path = temporaryFolder.getRoot().toPath().resolve("images").toString();
      DefaultDatastore store = new DefaultDatastore(null);
      StorageMultipageTiff storage = new StorageMultipageTiff(
            null, store, path, true, false, false);
      store.setStorage(storage);
      store.setSummaryMetadata(new DefaultSummaryMetadata.Builder()
            .intendedDimensions(new DefaultCoords.Builder().timePoint(3).build()).build());
      byte[][] pixels = new byte[3][256];
      for (int frame = 0; frame < pixels.length; frame++) {
         for (int pixel = 0; pixel < pixels[frame].length; pixel++) {
            pixels[frame][pixel] = (byte) (pixel + frame * 37);
         }
      }
      store.putImage(image(pixels[0], 0));
      CountDownLatch writing = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      storage.getWritingExecutor().execute(() -> {
         writing.countDown();
         await(release);
      });
      AtomicBoolean closed = new AtomicBoolean();
      AtomicBoolean interrupted = new AtomicBoolean();
      Thread closer = new Thread(() -> {
         try {
            store.close(); // freezes, finalizes metadata, drains writes, then closes channels
            closed.set(true);
            interrupted.set(Thread.currentThread().isInterrupted());
         } catch (Exception e) {
            throw new AssertionError(e);
         }
      });
      try {
         Assert.assertTrue(writing.await(5, TimeUnit.SECONDS));
         store.putImage(image(pixels[1], 1));
         store.putImage(image(pixels[2], 2));
         closer.start();
         awaitShutdown(storage.getWritingExecutor());
         closer.interrupt();
         closer.join(100);
         Assert.assertFalse("close must not return while pixels are pending", closed.get());
         release.countDown();
         closer.join(5000);
         Assert.assertFalse(closer.isAlive());
         Assert.assertTrue(closed.get());
         Assert.assertTrue("Closing must preserve the caller's interrupt", interrupted.get());
      } finally {
         release.countDown();
         closer.join(5000);
         store.close();
      }
      DefaultDatastore readerStore = new DefaultDatastore(null);
      StorageMultipageTiff reader = new StorageMultipageTiff(
            null, readerStore, path, false, false, false);
      readerStore.setStorage(reader);
      try {
         for (int frame = 0; frame < pixels.length; frame++) {
            Coords coords = new DefaultCoords.Builder().timePoint(frame).build();
            Image loaded = reader.getImage(coords);
            Assert.assertNotNull("Missing frame " + frame, loaded);
            Assert.assertArrayEquals(pixels[frame], (byte[]) loaded.getRawPixels());
         }
      } finally {
         readerStore.close();
      }
   }

   @Test
   public void concurrentCloseWaitsForFreezeToFinish() throws Exception {
      String path = temporaryFolder.getRoot().toPath().resolve("concurrent").toString();
      DefaultDatastore store = new DefaultDatastore(null);
      StorageMultipageTiff storage = new StorageMultipageTiff(
            null, store, path, true, false, false);
      store.setStorage(storage);
      store.setSummaryMetadata(new DefaultSummaryMetadata.Builder()
            .intendedDimensions(new DefaultCoords.Builder().timePoint(1).build()).build());
      store.putImage(image(new byte[256], 0));
      CountDownLatch writing = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch closing = new CountDownLatch(1);
      AtomicBoolean closed = new AtomicBoolean();
      AtomicReference<Throwable> failure = new AtomicReference<>();
      storage.getWritingExecutor().execute(() -> {
         writing.countDown();
         await(release);
      });
      Thread freezer = new Thread(() -> {
         try {
            store.freeze();
         } catch (Throwable e) {
            failure.set(e);
         }
      });
      Thread closer = new Thread(() -> {
         closing.countDown();
         try {
            store.close();
            closed.set(true);
         } catch (Throwable e) {
            failure.set(e);
         }
      });
      try {
         Assert.assertTrue(writing.await(5, TimeUnit.SECONDS));
         freezer.start();
         awaitShutdown(storage.getWritingExecutor());
         Assert.assertTrue(store.isFrozen());
         closer.start();
         Assert.assertTrue(closing.await(5, TimeUnit.SECONDS));
         closer.join(100);
         Assert.assertFalse("A concurrent close must await ongoing finalization", closed.get());
         release.countDown();
         freezer.join(5000);
         closer.join(5000);
         Assert.assertFalse(freezer.isAlive());
         Assert.assertFalse(closer.isAlive());
         Assert.assertNull(failure.get());
         Assert.assertTrue(closed.get());
      } finally {
         release.countDown();
         freezer.join(5000);
         closer.join(5000);
         store.close();
      }
   }

   @Test
   public void changingPositionsDuringFreezeKeepsWriterChannelOpen() throws Exception {
      String path = temporaryFolder.getRoot().toPath().resolve("positions").toString();
      DefaultDatastore store = new DefaultDatastore(null);
      StorageMultipageTiff storage = new StorageMultipageTiff(
            null, store, path, true, false, true);
      store.setStorage(storage);
      store.setSummaryMetadata(new DefaultSummaryMetadata.Builder()
            .intendedDimensions(new DefaultCoords.Builder().timePoint(1)
                  .stagePosition(2).build()).build());
      Coords first = new DefaultCoords.Builder().timePoint(0).stagePosition(0).build();
      Coords second = new DefaultCoords.Builder().timePoint(0).stagePosition(1).build();
      store.putImage(new DefaultImage(new byte[256], 16, 16, 1, 1, first,
            new DefaultMetadata.Builder().build()));
      store.putImage(new DefaultImage(new byte[256], 16, 16, 1, 1, second,
            new DefaultMetadata.Builder().build()));
      // Both images must have left the pending-image map so reads use their readers.
      storage.getWritingExecutor().submit(() -> {
      }).get(5, TimeUnit.SECONDS);
      Field readersField = StorageMultipageTiff.class.getDeclaredField("coordsToReader_");
      readersField.setAccessible(true);
      @SuppressWarnings("unchecked")
      Map<Coords, MultipageTiffReader> readers =
            (Map<Coords, MultipageTiffReader>) readersField.get(storage);
      Field channelField = MultipageTiffReader.class.getDeclaredField("fileChannel_");
      channelField.setAccessible(true);
      FileChannel channel = (FileChannel) channelField.get(readers.get(first));
      CountDownLatch writing = new CountDownLatch(1);
      CountDownLatch release = new CountDownLatch(1);
      AtomicReference<Throwable> failure = new AtomicReference<>();
      storage.getWritingExecutor().execute(() -> {
         writing.countDown();
         await(release);
      });
      Thread freezer = new Thread(() -> {
         try {
            store.freeze();
         } catch (Throwable e) {
            failure.set(e);
         }
      });
      try {
         Assert.assertTrue(writing.await(5, TimeUnit.SECONDS));
         freezer.start();
         awaitShutdown(storage.getWritingExecutor());
         Assert.assertNotNull(storage.getImage(first));
         Assert.assertNotNull(storage.getImage(second));
         Assert.assertTrue("Reader switching must not close a writer-owned channel",
               channel.isOpen());
         release.countDown();
         freezer.join(5000);
         Assert.assertFalse(freezer.isAlive());
         Assert.assertNull(failure.get());
         // Once finalization completes, switching readers should still release channels.
         Assert.assertNotNull(storage.getImage(first));
         Assert.assertNotNull(storage.getImage(second));
         Assert.assertNull(channelField.get(readers.get(first)));
      } finally {
         release.countDown();
         freezer.join(5000);
         store.close();
      }
   }

   private static Image image(byte[] pixels, int frame) {
      return new DefaultImage(pixels, 16, 16, 1, 1,
            new DefaultCoords.Builder().timePoint(frame).build(),
            new DefaultMetadata.Builder().build());
   }

   private static ThreadPoolExecutor installExecutor(StorageMultipageTiff storage)
         throws Exception {
      ThreadPoolExecutor executor = new ThreadPoolExecutor(1, 1, 0, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(),
            ThreadFactoryFactory.createThreadFactory("TiffShutdownTest"));
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
