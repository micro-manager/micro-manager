/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.imageflipper;

import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author arthur
 */
public class TaggedImageFlipper implements MMPlugin {
   public static String menuName = "Image Flipper";
   private ScriptInterface gui_;

   public void dispose() {
      //throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setApp(ScriptInterface app) {
      gui_ = app;
   }

   public void show() {
      ImageFlipperControls ctls = new ImageFlipperControls();
      AcquisitionEngine eng = gui_.getAcquisitionEngine();
      eng.addImageProcessor(ctls.getProcessor());
      ctls.show();
   }

   public void configurationChanged() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getDescription() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getInfo() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getVersion() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getCopyright() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

}
