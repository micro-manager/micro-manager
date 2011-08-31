/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.io.File;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class LiveAcq extends Thread {

   private static int untitledID_ = 0;
   protected final CMMCore core_;
   private boolean diskCached_ = false;
   private BlockingQueue<TaggedImage> imageProducingQueue_;
   private MMImageCache imageCache_ = null;
   private VirtualAcquisitionDisplay display_ = null;

   public LiveAcq(CMMCore core,
           BlockingQueue<TaggedImage> imageProducingQueue,
           JSONObject summaryMetadata,
           boolean diskCached,
           AcquisitionEngine eng) {
      core_ = core;
      diskCached_ = diskCached;
      imageProducingQueue_ = imageProducingQueue;

      String acqPath;

      TaggedImageStorage imageFileManager;

      if (diskCached_) {
         try {
            acqPath = createAcqPath(summaryMetadata.getString("Directory"),
                    summaryMetadata.getString("Prefix"));
            imageFileManager = ImageUtils.newImageStorageInstance(acqPath,
                    true, (JSONObject) null);
         } catch (Exception e) {
            ReportingUtils.showError(e, "Unable to create directory for saving images.");
            eng.stop(true);
            return;
         }
      } else {
         acqPath = getUniqueUntitledName();
         imageFileManager = new TaggedImageStorageRam(null);
      }

      imageCache_ = new MMImageCache(imageFileManager);
      imageCache_.setSummaryMetadata(summaryMetadata);

      display_ = new VirtualAcquisitionDisplay(imageCache_, eng);
      imageCache_.addImageStorageListener(display_);
   }

   public void start() {
      Thread savingThread = new Thread("LiveAcq saving thread.") {

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
         imageCache_.finished();
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private String createAcqPath(String root, String prefix) throws Exception {
      File rootDir = JavaUtils.createDirectory(root);
      int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");
      File acqDir = new File(root + "/" + prefix + "_" + (1 + curIndex));
      return acqDir.getAbsolutePath();
   }

   private int getCurrentMaxDirIndex(File rootDir, String prefix) throws NumberFormatException {
      int maxNumber = 0;
      int number;
      String theName;
      for (File acqDir : rootDir.listFiles()) {
         theName = acqDir.getName();
         if (theName.startsWith(prefix)) {
            try {
               //e.g.: "blah_32.ome.tiff"
               Pattern p = Pattern.compile("\\Q" + prefix + "\\E" + "(\\d+).*+");
               Matcher m = p.matcher(theName);
               if (m.matches()) {
                  number = Integer.parseInt(m.group(1));
                  if (number >= maxNumber) {
                     maxNumber = number;
                  }
               }
            } catch (NumberFormatException e) {
            } // Do nothing.
         }
      }
      return maxNumber;
   }
}
