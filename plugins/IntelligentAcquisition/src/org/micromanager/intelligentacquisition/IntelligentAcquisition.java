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
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class IntelligentAcquisition implements MMPlugin {
   public static final String menuName = "Intelligent Acquisition";
   public static final String tooltipDescription =
      "Use image analysis to drive image acquisition";

   private CMMCore core_;
   private ScriptInterface gui_;
   private IntelligentAcquisitionFrame myFrame_;

   public void setApp(ScriptInterface app) {
      gui_ = app;                                        
      core_ = app.getMMCore();
      if (myFrame_ == null)
         myFrame_ = new IntelligentAcquisitionFrame(gui_);
      myFrame_.setVisible(true);
      
      myFrame_.setBackground(gui_.getBackgroundColor());
      
      // Used to change the background layout of the form.  Does not work on Windows
      gui_.addMMBackgroundListener(myFrame_);
   }

   public void dispose() {
      myFrame_.closeWindow();
   }

   public void show() {
   }

   public void configurationChanged() {
   }

   public String getInfo () {
      return tooltipDescription;
   }

   public String getDescription() {
      return tooltipDescription;
   }
   
   public String getVersion() {
      return "1.0";
   }
   
   public String getCopyright() {
      return "University of California, 2012";
   }
}
