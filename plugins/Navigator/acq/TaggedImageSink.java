package acq;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import mmcorej.TaggedImage;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Dequeue tagged images and append to image cache
 *
 * copied from MM DefaultTaggedImageQueue
 */
public class TaggedImageSink  {

   private final BlockingQueue<TaggedImage> imageProducingQueue_;
   private ImageCache imageCache_ = null;
   private volatile String lastImageLabel_;

   public TaggedImageSink(BlockingQueue<TaggedImage> imageProducingQueue,
           ImageCache imageCache) {
      imageProducingQueue_ = imageProducingQueue;
      imageCache_ = imageCache;
   }
   
   public String getLastImageLabel() {
      return lastImageLabel_;
   }
   
   public void start() {
      Thread savingThread = new Thread("tagged image sink thread") {

         @Override
         public void run() {
            long t1 = System.currentTimeMillis();
            int imageCount = 0;
            try {
               while (true) {
                  TaggedImage image = imageProducingQueue_.poll(1, TimeUnit.SECONDS);
                  if (image != null) {
                     if (TaggedImageQueue.isPoison(image)) {
                        break;
                     }
                     ++imageCount;
                     imageCache_.putImage(image); 
                     //since MultiRes storage waits for writing to finish before returning, lastImageLabel_
                     //will reflect only indicate images that have been fully written to disk
                     lastImageLabel_ = MDUtils.getLabel(image.tags);
                     }
               }
            } catch (Exception ex2) {
               ReportingUtils.logError(ex2);
            }
            long t2 = System.currentTimeMillis();
            ReportingUtils.logMessage(imageCount + " images stored in " + (t2 - t1) + " ms.");
            imageCache_.finished();
         }
      };
      savingThread.start();
   }

}
