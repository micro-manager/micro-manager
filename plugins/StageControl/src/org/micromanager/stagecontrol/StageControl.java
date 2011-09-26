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
