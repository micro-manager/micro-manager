package org.micromanager.multicamera;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;


public class MultiCamera implements MMPlugin {
   public static String menuName = "Multi Camera";
   public static String tooltipDescription = "Plugin that allows you to use multiple cameras simultanuously"; 
   private CMMCore core_;
   private ScriptInterface gui_;
   private MultiCameraFrame myFrame_;

    @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;                                        
      core_ = app.getMMCore();
      if (myFrame_ == null) {
         try {
            myFrame_ = new MultiCameraFrame(gui_);
            gui_.addMMBackgroundListener(myFrame_);
         } catch (Exception e) {
            e.printStackTrace();
            return;
         }
      }
      myFrame_.setVisible(true);
   }

    @Override
   public void dispose() {
      if (myFrame_ != null)
         myFrame_.safePrefs();
   }

    @Override
   public void show() {
         String ig = "Multi Camera";
   }

    @Override
   public void configurationChanged() {
   }

    @Override
   public String getInfo () {
      return "Multi Camera Plugin";
   }

    @Override
   public String getDescription() {
      return tooltipDescription;
   }
   
    @Override
   public String getVersion() {
      return "0.11";
   }
   
    @Override
   public String getCopyright() {
      return "University of California, 2010, 2011";
   }
}
