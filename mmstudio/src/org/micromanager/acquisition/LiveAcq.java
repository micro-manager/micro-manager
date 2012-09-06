package org.micromanager.acquisition;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import mmcorej.TaggedImage;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class LiveAcq  {

   private static int untitledID_ = 0;
   private final BlockingQueue<TaggedImage> imageProducingQueue_;
   private ImageCache imageCache_ = null;

   public LiveAcq(BlockingQueue imageProducingQueue,
                  ImageCache imageCache) throws MMScriptException {
      imageProducingQueue_ = imageProducingQueue;
         imageCache_ = imageCache;
   }

   public void start() {
      Thread savingThread = new Thread("LiveAcq saving thread.") {

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
                  }
               }
            } catch (Exception ex2) {
               ReportingUtils.logError(ex2);
            }
            long t2 = System.currentTimeMillis();
            ReportingUtils.logMessage(imageCount + " images saved in " + (t2 - t1) + " ms.");
            imageCache_.finished();
         }
      };
      savingThread.start();
   }


   public ImageCache getImageCache() {
      return imageCache_;
   }
}
