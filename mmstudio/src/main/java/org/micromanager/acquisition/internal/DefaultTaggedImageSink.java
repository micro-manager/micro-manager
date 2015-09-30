package org.micromanager.acquisition.internal;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.events.internal.DefaultAcquisitionEndedEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This object spawns a new thread that receives images from the acquisition
 * engine and runs them through a Pipeline to the Datastore. It's also
 * responsible for posting the AcquisitionEndedEvent, which it recognizes when
 * it receives the TaggedImageQueue.POISON object.
 * Functionally this is just glue code between the old acquisition engine and
 * the 2.0 API.
 *
 * @author arthur, modified by Chris Weisiger
 */
public class DefaultTaggedImageSink  {

   private final BlockingQueue<TaggedImage> imageProducingQueue_;
   private Datastore store_;
   private Pipeline pipeline_;
   private AcquisitionEngine engine_;

   public DefaultTaggedImageSink(BlockingQueue<TaggedImage> queue,
         Pipeline pipeline, Datastore store, AcquisitionEngine engine) {
      imageProducingQueue_ = queue;
      pipeline_ = pipeline;
      store_ = store;
      engine_ = engine;
   }

   public void start() {
      start(null);
   }

   // sinkFullCallback is a way to stop production of images when/if the sink
   // can no longer accept images.
   public void start(final Runnable sinkFullCallback) {
      Thread savingThread = new Thread("TaggedImage sink thread") {

         @Override
         public void run() {
            long t1 = System.currentTimeMillis();
            int imageCount = 0;
            try {
               while (true) {
                  TaggedImage tagged = imageProducingQueue_.poll(1, TimeUnit.SECONDS);
                  if (tagged != null) {
                     if (TaggedImageQueue.isPoison(tagged)) {
                        // Acquisition has ended.
                        pipeline_.halt();
                        DefaultEventManager.getInstance().post(
                              new DefaultAcquisitionEndedEvent(store_));
                        break;
                     }
                     try {
                        ++imageCount;
                        DefaultImage image = new DefaultImage(tagged);
                        for (Image subImage : image.splitMultiComponent()) {
                           try {
                              pipeline_.insertImage(subImage);
                           }
                           catch (PipelineErrorException e) {
                              // TODO: make showing the dialog optional.
                              // TODO: allow user to cancel acquisition from
                              // here.
                              ReportingUtils.showError(e,
                                    "There was an error in processing images.");
                              pipeline_.clearExceptions();
                           }
                        }
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
