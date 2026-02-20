package org.micromanager.plugins.isim;

import mmcorej.StrVector;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class iSIM implements SciJavaPlugin, MenuPlugin {
   private Studio studio_;
   private iSIMFrame frame_;
   private String deviceLabel_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public void onPluginSelected() {
      if (frame_ == null) {
         deviceLabel_ = findDeviceLabel();
         if (deviceLabel_ == null) {
            studio_.logs().showError(
                  "No iSIMWaveforms device adapter found.\n"
                  + "Please add the iSIMWaveforms adapter in the Hardware Configuration Wizard.");
            return;
         }
         frame_ = new iSIMFrame(studio_, deviceLabel_);
      }
      frame_.setVisible(true);
   }

   private String findDeviceLabel() {
      try {
         StrVector devices = studio_.core().getLoadedDevices();
         for (int i = 0; i < devices.size(); i++) {
            String label = devices.get(i);
            try {
               if (studio_.core().getDeviceLibrary(label).equals("iSIMWaveforms")) {
                  return label;
               }
            } catch (Exception ignored) { }
         }
      } catch (Exception e) {
         studio_.logs().logError(e);
      }
      return null;
   }

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public String getName() {
      return "iSIM";
   }

   @Override
   public String getHelpText() {
      return "iSIM timing and control.";
   }

   @Override
   public String getVersion() {
      return "0.0.0";
   }

   @Override
   public String getCopyright() {
      return "The Laboratory of Experimental Biophysics (LEB), EPFL, 2026";
   }
}
