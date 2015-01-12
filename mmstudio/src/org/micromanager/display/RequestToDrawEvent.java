package org.micromanager.display;

import org.micromanager.data.Coords;

/**
 * By posting this event on an EventBus, you can request that an image window
 * refresh its display. Optionally, you can specify the coordinates of an
 * Image to display.
 */
public interface RequestToDrawEvent {
   public Coords getCoords();
}
