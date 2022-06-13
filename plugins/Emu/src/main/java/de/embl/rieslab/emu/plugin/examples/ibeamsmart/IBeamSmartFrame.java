package de.embl.rieslab.emu.plugin.examples.ibeamsmart;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.ui.ConfigurableMainFrame;
import de.embl.rieslab.emu.utils.settings.IntSetting;
import de.embl.rieslab.emu.utils.settings.Setting;
import java.util.HashMap;
import java.util.TreeMap;
import javax.swing.JTabbedPane;


public class IBeamSmartFrame extends ConfigurableMainFrame {

   private static final long serialVersionUID = 1L;
   private static final String SETTING_NLASERS = "Number of lasers";

   public IBeamSmartFrame(String title, SystemController controller,
                          TreeMap<String, String> pluginSettings) {
      super(title, controller, pluginSettings);
   }

   @Override
   public HashMap<String, Setting> getDefaultPluginSettings() {
      HashMap<String, Setting> sttgs = new HashMap<String, Setting>();
      sttgs.put(SETTING_NLASERS,
            new IntSetting(SETTING_NLASERS, "Number of iBeamSmart lasers.", 1));
      return sttgs;
   }

   @Override
   protected void initComponents() {

      try {
         for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
            if ("Windows".equals(info.getName())) {
               javax.swing.UIManager.setLookAndFeel(info.getClassName());
               break;
            }
         }
      } catch (ClassNotFoundException ex) {
         java.util.logging.Logger.getLogger(IBeamSmartFrame.class.getName())
               .log(java.util.logging.Level.SEVERE, null, ex);
      } catch (InstantiationException ex) {
         java.util.logging.Logger.getLogger(IBeamSmartFrame.class.getName())
               .log(java.util.logging.Level.SEVERE, null, ex);
      } catch (IllegalAccessException ex) {
         java.util.logging.Logger.getLogger(IBeamSmartFrame.class.getName())
               .log(java.util.logging.Level.SEVERE, null, ex);
      } catch (javax.swing.UnsupportedLookAndFeelException ex) {
         java.util.logging.Logger.getLogger(IBeamSmartFrame.class.getName())
               .log(java.util.logging.Level.SEVERE, null, ex);
      }

      JTabbedPane pane = new JTabbedPane();
      int N = ((IntSetting) this.getCurrentPluginSettings().get(SETTING_NLASERS)).getValue();
      for (int i = 0; i < N; i++) {
         pane.add("Laser #" + i, new IBeamSmartPanel("Laser #" + i));
      }
      this.add(pane);

      this.pack();
      this.setResizable(false);
   }

   @Override
   protected String getPluginInfo() {
      return
            "The iBeamSmart user interface was written by Joran Deschamps, EMBL (2019). It controls several iBeamSmart lasers from Toptica,"
                  + " and allows hiding/showing the fine or external trigger panels.";
   }

}
