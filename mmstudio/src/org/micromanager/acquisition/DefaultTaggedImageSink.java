package org.micromanager.acquisition;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.micromanager.api.ImageCache;
import org.micromanager.utils.ReportingUtils;

/**
 * Dequeue tagged images and append to image cache
 *
 * @author arthur
 */
public class DefaultTaggedImageSink  {

   private final BlockingQueue<TaggedImage> imageProducingQueue_;
   private ImageCache imageCache_ = null;

   public DefaultTaggedImageSink(BlockingQueue<TaggedImage> imageProducingQueue,
                  ImageCache imageCache) {
      imageProducingQueue_ = imageProducingQueue;
         imageCache_ = imageCache;
   }

   public void start() {
      start(null);
   }

   // sinkFullCallback is a way to stop production of images when/if the sink
   // can no longer accept images.
   public void start(final Runnable sinkFullCallback) {
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
                     try {
                        imageCache_.putImage(image);
                     }
                     catch (OutOfMemoryError e) {
                        handleOutOfMemory(e, sinkFullCallback);
                        break;
                     }
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

   // Never called from EDT
   private void handleOutOfMemory(final OutOfMemoryError e,
         Runnable sinkFullCallback)
   {
      ReportingUtils.logError(e);
      if (sinkFullCallback != null) {
         sinkFullCallback.run();
      }

      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            JOptionPane.showMessageDialog(null,
                  "Out of memory to store images: " + e.getMessage(),
                  "Out of image storage memory", JOptionPane.ERROR_MESSAGE);
         }
      });
   }

   public ImageCache getImageCache() {
      return imageCache_;
   }
}
