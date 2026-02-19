package org.micromanager.plugins.isim;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class iSIM implements SciJavaPlugin, MenuPlugin {
   private Studio studio_;
   private iSIMFrame frame_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public void onPluginSelected() {
      if (frame_ == null) {
         frame_ = new iSIMFrame(studio_);
      }
      frame_.setVisible(true);
   }

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public String getName() {
      return "iSIM";
   }

   @Override
   public String getHelpText() {
      return "iSIM timing and control.";
   }

   @Override
   public String getVersion() {
      return "0.0.0";
   }

   @Override
   public String getCopyright() {
      return "The Laboratory of Experimental Biophysics (LEB), EPFL, 2026";
   }
}
