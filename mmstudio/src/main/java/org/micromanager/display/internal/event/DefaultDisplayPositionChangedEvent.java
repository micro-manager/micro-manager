/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.event;

import org.micromanager.data.Coords;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayPositionChangedEvent;

/**
 * This event posts when the position (i.e., Channel, Time, Position, Slice,
 * possibly other Axes) in the display changed.
 *
 * This event posts on the DataViewer event bus.
 * Register using {@link DataViewer#registerForEvents(Object)}.
 */
public class DefaultDisplayPositionChangedEvent
      implements DisplayPositionChangedEvent
{
   private final DataViewer viewer_;
   private final Coords newPosition_;
   private final Coords oldPosition_;
   
   public static DisplayPositionChangedEvent create(DataViewer viewer,
         Coords oldPosition, Coords newPosition)
   {
      return new DefaultDisplayPositionChangedEvent(viewer, oldPosition, newPosition);
   }
   
   private DefaultDisplayPositionChangedEvent(DataViewer viewer,
         Coords oldPosition, Coords newPosition)
   {
      viewer_ = viewer;
      newPosition_ = newPosition;
      oldPosition_ = oldPosition;
   }

   /**
    * @return The new display position
    */
   @Override
   public Coords getDisplayPosition() {
      return newPosition_;
   }

   /**
    * @return The previous display position
    */
   @Override
   public Coords getPreviousDisplayPosition() {
      return oldPosition_;
   }

   /**
    * @return The DataViewer posting this event.  Useful when handling multiple
    * DataViewers.
    */
   @Override
   public DataViewer getDataViewer() {
      return viewer_;
   }
}