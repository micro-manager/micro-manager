package org.micromanager.display.internal.events;

import org.micromanager.data.Coords;

/**
 * This event is published whenever the MMVirtualStack updates its "position"
 * in ImageJ coordinates. 
 */
public class StackPositionChangedEvent {
   private Coords coords_;

   public StackPositionChangedEvent(Coords coords) {
      coords_ = coords;
   }

   public Coords getCoords() {
      return coords_;
   }
}
