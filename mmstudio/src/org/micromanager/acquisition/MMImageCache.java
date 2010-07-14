/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import ij.ImagePlus;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import mmcorej.Metadata;
import mmcorej.StrVector;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */

public class MMImageCache {
   private boolean firstElement_;
   private BufferedWriter metadataStream_;
   private final String dir_;
   private ConcurrentLinkedQueue<TaggedImage> taggedImgQueue_;
   private int taggedImgQueueSize_ = 50;
   private boolean newDataSet_;
   
   MMImageCache(String pathOnDisk, boolean newDataSet) {
      dir_ = pathOnDisk;
      newDataSet_ = newDataSet;
      
      try {
         if (newDataSet_)
            openNewDataSet();
         else
            openOldDataSet();
         taggedImgQueue_ = new ConcurrentLinkedQueue<TaggedImage>();
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   private void openNewDataSet() throws Exception, IOException {
      JavaUtils.createDirectory(dir_);
      firstElement_ = true;
      metadataStream_ = new BufferedWriter(new FileWriter(dir_ + "/metadata.txt"));
      metadataStream_.write("{" + "\r\n");
   }

   public void cleanup() {
      closeMetadataStream();
   }

   private void closeMetadataStream() {
      try {
         metadataStream_.write("\r\n}\r\n");
         metadataStream_.close();
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public String putImage(Object img, Metadata md) {
      return putImage(new TaggedImage(img, md));
   }

   public String putImage(TaggedImage taggedImg) {
      Metadata md = taggedImg.md;
      Object img = taggedImg.img;
      String tiffFileName = createFileName(md);
      saveImageFile(img, md, dir_, tiffFileName);
      writeFrameMetadata(md, tiffFileName);
      putImageInRAM(tiffFileName, img, md);
      return tiffFileName;
   }

   public TaggedImage getImage(String filename) {
      for (TaggedImage taggedImg:taggedImgQueue_) {
         if (taggedImg.filename.equals(filename)) {
            return taggedImg;
         }
      }

      ImagePlus imp = new Opener().openImage(dir_ + "/" + filename);
      if (imp == null)
         System.out.println(filename);
      Object img = imp.getProcessor().getPixels();
      Metadata md = yamlToMetadata((String) imp.getProperty("Info"));
      TaggedImage taggedImg = new TaggedImage(filename, img, md);
      cacheImage(taggedImg);
      return taggedImg;
   }

   private void cacheImage(TaggedImage taggedImg) {
      taggedImgQueue_.add(taggedImg);
      if (taggedImgQueue_.size() > taggedImgQueueSize_) { // If the queue is full,
         taggedImgQueue_.poll();                       // remove the oldest image.
      }
   }

   private void putImageInRAM(String filename, Object img, Metadata md) {
      TaggedImage taggedImg = new TaggedImage(filename, img, md);
      cacheImage(taggedImg);
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
         imp.setProperty("Info", metadataToYaml(md));
         FileSaver fs = new FileSaver(imp);
         fs.saveAsTiff(path + "/" + tiffFileName);
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

   private static String metadataToYaml(Metadata md) {
      String yaml = "";
      for (String key:md.getFrameKeys()) {
         yaml += key + ": " +md.get(key) + "\r\n";
      }
      return yaml;
   }

   private static Metadata yamlToMetadata(String yaml) {
      Metadata md = new Metadata();
      String [] lines = yaml.split("\r\n");
      for (String line:lines) {
         String [] parts = line.split(": ");
         if (parts.length == 2)
           md.put(parts[0], parts[1]);
      }
      return md;
   }

   private void openOldDataSet() {
      throw new UnsupportedOperationException("Not yet implemented");
   }

}
