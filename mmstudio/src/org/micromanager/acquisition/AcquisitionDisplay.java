/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.io.File;
import java.util.ArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.AcquisitionSettings;
import mmcorej.CMMCore;
import mmcorej.Metadata;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ChannelSpec;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionDisplay extends Thread {

   protected final CMMCore core_;
   protected int imgCount_;
   private final ScriptInterface gui_;
   AcquisitionSettings acqSettings_;
   private boolean diskCached_ = false;

   AcquisitionDisplay(ScriptInterface gui, CMMCore core, AcquisitionSettings acqSettings, ArrayList<ChannelSpec> channels, boolean diskCached) {
      gui_ = gui;
      core_ = core;
      acqSettings_ = acqSettings;
      diskCached_ = diskCached;

      int nPositions = Math.max(1, (int) acqSettings.getPositionList().size());
      int nTimes = Math.max(1, (int) acqSettings.getTimeSeries().size());
      int nChannels = Math.max(1, (int) acqSettings.getChannelList().size());
      int nSlices = Math.max(1, (int) acqSettings.getZStack().size());

      String acqPath;
      try {
         acqPath = createAcqPath(acqSettings.getRoot(), acqSettings.getPrefix());

         String posName;
         for (int posIndex = 0; posIndex < nPositions; ++posIndex) {
            posName = getPosName(posIndex);

            String fullPath = createPositionPath(acqPath, posName);
            gui_.openAcquisition(posName, fullPath, nTimes, nChannels, nSlices, true, diskCached_);
            for (int i = 0; i < channels.size(); ++i) {
               gui_.setChannelColor(posName, i, channels.get(i).color_);
               gui_.setChannelName(posName, i, channels.get(i).config_);
               gui.setAcquisitionProperties(posName, core_.getAcquisitionInitialMetadata());
            }
            gui_.initializeAcquisition(posName, 512, 512, 1);

         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private String getPosName(int posIndex) {
      String posName;
      if (acqSettings_.getPositionList().isEmpty()) {
         posName = "";
      } else {
         posName = acqSettings_.getPositionList().get(posIndex).getName();
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
                  Metadata mdCopy = new Metadata();
                  if (true /*! diskCached_*/) {
                     img = core_.popNextImageMD(0, 0, mdCopy);
                  } else {
                     img = core_.getLastImageMD(0, 0, mdCopy);
                     Thread.sleep(10);
                  }
                  MMImageBuffer imgBuf = new MMImageBuffer(img, mdCopy);

                  displayImage(imgBuf);
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

   private int getMetadataIndex(Metadata m, String key) {
      if (m.getFrameData().has_key(key)) {
         String val = m.get(key);
         if (val == null || val.length() == 0) {
            return -1;
         } else {
            System.out.println("frameData[\"" + key + "\"] = \"" + val + "\"");
         }
         int result;
         try {
            result = Integer.parseInt(val);
         } catch (Exception e) {
            e.printStackTrace();
            System.out.println("error. now val = \"" + val + "\"");
            result = -1;
         }
         return result;
      } else {
         return -1;
      }
   }

   protected boolean sameFrame(Metadata lastMD, Metadata mdCopy) {
      if (lastMD == null || mdCopy == null) {
         return false;
      }
      boolean same =
              (getMetadataIndex(lastMD, "Position") == getMetadataIndex(mdCopy, "Position"))
              && (getMetadataIndex(lastMD, "ChannelIndex") == getMetadataIndex(mdCopy, "ChannelIndex"))
              && (getMetadataIndex(lastMD, "Slice") == getMetadataIndex(mdCopy, "Slice"))
              && (getMetadataIndex(lastMD, "Frame") == getMetadataIndex(mdCopy, "Frame"));
      return same;
   }

   protected void cleanup() {
      try {
         gui_.closeAllAcquisitions();
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private void displayImage(MMImageBuffer imgBuf) {

      Metadata m = imgBuf.md;
      int posIndex = m.getPositionIndex();

      try {
         gui_.addImage(getPosName(posIndex), imgBuf);
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
