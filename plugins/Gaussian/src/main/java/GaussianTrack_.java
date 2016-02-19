/*
 * GaussianTrack_.java
 *
 * ImageJ plugin whose sole function is to invoke the MainForm UI
 *
 * Created on Sep 15, 2010, 9:29:05 PM
 *
 * @author nico
 */

import edu.valelab.gaussianfit.MainForm;

import ij.plugin.*;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.SciJavaPlugin;

/**
 *
 * @author nico
 */
@org.scijava.plugin.Plugin(type = MenuPlugin.class)
public class GaussianTrack_ implements PlugIn, MenuPlugin, SciJavaPlugin {
    public static final String menuName = "Localization Microscopy";
    public static final String tooltipDescription =
       "Toolbox for analyzing spots using Gaussian fitting";

    private MainForm theForm_;

   @Override
   public String getName() {
      return menuName;
   }

   @Override
   public String getSubMenu() {
      return "Acquisition Tools";
   }

    @Override
    public void run(String arg) {
      if (!MainForm.WINDOWOPEN) {
         theForm_ = new MainForm();
      }
      theForm_.setVisible(true);
      /*
      if (gui_ != null) {
         theForm_.setBackground(gui_.getBackgroundColor());
         gui_.addMMBackgroundListener(theForm_);
      }
       */
      theForm_.formWindowOpened();
      theForm_.toFront();
   }



   @Override
   public void setContext(Studio app) {      
   }

   @Override
   public void onPluginSelected() {
      run("");
   }

   public void dispose() {
      if (theForm_ != null)
         theForm_.dispose();
   }

   @Override
   public String getHelpText() {
      return "Gaussian Fitting Plugin";
   }

   @Override
   public String getVersion() {
      return "0.32";
   }

   @Override
   public String getCopyright() {
      return "University of California, 2010-2014";
   }

}
