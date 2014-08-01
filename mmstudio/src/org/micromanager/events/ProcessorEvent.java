package org.micromanager.events;

import org.micromanager.api.DataProcessor;


// This class represents the registration of a new DataProcessor class.
public class ProcessorEvent {
   private String name_;
   private Class<?> processorClass_;
   public ProcessorEvent(String name, Class<?> processorClass) {
      name_ = name;
      processorClass_ = processorClass;
   }

   public String getName() {
      return name_;
   }

   public Class<?> getProcessorClass() {
      return processorClass_;
   }
}
