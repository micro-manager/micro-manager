/**
 *
 * @author kthorn
 */

package org.micromanager.lightsheetcontrol;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class LightSheetControl implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Light Sheet Control";
   public static final String tooltipDescription =
         "Controls AZ100-based light sheet microscope at the Nikon Imaging Center";
   private Studio studio_;
   private LightSheetControlFrame myFrame_;

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public void onPluginSelected() {
      if (myFrame_ == null) {
         myFrame_ = new LightSheetControlFrame(studio_);
      }
      myFrame_.setVisible(true);
   }

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return menuName;
   }

   @Override
   public String getHelpText() {
      return "Plugin for controlling AZ100 light sheet microscope in the Nikon Imaging Center,"
            + " UCSF. See Kurt Thorn with questions.";
   }

   @Override
   public String getVersion() {
      return "v0.2";
   }

   @Override
   public String getCopyright() {
      return "University of California, 2016, 2024";
   }
}