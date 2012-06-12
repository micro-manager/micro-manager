/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ScriptInterface;

/**
 * This is the default setup for the acquisition engine pipeline.
 * We create a default display,
 * a DataProcessor<TaggedImage> queue,
 * and a default saving mechanism and connect them to the acqEngine.
 * Alternate setups are possible.
 */
public class DefaultTaggedImagePipeline {

   final String acqName_;
   final JSONObject summaryMetadata_;
   final ImageCache imageCache_;

   public DefaultTaggedImagePipeline(
           IAcquisitionEngine2010 acqEngine,
           SequenceSettings sequenceSettings,
           List<DataProcessor<TaggedImage>> imageProcessors,
           ScriptInterface gui,
           boolean diskCached) throws ClassNotFoundException, InstantiationException, IllegalAccessException {
      BlockingQueue<TaggedImage> taggedImageQueue = acqEngine.run(sequenceSettings);
      ProcessorStack processorStack = new ProcessorStack((BlockingQueue) taggedImageQueue, imageProcessors);
      BlockingQueue<TaggedImage> taggedImageQueue2 = processorStack.begin();
      summaryMetadata_ = acqEngine.getSummaryMetadata();
      LiveAcq liveAcq = new LiveAcq(taggedImageQueue2,
              summaryMetadata_,
              gui,
              diskCached);
      liveAcq.start();
      imageCache_ = liveAcq.getImageCache();
      acqName_ = liveAcq.getAcquisitionName();
   }
}
