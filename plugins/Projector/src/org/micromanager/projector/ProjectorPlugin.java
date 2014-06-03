/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.projector;

import ij.IJ;
import ij.plugin.frame.RoiManager;
import java.awt.Checkbox;
import java.awt.Panel;
import java.awt.event.ItemEvent;
import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class ProjectorPlugin implements MMPlugin {
   public static final String menuName = "Projector";
   public static final String tooltipDescription =
      "Control galvo or SLM devices that project a spot or pattern " +
      "on the sample";
   
   private ScriptInterface app_;
   private CMMCore core_;

  // Show the ImageJ Roi Manager and return a reference to it.   
   public static RoiManager showRoiManager() {
      IJ.run("ROI Manager...");
      final RoiManager roiManager = RoiManager.getInstance();
      GUIUtils.recallPosition(roiManager);
      // "Get the "Show All" checkbox and make sure it is checked.
      Checkbox checkbox = (Checkbox) ((Panel) roiManager.getComponent(1)).getComponent(9);
      checkbox.setState(true);
      // Simulated click of the "Show All" checkbox to force ImageJ
      // to show all of the ROIs.
      roiManager.itemStateChanged(new ItemEvent(checkbox, 0, null, ItemEvent.SELECTED));
      return roiManager;
   }
   
   public void dispose() {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setApp(ScriptInterface app) {
      app_ = app;
      core_ = app_.getMMCore();
   }

   public void show() {
      if (core_.getSLMDevice().length()==0 && core_.getGalvoDevice().length()==0) {
         ReportingUtils.showMessage("Please load an SLM (Spatial Light Modulator) " +
               "or a Galvo-based phototargeting device " +
               "before using the Projector plugin.");
         return;
      }

      ProjectorController controller = new ProjectorController(app_);
      ProjectorControlForm form = new ProjectorControlForm(this, controller, core_);
      controller.addOnStateListener(form);
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
