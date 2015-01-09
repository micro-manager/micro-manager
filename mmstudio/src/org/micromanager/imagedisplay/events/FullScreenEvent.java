package org.micromanager.imagedisplay.events;

import java.awt.GraphicsConfiguration;

import org.micromanager.api.display.DisplayWindow;

/**
 * Indicates that full-screen mode has been turned on/off for the given
 * monitor.
 */
public class FullScreenEvent {
   private DisplayWindow source_;
   private GraphicsConfiguration displayConfig_;
   private boolean isFullScreen_;

   public FullScreenEvent(DisplayWindow source, GraphicsConfiguration config,
         boolean isFullScreen) {
      source_ = source;
      displayConfig_ = config;
      isFullScreen_ = isFullScreen;
   }

   public DisplayWindow getSource() {
      return source_;
   }

   public GraphicsConfiguration getConfig() {
      return displayConfig_;
   }

   public boolean getIsFullScreen() {
      return isFullScreen_;
   }
}
