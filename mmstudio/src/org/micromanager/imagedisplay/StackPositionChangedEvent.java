package org.micromanager.imagedisplay;

import org.micromanager.api.data.Coords;

/**
 * This event is published whenever the MMVirtualStack updates its "position"
 * in ImageJ coordinates. 
 */
class StackPositionChangedEvent {
   private Coords coords_;

   public StackPositionChangedEvent(Coords coords) {
      coords_ = coords;
   }

   public Coords getCoords() {
      return coords_;
   }
}
