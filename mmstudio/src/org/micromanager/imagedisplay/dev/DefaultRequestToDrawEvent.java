package org.micromanager.imagedisplay.dev;

import org.micromanager.api.data.Coords;
import org.micromanager.api.display.RequestToDrawEvent;

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
