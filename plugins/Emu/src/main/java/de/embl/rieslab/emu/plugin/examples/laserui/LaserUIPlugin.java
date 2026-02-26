package de.embl.rieslab.emu.plugin.examples.laserui;


import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.plugin.UIPlugin;
import de.embl.rieslab.emu.ui.ConfigurableMainFrame;
import java.util.TreeMap;

/**
 * EMU plugin that operates the power set point of an arbitrary number of lasers.
 */
public class LaserUIPlugin implements UIPlugin {

   @Override
   public ConfigurableMainFrame getMainFrame(SystemController controller,
                                             TreeMap<String, String> pluginSettings) {
      return new LaserUIFrame("Laser UI", controller, pluginSettings);
   }

   @Override
   public String getName() {
      return "Laser UI";
   }
}
