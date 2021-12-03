package org.micromanager.events.internal;

import org.micromanager.events.ShutdownCommencingEvent;

/**
 * This event posts when the user requests the program to shut down.
 *
 * <p>It gives subscribers the opportunity to cancel shutdown (ideally only to
 * ensure that data can be saved or other similarly-critical decisions).</p>
 *
 * <p>All subscribers must first check if the shutdown has been canceled by
 * calling {@link #isCanceled()}. If the shutdown has been canceled, the
 * event must be ignored.</p>
 *
 * <p>This event posts on the Studio event bus,
 * so subscribe using {@link org.micromanager.events.EventManager}.</p>
 */
public class DefaultShutdownCommencingEvent implements ShutdownCommencingEvent {
   private boolean isCanceled_ = false;

   /**
    * Cancel shutdown.
    */
   public void cancelShutdown() {
      isCanceled_ = true;
   }

   /**
    * Return whether or not shutdown has been canceled.
    *
    * @return true when shutdown was canceled
    */
   public boolean isCanceled() {
      return isCanceled_;
   }

   /**
    * Deprecated version of isCanceled().
    *
    * @return true when shutdown was canceled
    * @deprecated use {@link #isCanceled()} instead
    */
   @Deprecated
   public boolean getIsCancelled() {
      return isCanceled();
   }
}
