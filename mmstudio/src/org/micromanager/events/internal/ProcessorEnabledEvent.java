package org.micromanager.events.internal;

import mmcorej.TaggedImage;
import org.micromanager.DataProcessor;

/**
 * Event indicating DataProcessor enabled or disabled.
 */
public class ProcessorEnabledEvent {
   private final DataProcessor<?> processor_;
   private final boolean enabled_;
   public ProcessorEnabledEvent(DataProcessor<?> processor,
         boolean enabled)
   {
      processor_ = processor;
      enabled_ = enabled;
   }

   public DataProcessor<?> getProcessor() {
      return processor_;
   }

   public boolean getEnabled() {
      return enabled_;
   }
}