/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.CRISP;

import mmcorej.CMMCore;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Nico Stuurman
 */

public class CRISP implements MMPlugin {
   public static final String menuName = "ASI CRISP Control";
   public static final String tooltipDescription =
      "Control the ASI CRISP Autofocus System";
   @SuppressWarnings("unused")
   private CMMCore core_;
   private ScriptInterface gui_;
   private CRISPFrame myFrame_;

    @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;
      core_ = app.getMMCore();
      if (myFrame_ == null) {
         try {
            myFrame_ = new CRISPFrame(gui_);
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
       @SuppressWarnings("unused")
       String ig = "ASI CRISP Control";
   }

    @Override
   public String getInfo () {
      return "ASI CRISP Plugin";
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
      return "University of California, 20111";
   }
}
