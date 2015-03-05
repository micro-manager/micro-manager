package org.micromanager.CRISP;

import org.micromanager.MMPlugin;
import org.micromanager.ScriptInterface;

/**
 *
 * @author Nico Stuurman
 */

public class CRISP implements MMPlugin {
   public static final String menuName = "ASI CRISP Control";
   public static final String tooltipDescription =
      "Control the ASI CRISP Autofocus System";
   @SuppressWarnings("unused")
   private ScriptInterface gui_;
   private CRISPFrame myFrame_;

    @Override
   public void setApp(ScriptInterface app) {
      gui_ = app;
      if (myFrame_ == null) {
         try {
            myFrame_ = new CRISPFrame(gui_);
         } catch (Exception e) {
            gui_.logs().logError(e);
            return;
         }
      }
      myFrame_.setVisible(true);
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

   @Override
   public void dispose() {
      // nothing to do....
   }
}
