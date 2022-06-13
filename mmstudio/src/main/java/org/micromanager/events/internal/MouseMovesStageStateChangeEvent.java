package org.micromanager.events.internal;

/**
 * This class signifies that someone has changed whether or not the mouse can
 * be used to control the stage position.
 */
public final class MouseMovesStageStateChangeEvent {
   private final boolean isEnabled_;

   public MouseMovesStageStateChangeEvent(boolean isEnabled) {
      isEnabled_ = isEnabled;
   }

   public boolean isEnabled() {
      return isEnabled_;
   }
}
