package de.embl.rieslab.emu.plugin.examples.laserui;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.ui.ConfigurableMainFrame;
import de.embl.rieslab.emu.utils.settings.IntSetting;
import de.embl.rieslab.emu.utils.settings.Setting;
import java.awt.GridLayout;
import java.util.HashMap;
import java.util.TreeMap;
import javax.swing.JPanel;

/**
 * Creates the Frame of the LaserUI plugin.  The LaserUI plugin lets the user
 * operate the set point of an arbitrary number of lasers.
 */
public class LaserUIFrame extends ConfigurableMainFrame {

   private static final long serialVersionUID = 1L;
   public static final String NUMBER_OF_LASERS = "Number of Lasers";

   public LaserUIFrame(String title, SystemController controller,
                       TreeMap<String, String> settings) {
      super(title, controller, settings);
   }

   @Override
   protected void initComponents() {

      HashMap<String, Setting> pluginSettings = getCurrentPluginSettings();
      Setting nrOfLasersSetting = pluginSettings.get(NUMBER_OF_LASERS);
      int nrOfLasers = (int) nrOfLasersSetting.getValue();

      setBounds(100, 100, nrOfLasers * 80 + 40, 350);
      getContentPane().setLayout(null);
      JPanel panel = new JPanel();

      panel.setBounds(10, 11, nrOfLasers * 80, 250);
      getContentPane().add(panel);
      panel.setLayout(new GridLayout(1, 0, 0, 0));

      for (int n = 1; n <= nrOfLasers; n++) {
         LaserPanel
               laserPanel = new LaserPanel("Laser" + n);
         panel.add(laserPanel);
      }
   }

   @Override
   public HashMap<String, Setting> getDefaultPluginSettings() {
      /*
       * In this method, declare a HashMap<String, Setting> and then add any useful
       * plugin setting. These will allow the user to configure options related to the
       * ConfigurablePanels, such as number of panels, names or enabling / disabling.
       */

      HashMap<String, Setting> settings = new HashMap<>();

      // Provide UI input for the number of lasers in the system.
      settings.put(NUMBER_OF_LASERS,
            new IntSetting(NUMBER_OF_LASERS, "Number of Lasers", 4));

      return settings;
   }

   @Override
   protected String getPluginInfo() {
      return
           "The LaserUI code was written by Nico Stuurman (2022), "
            + "based on code by Joran Deschamps, EMBL (2019),"
            + "It lets you set the output of an arbitrary number of lasers.";
   }
}