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
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * The Projector plugin provides a user interface for calibration and control
 * of SLM- and Galvo-based phototargeting devices. Phototargeting can be
 * ad-hoc or planned as part of a multi-dimensional acquisition.
*/
@Plugin(type = MenuPlugin.class)
public class ProjectorPlugin implements MenuPlugin, SciJavaPlugin {
   public static final String MENUNAME = "Projector";
   public static final String TOOLTIP_DESCRIPTION =
      "Control galvo or SLM devices that project a spot or pattern " +
      "on the sample";
   
   private Studio app_;
   private CMMCore core_;

  /** Shows the ImageJ Roi Manager and return a reference to it. 
   * 
   * @return instance of the ImageJ RoiManager
   */  
   public static RoiManager showRoiManager() {
      IJ.run("ROI Manager...");
      final RoiManager roiManager = RoiManager.getInstance();
      // "Get the "Show All" checkbox and make sure it is checked.
      Checkbox checkbox = (Checkbox) ((Panel) roiManager.getComponent(1)).getComponent(9);
      checkbox.setState(true);
      // Simulated click of the "Show All" checkbox to force ImageJ
      // to show all of the ROIs.
      roiManager.itemStateChanged(new ItemEvent(checkbox, 0, null, ItemEvent.SELECTED));
      return roiManager;
   }
   
   public void dispose() {
      ProjectorControlForm pcf = ProjectorControlForm.getSingleton();
      if (pcf != null) {
         pcf.dispose();
      }
   }

   @Override
   public void setContext(Studio app) {
      app_ = app;
      core_ = app_.getCMMCore();
   }

   /**
    * Instantiate the ProjectorControlForm window if necessary, and show it.
   */
   @Override
   public void onPluginSelected() {
      getControlForm();
   }
      
   /**
    * Give plugins and scripts a way to access the singleton instance
    * of the ProjectorControlForm
    * @return singleton instance of ProjectorControlForm or null if it was not 
    *         present and it was not created due to lack of equipment.
   */
   public ProjectorControlForm getControlForm() {
      if (core_.getSLMDevice().length()==0 && core_.getGalvoDevice().length()==0) {
         app_.logs().showMessage("Please load an SLM (Spatial Light Modulator) " +
               "or a Galvo-based phototargeting device " +
               "before using the Projector plugin.");
         return null;
      }
      return ProjectorControlForm.showSingleton(core_, app_);
   }

   public void configurationChanged() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   @Override
   public String getName() {
      return MENUNAME;
   }

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public String getHelpText() {
      return TOOLTIP_DESCRIPTION;
   }

   @Override
   public String getVersion() {
      return "V0.1";
   }

   @Override
   public String getCopyright() {
      return "Copyright Regents of the University of California, 2010-2016";
   }
}
