package org.micromanager.acqj.api;

import java.util.concurrent.LinkedBlockingDeque;
import org.micromanager.acqj.api.ImageAcqTuple;


public interface TaggedImageProcessor {

   /**
    * Class for modifying/adding/deleting images after they're acquired before 
    * saving. Expects the implementing class to be pulling data off of the front
    * of the source Dequeue, and then optionally adding it to the end of the sink
    * Dequeue. This method will get called by the aquisition engine immediately
    * after adding the TaggedImageProcessor.
    * 
    * @param source
    * @param sink 
    */
   public void setDequeues(LinkedBlockingDeque<ImageAcqTuple> source,
           LinkedBlockingDeque<ImageAcqTuple> sink);

}