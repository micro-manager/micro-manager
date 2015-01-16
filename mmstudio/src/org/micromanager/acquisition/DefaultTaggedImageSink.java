package org.micromanager.acquisition;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.micromanager.api.data.Datastore;
import org.micromanager.data.DefaultImage;
import org.micromanager.utils.ReportingUtils;

/**
 * Dequeue tagged images and append to image cache
 *
 * @author arthur
 */
public class DefaultTaggedImageSink  {

   private final BlockingQueue<TaggedImage> imageProducingQueue_;
   private Datastore store_;

   public DefaultTaggedImageSink(
         BlockingQueue<TaggedImage> imageProducingQueue, Datastore store) {
      imageProducingQueue_ = imageProducingQueue;
      store_ = store;
   }

   public void start() {
      start(null);
   }

   // sinkFullCallback is a way to stop production of images when/if the sink
   // can no longer accept images.
   public void start(final Runnable sinkFullCallback) {
      Thread savingThread = new Thread("TaggedImage sink thread for " + store_.hashCode()) {

         @Override
         public void run() {
            long t1 = System.currentTimeMillis();
            int imageCount = 0;
            try {
               while (true) {
                  TaggedImage tagged = imageProducingQueue_.poll(1, TimeUnit.SECONDS);
                  if (tagged != null) {
                     if (TaggedImageQueue.isPoison(tagged)) {
                        break;
                     }
                     ++imageCount;
                     DefaultImage image = new DefaultImage(tagged);
                     try {
                        image.splitMultiComponentIntoStore(store_);
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
//            store_.lock();
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
}
