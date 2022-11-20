package org.micromanager.slideexplorer;


import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.internal.utils.ReportingUtils;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class SlideExplorerPlugin implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Slide Explorer";
   public static final String tooltipDescription =
         "Automatically acquire images as you pan and zoom, "
               + "constructing a tiled map of the sample";

   private Hub hub_;
   private Studio app_;


   public void configurationChanged() {
      // TODO Auto-generated method stub

   }

   public void dispose() {
      hub_.shutdown();
   }

   @Override
   public String getName() {
      return menuName;
   }

   @Override
   public String getCopyright() {
      // TODO Auto-generated method stub
      return "University of California, San Francisco, 2009. Author: Arthur Edelstein";
   }

   @Override
   public String getHelpText() {
      // TODO Auto-generated method stub
      return tooltipDescription;
   }

   @Override
   public String getVersion() {
      return "V1.0";
   }

   @Override
   public void setContext(Studio app) {
      app_ = app;
   }

   @Override
   public String getSubMenu() {
      return "Acquisition Tools";
   }

   @Override
   public void onPluginSelected() {
      if (hub_ == null) {
         ReportingUtils.showMessage(
               "Warning: the Slide Explorer plugin can move the XY-stage\n"
                     + "long distances. Please be careful not to pan far from the\n"
                     + "slide and make sure the objectives don't hit any other\n"
                     + "hardware. Use at your own risk!");
         hub_ = new Hub(app_);
      } else if (!hub_.setVisible(true)) {
         // Display was destroyed; recreate it.
         hub_ = new Hub(app_);
      }
   }
}
