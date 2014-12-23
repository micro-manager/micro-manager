package org.micromanager.imagedisplay.events;

// This class provides information when the mouse moves over an image.
public class MouseMovedEvent {
   private int x_;
   private int y_;
   public MouseMovedEvent(int x, int y) {
      x_ = x;
      y_ = y;
   }

   public int getX() {
      return x_;
   }

   public int getY() {
      return y_;
   }
}
