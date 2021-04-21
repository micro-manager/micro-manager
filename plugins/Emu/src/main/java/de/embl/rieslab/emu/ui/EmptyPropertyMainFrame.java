package de.embl.rieslab.emu.ui;

import java.awt.Dimension;
import java.util.HashMap;
import java.util.TreeMap;

import javax.swing.JPanel;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.ui.ConfigurableMainFrame;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.utils.settings.Setting;

/**
 * Empty ConfigurableMainFrame. Used when no plugin is found.
 *
 * @author Joran Deschamps
 */
public class EmptyPropertyMainFrame extends ConfigurableMainFrame {

  private static final long serialVersionUID = 1L;

  public EmptyPropertyMainFrame(SystemController controller) {
    super("No plugin found", controller, new TreeMap<String, String>());
  }

  @Override
  protected void initComponents() {

    JPanel panel = new JPanel();
    panel.setPreferredSize(new Dimension(300, 200));
    this.add(panel);

    this.pack();
    this.setResizable(false);
    this.setVisible(true);
  }

  @Override
  public HashMap<String, UIProperty> getUIProperties() {
    return new HashMap<String, UIProperty>();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public HashMap<String, UIParameter> getUIParameters() {
    return new HashMap<String, UIParameter>();
  }

  @SuppressWarnings("rawtypes")
  @Override
  public HashMap<String, Setting> getDefaultPluginSettings() {
    return new HashMap<String, Setting>();
  }

  @Override
  protected String getPluginInfo() {
    return "";
  }
}
