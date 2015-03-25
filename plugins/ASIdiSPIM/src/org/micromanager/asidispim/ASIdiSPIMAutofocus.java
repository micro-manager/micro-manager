
package org.micromanager.asidispim;

import org.micromanager.api.ScriptInterface;
import org.micromanager.asidispim.Data.Devices;

/**
 *
 * @author nico
 */
public class ASIdiSPIMAutofocus {
   private final ScriptInterface gui_;
   private final Devices devices_;
   private int nrImages_ = 10;
   private double step_ = 0.2;
   
   public ASIdiSPIMAutofocus(ScriptInterface gui, Devices devices) {
      gui_ = gui;
      devices_ = devices;
   }
   
   public String getAutofocusMethod() {
      return gui_.getAutofocus().getDeviceName();
   } 
   
   public void setNrImages(int nrImages) {
      nrImages_ = nrImages;
   }
   
   public void setStep(double step) {
      step_ = step;
   }
   
   /**
    * Acquires image stack by scanning the mirror, 
    * calculates focus scores
    * @param side
    * @param nrImages
    * @param start
    * @param stepSize
    * @return 
    */
   public double runFocus(Devices.Sides side, int nrImages, 
           double start, double stepSize) {
      
      String camera = devices_.getMMDevice(Devices.Keys.CAMERAA);
      if (side.equals(Devices.Sides.B)) 
         camera = devices_.getMMDevice(Devices.Keys.CAMERAB);
      
      
      
      return 0.0;
   }
   
   
   
   
   
}
