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
import mmcorej.Metadata;
import mmcorej.StrVector;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */

public class MMImageWriter {

   private boolean firstElement_;
   private BufferedWriter metadataStream_;
   private final String dir_;

   MMImageWriter(String dir) {
      dir_ = dir;

      try {
         JavaUtils.createDirectory(dir_);
         metadataStream_ = new BufferedWriter(new FileWriter(dir_ + "/metadata.txt"));
         metadataStream_.write("{" + "\r\n");
         firstElement_ = true;
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   public void writeImage(Object img, Metadata md) {
      String tiffFileName = createFileName(md);
      saveImageFile(img, md, dir_, tiffFileName);
      writeFrameMetadata(md, tiffFileName);
   }

   public void cleanup() {
      try {
         metadataStream_.write("\r\n}\r\n");
         metadataStream_.close();
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public void writeMetadata(Metadata md, String title) {
      try {
         if (!firstElement_) {
            metadataStream_.write(",\r\n");
         }
         metadataStream_.write("\t\"" + title + "\": {\r\n");

         StrVector keys = md.getFrameKeys();
         long n = keys.size();

         for (int i = 0; i < n; ++i) {
            metadataStream_.write("\t\t\"" + keys.get(i) + "\": \"" + md.get(keys.get(i)) + "\"");
            if (i < (n - 1)) {
               metadataStream_.write(",\r\n");
            } else {
               metadataStream_.write("\r\n");
            }
         }

         metadataStream_.write("\t}");
         metadataStream_.flush();
         firstElement_ = false;
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private void saveImageFile(Object img, Metadata md, String path, String tiffFileName) {
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
         ImagePlus imp = new ImagePlus(path + "/" + tiffFileName, ip);
         FileSaver fs = new FileSaver(imp);
         fs.saveAsTiff(path + "/" + tiffFileName);
      }
   }

   private void writeFrameMetadata(Metadata md, String fileName) {
      String title = "FrameKey-" + md.getFrame()
              + "-" + md.getChannelIndex() + "-" + md.getSlice();
      md.put("Filename", fileName);
      writeMetadata(md, title);
   }

   private String createFileName(Metadata md) {
      return "img_"
              + String.format("%09d", md.getFrame())
              + "_"
              + md.getChannel()
              + "_"
              + String.format("%03d", md.getSlice())
              + ".tif";
   }
}
