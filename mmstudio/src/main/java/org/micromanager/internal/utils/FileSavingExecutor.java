package org.micromanager.internal.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Last-resort protection for accepted file writes during explicit JVM exit.
 *
 * <p>Normal shutdown must still finalize datasets and flush profiles. This hook
 * only drains work already submitted to the registered executors, without using
 * Swing or other application services that may already have shut down. A stuck
 * disk must not prevent JVM shutdown indefinitely; after one minute, incomplete
 * saves are reported to standard error. Forced process termination cannot be
 * protected by a shutdown hook.
 */
public final class FileSavingExecutor {
   private static final Set<ExecutorService> EXECUTORS =
         new HashSet<>();

   static {
      Runtime.getRuntime().addShutdownHook(new Thread(() -> {
         Collection<ExecutorService> executors;
         synchronized (EXECUTORS) {
            executors = new ArrayList<>(EXECUTORS);
         }
         if (!drain(executors, 60, TimeUnit.SECONDS)) {
            System.err.println("Micro-Manager: file saving did not finish within the "
                  + "JVM shutdown timeout; saved data may be incomplete.");
         }
      }, "Micro-Manager file saving shutdown"));
   }

   private FileSavingExecutor() {
   }

   /** Register before submitting any file-writing tasks. */
   public static <T extends ExecutorService> T register(T executor) {
      synchronized (EXECUTORS) {
         // Executor wrappers may be collected while their delegate still runs.
         // Retain registrations until termination so accepted writes stay covered.
         EXECUTORS.removeIf(ExecutorService::isTerminated);
         EXECUTORS.add(executor);
      }
      return executor;
   }

   static boolean drain(Collection<ExecutorService> executors, long timeout, TimeUnit unit) {
      long deadline = System.nanoTime() + unit.toNanos(timeout);
      for (ExecutorService executor : executors) {
         executor.shutdown();
      }
      boolean interrupted = false;
      try {
         for (ExecutorService executor : executors) {
            while (!executor.isTerminated()) {
               long remaining = deadline - System.nanoTime();
               if (remaining <= 0) {
                  return false;
               }
               try {
                  if (!executor.awaitTermination(remaining, TimeUnit.NANOSECONDS)) {
                     return false;
                  }
               } catch (InterruptedException e) {
                  interrupted = true;
               }
            }
         }
         return true;
      } finally {
         if (interrupted) {
            Thread.currentThread().interrupt();
         }
      }
   }
}
