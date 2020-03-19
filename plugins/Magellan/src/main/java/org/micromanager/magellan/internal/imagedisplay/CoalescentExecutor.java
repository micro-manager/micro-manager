package org.micromanager.magellan.internal.imagedisplay;


import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

class CoalescentExecutor {

   // Guarded by monitor on this
   private final Map<Class<?>, CoalescentRunnable> coalescedRunnables_ = new HashMap<Class<?>, CoalescentRunnable>();
   private final Map<Class<?>, Long> skipCounts_ = new HashMap<Class<?>, Long>();

   private ExecutorService executor_;

   public CoalescentExecutor(final String name) {
      executor_ = Executors.newSingleThreadExecutor(new ThreadFactory() {
         @Override
         public Thread newThread(Runnable r) {
            return new Thread(r, name);
         }
      });
   }
   
   public void shutdownNow() {
      executor_.shutdownNow();
   }

   public void submitNonCoalescent(Runnable r) {
      executor_.submit(r);
   }
   
   /**
    * Invoke the given runnable on the EDT, coalescing multiple invocations on
    * the event queue.
    *
    * This is a mechanism to coalesce refresh-like tasks, in the manner of
    * Swing's {@code RepaintManager}, without having to replace the system
    * global event queue via EventQueue.push.
    *
    * The given runnable is scheduled to run on the EDT, just as with
    * {@code SwingUtilities.invokeLater}, but when invoked, all outstanding
    * runnables with the same "coalescence class" (see
    * CoalescentRunnable.getCoalescenceClass) will be coalesced and the result
    * will be run.
    *
    * @param runnable the coalescent runnable to invoke on the EDT
    */
   public void submitWithCoalescence(CoalescentRunnable runnable) {
      final Class<?> coalescenceClass = runnable.getCoalescenceClass();
      synchronized (this) {
         CoalescentRunnable coalesced
                 = coalescedRunnables_.get(coalescenceClass);
         if (coalesced != null) {
            coalesced = coalesced.coalesceWith(runnable);
         } else {
            coalesced = runnable;
         }
         coalescedRunnables_.put(coalescenceClass, coalesced);
      }

      executor_.submit(new Runnable() {
         @Override
         public void run() {
            final CoalescentRunnable coalesced;
            synchronized (CoalescentExecutor.this) {
               coalesced = coalescedRunnables_.remove(coalescenceClass);
            }
            if (coalesced == null) {
               return; // Already handled by previous invocations
            }

            coalesced.run();
         }
      });
   }

   /**
    * Like {@code invokeLaterWithCoalescence}, but defers invocation until the
    * last scheduled task on the event queue is processed.
    *
    * Note that if you keep calling this method at a higher rate than the EDT is
    * processing events, the runnable will not be executed until the EDT becomes
    * otherwise idle.
    *
    * @param runnable the coalescent runnable to invoke on the EDT
    */
   public void invokeAsLateAsPossibleWithCoalescence(
           CoalescentRunnable runnable) {
      final Class<?> coalescenceClass = runnable.getCoalescenceClass();
      synchronized (this) {
         CoalescentRunnable coalesced
                 = coalescedRunnables_.get(coalescenceClass);
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
      }

      executor_.submit(new Runnable() {
         @Override
         public void run() {
            final CoalescentRunnable coalesced;
            synchronized (CoalescentExecutor.this) {
               Long skipCount = skipCounts_.get(coalescenceClass);
               if (skipCount != null && skipCount > 0) {
                  skipCounts_.put(coalescenceClass, skipCount - 1);
                  return;
               }
               coalesced = coalescedRunnables_.remove(coalescenceClass);
            }
            if (coalesced == null) {
               return; // Be defensive
            }

            coalesced.run();
         }
      });
   }

 
}
