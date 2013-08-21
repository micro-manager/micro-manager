package org.micromanager.acquisition;

import java.util.List;
import java.util.concurrent.BlockingQueue;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.DataProcessor;
import org.micromanager.api.IAcquisitionEngine2010;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMScriptException;

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
   final VirtualAcquisitionDisplay display_;

   /*
    * This class creates the default sequence of modules
    * that digest a TaggedImage. They are
    * AcquisitionEngine2010 -> ProcessorStack -> DefaultTaggedImageSink -> ImageCache
    *   -> VirtualAcquisitionDisplay
    * Other kinds of pipelines can be set up in this way.
    */
   public DefaultTaggedImagePipeline(
           IAcquisitionEngine2010 acqEngine,
           SequenceSettings sequenceSettings,
           List<DataProcessor<TaggedImage>> imageProcessors,
           ScriptInterface gui,
           boolean diskCached) throws ClassNotFoundException, InstantiationException, IllegalAccessException, MMScriptException {

      // Start up the acquisition engine
      BlockingQueue<TaggedImage> engineOutputQueue = acqEngine.run(sequenceSettings, true, gui.getPositionList(), gui.getAutofocusManager().getDevice());
      summaryMetadata_ = acqEngine.getSummaryMetadata();

      // Set up the DataProcessor<TaggedImage> sequence
      BlockingQueue<TaggedImage> procStackOutputQueue = ProcessorStack.run(engineOutputQueue, imageProcessors);

      // Create the default display
      acqName_ = gui.createAcquisition(summaryMetadata_, diskCached, gui.getHideMDADisplayOption());
      MMAcquisition acq = gui.getAcquisition(acqName_);
      display_ = acq.getAcquisitionWindow();
      imageCache_ = acq.getImageCache();

      // Start pumping images into the ImageCache
      DefaultTaggedImageSink sink = new DefaultTaggedImageSink(procStackOutputQueue, imageCache_);
      sink.start();
   }

}
