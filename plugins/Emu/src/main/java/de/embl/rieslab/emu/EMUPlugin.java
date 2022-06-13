package de.embl.rieslab.emu;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.controller.utils.GlobalSettings;
import java.io.File;
import javax.swing.SwingUtilities;
import org.micromanager.MenuPlugin;
import org.micromanager.Studio;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;


@Plugin(type = MenuPlugin.class)
public class EMUPlugin implements MenuPlugin, SciJavaPlugin {

   private static Studio studio_;
   private static final String name = "EMU";
   private static final String description =
         "Easier Micro-manager User interface: loads its own UI plugins and interfaces them "
               + "with Micro-manager device properties.";
   private static final String copyright = "Joran Deschamps, EMBL, 2016-2020.";
   private static final String version = "v1.1";
   private SystemController controller_;

   @Override
   public String getCopyright() {
      return copyright;
   }

   @Override
   public String getHelpText() {
      return description;
   }

   @Override
   public String getName() {
      return name;
   }

   @Override
   public void setContext(Studio mmAPI) {
      studio_ = mmAPI;
   }

   @Override
   public String getSubMenu() {
      return "User Interface";
   }

   @Override
   public void onPluginSelected() {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            // make sure the directory EMU exist
            if (!(new File(GlobalSettings.HOME)).exists()) {
               new File(GlobalSettings.HOME).mkdirs();
            }

            controller_ = new SystemController(studio_);
            controller_.start();
         }
      });
   }

   @Override
   public String getVersion() {
      return version;
   }

}
