package org.micromanager.explorer;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class Explorer implements MenuPlugin, SciJavaPlugin {
   private static final String MENU_NAME = "Explorer";
   private static final String TOOL_TIP_DESCRIPTION =
            "Interactively acquire and stitch tiles through the active processing pipeline";
   private static final String VERSION = "0.1";

   private Studio studio_;
   private ExplorerFrame frame_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public void onPluginSelected() {
      if (frame_ == null || !frame_.isDisplayable()) {
         frame_ = new ExplorerFrame(studio_);
      }
      frame_.setVisible(true);
      frame_.toFront();
   }

   @Override
   public String getSubMenu() {
      return "Navigation Tools";
   }

   @Override
   public String getName() {
      return MENU_NAME;
   }

   @Override
   public String getHelpText() {
      return TOOL_TIP_DESCRIPTION;
   }

   @Override
   public String getVersion() {
      return VERSION;
   }

   @Override
   public String getCopyright() {
      return "Altos Labs, 2024";
   }
}
