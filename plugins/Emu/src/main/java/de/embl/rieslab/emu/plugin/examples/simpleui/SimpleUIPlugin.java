package de.embl.rieslab.emu.plugin.examples.simpleui;


import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.plugin.UIPlugin;
import de.embl.rieslab.emu.ui.ConfigurableMainFrame;
import java.util.TreeMap;


public class SimpleUIPlugin implements UIPlugin {

   @Override
   public ConfigurableMainFrame getMainFrame(SystemController controller,
                                             TreeMap<String, String> pluginSettings) {
      return new SimpleUIFrame("Simple UI", controller, pluginSettings);
   }

   @Override
   public String getName() {
      return "Simple UI";
   }
}
