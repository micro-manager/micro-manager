package org.micromanager.internal.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.ref.WeakReference;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class FileSavingExecutorTest {
   @Test
   public void explicitExitRetainsScheduledExecutorWrapperDuringGarbageCollection() throws Exception {
      Path output = Files.createTempFile("mm-saving-wrapper-exit", ".txt");
      Process child = new ProcessBuilder(
            new File(System.getProperty("java.home"), "bin/java").getPath(),
            "-cp", System.getProperty("java.class.path"),
            WrapperHelper.class.getName(), output.toString()).inheritIO().start();
      try {
         assertTrue("JVM hung while checking executor retention",
               child.waitFor(10, TimeUnit.SECONDS));
         assertEquals("Registered wrapper must survive until its accepted write finishes",
               0, child.exitValue());
         assertEquals("saved", new String(Files.readAllBytes(output), "UTF-8"));
      } finally {
         child.destroyForcibly();
         child.waitFor(5, TimeUnit.SECONDS);
         Files.deleteIfExists(output);
      }
   }

   @Test
   public void explicitExitDrainsQueuedWritesFromEdt() throws Exception {
      Path output = Files.createTempFile("mm-saving-exit", ".txt");
      Process child = new ProcessBuilder(
            new File(System.getProperty("java.home"), "bin/java").getPath(),
            "-Djava.awt.headless=true", "-cp", System.getProperty("java.class.path"),
            Helper.class.getName(), output.toString()).inheritIO().start();
      try {
         assertTrue("JVM hung at exit", child.waitFor(10, TimeUnit.SECONDS));
         assertEquals(0, child.exitValue());
         assertEquals("firstlast", new String(Files.readAllBytes(output), "UTF-8"));
      } finally {
         child.destroyForcibly();
         Files.deleteIfExists(output);
      }
   }

   @Test
   public void deadlineIsSharedAndQueuedWorkIsNotCancelled() throws Exception {
      ExecutorService first = newExecutor();
      ExecutorService second = newExecutor();
      CountDownLatch release = new CountDownLatch(1);
      CountDownLatch completed = new CountDownLatch(2);
      first.submit(() -> await(release));
      second.submit(() -> await(release));
      first.submit(completed::countDown);
      second.submit(completed::countDown);
      try {
         long start = System.nanoTime();
         assertFalse(FileSavingExecutor.drain(Arrays.asList(first, second),
               100, TimeUnit.MILLISECONDS));
         assertTrue(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start) < 1500);
         assertTrue(first.isShutdown());
         assertTrue(second.isShutdown());
         assertEquals(2, completed.getCount());
         release.countDown();
         assertTrue(completed.await(5, TimeUnit.SECONDS));
      } finally {
         release.countDown();
         first.shutdown();
         second.shutdown();
         assertTrue(first.awaitTermination(5, TimeUnit.SECONDS));
         assertTrue(second.awaitTermination(5, TimeUnit.SECONDS));
      }
   }

   @Test
   public void interruptedDrainCompletesAndRestoresInterrupt() {
      ExecutorService executor = newExecutor();
      executor.submit(() -> {
         try {
            Thread.sleep(100);
         } catch (InterruptedException e) {
            throw new AssertionError(e);
         }
      });
      Thread.currentThread().interrupt();
      try {
         assertTrue(FileSavingExecutor.drain(Collections.singleton(executor),
               5, TimeUnit.SECONDS));
         assertTrue(Thread.currentThread().isInterrupted());
      } finally {
         Thread.interrupted();
         executor.shutdown();
      }
   }

   private static ExecutorService newExecutor() {
      return Executors.newSingleThreadExecutor(
            ThreadFactoryFactory.createNonDaemonThreadFactory("Test save"));
   }

   private static void await(CountDownLatch latch) {
      try {
         latch.await();
      } catch (InterruptedException e) {
         throw new AssertionError(e);
      }
   }

   public static class WrapperHelper {
      private static WeakReference<ExecutorService> queueWrite(String output,
                                                              CountDownLatch release)
            throws InterruptedException {
         ExecutorService executor = FileSavingExecutor.register(
               Executors.newSingleThreadScheduledExecutor());
         CountDownLatch started = new CountDownLatch(1);
         executor.submit(() -> {
            started.countDown();
            await(release);
            try {
               Files.write(new File(output).toPath(), "saved".getBytes("UTF-8"));
            } catch (Exception e) {
               throw new AssertionError(e);
            }
         });
         if (!started.await(5, TimeUnit.SECONDS)) {
            System.exit(3);
         }
         return new WeakReference<>(executor);
      }

      public static void main(String[] args) throws Exception {
         CountDownLatch release = new CountDownLatch(1);
         WeakReference<ExecutorService> wrapper = queueWrite(args[0], release);
         // The worker retains its delegate, but not this public executor wrapper.
         // A separate weak reference establishes that collection actually ran.
         WeakReference<Object> control = new WeakReference<>(new Object());
         for (int i = 0; i < 100 && control.get() != null; i++) {
            System.gc();
            Thread.sleep(10);
         }
         if (control.get() != null || wrapper.get() == null) {
            System.err.println("Executor retention failed, or garbage collection did not run");
            release.countDown();
            System.exit(2);
         }
         // Release only after shutdown starts, so ordinary task completion cannot
         // accidentally make an unregistered executor look correctly drained.
         Runtime.getRuntime().addShutdownHook(new Thread(release::countDown));
         System.exit(0);
      }
   }

   public static class Helper {
      public static void main(String[] args) {
         ExecutorService executor = FileSavingExecutor.register(newExecutor());
         executor.submit(() -> {
            try {
               Thread.sleep(300);
               Files.write(new File(args[0]).toPath(), "first".getBytes("UTF-8"));
            } catch (Exception e) {
               throw new AssertionError(e);
            }
         });
         executor.submit(() -> {
            try {
               Files.write(new File(args[0]).toPath(), "last".getBytes("UTF-8"),
                     java.nio.file.StandardOpenOption.APPEND);
            } catch (Exception e) {
               throw new AssertionError(e);
            }
         });
         // No orderly shutdown: exercise the explicit-exit fallback on Swing's EDT.
         javax.swing.SwingUtilities.invokeLater(() -> System.exit(0));
      }
   }
}
