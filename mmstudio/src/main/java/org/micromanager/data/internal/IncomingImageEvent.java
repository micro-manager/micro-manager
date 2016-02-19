package org.micromanager.data.internal;

import org.micromanager.data.Datastore;
import org.micromanager.data.Image;

/**
 * This class signifies that a new image is expected to arrive for the given
 * Datastore at some time in the future.
 */
public class IncomingImageEvent {
   private double nextImageTime_;

   public IncomingImageEvent(double nextImageTime) {
      nextImageTime_ = nextImageTime;
   }

   public double getNextImageTime() {
      return nextImageTime_;
   }
}
