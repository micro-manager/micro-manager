/*
 * GaussianTrack).java
 *
 * ImageJ plugin whose sole function is to invoke the MainForm UI
 *
 * Created on Sep 15, 2010, 9:29:05 PM
 *
 * @author nico
 */

import edu.valelab.GaussianFit.MainForm;

import ij.plugin.*;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author nico
 */
public class GaussianTrack_ implements PlugIn, MMPlugin {
    public static final String menuName = "Localization Microscopy";
    public static final String tooltipDescription =
       "Toolbox for analyzing spots using Gaussian fitting";

    private MainForm theForm_;

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



   public void setApp(ScriptInterface app) {
      
      run("");
   }

   public void dispose() {
      if (theForm_ != null)
         theForm_.dispose();
   }

   public void show() {
         String ig = "GaussianFit";
   }

   public void configurationChanged() {
   }

   public String getInfo () {
      return "Gaussian Fitting Plugin";
   }

   public String getDescription() {
      return "";
   }

   public String getVersion() {
      return "0.3";
   }

   public String getCopyright() {
      return "University of California, 2010-2013";
   }

}
