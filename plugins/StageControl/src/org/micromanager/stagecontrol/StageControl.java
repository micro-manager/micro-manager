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

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class StageControl implements MMPlugin {
   public static final String menuName = "Stage Control";
   public static final String tooltipDescription =
      "A virtual joystick for manual control of the current XY and Z stages";

   private ScriptInterface gui_;
   private StageControlFrame myFrame_;

   @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;       
      if (myFrame_ == null) {
         myFrame_ = new StageControlFrame(gui_);
         myFrame_.setBackground(gui_.getBackgroundColor());
         gui_.addMMBackgroundListener(myFrame_);
         gui_.addMMListener(myFrame_);
      }
      myFrame_.initialize();
      myFrame_.setVisible(true);
   }

   @Override
   public void dispose() {
      // nothing todo:
   }

   @Override
   public void show() {
         String ig = "Stage Control";
   }

   @Override
   public String getInfo () {
      return "Stage Control Plugin";
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }
   
   @Override
   public String getVersion() {
      return "First version";
   }
   
   @Override
   public String getCopyright() {
      return "University of California, 2010";
   }
}
