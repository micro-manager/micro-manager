package org.micromanager.plugins.framecombiner;

import org.micromanager.LogManager;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

public class FrameCombinerFactory implements ProcessorFactory {

   private final Studio studio_;
   private final PropertyMap settings_;
   private final LogManager log_;

   public FrameCombinerFactory(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
      log_ = studio_.logs();
   }

   @Override
   public Processor createProcessor() {
      // log_.logMessage("FrameCombiner : Create FrameCombinerProcessor");
      return new FrameCombiner(studio_,
              settings_.getString("processorDimension", FrameCombinerPlugin.PROCESSOR_DIMENSION_TIME),
              settings_.getString("processorAlgo", FrameCombinerPlugin.PROCESSOR_ALGO_MEAN),
              settings_.getInt("numerOfImagesToProcess", 10),
              settings_.getString("channelsToAvoid", ""));
   }
}
