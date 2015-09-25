///////////////////////////////////////////////////////////////////////////////
//FILE:          ProjectorPlugin.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Projector plugin
//-----------------------------------------------------------------------------
//AUTHOR:        Arthur Edelstein
//COPYRIGHT:     University of California, San Francisco, 2010-2014
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

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


// The Projector plugin provides a user interface for calibration and control
// of SLM- and Galvo-based phototargeting devices. Phototargeting can be
// ad-hoc or planned as part of a multi-dimensional acquisition.
public class ProjectorPlugin implements MMPlugin {
   public static final String menuName = "Projector";
   public static final String tooltipDescription =
      "Control galvo or SLM devices that project a spot or pattern " +
      "on the sample";
   
   private ScriptInterface app_;
   private CMMCore core_;
   private ProjectorControlForm form_;

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
   
    @Override
    public void dispose() {
        if (form_ != null) {
            form_.dispose();
        }
    }

   @Override
   public void setApp(ScriptInterface app) {
      app_ = app;
      core_ = app_.getMMCore();
   }

   // Instantiate the ProjectorControlForm window if necessary, and show it
   // if it's not visible.
   @Override
   public void show() {
      if (core_.getSLMDevice().length()==0 && core_.getGalvoDevice().length()==0) {
         ReportingUtils.showMessage("Please load an SLM (Spatial Light Modulator) " +
               "or a Galvo-based phototargeting device " +
               "before using the Projector plugin.");
         return;
      }
      form_ = ProjectorControlForm.showSingleton(core_, app_);
   }

   public void configurationChanged() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }

   @Override
   public String getInfo() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getVersion() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getCopyright() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

}
