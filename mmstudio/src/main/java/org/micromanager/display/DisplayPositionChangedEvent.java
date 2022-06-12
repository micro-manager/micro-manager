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
 * <p>The default implementation of this event posts on the DataViewer event bus.
 * Register using {@link DataViewer#registerForEvents(Object)}.</p>
 */
public interface DisplayPositionChangedEvent extends MMEvent {

   /**
    * returns the new display position.
    *
    * @return The new display position
    */
   Coords getDisplayPosition();

   /**
    * Returns the previously displayed position.
    *
    * @return The previous display position
    */
   Coords getPreviousDisplayPosition();

   /**
    * The dataViewer posting this event.
    *
    * @return The DataViewer posting this event.  Useful when handling multiple
    * DataViewers.
    */
   DataViewer getDataViewer();
}