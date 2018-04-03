/**
 * StageDisplay plugin
 * 
 * Author: Jon Daniels (ASI)
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

package org.micromanager.stagedisplay;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


/**
 * @author jon
 */
public class StageDisplay implements MMPlugin {
   public static final String menuName = "Stage Display";
   public static final String tooltipDescription =
      "A virtual LCD for position readout of the current XY and Z stages";

   private ScriptInterface gui_;
   private static StageDisplayFrame myFrame_ = null;

   @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;
      if (myFrame_ == null) {
         myFrame_ = new StageDisplayFrame(gui_);
         myFrame_.setBackground(gui_.getBackgroundColor());
         gui_.addMMBackgroundListener(myFrame_);
         gui_.addMMListener(myFrame_);
      } else {
         // frame has been opened and then closed and so we need to restart the timer
         // (N.B. when user closes window we intentionally dispose to fully stop the timer)
         myFrame_.refreshTimer();
      }
      myFrame_.setVisible(true);
   }

   @Override
   public void dispose() {
      if (myFrame_ != null)
         myFrame_.dispose();
   }

   @Override
   public void show() {
      @SuppressWarnings("unused")
      String ig = "Stage Display";
   }

   @Override
   public String getInfo () {
      return "Stage Display Plugin";
   }

   @Override
   public String getDescription() {
      return tooltipDescription;
   }
   
   @Override
   public String getVersion() {
      return "0.1";
   }
   
   @Override
   public String getCopyright() {
      return "Applied Scientific Instrumentation, 2017";
   }
}

