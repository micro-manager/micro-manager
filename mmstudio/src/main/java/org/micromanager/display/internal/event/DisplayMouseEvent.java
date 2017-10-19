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

import java.awt.Rectangle;
import java.awt.event.MouseEvent;

/**
 * Provides a MouseEvent and the location (in image coordinates) where
 * the event happened
 * 
 * @author Nico
 */
public class DisplayMouseEvent {
   private final MouseEvent event_;
   private final Rectangle location_;
   
   public DisplayMouseEvent(MouseEvent e, Rectangle imageLocation) {
      event_ = e;
      location_ = imageLocation;
   }
   
   public MouseEvent getEvent() {
      return event_;
   }
   
   public Rectangle getLocation() {
      return location_;
   }
   
}
