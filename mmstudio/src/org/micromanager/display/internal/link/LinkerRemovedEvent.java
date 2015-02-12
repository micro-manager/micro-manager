package org.micromanager.display.internal.link;

/**
 * This class signifies that a SettingsLinker is being removed from the GUI.
 * It's only needed when a GUI component is removed without the display that
 * contains it going away, since otherwise we use the DisplayDestroyedEvent.
 */
public class LinkerRemovedEvent {
   private SettingsLinker linker_;

   public LinkerRemovedEvent(SettingsLinker linker) {
      linker_ = linker;
   }

   public SettingsLinker getLinker() {
      return linker_;
   }
}
