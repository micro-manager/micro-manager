package org.micromanager.plugins.framecombiner;

import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.ProcessorPlugin;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.scijava.plugin.SciJavaPlugin;
import org.scijava.plugin.Plugin;

@Plugin(type = ProcessorPlugin.class)
public class FrameCombinerPlugin implements ProcessorPlugin, SciJavaPlugin {

   public final static String MENU_NAME = "Frame Combiner";
   public final static String TOOL_TIP_DESCRIPTION = "Combine multiple images into a single output image (mean/sum/max/min)";
   public final static String VERSION_NUMBER = "1.0";
   public final static String COPYRIGHT = "Hadrien Mary";

   public final static String PROCESSOR_ALGO_MEAN = "Mean";
   public final static String PROCESSOR_ALGO_SUM = "Sum";
   public final static String PROCESSOR_ALGO_MAX = "Max";
   public final static String PROCESSOR_ALGO_MIN = "Min";
   public final static String PROCESSOR_DIMENSION_TIME = "Time";
   public final static String PROCESSOR_DIMENSION_Z = "Z";

   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public ProcessorConfigurator createConfigurator(PropertyMap settings) {
      return new FrameCombinerConfigurator(settings, studio_);
   }

   @Override
   public ProcessorFactory createFactory(PropertyMap settings) {
      return new FrameCombinerFactory(studio_, settings);
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
      return VERSION_NUMBER;
   }

   @Override
   public String getCopyright() {
      return COPYRIGHT;
   }
}
