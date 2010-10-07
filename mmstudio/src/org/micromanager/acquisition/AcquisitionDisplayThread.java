/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.acquisition.engine.SequenceSettings;
import org.micromanager.api.AcquisitionEngine;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionDisplayThread extends Thread {
   private static int untitledID_ = 0;

   private static String getUniqueUntitledName() {
      ++untitledID_;
      return "Untitled" + untitledID_;
   }

   protected final CMMCore core_;
   protected int imgCount_;
   private final ScriptInterface gui_;
   SequenceSettings acqSettings_;
   private boolean diskCached_ = false;
   private ArrayList<String> acqNames_ = new ArrayList<String>();
   private BlockingQueue<TaggedImage>  imageProducingQueue_;
   private boolean singleWindow_;
   private MMImageCache imageCache_ = null;

   AcquisitionDisplayThread(ScriptInterface gui, CMMCore core,
           BlockingQueue<TaggedImage> imageProducingQueue, SequenceSettings acqSettings,
           ArrayList<ChannelSpec> channels, boolean diskCached, AcquisitionEngine eng,
           boolean singleWindow) {
      gui_ = gui;
      core_ = core;
      singleWindow_ = singleWindow;
      acqSettings_ = acqSettings;
      diskCached_ = diskCached;
      imageProducingQueue_ = imageProducingQueue;

      int nPositions = Math.max(1, (int) acqSettings.positions.size());
      int nTimes = Math.max(1, (int) acqSettings.numFrames);
      int nChannels = Math.max(1, (int) acqSettings.channels.size());
      int nSlices = Math.max(1, (int) acqSettings.slices.size());
      boolean usingChannels = acqSettings.channels.size() > 0;
      String acqPath;
      try {
         if (diskCached_) {
            acqPath = createAcqPath(acqSettings.root, acqSettings.prefix);
         } else {
            acqPath = getUniqueUntitledName();
         }

         String fullPath = createPositionPath(acqPath, "");
         String acqName = fullPath + "/" + "";
         acqNames_.add(acqName);
         gui_.openAcquisition(acqName, fullPath, nTimes, nChannels, nSlices, nPositions, true, diskCached_);
         gui_.setAcquisitionEngine(acqName, eng);

         //Map<String, String> summaryMetadata = makeMetadataFromAcqSettings(acqSettings);
         TaggedImageStorage imageFileManager;
         if (diskCached_) {
            imageFileManager = new TaggedImageStorageDiskDefault(acqName, true, null);
         } else {
            imageFileManager = new TaggedImageStorageRam(null);
         }
         imageCache_ = new MMImageCache(imageFileManager);
         imageCache_.setComment(acqSettings.comment);
         gui_.setAcquisitionCache(acqName, imageCache_);
         //gui_.setAcquisitionSummary(acqName, summaryMetadata);
         gui_.initializeAcquisition(acqName, (int) core_.getImageWidth(), (int) core_.getImageHeight(), (int) core_.getBytesPerPixel());
         if (usingChannels) {
            for (int i = 0; i < channels.size(); ++i) {
               gui_.setChannelColor(acqName, i, channels.get(i).color_);
               gui_.setChannelName(acqName, i, channels.get(i).config_);
            }
         }
         
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private Map<String,String> makeMetadataFromAcqSettings(SequenceSettings acqSettings) {
      Map md = Collections.synchronizedMap(new HashMap<String, String>());
      md.put("Acquisition-KeepShutterOpenChannels",acqSettings.keepShutterOpenChannels + "");
      md.put("Acquisition-KeepShutterOpenSlices", acqSettings.keepShutterOpenSlices + "");
      MDUtils.put(md,"Acquisition-IntervalMs", acqSettings.intervalMs);
      md.put("Acquisition-SlicesFirst", acqSettings.slicesFirst + "");
      md.put("Acquisition-TimeFirst", acqSettings.timeFirst + "");
      MDUtils.put(md,"Acquisition-Slices",acqSettings.slices.size());
      MDUtils.put(md,"Acquisition-Frames",acqSettings.numFrames);
      MDUtils.put(md,"Acquisition-Channels",acqSettings.channels.size());
      MDUtils.put(md,"Acquisition-Positions",acqSettings.positions.size());
      return md;
   }

   private String getPosName(int posIndex) {
      String posName;
      if (acqSettings_.positions.isEmpty()) {
         posName = "";
      } else {
         posName = acqSettings_.positions.get(posIndex).getLabel();
      }
      return posName;
   }

   public void run() {
      long t1 = System.currentTimeMillis();


      try {
         while (true) {
            TaggedImage image = imageProducingQueue_.poll(1, TimeUnit.SECONDS);
            if (image != null) {
               if (TaggedImageQueue.isPoison(image)) {
                  break;
               }
               displayImage(image);
            }
         }
      } catch (Exception ex2) {
         ReportingUtils.logError(ex2);
      }

      long t2 = System.currentTimeMillis();
      ReportingUtils.logMessage(imgCount_ + " images in " + (t2 - t1) + " ms.");

      cleanup();
   }

   protected void cleanup() {
      try {
         gui_.closeAllAcquisitions();
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private void displayImage(TaggedImage taggedImg) {

      if (singleWindow_) {
         if (taggedImg.pix != null) {
            gui_.displayImage(taggedImg.pix);
            imageCache_.putImage(taggedImg);
         }
      }  else {
         Map<String, String> m = taggedImg.tags;
         try {
            int posIndex = MDUtils.getPositionIndex(m);
            gui_.addImage(acqNames_.get(0), taggedImg);
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }
   }

   private String createAcqPath(String root, String prefix) throws Exception {
      File rootDir = JavaUtils.createDirectory(root);
      int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");

      File acqDir = null;
      acqDir = JavaUtils.createDirectory(root + "/" + prefix + "_" + (1 + curIndex));

      if (acqDir != null) {
         return acqDir.getAbsolutePath();
      } else {
         return "";
      }
   }

   private String createPositionPath(String acqPath, String position) throws Exception {
      if (position.length() == 0) {
         return acqPath;
      }

      File acqDir = null;
      if (position.length() == 0) {
         return acqPath;
      }

      acqDir = JavaUtils.createDirectory(acqPath + "/" + position);

      if (acqDir != null) {
         return acqDir.getAbsolutePath();
      } else {
         return "";
      }
   }

   private int getCurrentMaxDirIndex(File rootDir, String prefix) throws NumberFormatException {
      int maxNumber = 0;
      int number;
      String theName;
      for (File acqDir : rootDir.listFiles()) {
         theName = acqDir.getName();
         if (theName.startsWith(prefix)) {
            try {
               number = Integer.parseInt(theName.substring(prefix.length()));
               if (number >= maxNumber) {
                  maxNumber = number;
               }
            } catch (NumberFormatException e) {
            } // Do nothing.
         }
      }
      return maxNumber;
   }
}
