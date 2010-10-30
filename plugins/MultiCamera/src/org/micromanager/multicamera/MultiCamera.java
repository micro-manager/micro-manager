package org.micromanager.multicamera;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class MultiCamera implements MMPlugin {
   public static String menuName = "Multi Camera";
   private CMMCore core_;
   private ScriptInterface gui_;
   private MultiCameraFrame myFrame_;

   public void setApp(ScriptInterface app) {
      gui_ = app;                                        
      core_ = app.getMMCore();
      if (myFrame_ == null) {
         try {
            myFrame_ = new MultiCameraFrame(gui_);
         } catch (Exception e) {
            return;
         }
      }
      myFrame_.setVisible(true);
   }

   public void dispose() {
      // nothing todo:
   }

   public void show() {
         String ig = "Multi Camera";
   }

   public void configurationChanged() {
   }

   public String getInfo () {
      return "Multi Camera Plugin";
   }

   public String getDescription() {
      return "Plugin that send commands to multiple cameras simultanuously";
   }
   
   public String getVersion() {
      return "0.1";
   }
   
   public String getCopyright() {
      return "University of California, 2010";
   }
}
