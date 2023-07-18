package org.micromanager.deskew;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

@Plugin(type = ProcessorPlugin.class)
public class Deskew implements ProcessorPlugin, SciJavaPlugin {
   private static final String MENU_NAME = "Deskew";
   private static final String TOOL_TIP_DESCRIPTION =
            "Deskews Z-stacks";
   private static final String VERSION = "0.1";


   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return MENU_NAME;
   }

   @Override
   public String getHelpText() {
      return TOOL_TIP_DESCRIPTION;
   }

   @Override
   public String getVersion() {
      return VERSION;
   }

   @Override
   public String getCopyright() {
      return "Altos Labs, 2023";
   }

   @Override
   public ProcessorConfigurator createConfigurator(PropertyMap settings) {
      return new DeskewFrame(settings, studio_);
   }

   @Override
   public ProcessorFactory createFactory(PropertyMap settings) {
      return new DeskewFactory(settings);
   }
}
