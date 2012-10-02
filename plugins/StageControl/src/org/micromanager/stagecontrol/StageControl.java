/**
 * StageControl plugin
 * 
 * This Micro-Manager plugin provides a simple interface to the currently 
 * active XY stage and Z (focus) drive
 *
 * Created on Aug 19, 2010, 10:04:49 PM
 * Nico Stuurman, copyright UCSF, 2010
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
 **/

package org.micromanager.stagecontrol;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class StageControl implements MMPlugin {
   public static String menuName = "Stage Control";
   public static String tooltipDescription = "A virtual joystick that allows for manual control"
		   +" of the XY and Z stages";
   private CMMCore core_;
   private ScriptInterface gui_;
   private StageControlFrame myFrame_;

   public void setApp(ScriptInterface app) {
      gui_ = app;                                        
      core_ = app.getMMCore();
      if (myFrame_ == null)
         myFrame_ = new StageControlFrame(gui_);
      myFrame_.setVisible(true);
   }

   public void dispose() {
      // nothing todo:
   }

   public void show() {
         String ig = "Stage Control";
   }

   public void configurationChanged() {
   }

   public String getInfo () {
      return "Stage Control Plugin";
   }

   public String getDescription() {
      return tooltipDescription;
   }
   
   public String getVersion() {
      return "First version";
   }
   
   public String getCopyright() {
      return "University of California, 2010";
   }
}
