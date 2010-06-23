/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import mmcorej.AcquisitionSettings;
import mmcorej.CMMCore;
import mmcorej.Metadata;
import mmcorej.StrVector;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class AcquisitionSaver extends Thread {

   private final CMMCore core_;
   private String root_;
   private String prefix_;
   private BufferedWriter metadataStream_;
   private boolean firstElement_;

   public void run() {
      try {
         Metadata initMD = core_.getAcquisitionInitialMetadata();
         writeMetadata(initMD, "SystemState");
         do {
            while (core_.getRemainingImageCount() > 0) {
               try {
                  Metadata md = new Metadata();
                  Object img = core_.popNextImageMD(0, 0, md);
                  writeImage(img, md);
                  writeFrameMetadata(md);

               } catch (Exception ex) {
                  ReportingUtils.logError(ex);
               }
            }
            Thread.sleep(30);
         } while (!core_.acquisitionIsFinished() || core_.getRemainingImageCount() > 0);

         cleanup();
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }

   }

   AcquisitionSaver(CMMCore core, AcquisitionSettings acqSettings) {
      prefix_ = acqSettings.getPrefix();
      core_ = core;
      try {
         metadataStream_ = new BufferedWriter(new FileWriter("metadata.txt"));
         metadataStream_.write("{\n");
         firstElement_ = true;
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public void cleanup() {
      try {
         metadataStream_.write("\n}\n");
         metadataStream_.close();
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
   }

   void SetPaths(String root, String prefix) {
      root_ = root;
      prefix_ = prefix;

   }

   public void writeImage(Object img, Metadata md) {
      String tiffFileName;
      tiffFileName = prefix_ + "_" + md.getFrame()
              + "_" + md.getChannel() + "_" + md.getSlice() + ".tif";

      ImageProcessor ip = null;
      int width = md.getWidth();
      int height = md.getHeight();
      if (img instanceof byte[]) {
         ip = new ByteProcessor(width, height);
         ip.setPixels((byte[]) img);
      } else if (img instanceof short[]) {
         ip = new ShortProcessor(width, height);
         ip.setPixels((short[]) img);
      }

      if (ip != null) {
         ImagePlus imp = new ImagePlus(tiffFileName, ip);
         FileSaver fs = new FileSaver(imp);
         fs.saveAsTiff(tiffFileName);
      }
   }

   public void writeFrameMetadata(Metadata md) {
      String title = "FrameKey-" + md.getFrame()
           + "-" + md.getChannelIndex() + "-" + md.getSlice();
      writeMetadata(md, title);
   }

   public void writeMetadata(Metadata md, String title) {
      try {
         if (!firstElement_) {
            metadataStream_.write(",\n");
         }

         metadataStream_.write("\t\"" + title + "\": {\n");

         StrVector keys = md.getFrameKeys();
         long n = keys.size();

         for (int i = 0; i < n; ++i) {
            metadataStream_.write("\t\t\"" + keys.get(i) + "\": \"" + md.get(keys.get(i)) + "\"");
            if (i < (n - 1)) {
               metadataStream_.write(",\n");
            } else {
               metadataStream_.write("\n");
            }
         }

         metadataStream_.write("\t}");
         metadataStream_.flush();
         firstElement_ = false;
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }
}
