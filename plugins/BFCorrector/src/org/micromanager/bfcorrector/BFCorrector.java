/**
 * Background and flatfield correction plugin
 * 
 * This plugin will subtract background and perform flatfield correction 
 * on all images going through the image processing pipeline.
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


package org.micromanager.bfcorrector;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class BFCorrector implements MMPlugin {
   public static String menuName = "BFCorrector";
   public static String tooltipDescription = "This Micro-Manager plugin does nothing. "
		   +"Its only purpose is to be an example for developers wishing to write their own plugin.";
   private CMMCore core_;
   private ScriptInterface gui_;
   private BFCorrectorFrame myFrame_;

   public void setApp(ScriptInterface app) {
      gui_ = app;                                        
      core_ = app.getMMCore();
      if (myFrame_ == null)
         myFrame_ = new BFCorrectorFrame(gui_);
      myFrame_.setVisible(true);
      
      // Used to change the background layout of the form.  Does not work on Windows
      gui_.addMMBackgroundListener(myFrame_);
   }

   public void dispose() {
      // nothing todo:
   }

   public void show() {
   }

   public void configurationChanged() {
   }

   public String getInfo () {
      return "BFCorrector plugin";
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
