/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class ProjectorPlugin implements MMPlugin {
   public static String menuName = "Projector";
   public static String tooltipDescription = "Plugin that allows for light to be targeted"+
   " to specific locations on the sample.  Requires a microscope with a Spatial Light Modulator";
   
   private ScriptInterface app_;
   private CMMCore core_;

   public void dispose() {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setApp(ScriptInterface app) {
      app_ = app;
      core_ = app_.getMMCore();
   }

   public void show() {
      if (core_.getSLMDevice().length()==0 && core_.getGalvoDevice().length()==0) {
         ReportingUtils.showMessage("Please load an SLM (Spatial Light Modulator) device\n" +
               "before using the Projector plugin.");
         return;
      }

      ProjectorController controller = new ProjectorController(app_);
      ProjectorControlForm form = new ProjectorControlForm(this, controller);
      form.setVisible(true);
   }

   public void configurationChanged() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getDescription() {
      return tooltipDescription;
   }

   public String getInfo() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getVersion() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getCopyright() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

}
