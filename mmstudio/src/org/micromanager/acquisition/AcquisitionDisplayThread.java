/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;
import mmcorej.AcquisitionSettings;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.acquisition.engine.SequenceSettings;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionDisplayThread extends Thread {

   protected final CMMCore core_;
   protected int imgCount_;
   private final ScriptInterface gui_;
   SequenceSettings acqSettings_;
   private boolean diskCached_ = false;
   private ArrayList<String> acqNames_ = new ArrayList<String>();

   AcquisitionDisplayThread(ScriptInterface gui, CMMCore core, SequenceSettings acqSettings, ArrayList<ChannelSpec> channels, boolean diskCached) {
      gui_ = gui;
      core_ = core;
      acqSettings_ = acqSettings;
      diskCached_ = diskCached;

      int nPositions = Math.max(1, (int) acqSettings.positions.size());
      int nTimes = Math.max(1, (int) acqSettings.numFrames);
      int nChannels = Math.max(1, (int) acqSettings.channels.size());
      int nSlices = Math.max(1, (int) acqSettings.slices.size());
      boolean usingChannels = acqSettings.channels.size() > 0;

      String acqPath;
      try {
         acqPath = createAcqPath(acqSettings.root, acqSettings.prefix);

         String posName;
         for (int posIndex = 0; posIndex < nPositions; ++posIndex) {
            posName = getPosName(posIndex);
            String fullPath = createPositionPath(acqPath, posName);
            String acqName = fullPath + "/" + posName;
            acqNames_.add(acqName);
            gui_.openAcquisition(acqName, fullPath, nTimes, nChannels, nSlices, true, diskCached_);
            if (usingChannels) {
               for (int i = 0; i < channels.size(); ++i) {
                  gui_.setChannelColor(acqName, i, channels.get(i).color_);
                  gui_.setChannelName(acqName, i, channels.get(i).config_);
               }
            }
            
//            gui_.setAcquisitionSystemState(acqName, core_.getAcquisitionMetadataMap());
            gui_.initializeAcquisition(acqName, (int) core_.getImageWidth(), (int) core_.getImageHeight(), (int) core_.getBytesPerPixel());
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
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
      Object img;
      try {
         do {
            while (core_.getRemainingImageCount() > 0) {
               ++imgCount_;
               try {
                  TaggedImage taggedImg;
                  if (true /*! diskCached_*/) {
                     taggedImg = core_.popNextTaggedImage();
                  } else {
                     taggedImg = core_.getLastTaggedImage();
                     Thread.sleep(10);
                  }
                  //TaggedImage taggedImg = new TaggedImage(img, mdCopy);

                  displayImage(taggedImg);
                  //    ReportingUtils.logMessage("time=" + mdCopy.getFrame() + ", position=" +
                  //            mdCopy.getPositionIndex() + ", channel=" + mdCopy.getChannelIndex() +
                  //            ", slice=" + mdCopy.getSliceIndex()
                  //            + ", remaining images =" + core_.getRemainingImageCount());
               } catch (Exception ex) {
                  ReportingUtils.logError(ex);
               }
            }
            Thread.sleep(30);
         } while (!core_.acquisitionIsFinished() || core_.getRemainingImageCount() > 0);
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

      Map<String,String> m = taggedImg.tags;
      try {
         int posIndex = MDUtils.getPositionIndex(m);
         gui_.addImage(acqNames_.get(posIndex), taggedImg);
      } catch (Exception e) {
         ReportingUtils.logError(e);
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
            number = Integer.parseInt(theName.substring(prefix.length()));
            if (number >= maxNumber) {
               maxNumber = number;
            }
         }
      }
      return maxNumber;
   }
}
