/**
 *
 * @author kthorn
 */

package org.micromanager.pairedstagecontrol;

import java.util.Iterator;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = MenuPlugin.class)
public class PairedStageControl implements MenuPlugin, SciJavaPlugin {
   public static final String menuName = "Paired Stage Control";
   public static final String tooltipDescription =
         "Combines the movement of two stages through the Utilities-MultiStage device.";
   private Studio studio_;
   private PairedStageControlFrame myFrame_;

   @Override
   public String getSubMenu() {
      return "Device Control";
   }

   @Override
   public void onPluginSelected() {
      if (myFrame_ == null) {
         String multiStageName = "";
         //Find multi stage device
         StrVector stages = studio_.core().getLoadedDevicesOfType(DeviceType.StageDevice);
         final Iterator<String> stageIter = stages.iterator();
         while (stageIter.hasNext()) {
            String devName = "";
            String devLabel = stageIter.next();
            try {
               devName = studio_.core().getDeviceName(devLabel);
            } catch (Exception ex) {
               studio_.logs().logError(ex, "Error when requesting stage name");
            }
            if (devName.equals("Multi Stage")) {
               multiStageName = devLabel;
            }
         }
         if (multiStageName.isEmpty()) {
            studio_.logs().showError("Cannot find multi stage device. "
                  + "This plugin does not work without one. "
                  + "Use the Hardware Configuration wizard and select Utilities > MultiStage");
            return;
         }
         myFrame_ = new PairedStageControlFrame(studio_, multiStageName);
      }
      myFrame_.setVisible(true);
   }

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return menuName;
   }

   @Override
   public String getHelpText() {
      return "Plugin for controlling AZ100 light sheet microscope in the Nikon Imaging Center,"
            + " UCSF. See Kurt Thorn with questions.";
   }

   @Override
   public String getVersion() {
      return "v0.2";
   }

   @Override
   public String getCopyright() {
      return "University of California, 2016, 2024";
   }
}