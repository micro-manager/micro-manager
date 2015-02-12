package org.micromanager.display.internal.events;

import java.awt.GraphicsConfiguration;

import org.micromanager.display.DisplayWindow;

/**
 * Indicates that full-screen mode has been turned on/off for the given
 * monitor.
 */
public class FullScreenEvent {
   private GraphicsConfiguration displayConfig_;
   private boolean isFullScreen_;

   public FullScreenEvent(GraphicsConfiguration config,
         boolean isFullScreen) {
      displayConfig_ = config;
      isFullScreen_ = isFullScreen;
   }

   public GraphicsConfiguration getConfig() {
      return displayConfig_;
   }

   public boolean getIsFullScreen() {
      return isFullScreen_;
   }
}
