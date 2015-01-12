package org.micromanager.display.internal.link;

import org.micromanager.display.DisplayWindow;

/**
 * This class signifies that a LinkButton has been clicked.
 */
public class LinkButtonEvent {
   private SettingsLinker linker_;
   private DisplayWindow display_;
   private boolean isLinked_;

   public LinkButtonEvent(SettingsLinker linker,
         DisplayWindow display, boolean isLinked) {
      linker_ = linker;
      display_ = display;
      isLinked_ = isLinked;
   }

   public SettingsLinker getLinker() {
      return linker_;
   }

   public DisplayWindow getDisplay() {
      return display_;
   }

   public boolean getIsLinked() {
      return isLinked_;
   }
}
