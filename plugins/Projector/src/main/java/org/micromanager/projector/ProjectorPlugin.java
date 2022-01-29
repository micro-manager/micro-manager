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

import mmcorej.CMMCore;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.micromanager.projector.internal.ProjectorControlForm;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/** The Projector plugin provides a user interface for calibration and control
 * of SLM- and Galvo-based phototargeting devices. Phototargeting can be
 * ad-hoc or planned as part of a multi-dimensional acquisition.
 */
@Plugin(type = MenuPlugin.class)
public class ProjectorPlugin implements MenuPlugin, SciJavaPlugin {
   public static final String MENUNAME = "Projector";
   public static final String TOOLTIP_DESCRIPTION =
         "Control galvo or SLM devices that project a spot or pattern "
               + "on the sample";
   
   private Studio app_;
   private CMMCore core_;

   /**
    * Disposes of the Projector control form.
    */
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

   // Instantiate the ProjectorControlForm window if necessary, and show it
   // if it's not visible.
   @Override
   public void onPluginSelected() {
      if (core_.getSLMDevice().length() == 0 && core_.getGalvoDevice().length() == 0) {
         app_.logs().showMessage("Please load an SLM (Spatial Light Modulator) "
               + "or a Galvo-based phototargeting device "
               + "before using the Projector plugin.");
         return;
      }
      ProjectorControlForm.showSingleton(core_, app_);
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
      throw new UnsupportedOperationException("Not supported yet.");
   }
}
