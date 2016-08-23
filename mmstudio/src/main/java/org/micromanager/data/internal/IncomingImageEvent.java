package org.micromanager.data.internal;

/**
 * This class signifies that a new image is expected to arrive for the given
 * Datastore at some time in the future.
 */
public final class IncomingImageEvent {
   private double nextImageTime_;

   public IncomingImageEvent(double nextImageTime) {
      nextImageTime_ = nextImageTime;
   }

   /**
    * This method is so named because the value it provides was generated
    * using System.nanoTime() (see the acquisition engine), so this is a
    * reminder to users that they shouldn't compare it to
    * System.currentTimeMillis().
    */
   public double getNextImageNanoTime() {
      return nextImageTime_;
   }
}
