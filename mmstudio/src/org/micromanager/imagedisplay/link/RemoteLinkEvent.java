package org.micromanager.imagedisplay.link;

/**
 * This class is used to inform one DisplayWindow that another, different
 * DisplayWindow has changed a LinkButton's state.
 */
public class RemoteLinkEvent {
   private SettingsLinker linker_;
   private boolean isLinked_;

   public RemoteLinkEvent(SettingsLinker linker, boolean isLinked) {
      linker_ = linker;
      isLinked_ = isLinked;
   }

   public SettingsLinker getLinker() {
      return linker_;
   }

   public boolean getIsLinked() {
      return isLinked_;
   }
}
