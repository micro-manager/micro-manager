package org.micromanager.aidpc;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Micro-Manager Processor plugin to create asymmetric illumination differential
 * phase contrast (AIDPC) image.
 *
 * @author nico
 */
@Plugin(type = ProcessorPlugin.class)
public class AIDPCProcessorPlugin implements ProcessorPlugin, SciJavaPlugin {
   private Studio studio_;
   static final String CHANNEL1 = "channel1";
   static final String CHANNEL2 = "channel2";
   static final String INCLUDE_AVG = "includeAvg";

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public ProcessorConfigurator createConfigurator(PropertyMap settings) {
      AIDPCConfigurator configurator = new AIDPCConfigurator(settings, studio_);
      studio_.events().registerForEvents(configurator);
      return configurator;
   }

   @Override
   public ProcessorFactory createFactory(PropertyMap settings) {
      return new AIDPCFactory(settings, studio_);
   }

   @Override
   public String getName() {
      return "AIDPC Processor";
   }

   @Override
   public String getHelpText() {
      return "Calculates Differential Phase Contrast from two channels using formula: "
             + "2*(I_R - I_L)/(I_R + I_L)";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Regents of the University of California, 2024";
   }
}
