package org.micromanager.stagecontrol;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MMScriptException;



public class StageControl implements MMPlugin {
   public static String menuName = "Stage Control";
   private CMMCore core_;
   private MMStudioMainFrame gui_;

   public void setApp(ScriptInterface app) {
      gui_ = (MMStudioMainFrame) app;                                        
      core_ = app.getMMCore();                                               
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
      return "Stage Control Plugin";
   }
   
   public String getVersion() {
      return "First version";
   }
   
   public String getCopyright() {
      return "University of California, 2010";
   }
}
