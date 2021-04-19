/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display;

import org.micromanager.MMEvent;
import org.micromanager.data.Coords;

/**
 * This event posts when the position (i.e., Channel, Time, Position, Slice,
 * possibly other Axes) in the display changed.
 *
 * The default implementation of this event posts on the DataViewer event bus.
 * Register using {@link DataViewer#registerForEvents(Object)}.
 */
public interface DisplayPositionChangedEvent extends MMEvent {

   /**
    * @return The new display position
    */
   Coords getDisplayPosition();

   /**
    * @return The previous display position
    */
   Coords getPreviousDisplayPosition();

   /**
    * @return The DataViewer posting this event.  Useful when handling multiple
    * DataViewers.
    */
   DataViewer getDataViewer();
}