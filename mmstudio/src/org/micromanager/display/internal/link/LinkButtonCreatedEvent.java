package org.micromanager.display.internal.link;

/**
 * This event is published when a LinkButton is created, so that its Linker
 * can be tracked by the DisplayGroupManager.
 */
public class LinkButtonCreatedEvent {
   private LinkButton button_;
   private SettingsLinker linker_;

   public LinkButtonCreatedEvent(LinkButton button, SettingsLinker linker) {
      button_ = button;
      linker_ = linker;
   }

   public LinkButton getButton() {
      return button_;
   }

   public SettingsLinker getLinker() {
      return linker_;
   }
}
