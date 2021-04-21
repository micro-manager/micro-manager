/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.display.internal.event;

import org.micromanager.data.Coords;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplayPositionChangedEvent;

/** @author Mark A. Tsuchida */
public class DefaultDisplayPositionChangedEvent implements DisplayPositionChangedEvent {
  private final DataViewer viewer_;
  private final Coords newPosition_;
  private final Coords oldPosition_;

  public static DisplayPositionChangedEvent create(
      DataViewer viewer, Coords oldPosition, Coords newPosition) {
    return new DefaultDisplayPositionChangedEvent(viewer, oldPosition, newPosition);
  }

  private DefaultDisplayPositionChangedEvent(
      DataViewer viewer, Coords oldPosition, Coords newPosition) {
    viewer_ = viewer;
    newPosition_ = newPosition;
    oldPosition_ = oldPosition;
  }

  @Override
  public Coords getDisplayPosition() {
    return newPosition_;
  }

  @Override
  public Coords getPreviousDisplayPosition() {
    return oldPosition_;
  }

  @Override
  public DataViewer getDataViewer() {
    return viewer_;
  }
}
