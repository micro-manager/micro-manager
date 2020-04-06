package org.micromanager.multicamera;

import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class MultiCamera implements MenuPlugin, SciJavaPlugin {
   public static final String MENU_NAME = "Multi-Andor Control";
   public static final String TOOL_TIP_DESCRIPTION =
      "Control settings for one or more Andor EM-CCD cameras via the " +
      "Multi Camera device"; 

   private Studio gui_;
   private MultiCameraFrame myFrame_;

   @Override
   public String getName() {
      return MENU_NAME;
   }

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public void setContext(Studio app) {
      gui_ = app; 
   }

   @Override
   public void onPluginSelected() {
      if (myFrame_ == null) {
         try {
            myFrame_ = new MultiCameraFrame(gui_);
            gui_.events().registerForEvents(myFrame_);
         } catch (Exception e) {
            gui_.logs().showError(e);
            return;
         }
      }
      myFrame_.setVisible(true);
   }

   @Override
   public String getHelpText() {
      return TOOL_TIP_DESCRIPTION;
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
