/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class LiveAcq  {

   private static int untitledID_ = 0;
   private BlockingQueue<TaggedImage> imageProducingQueue_;
   private ImageCache imageCache_ = null;
   private String acqName_;

   public LiveAcq(BlockingQueue<TaggedImage> imageProducingQueue,
                  JSONObject summaryMetadata,
                  ScriptInterface gui,
                  boolean diskCached) {
      imageProducingQueue_ = imageProducingQueue;
      acqName_ = gui.createAcquisition(summaryMetadata, diskCached);
      try {
         imageCache_ = gui.getAcquisition(acqName_).getImageCache();
      } catch (MMScriptException ex) {
         ReportingUtils.showError(ex);
      }
   }

   public String getAcquisitionName() {
      return acqName_;
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
            cleanup();
         }
      };
      savingThread.start();
   }

   private static String getUniqueUntitledName() {
      ++untitledID_;
      return "Untitled" + untitledID_;
   }

   protected void cleanup() {
      try {
//         imageCache_.finished();
         MMStudioMainFrame.getInstance().closeAcquisition(acqName_);
         imageCache_ = null;
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   public ImageCache getImageCache() {
      return imageCache_;
   }
}
