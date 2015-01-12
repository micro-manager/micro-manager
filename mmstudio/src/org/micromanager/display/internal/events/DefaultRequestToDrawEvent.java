package org.micromanager.display.internal.events;

import org.micromanager.data.Coords;
import org.micromanager.display.RequestToDrawEvent;

public class DefaultRequestToDrawEvent implements RequestToDrawEvent {
   private Coords coords_;
   
   public DefaultRequestToDrawEvent() {
   }

   public DefaultRequestToDrawEvent(Coords coords) {
      coords_ = coords;
   }

   public Coords getCoords() {
      return coords_;
   }
}
