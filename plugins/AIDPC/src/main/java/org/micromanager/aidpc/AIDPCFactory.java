package org.micromanager.aidpc;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;

class AIDPCFactory implements ProcessorFactory {
   private final PropertyMap settings_;
   private final Studio studio_;

   public AIDPCFactory(PropertyMap settings, Studio studio) {
      settings_ = settings;
      studio_ = studio;
   }

   @Override
   public Processor createProcessor() {
      return new AIDPCProcessor(settings_, studio_);
   }
}
