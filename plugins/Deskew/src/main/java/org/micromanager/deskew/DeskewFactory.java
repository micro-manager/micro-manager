package org.micromanager.deskew;

import org.micromanager.PropertyMap;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;

public class DeskewFactory implements ProcessorFactory {
   private final PropertyMap settings_;

   public DeskewFactory(PropertyMap settings) {
      settings_ = settings;
   }

   @Override
   public Processor createProcessor() {
      return new DeskewProcessor();
   }
}