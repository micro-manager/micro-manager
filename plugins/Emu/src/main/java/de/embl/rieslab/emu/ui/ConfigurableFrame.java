package de.embl.rieslab.emu.ui;

import de.embl.rieslab.emu.ui.uiparameters.UIParameter;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.utils.settings.Setting;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Interface giving access to a list of {@link ConfigurablePanel}s and maps of
 * UIProperties and UIparameters.
 *
 * @author Joran Deschamps
 */
public interface ConfigurableFrame {

   ArrayList<ConfigurablePanel> getConfigurablePanels();

   HashMap<String, UIProperty> getUIProperties();

   @SuppressWarnings("rawtypes")
   HashMap<String, UIParameter> getUIParameters();

   @SuppressWarnings("rawtypes")
   HashMap<String, Setting> getDefaultPluginSettings();

   @SuppressWarnings("rawtypes")
   HashMap<String, Setting> getCurrentPluginSettings();

}
