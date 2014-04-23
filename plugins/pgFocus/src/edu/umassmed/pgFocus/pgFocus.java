package edu.umassmed.pgfocus;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

import org.jfree.ui.RefineryUtilities;

/**
 *
 * @author Karl Bellve
 * Biomedical Imaging Group
 * Molecular Medicine
 * University of Massachusetts Medical School
 * 
 */

public class pgFocus implements MMPlugin {
   public static final String menuName = "pgFocus";
   public static final String tooltipDescription =
      "Control the pgFocus open-source software and open hardware " +
      "focus stabization device";

   private CMMCore core_;
   private ScriptInterface gui_;
   private pgFocusFrame myFrame_;

    @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;
      setCore_(app.getMMCore());
      if (myFrame_ == null) {
         try {
            myFrame_ = new pgFocusFrame(gui_);
            gui_.addMMBackgroundListener(myFrame_);
         } catch (Exception e) {
            e.printStackTrace();
            return;
         }
      } 
      myFrame_.pack();
      RefineryUtilities.centerFrameOnScreen(myFrame_);
      myFrame_.setVisible(true);
   }
   

    @Override
   public void dispose() {
      if (myFrame_ != null)
    	  myFrame_.safePrefs();
   }

    @Override
   public void show() {
  //       String ig = "pgFocus focus stabilization";
   }

    @Override
   public String getInfo () {
      return "pgFocus Plugin";
   }

    @Override
   public String getDescription() {
      return tooltipDescription;
   }

    @Override
   public String getVersion() {
      return "0.10";
   }

    @Override
   public String getCopyright() {
      return "(C) 2014 Karl Bellve, Biomedical Imaging Group, Molecular Medicine, Umass Medical School";
   }


	public CMMCore getCore_() {
		return core_;
	}


	public void setCore_(CMMCore core_) {
		this.core_ = core_;
	}
}
