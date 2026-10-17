package org.micromanager.internal.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import org.junit.Test;

public class ThreadFactoryFactoryTest {
   @Test
   public void namesThreadsInEachPool() {
      ThreadFactory factory = ThreadFactoryFactory.createNonDaemonThreadFactory("Save");
      assertEquals("Save Pool Thread 0", factory.newThread(() -> {}).getName());
      assertEquals("Save Pool Thread 1", factory.newThread(() -> {}).getName());
   }

   @Test
   public void queuedWritesCompleteBeforeNormalJvmExit() throws Exception {
      assertEquals("complete", runChild("save"));
   }

   @Test
   public void backgroundWorkDoesNotPreventNormalJvmExit() throws Exception {
      assertEquals("", runChild("background"));
   }

   @Test
   public void explicitExitStillRequiresApplicationDrain() throws Exception {
      assertEquals("", runChild("exit"));
   }

   private String runChild(String mode) throws Exception {
      Path output = Files.createTempFile("mm-thread-factory", ".txt");
      Process child = new ProcessBuilder(
            new File(System.getProperty("java.home"), "bin/java").getPath(),
            "-cp", System.getProperty("java.class.path"),
            Helper.class.getName(), mode, output.toString()).inheritIO().start();
      try {
         assertTrue("Child JVM did not exit", child.waitFor(10, TimeUnit.SECONDS));
         assertEquals(0, child.exitValue());
         return new String(Files.readAllBytes(output), "UTF-8");
      } finally {
         child.destroyForcibly();
         Files.deleteIfExists(output);
      }
   }

   public static class Helper {
      public static void main(String[] args) {
         ThreadFactory factory = "background".equals(args[0])
               ? ThreadFactoryFactory.createDaemonThreadFactory("Background")
               : ThreadFactoryFactory.createNonDaemonThreadFactory("Save");
         ExecutorService executor = Executors.newSingleThreadExecutor(factory);
         executor.submit(() -> {
            try {
               Thread.sleep(500);
               Files.write(new File(args[1]).toPath(), "complete".getBytes("UTF-8"));
            } catch (Exception e) {
               throw new RuntimeException(e);
            }
         });
         executor.shutdown();
         if ("exit".equals(args[0])) {
            System.exit(0);
         }
      }
   }
}
