/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.utils;

import java.util.prefs.Preferences;

/**
 *
 * @author Henry
 * This class holds the contrast settings of the snap/live window
 * specific to pixel type
 */
public class SnapLiveContrastSettings {
   private static final String CHANNEL = "_CHANNEL";
   private static final String GAMMA = "_GAMMA";
   private static final String MIN = "_MIN";
   private static final String MAX = "_MAX";
   
   
   private Preferences prefs_;
   
   public SnapLiveContrastSettings() {
       prefs_ = Preferences.userNodeForPackage(this.getClass());
   }
   
   public void saveSettings(ContrastSettings c, int channel, String pixelType ) {      
      if (!isValidPixelType(pixelType))
         return;
      prefs_.putDouble(pixelType + CHANNEL + channel + GAMMA, c.gamma);
      prefs_.putInt(pixelType + CHANNEL + channel + MAX, c.max);
      prefs_.putInt(pixelType + CHANNEL + channel + MIN, c.min);
   }
   
   private int loadMin(String pixelType, int channel) throws MMException {
      if (!isValidPixelType(pixelType))
         throw new MMException("Invalid pixel type");
      return prefs_.getInt(pixelType+CHANNEL + channel + MIN, 0);
   }
   
   private int loadMax(String pixelType, int channel) throws MMException {
      if (!isValidPixelType(pixelType))
         throw new MMException("Invalid pixel type");
      int defaultVal;
      if ( pixelType.startsWith("GRAY")) {
         if (pixelType.equals("GRAY8"))
            defaultVal = 255;
         else 
            defaultVal = 65535;    
      } else if (pixelType.equals("RGB32"))
         defaultVal = 255;
      else
         defaultVal = 65535;
      
      return prefs_.getInt(pixelType+CHANNEL + channel + MAX, defaultVal);
   }
   
   private double loadGamma(String pixelType,int channel) throws MMException {
      if (!isValidPixelType(pixelType))
         throw new MMException("Invalid pixel type");
      return prefs_.getDouble(pixelType +CHANNEL + channel +  GAMMA, 1.0);
   }
    
   public ContrastSettings loadSettings(String pixelType, int channel)throws MMException {
      return new ContrastSettings(loadMin(pixelType,channel),
              loadMax(pixelType,channel),loadGamma(pixelType,channel));
   }
   
   private boolean isValidPixelType(String type) {
      if (type.equals("GRAY8") || type.equals("GRAY16") || type.equals("GRAY32") 
              || type.equals("RGB32") || type.equals("RGB64"))
         return true;
      return false;
   }
  
}
