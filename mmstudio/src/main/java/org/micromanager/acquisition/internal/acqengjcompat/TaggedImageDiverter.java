package org.micromanager.acquisition.internal.acqengjcompat;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingDeque;
import mmcorej.TaggedImage;
import org.micromanager.acqj.api.TaggedImageProcessor;

/**
 * Simple processor class for diverting images away from AcqEngJ and into an
 * alternate processing/saving system.
 */
public class TaggedImageDiverter implements TaggedImageProcessor {

   private BlockingQueue<TaggedImage> diverter_;

   @Override
   public void setDequeues(LinkedBlockingDeque<TaggedImage> source,
                           LinkedBlockingDeque<TaggedImage> sink) {
      diverter_ = source;
   }

   public BlockingQueue<TaggedImage> getQueue() {
      return diverter_;
   }
}
