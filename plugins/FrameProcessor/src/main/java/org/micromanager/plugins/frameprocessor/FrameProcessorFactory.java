package org.micromanager.plugins.frameprocessor;

import org.micromanager.LogManager;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

public class FrameProcessorFactory implements ProcessorFactory {

   private final Studio studio_;
   private final PropertyMap settings_;
   private final LogManager log_;

   public FrameProcessorFactory(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
      log_ = studio_.logs();
   }

   @Override
   public Processor createProcessor() {
      // log_.logMessage("FrameProcessor : Create FrameProcessorProcessor");
      return new FrameProcessor(studio_,
              settings_.getString("processorAlgo", FrameProcessorPlugin.PROCESSOR_ALGO_MEAN),
              settings_.getInt("numerOfImagesToProcess", 10),
              settings_.getBoolean("enableDuringAcquisition", true),
              settings_.getBoolean("enableDuringLive", true),
              settings_.getString("channelsToAvoid", ""));
   }
}
