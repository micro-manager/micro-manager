package org.micromanager.splitview;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class SplitView implements MMPlugin {
   public static String menuName = "SplitView";
   private CMMCore core_;
   private ScriptInterface gui_;
   private SplitViewFrame myFrame_;

   public void setApp(ScriptInterface app) {
      gui_ = app;                                        
      core_ = app.getMMCore();
      if (myFrame_ == null) {
         try {
            myFrame_ = new SplitViewFrame(gui_);
            gui_.addMMBackgroundListener(myFrame_);
         } catch (Exception e) {
            e.printStackTrace();
            return;
         }
      }
      myFrame_.setVisible(true);
   }

   public void dispose() {
      //if (myFrame_ != null)
         //myFrame_.safePrefs();
   }

   public void show() {
         String ig = "SplitView";
   }

   public void configurationChanged() {
   }

   public String getInfo () {
      return "SplitView Plugin";
   }

   public String getDescription() {
      return "";
   }
   
   public String getVersion() {
      return "0.1";
   }
   
   public String getCopyright() {
      return "University of California, 2011";
   }
}
