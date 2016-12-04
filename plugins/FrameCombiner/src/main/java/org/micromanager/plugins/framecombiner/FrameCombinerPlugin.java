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

   public static String menuName = "Frame Combiner";
   public static String tooltipDescription = "Combine multiple images into a single output image (mean/sum/max/min)";
   public static String versionNumber = "1.0";
   public static String copyright = "Hadrien Mary";

   public static String PROCESSOR_ALGO_MEAN = "Mean";
   //public static String PROCESSOR_ALGO_MEDIAN = "Median";
   public static String PROCESSOR_ALGO_SUM = "Sum";
   public static String PROCESSOR_ALGO_MAX = "Max";
   public static String PROCESSOR_ALGO_MIN = "Min";
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
      return menuName;
   }

   @Override
   public String getHelpText() {
      return tooltipDescription;
   }

   @Override
   public String getVersion() {
      return versionNumber;
   }

   @Override
   public String getCopyright() {
      return copyright;
   }
}
