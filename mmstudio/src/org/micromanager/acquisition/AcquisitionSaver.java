/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.io.File;
import java.util.HashMap;
import mmcorej.AcquisitionSettings;
import mmcorej.CMMCore;
import mmcorej.Metadata;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionSaver extends Thread {

   private final CMMCore core_;
   HashMap<String, MMImageWriter> imageWriters_;

   MMImageWriter currentImageWriter_;
   AcquisitionSettings acqSettings_;

   public void run() {
      try {
         Metadata initMD = core_.getAcquisitionInitialMetadata();
         imageWriters_ = new HashMap<String, MMImageWriter>();
         String root = acqSettings_.getRoot();
         String prefix = acqSettings_.getPrefix();
         String acqPath = createAcqPath(root, prefix);

         do {
            while (core_.getRemainingImageCount() > 0) {
               try {
                  Metadata md = new Metadata();
                  Object img = core_.popNextImageMD(0, 0, md);
                  String posName = md.getPositionName();

                  if (imageWriters_.containsKey(posName)) {
                     currentImageWriter_ = imageWriters_.get(posName);
                  } else {
                     String cachePath = createPositionPath(acqPath, posName);
                     currentImageWriter_ = new MMImageWriter(cachePath);
                     imageWriters_.put(posName, currentImageWriter_);
                     currentImageWriter_.writeMetadata(initMD, "SystemState");
                  }
                  currentImageWriter_.writeImage(img, md);
               } catch (Exception ex) {
                  ReportingUtils.showError(ex);
               }
            }
            Thread.sleep(30);
         } while (!core_.acquisitionIsFinished() || core_.getRemainingImageCount() > 0);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
      cleanup();
   }

   public void cleanup() {
      for (MMImageWriter imageCache:imageWriters_.values())
         imageCache.cleanup();
      imageWriters_.clear();
   }

   AcquisitionSaver(CMMCore core, AcquisitionSettings acqSettings) {
      core_ = core;
      acqSettings_ = acqSettings;
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

