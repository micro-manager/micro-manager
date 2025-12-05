// Copyright (C) 2017 Open Imaging, Inc.
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.SwingUtilities;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Provide versions of SwingUtilities.invokeLater that coalesce with other
 * outstanding invocations.
 *
 * @author Mark A. Tsuchida
 */
public class CoalescentEDTRunnablePool {
   // Guarded by monitor on this
   private final Map<Class<?>, CoalescentRunnable> coalescedRunnables_ = new HashMap<>();
   private final Map<Class<?>, Long> coalescedTimestamps_ = new HashMap<>();  // Track entry creation time
   private final Map<Class<?>, Long> skipCounts_ = new HashMap<>();
   private static final long STALE_ENTRY_TIMEOUT_NS = 5_000_000_000L;  // 5 seconds

   public static CoalescentEDTRunnablePool create() {
      return new CoalescentEDTRunnablePool();
   }

   private CoalescentEDTRunnablePool() {
   }

   /**
    * Invoke the given runnable on the EDT, coalescing multiple invocations on
    * the event queue.
    *
    * <p>This is a mechanism to coalesce refresh-like tasks, in the manner of
    * Swing's {@code RepaintManager}, without having to replace the system
    * global event queue via EventQueue.push.
    *
    * <p>The given runnable is scheduled to run on the EDT, just as with
    * {@code SwingUtilities.invokeLater}, but when invoked, all outstanding
    * runnables with the same "coalescence class"
    * (see CoalescentRunnable.getCoalescenceClass) will be coalesced and the
    * result will be run.
    *
    * @param runnable the coalescent runnable to invoke on the EDT
    */
   public void invokeLaterWithCoalescence(CoalescentRunnable runnable) {
      final Class<?> coalescenceClass = runnable.getCoalescenceClass();
      synchronized (this) {
         // Clean up stale entries that have been waiting > 5 seconds
         cleanStaleEntries();

         CoalescentRunnable coalesced =
               coalescedRunnables_.get(coalescenceClass);
         if (coalesced != null) {
            coalesced = coalesced.coalesceWith(runnable);
         } else {
            coalesced = runnable;
         }
         coalescedRunnables_.put(coalescenceClass, coalesced);
         coalescedTimestamps_.put(coalescenceClass, System.nanoTime());
      }

      SwingUtilities.invokeLater(() -> {
         final CoalescentRunnable coalesced;
         synchronized (CoalescentEDTRunnablePool.this) {
            coalesced = coalescedRunnables_.remove(coalescenceClass);
            coalescedTimestamps_.remove(coalescenceClass);
         }
         if (coalesced == null) {
            return; // Already handled by previous invocations
         }

         coalesced.run();
      });
   }

   /**
    * Like {@code invokeLaterWithCoalescence}, but defers invocation until the
    * last scheduled task on the event queue is processed.
    *
    * <p>Note that if you keep calling this method at a higher rate than the EDT
    * is processing events, the runnable will not be executed until the EDT
    * becomes otherwise idle.
    *
    * @param runnable the coalescent runnable to invoke on the EDT
    */
   public void invokeAsLateAsPossibleWithCoalescence(
         CoalescentRunnable runnable) {
      final Class<?> coalescenceClass = runnable.getCoalescenceClass();
      synchronized (this) {
         CoalescentRunnable coalesced =
               coalescedRunnables_.get(coalescenceClass);
         if (coalesced != null) {
            coalesced = coalesced.coalesceWith(runnable);
            // Increment skip count
            Long oldSkipCount = skipCounts_.get(coalescenceClass);
            skipCounts_.put(coalescenceClass,
                  (oldSkipCount == null ? 0 : oldSkipCount) + 1);
         } else {
            coalesced = runnable;
         }
         coalescedRunnables_.put(coalescenceClass, coalesced);
         coalescedTimestamps_.put(coalescenceClass, System.nanoTime());
      }

      SwingUtilities.invokeLater(() -> {
         final CoalescentRunnable coalesced;
         synchronized (CoalescentEDTRunnablePool.this) {
            Long skipCount = skipCounts_.get(coalescenceClass);
            if (skipCount != null && skipCount > 0) {
               long newCount = skipCount - 1;
               if (newCount == 0) {
                  // Remove entry when count reaches 0 to prevent unbounded HashMap growth
                  skipCounts_.remove(coalescenceClass);
               } else {
                  skipCounts_.put(coalescenceClass, newCount);
               }
               return;
            }
            coalesced = coalescedRunnables_.remove(coalescenceClass);
            coalescedTimestamps_.remove(coalescenceClass);
         }
         if (coalesced == null) {
            return; // Be defensive
         }

         coalesced.run();
      });
   }

   /**
    * Clean up stale entries that have been in the map for more than 5 seconds.
    * This prevents HashMap accumulation when EDT is saturated and entries never
    * get removed because SwingUtilities.invokeLater() never executes.
    * Must be called while holding monitor on this.
    */
   private void cleanStaleEntries() {
      long now = System.nanoTime();
      List<Class<?>> staleKeys = new ArrayList<>();

      for (Map.Entry<Class<?>, Long> entry : coalescedTimestamps_.entrySet()) {
         if (now - entry.getValue() > STALE_ENTRY_TIMEOUT_NS) {
            staleKeys.add(entry.getKey());
         }
      }

      if (!staleKeys.isEmpty()) {
         ReportingUtils.logMessage("CoalescentEDTRunnablePool: Removing " + staleKeys.size()
               + " stale entries older than 5 seconds");
         for (Class<?> key : staleKeys) {
            coalescedRunnables_.remove(key);
            coalescedTimestamps_.remove(key);
            skipCounts_.remove(key);  // Also clean up skip counts
         }
      }
   }

   /**
    * A "coalescent" runnable, for use with {@code invokeLaterWithCoalescence}.
    */
   public interface CoalescentRunnable extends Runnable {
      /**
       * Return a tag class used to group instances that can be coalesced.
       *
       * @return a tag class
       */
      Class<?> getCoalescenceClass();

      /**
       * Return a new runnable formed by coalescing this instance with another.
       *
       * <p>It is guaranteed that {@code another} has the same coalescence class
       * as this instance, and that {@code another} was scheduled <i>after</i>
       * this instance (or the instances coalesced to form this instance).
       *
       * <p>Note that this method should not have any side effects.
       *
       * @param later a newer coalescent runnable with which to coalesce
       * @return the coalesced runnable formed from this instance and {@code
       * another}
       */
      CoalescentRunnable coalesceWith(CoalescentRunnable later);
   }
}