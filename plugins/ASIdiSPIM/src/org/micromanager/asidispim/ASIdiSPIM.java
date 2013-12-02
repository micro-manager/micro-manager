package org.micromanager.asidispim;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;


public class ASIdiSPIM implements MMPlugin {
   public static String menuName = "ASI diSPIM";
   public static String tooltipDescription = "Control the ASI diSPIM ";
   private CMMCore core_;
   private ScriptInterface gui_;
   private ASIdiSPIMFrame myFrame_;

    @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;                                        
      core_ = app.getMMCore();
     // if (myFrame_ == null) {
         try {
            myFrame_ = new ASIdiSPIMFrame(gui_);
            gui_.addMMListener(myFrame_);
            gui_.addMMBackgroundListener(myFrame_);
         } catch (Exception e) {
            ReportingUtils.showError(e);
         }
      //}
      myFrame_.setVisible(true);
   }

    @Override
   public void dispose() {
      //if (myFrame_ != null)
         //myFrame_.safePrefs();
   }

    @Override
   public void show() {
         String ig = "ASI diSPIM";
   }

    @Override
   public String getInfo () {
      return "ASI diSPIM";
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
      return "University of California and ASI, 2013";
   }
}
