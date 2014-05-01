package org.micromanager.events;

import java.util.List;

import mmcorej.TaggedImage;

import org.micromanager.api.DataProcessor;


// This class represents the modification of the DataProcessor image pipeline.
public class PipelineEvent {
   private List<DataProcessor<TaggedImage>> pipeline_; 
   public PipelineEvent(List<DataProcessor<TaggedImage>> pipeline) {
      pipeline_ = pipeline;
   }

   public List<DataProcessor<TaggedImage>> getPipeline() {
      return pipeline_;
   }
}
