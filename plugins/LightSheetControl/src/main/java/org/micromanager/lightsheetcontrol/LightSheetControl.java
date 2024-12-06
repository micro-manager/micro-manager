/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.lightsheetcontrol;

/**
 *
 * @author kthorn
 */
import mmcorej.CMMCore;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class LightSheetControl implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Light Sheet Control";
   public static final String tooltipDescription =
      "Controls AZ100-based light sheet microscope at the Nikon Imaging Center";
   private CMMCore core_;
   private Studio studio_;
   private LightSheetControlForm myFrame_;
   
    @Override
    public String getSubMenu() {
        return "";
    }

    @Override
    public void onPluginSelected() {
      if (myFrame_ == null)
         myFrame_ = new LightSheetControlForm(studio_);
      myFrame_.setVisible(true);
    }

    @Override
    public void setContext(Studio studio) {
      studio_ = (Studio) studio;
      core_ = studio.getCMMCore();
    }

    @Override
    public String getName() {
        return menuName;
    }

    @Override
    public String getHelpText() {
        return "Plugin for controlling AZ100 light sheet microscope in the Nikon Imaging Center, UCSF. See Kurt Thorn with questions.";
    }

    @Override
    public String getVersion() {
        return "v0.1";
    }

    @Override
    public String getCopyright() {
        return "University of California, 2016";
    }
    
}
