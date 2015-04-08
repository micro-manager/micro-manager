package acq;

import java.util.concurrent.BlockingQueue;
import mmcorej.TaggedImage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 * Dequeue tagged images and append to image cache
 *
 * copied from MM DefaultTaggedImageQueue
 */
public class TaggedImageSink  {

   private final BlockingQueue<TaggedImage> imageProducingQueue_;
   private MMImageCache imageCache_ = null;
   private volatile String lastImageLabel_;
   private Thread savingThread_;
   private Acquisition acq_;
   
   public TaggedImageSink(BlockingQueue<TaggedImage> imageProducingQueue,
           MMImageCache imageCache, Acquisition acq) {
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
   
   public void start() {
      savingThread_ = new Thread(acq_.getName() +": Tagged image sink thread") {

         @Override
         public void run() {
            long t1 = System.currentTimeMillis();
            int imageCount = 0;
            while (true) {
               TaggedImage image;
               try {
                  image = imageProducingQueue_.take();
               } catch (InterruptedException ex) {
                  //shouldn't ever happen because signal images are what terminates this thread
                  break;
               }
               if (image != null) {
                  if (SignalTaggedImage.isAcquisitionFinsihedSignal(image)) {
                     System.out.println("Signal to close image sink received");
                     break;
                  } else if (SignalTaggedImage.isTimepointFinishedSignal(image)) {
                     ((FixedAreaAcquisition) acq_).imagesAtTimepointFinishedWriting();
                     continue;
                  } else {
                     ++imageCount;
                     try {
                        imageCache_.putImage(image);
                     } catch (Exception ex) {
                        ReportingUtils.showError("Couldn't add image to storage");
                     }
                     lastImageLabel_ = MDUtils.getLabel(image.tags);
                  }
               } 
            }
            long t2 = System.currentTimeMillis();
            ReportingUtils.logMessage(imageCount + " images stored in " + (t2 - t1) + " ms.");
            acq_.markAsFinished();
            imageCache_.finished();
            if (acq_ instanceof FixedAreaAcquisition) {
               //once everything is done, can signal back upstream to multiple acquisition manager
               ((FixedAreaAcquisition) acq_).imagesAtTimepointFinishedWriting();
            }
         }
      };
      savingThread_.start();
   }

}
