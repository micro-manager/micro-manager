package org.micromanager.events.internal;

import org.micromanager.events.ShutdownCommencingEvent;

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
    */
   public boolean isCanceled() {
      return isCanceled_;
   }

   @Deprecated
   public boolean getIsCancelled() {
      return isCanceled();
   }
}
