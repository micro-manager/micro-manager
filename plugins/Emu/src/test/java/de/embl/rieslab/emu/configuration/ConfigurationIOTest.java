package de.embl.rieslab.emu.configuration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TreeMap;

import org.junit.Test;

import de.embl.rieslab.emu.configuration.data.GlobalConfiguration;
import de.embl.rieslab.emu.configuration.data.GlobalConfigurationWrapper;
import de.embl.rieslab.emu.configuration.data.PluginConfiguration;
import de.embl.rieslab.emu.configuration.io.ConfigurationIO;
import de.embl.rieslab.emu.controller.utils.GlobalSettings;

public class ConfigurationIOTest {

  @Test
  public void testWriteReadConfiguration() {
    HashMap<String, String> properties = new HashMap<String, String>();
    HashMap<String, String> parameters = new HashMap<String, String>();
    HashMap<String, String> plugsettings = new HashMap<String, String>();
    TreeMap<String, String> globalSettingsMap = new TreeMap<String, String>();

    // global settings
    final String globsett1 = "GlobSett1";
    final String globsett2 = "GlobSett2";
    final String globsettVal1 = "GlobSett Val1";
    final String globsettVal2 = "GlobSett Val2";
    globalSettingsMap.put(globsett1, globsettVal1);
    globalSettingsMap.put(globsett2, globsettVal2);

    // properties
    final String prop1 = "Prop1";
    final String prop2 = "Prop2";
    final String prop3 = "Prop3";
    final String mmprop1 = "MM Prop1";
    final String mmprop2 = "MM Prop2";
    final String mmprop3 = "MM Prop3";
    properties.put(prop1, mmprop1);
    properties.put(prop2, mmprop2);
    properties.put(prop3, mmprop3);

    // parameters
    final String param1 = "Param1";
    final String param2 = "Param2";
    final String paramVal1 = "Param Val1";
    final String paramVal2 = "Param Val2";
    parameters.put(param1, paramVal1);
    parameters.put(param2, paramVal2);

    // plugin settings
    final String sett1 = "Sett1";
    final String sett2 = "Sett2";
    final String settVal1 = "Sett Val1";
    final String settVal2 = "Sett Val2";
    plugsettings.put(sett1, settVal1);
    plugsettings.put(sett2, settVal2);

    final String plugin = "MyPlugin";
    final String config = "MyConfig";

    PluginConfiguration mypluginconfig = new PluginConfiguration();
    mypluginconfig.configure(config, plugin, properties, parameters, plugsettings);

    GlobalConfigurationWrapper globConfigWrapper = new GlobalConfigurationWrapper();
    globConfigWrapper.setDefaultConfigurationName(config);
    globConfigWrapper.setGlobalSettings(globalSettingsMap);

    ArrayList<PluginConfiguration> list = new ArrayList<PluginConfiguration>();
    list.add(mypluginconfig);
    globConfigWrapper.setPluginConfigurations(list);

    // Test writing
    final String path = "TestConfigIO." + GlobalSettings.CONFIG_EXT;
    boolean write =
        ConfigurationIO.write(new File(path), new GlobalConfiguration(globConfigWrapper));
    assertTrue(write);

    // Test reading
    GlobalConfiguration readconfig = ConfigurationIO.read(new File(path));

    // tests if the configurations match
    assertTrue(readconfig.doesConfigurationExist(config));
    assertEquals(config, readconfig.getCurrentConfigurationName());

    ArrayList<PluginConfiguration> newlist = readconfig.getPluginConfigurations();
    assertEquals(list.size(), newlist.size());

    PluginConfiguration newpluginconfig = newlist.get(0);
    assertEquals(config, newpluginconfig.getConfigurationName());
    assertEquals(plugin, newpluginconfig.getPluginName());

    // properties
    Map<String, String> mapprop = newpluginconfig.getProperties();
    assertEquals(properties.size(), mapprop.size());

    Iterator<String> it = properties.keySet().iterator();
    while (it.hasNext()) {
      String s = it.next();
      assertTrue(mapprop.containsKey(s));
      assertEquals(properties.get(s), mapprop.get(s));
    }

    // parameters
    Map<String, String> mapparam = newpluginconfig.getParameters();
    assertEquals(parameters.size(), mapparam.size());

    it = parameters.keySet().iterator();
    while (it.hasNext()) {
      String s = it.next();
      assertTrue(mapparam.containsKey(s));
      assertEquals(parameters.get(s), mapparam.get(s));
    }

    // settings
    Map<String, String> mapsett = newpluginconfig.getPluginSettings();
    assertEquals(plugsettings.size(), mapsett.size());

    it = plugsettings.keySet().iterator();
    while (it.hasNext()) {
      String s = it.next();
      assertTrue(mapsett.containsKey(s));
      assertEquals(plugsettings.get(s), mapsett.get(s));
    }

    // global settings
    Map<String, String> mapglobsett = readconfig.getGlobalSettings();
    assertEquals(globalSettingsMap.size(), mapglobsett.size());

    it = globalSettingsMap.keySet().iterator();
    while (it.hasNext()) {
      String s = it.next();
      assertTrue(mapglobsett.containsKey(s));
      assertEquals(globalSettingsMap.get(s), mapglobsett.get(s));
    }
  }
}
