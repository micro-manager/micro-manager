package acq;

import java.util.concurrent.BlockingQueue;
import misc.Log;
import misc.MD;

/**
 * Dequeue tagged images and append to image cache
 *
 * copied from MM DefaultMagellanTaggedImageQueue
 */
public class TaggedImageSink  {

   private final BlockingQueue<MagellanTaggedImage> imageProducingQueue_;
   private MMImageCache imageCache_ = null;
   private volatile String lastImageLabel_;
   private Thread savingThread_;
   private Acquisition acq_;
   
   public TaggedImageSink(BlockingQueue<MagellanTaggedImage> imageProducingQueue,
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
               MagellanTaggedImage image;
               try {
                  image = imageProducingQueue_.take();
               } catch (InterruptedException ex) {
                  //shouldn't ever happen because signal images are what terminates this thread
                  break;
               }
               if (image != null) {
                  if (SignalTaggedImage.isAcquisitionFinsihedSignal(image)) {
                     break;
                  } else if (SignalTaggedImage.isTimepointFinishedSignal(image)) {
                     ((FixedAreaAcquisition) acq_).imagesAtTimepointFinishedWriting();
                     continue;
                  } else {
                     ++imageCount;
                     try {
                        imageCache_.putImage(image);
                     } catch (Exception ex) {
                        Log.log("Couldn't add image to storage");
                     }
                     lastImageLabel_ = MD.getLabel(image.tags);
                  }
               } 
            }
            long t2 = System.currentTimeMillis();
            Log.log(imageCount + " images stored in " + (t2 - t1) + " ms.", false);
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
