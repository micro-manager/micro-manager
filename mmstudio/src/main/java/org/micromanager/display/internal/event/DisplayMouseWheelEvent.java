package org.micromanager.display.internal.event;

import java.awt.event.MouseWheelEvent;

/** @author Nico */
public class DisplayMouseWheelEvent {
  final MouseWheelEvent mwe_;

  public DisplayMouseWheelEvent(MouseWheelEvent e) {
    mwe_ = e;
  }

  public MouseWheelEvent getEvent() {
    return mwe_;
  }
}
