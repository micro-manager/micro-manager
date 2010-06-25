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
   HashMap<String, MMImageCache> imageCaches_;
   MMImageCache currentImageCache_;
   AcquisitionSettings acqSettings_;

   public void run() {
      try {
         Metadata initMD = core_.getAcquisitionInitialMetadata();
         imageCaches_ = new HashMap<String, MMImageCache>();
         String root = acqSettings_.getRoot();
         String prefix = acqSettings_.getPrefix();

         do {
            while (core_.getRemainingImageCount() > 0) {
               try {
                  Metadata md = new Metadata();
                  Object img = core_.popNextImageMD(0, 0, md);
                  String posName = md.getPositionName();

                  if (imageCaches_.containsKey(posName)) {
                     currentImageCache_ = imageCaches_.get(posName);
                  } else {
                     String cachePath = createFilePath(root, prefix, posName);
                     currentImageCache_ = new MMImageCache(cachePath);
                     currentImageCache_.writeMetadata(initMD, "SystemState");
                  }
               } catch (Exception ex) {
                  ReportingUtils.showError(ex);
               }
            }
            Thread.sleep(30);
         } while (!core_.acquisitionIsFinished() || core_.getRemainingImageCount() > 0);
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   AcquisitionSaver(CMMCore core, AcquisitionSettings acqSettings) {
      core_ = core;
      acqSettings_ = acqSettings;
   }

   private String createFilePath(String root, String prefix, String positionName) throws Exception {
      File rootDir = JavaUtils.createDirectory(root);
      int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");

      File acqDir = null;
      if (positionName.length() == 0) {
         acqDir = JavaUtils.createDirectory(root + "/" + prefix + "_" + (1 + curIndex));
      }
      if (acqDir == null)
         return "";
      
      if (positionName.length() > 0) {
         acqDir = JavaUtils.createDirectory(acqDir.getAbsolutePath() + "/" + positionName);
      }
      return acqDir.getAbsolutePath();
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

