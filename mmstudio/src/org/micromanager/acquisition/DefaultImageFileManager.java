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
import java.util.ArrayList;
import java.util.Iterator;
import mmcorej.Metadata;
import mmcorej.StrVector;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.TextUtils;

/**
 *
 * @author arthur
 */
public class DefaultImageFileManager implements ImageFileManagerInterface {

   private final String dir_;
   private boolean firstElement_;
   private BufferedWriter metadataStream_;
   private boolean newDataSet_;
   private Metadata summaryMetadata_;
   private Metadata systemMetadata_;
   private ArrayList<Metadata> imageMetadata_;

   DefaultImageFileManager(String dir) {
      this(dir, false, null, null);
   }

   DefaultImageFileManager(String dir, boolean newDataSet,
           Metadata summaryMetadata, Metadata systemMetadata) {
      summaryMetadata_ = summaryMetadata;
      systemMetadata_ = systemMetadata;
      dir_ = dir;
      newDataSet_ = newDataSet;

      try {
         if (newDataSet_) {
            openNewDataSet();
         } else {
            openExistingDataSet();
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   public String writeImage(TaggedImage taggedImg) throws MMException {
      if (newDataSet_ == false) {
         throw new MMException("This ImageFileManager is read-only.");
      }
      Metadata md = taggedImg.md;
      Object img = taggedImg.img;
      String tiffFileName = createFileName(md);
      saveImageFile(img, md, dir_, tiffFileName);
      writeFrameMetadata(md, tiffFileName);
      return tiffFileName;
   }

   public TaggedImage readImage(String filename) {
      ImagePlus imp = new Opener().openImage(dir_ + "/" + filename);
      if (imp != null) {
         ImageProcessor proc = imp.getProcessor();
         Object img = proc.getPixels();
         Metadata md = yamlToMetadata((String) imp.getProperty("Info"));
         TaggedImage taggedImg = new TaggedImage(filename, img, md);
         return taggedImg;
      } else {
         return null;
      }
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
      for (String key : md.getFrameKeys()) {
         yaml += key + ": " + md.get(key) + "\r\n";
      }
      return yaml;
   }

   private static Metadata yamlToMetadata(String yaml) {
      Metadata md = new Metadata();
      String[] lines = yaml.split("\r\n");
      for (String line : lines) {
         String[] parts = line.split(": ");
         if (parts.length == 2) {
            md.put(parts[0], parts[1]);
         }
      }
      return md;
   }

   private void writeFrameMetadata(Metadata md, String fileName) {
      String title = "FrameKey-" + md.getFrame()
              + "-" + md.getChannelIndex() + "-" + md.getSlice();
      md.put("Filename", fileName);
      writeMetadata(md, title);
   }

   private void writeMetadata(Metadata md, String title) {
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
         imp.setProperty("Info", metadataToYaml(md));
         FileSaver fs = new FileSaver(imp);
         fs.saveAsTiff(path + "/" + tiffFileName);
      }
   }

   private void openNewDataSet() throws Exception, IOException {
      JavaUtils.createDirectory(dir_);
      firstElement_ = true;
      metadataStream_ = new BufferedWriter(new FileWriter(dir_ + "/metadata.txt"));
      metadataStream_.write("{" + "\r\n");
      writeMetadata(getSummaryMetadata(), "Summary");
      writeMetadata(getSystemMetadata(), "SystemState");
   }

   public void finishWriting() {
      closeMetadataStream();
      newDataSet_ = false;
   }

   private void closeMetadataStream() {
      if (newDataSet_) {
         try {
            metadataStream_.write("\r\n}\r\n");
            metadataStream_.close();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   private void openExistingDataSet() {
      JSONObject data = readJsonMetadata();
      if (data != null) {
         try {
            summaryMetadata_ = jsonToMetadata(data.getJSONObject("Summary"));
            systemMetadata_ = jsonToMetadata(data.getJSONObject("SystemState"));
            imageMetadata_ = new ArrayList<Metadata>();
            for (String key:makeJsonIterableKeys(data)) {
               JSONObject chunk = data.getJSONObject(key);
               if (key.startsWith("FrameKey")) {
                  imageMetadata_.add(jsonToMetadata(chunk));
               }
            }
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   private Iterable<String> makeJsonIterableKeys(final JSONObject data) {
      return new Iterable<String>() {
            public Iterator<String> iterator() {
               return data.keys();
            }
         };
   }

   private Metadata jsonToMetadata(final JSONObject data) {
      Metadata md = new Metadata();
      try {
         Iterable<String> keys = makeJsonIterableKeys(data);
         for (String key:keys) {
            md.put((String) key, (String) data.getString(key));
         }
      } catch (JSONException ex) {
         ReportingUtils.showError(ex);
      }

      return md;

   }

   private JSONObject readJsonMetadata() {
      try {
         String fileStr;
         fileStr = TextUtils.readTextFile(dir_ + "/metadata.txt");
         try {
            return new JSONObject(fileStr);
         } catch (JSONException ex) {
            return new JSONObject(fileStr.concat("}"));
         }
      } catch (IOException ex) {
         ReportingUtils.showError(ex, "Unable to open metadata.txt");
         return null;
      } catch (Exception ex) {
         ReportingUtils.showError(ex, "Unable to read metadata.txt");
         return null;
      }
   }

   /**
    * @return the summaryMetadata_
    */
   public Metadata getSummaryMetadata() {
      return summaryMetadata_;
   }

   /**
    * @param summaryMetadata the summaryMetadata to set
    */
   public void setSummaryMetadata(Metadata summaryMetadata) {
      this.summaryMetadata_ = summaryMetadata;
   }

   public void setSystemMetadata(Metadata md) {
      systemMetadata_ = md;
   }

   public Metadata getSystemMetadata() {
      return systemMetadata_;
   }

   public ArrayList<Metadata> getImageMetadata() {
      return imageMetadata_;
   }

}
