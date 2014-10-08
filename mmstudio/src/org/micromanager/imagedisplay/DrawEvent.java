package org.micromanager.imagedisplay;

import org.micromanager.api.data.Coords;

// This class is used to notify entities that drawing logic is about to be 
// invoked.
public class DrawEvent implements org.micromanager.api.display.DrawEvent {
   private Coords coords_;

   // No-op constructor leaves coords null.
   public DrawEvent() {}

   public DrawEvent(Coords coords) {
      coords_ = coords;
   }

   public Coords getCoords() {
      return coords_;
   }
}
