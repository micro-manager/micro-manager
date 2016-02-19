package org.micromanager.multicamera;

import mmcorej.CMMCore;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class MultiCamera implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Multi-Andor Control";
   public static final String tooltipDescription =
      "Control settings for one or more Andor EM-CCD cameras via the " +
      "Multi Camera device"; 

   private CMMCore core_;
   private Studio gui_;
   private MultiCameraFrame myFrame_;

   @Override
   public String getName() {
      return menuName;
   }

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public void setContext(Studio app) {
      gui_ = app;                                        
      core_ = app.getCMMCore();
   }

   @Override
   public void onPluginSelected() {
      if (myFrame_ == null) {
         try {
            myFrame_ = new MultiCameraFrame(gui_);
            gui_.compat().addMMListener(myFrame_);
         } catch (Exception e) {
            gui_.logs().showError(e);
            return;
         }
      }
      myFrame_.setVisible(true);
   }

   @Override
   public String getHelpText() {
      return tooltipDescription;
   }
   
    @Override
   public String getVersion() {
      return "0.13";
   }
   
    @Override
   public String getCopyright() {
      return "University of California, 2010, 2011";
   }
}
