package org.micromanager.recall;

import mmcorej.CMMCore;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MMScriptException;



public class RecallPlugin implements MMPlugin {
   public static String menuName = "Live Recall";
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
      try {
         String ig = "Recall";
         if (gui_.acquisitionExists(ig))
            gui_.closeAcquisition(ig);

         int remaining = core_.getRemainingImageCount();

         if (remaining < 1) {
            gui_.message ("No images in buffer");
            return;
         }

         gui_.openAcquisition(ig, "tmp", core_.getRemainingImageCount(), 1, 1, true);

         long width = core_.getImageWidth();
         long height = core_.getImageHeight();
         long depth = core_.getBytesPerPixel();

         gui_.initializeAcquisition(ig, (int) width,(int) height, (int) depth);

         try {
            gui_.addImage(ig, core_.popNextImage(), 0, 0, 0);
            gui_.setContrastBasedOnFrame(ig, 0, 0);

            for (int i=0; i < remaining-1; i++) {
               gui_.addImage(ig, core_.popNextImage(), i, 0, 0);
            }
         } catch (Exception ex){
         }
      } catch (MMScriptException e) {
      }

   }

   public void configurationChanged() {
   }

   public String getInfo () {
      return "Recalls live images remaining in internal buffer.  Set size of the buffer in options (under Tools menu)";
   }

   public String getDescription() {
      return "Recalls live images remaining in internal buffer.  Set size of the buffer in options (under Tools menu)";
   }
   
   public String getVersion() {
      return "First version";
   }
   
   public String getCopyright() {
      return "University of California, 2010";
   }
}
