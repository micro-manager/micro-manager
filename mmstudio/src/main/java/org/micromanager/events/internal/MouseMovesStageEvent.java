package org.micromanager.events.internal;

// This class signifies that someone has changed whether or not the mouse can
// be used to control the stage position.
public final class MouseMovesStageEvent {
   private boolean isEnabled_;
   public MouseMovesStageEvent(boolean isEnabled) {
      isEnabled_ = isEnabled;
   }

   public boolean getIsEnabled() {
      return isEnabled_;
   }
}
