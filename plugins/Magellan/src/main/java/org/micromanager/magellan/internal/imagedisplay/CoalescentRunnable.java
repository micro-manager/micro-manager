package org.micromanager.magellan.internal.imagedisplay;

  /**
    * A "coalescent" runnable, for use with {@code invokeLaterWithCoalescence}.
    */
    interface CoalescentRunnable extends Runnable {

      /**
       * Return a tag class used to group instances that can be coalesced.
       *
       * @return a tag class
       */
      Class<?> getCoalescenceClass();

      /**
       * Return a new runnable formed by coalescing this instance with another.
       *
       * It is guaranteed that {@code another} has the same coalescence class as
       * this instance, and that {@code another} was scheduled <i>after</i>
       * this instance (or the instances coalesced to form this instance).
       *
       * Note that this method should not have any side effects.
       *
       * @param later a newer coalescent runnable with which to coalesce
       * @return the coalesced runnable formed from this instance and {@code
       * another}
       */
      CoalescentRunnable coalesceWith(CoalescentRunnable later);
   }