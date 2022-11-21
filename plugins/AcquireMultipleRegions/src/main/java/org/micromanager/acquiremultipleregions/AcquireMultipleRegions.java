
package org.micromanager.acquiremultipleregions;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * @author kthorn
 */
@Plugin(type = MenuPlugin.class)
public class AcquireMultipleRegions implements MenuPlugin, SciJavaPlugin {
   public static final String MENU_NAME = "Acquire Multiple Regions";
   public static final String TOOL_TIP_DESCRIPTION =
         "Automatically acquire multiple regions of a sample";
   public static String versionNumber = "0.4";
   private Studio gui_;
   private AcquireMultipleRegionsForm myFrame_;

   //Static variables so we can use script panel to tweak interpolation params
   //Exponent for Shepard interpolation
   public static double shepardExponent = 2;

   @Override
   public void setContext(Studio si) {
      gui_ = si;
   }

   @Override
   public void onPluginSelected() {
      if (myFrame_ == null) {
         myFrame_ = new AcquireMultipleRegionsForm(gui_);
      }
      myFrame_.setVisible(true);
   }

   @Override
   public String getName() {
      return MENU_NAME;
   }

   @Override
   public String getSubMenu() {
      return "Acquisition Tools";
   }

   @Override
   public String getHelpText() {
      return TOOL_TIP_DESCRIPTION;
   }

   @Override
   public String getVersion() {
      return versionNumber;
   }

   @Override
   public String getCopyright() {
      return "University of California, 2014";
   }

}
