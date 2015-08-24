package org.micromanager.events.internal;

import java.util.List;

import org.micromanager.data.ProcessorFactory;


// This class represents the modification of the image processing pipeline.
public class PipelineEvent {
   private final List<ProcessorFactory> factories_;
   public PipelineEvent(List<ProcessorFactory> factories) {
      factories_ = factories;
   }

   public List<ProcessorFactory> getPipelineFactories() {
      return factories_;
   }
}
