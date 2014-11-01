package org.micromanager.events;

import java.util.ArrayList;
import java.util.List;

import mmcorej.TaggedImage;

import org.micromanager.api.DataProcessor;


// This class represents the modification of the DataProcessor image pipeline.
public class PipelineEvent {
   final private List<DataProcessor<TaggedImage>> pipeline_;
   public PipelineEvent(List<DataProcessor<TaggedImage>> pipeline) {
      pipeline_ = pipeline;
   }

   public List<DataProcessor<TaggedImage>> getPipeline() {
      return new ArrayList<DataProcessor<TaggedImage>>(pipeline_);
   }
}
