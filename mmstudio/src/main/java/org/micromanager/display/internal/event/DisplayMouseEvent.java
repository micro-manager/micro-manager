// Copyright
//           (C) 2017 Regents of the University of California
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display.internal.event;

import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.geom.Point2D;

/**
 * Provides a MouseEvent and the location (in image pixel coordinates) where the event happened
 *
 * @author Nico
 */
public class DisplayMouseEvent {
  private final MouseEvent event_;
  private final Rectangle location_;
  private final int IJToolId_;

  public DisplayMouseEvent(final MouseEvent e, final Rectangle imageLocation, final int IJToolId) {
    event_ = e;
    location_ = imageLocation;
    IJToolId_ = IJToolId;
  }

  /**
   * The MouseEvent as received by the display. Use the getID() function to find out what this event
   * signifies.
   *
   * @return MouseEvent received by the display
   */
  public MouseEvent getEvent() {
    return event_;
  }

  /**
   * Provides location in image coordinates. This parameter can be a rectangle containing more than
   * one pixel, for example if the point comes from a zoomed-out canvas.
   *
   * @return Location in image coordinates
   */
  public Rectangle getLocation() {
    return location_;
  }

  /**
   * Provides center location in image coordinates Calculates the center of the Rectangle returned
   * in getLocation
   *
   * @return Center location in image coordinates
   */
  public Point2D getCenterLocation() {
    return new Point(location_.x + location_.width / 2, location_.y + location_.height / 2);
  }

  /**
   * The currently selected ImageJ Tool Regretfully, this creates some dependencies on ImageJ code,
   * however only an integer is being propagated. Possible values are defined in ij.gui.Toolbar
   *
   * @return ID of the tool selected in the ImageJ Tool-bar
   */
  public int getToolId() {
    return IJToolId_;
  }
}
