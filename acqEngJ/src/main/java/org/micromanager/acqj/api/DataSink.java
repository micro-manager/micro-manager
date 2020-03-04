package org.micromanager.acqj.api;

import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;

/**
 * Where the acquisition sends data to. Conventionally would be a
 * display + saving to disk.
 */
public interface DataSink {

   /**
    * Called when the Acquisition is initialized 
    * @param acq
    * @param summaryMetadata 
    */
   public void initialize(AcquisitionBase acq, JSONObject summaryMetadata);   

   /**
    * No more data will be collected. Ideally should block until all resources cleaned up
    */
   public void finished();

   /**
    * Add a new image to saving/display etc
    * @param image 
    */
   public void putImage(TaggedImage image);

   /**
    * Has putImage been called yet?
    * @return 
    */
   public boolean anythingAcquired();

}
