package org.micromanager.multicamera;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;


public class MultiCamera implements MMPlugin {
   public static final String menuName = "Multi-Andor Control";
   public static final String tooltipDescription =
      "Control settings for one or more Andor EM-CCD cameras via the " +
      "Multi Camera device"; 

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
            gui_.addMMListener(myFrame_);
            gui_.addMMBackgroundListener(myFrame_);
         } catch (Exception e) {
            ReportingUtils.showError(e);
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
         String ig = "Andor Control";
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
      return "0.12";
   }
   
    @Override
   public String getCopyright() {
      return "University of California, 2010, 2011";
   }
}
