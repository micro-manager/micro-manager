/**
 * 
 * Nico Stuurman, 2012
 * copyright University of California
 *  
 * LICENSE:      This file is distributed under the BSD license.
 *               License text is included with the source distribution.
 *
 *               This file is distributed in the hope that it will be useful,
 *               but WITHOUT ANY WARRANTY; without even the implied warranty
 *               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *
 *               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 *               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 *               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */


package org.micromanager.intelligentacquisition;

import mmcorej.CMMCore;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;

import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class IntelligentAcquisition implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Intelligent Acquisition";
   public static final String tooltipDescription =
      "Use image analysis to drive image acquisition";

   private CMMCore core_;
   private Studio gui_;
   private IntelligentAcquisitionFrame myFrame_;

   public void setContext(Studio app) {
      gui_ = app;
      core_ = app.getCMMCore();
   }

   @Override
   public String getName() {
      return menuName;
   }

   @Override
   public String getSubMenu() {
      return "Acquisition Tools";
   }

   @Override
   public void onPluginSelected() {
      if (myFrame_ == null)
         myFrame_ = new IntelligentAcquisitionFrame(gui_);
      myFrame_.setVisible(true);
   }

   public void dispose() {
      myFrame_.closeWindow();
   }

   @Override
   public String getHelpText() {
      return tooltipDescription;
   }

   public String getVersion() {
      return "1.0";
   }
   
   public String getCopyright() {
      return "University of California, 2012";
   }
}
