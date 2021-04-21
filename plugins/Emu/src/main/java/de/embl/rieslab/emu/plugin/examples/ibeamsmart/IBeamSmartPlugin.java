package de.embl.rieslab.emu.plugin.examples.ibeamsmart;

import java.util.TreeMap;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.plugin.UIPlugin;
import de.embl.rieslab.emu.ui.ConfigurableMainFrame;

public class IBeamSmartPlugin implements UIPlugin {

  @Override
  public ConfigurableMainFrame getMainFrame(
      SystemController controller, TreeMap<String, String> pluginSettings) {
    return new IBeamSmartFrame("iBeamSmart", controller, pluginSettings);
  }

  @Override
  public String getName() {
    return "iBeamSmart control";
  }
}
