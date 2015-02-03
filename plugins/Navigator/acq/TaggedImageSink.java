package acq;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
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
   private Thread savingThread_;
   private Acquisition acq_;
   private volatile boolean idle_;
   private Object idleLock_ = new Object();
   
   public TaggedImageSink(BlockingQueue<TaggedImage> imageProducingQueue,
           ImageCache imageCache, Acquisition acq) {
      imageProducingQueue_ = imageProducingQueue;
      imageCache_ = imageCache;
      acq_ = acq;
   }
   
   public String getLastImageLabel() {
      return lastImageLabel_;
   }
   
   public void waitToDie() {
      try {
         savingThread_.join();
      } catch (InterruptedException ex) {
         throw new RuntimeException("saving Thread interrupted");
      }
   }
   
   public boolean isIdle() {    
      synchronized (idleLock_) {
         return imageProducingQueue_.size() == 0 && idle_;
      }
   }
   
   public void start() {
      savingThread_ = new Thread("tagged image sink thread") {

         @Override
         public void run() {
            long t1 = System.currentTimeMillis();
            int imageCount = 0;
            try {
               while (true) {
                  TaggedImage image;
                  synchronized (idleLock_) {
                     image = imageProducingQueue_.poll(1, TimeUnit.SECONDS);
                     idle_ = image == null;
                  }
                  if (image != null) {
                     if (((image.pix == null) && (image.tags == null))) { //check for Poison final image
                        break;
                     }
                     ++imageCount;
                     imageCache_.putImage(image);
                     lastImageLabel_ = MDUtils.getLabel(image.tags);
                  }
               }
            } catch (Exception ex2) {
               ReportingUtils.logError(ex2);
            }
            long t2 = System.currentTimeMillis();
            ReportingUtils.logMessage(imageCount + " images stored in " + (t2 - t1) + " ms.");          
            acq_.finish();
            imageCache_.finished();
         }
      };
      savingThread_.start();
   }

}
