/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import org.micromanager.api.TaggedImageStorage;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;
import org.micromanager.utils.TextUtils;

/**
 *
 * @author arthur
 */
public class TaggedImageStorageDiskDefault implements TaggedImageStorage {

   private final String dir_;
   private boolean firstElement_;
   private BufferedWriter metadataStream_;
   private boolean newDataSet_;
   private Map<String,String> summaryMetadata_;
   private HashMap <String,String> filenameTable_;

   TaggedImageStorageDiskDefault(String dir) {
      this(dir, false, null);
   }

   TaggedImageStorageDiskDefault(String dir, boolean newDataSet,
           Map<String,String> summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      dir_ = dir;
      newDataSet_ = newDataSet;
      filenameTable_ = new HashMap<String,String>();

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

   public String putImage(TaggedImage taggedImg) throws MMException {
      if (newDataSet_ == false) {
         throw new MMException("This ImageFileManager is read-only.");
      }
      Map<String, String> md = taggedImg.tags;
      Object img = taggedImg.pix;
      String tiffFileName = createFileName(md);
      MDUtils.setFileName(md, tiffFileName);
      saveImageFile(img, md, dir_, tiffFileName);
      writeFrameMetadata(md, tiffFileName);
      String label = MDUtils.getLabel(md);
      filenameTable_.put(label, tiffFileName);
      return MDUtils.getLabel(md);
   }

   public TaggedImage getImage(int channel, int slice, int frame) {
      String label = MDUtils.generateLabel(channel, slice, frame);
      ImagePlus imp = new Opener().openImage(dir_ + "/" + filenameTable_.get(label));
      if (imp != null) {
         try {
            ImageProcessor proc = imp.getProcessor();
            Map<String, String> md = yamlToMetadata((String) imp.getProperty("Info"));
            String pixelType = MDUtils.getPixelType(md);
            Object img;
            if (pixelType.contentEquals("GRAY8") || pixelType.contentEquals("GRAY16")) {
               img = proc.getPixels();
            } else if (pixelType.contentEquals("RGB32")) {
               img = proc.getPixels();
               img = ImageUtils.convertRGB32IntToBytes((int []) img);
            } else if (pixelType.contentEquals("RGB64")) {
               ImageStack stack = ((CompositeImage) imp).getStack();
               short [] r = (short []) stack.getProcessor(1).getPixels();
               short [] g = (short []) stack.getProcessor(2).getPixels();
               short [] b = (short []) stack.getProcessor(3).getPixels();
               short [][] planes = {r,g,b};
               img = ImageUtils.getRGB64PixelsFromColorPlanes(planes);
            } else {
               return null;
            }  

            TaggedImage taggedImg = new TaggedImage(img, md);
            return taggedImg;
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
            return null;
         }
      } else {
         return null;
      }
   }

   private String createFileName(Map<String,String> md) {
      try {
         return String.format("img_%09d_%s_%03d.tif",
                 MDUtils.getFrameIndex(md),
                 MDUtils.getChannelName(md),
                 MDUtils.getSliceIndex(md));
      } catch (Exception e) {
         ReportingUtils.logError(e);
         return "";
      }
   }

   private static String metadataToYaml(Map<String,String> md) {
      String yaml = "";
      for (String key : md.keySet()) {
         yaml += key + ": " + md.get(key) + "\r\n";
      }
      return yaml;
   }

   private static Map<String,String> yamlToMetadata(String yaml) {
      Map<String,String> md = new HashMap<String,String>();
      String[] lines = yaml.split("\r\n");
      for (String line : lines) {
         String[] parts = line.split(": ");
         if (parts.length == 2) {
            md.put(parts[0], parts[1]);
         }
      }
      return md;
   }

   private void writeFrameMetadata(Map<String,String> md, String fileName) {
      try {
         String title = "FrameKey-" + MDUtils.getFrameIndex(md) + "-" + MDUtils.getChannelName(md) + "-" + MDUtils.getSliceIndex(md);
         md.put("Filename", fileName);
         writeMetadata(md, title);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void writeMetadata(Map<String,String> md, String title) {
      try {
         if (!firstElement_) {
            metadataStream_.write(",\r\n");
         }
         metadataStream_.write("\t\"" + title + "\": {\r\n");
         Set<String> keys = md.keySet();
         long n = keys.size();
         int i=0;
         for (String key:keys) {
            ++i;
            metadataStream_.write("\t\t\"" + key + "\": \"" + md.get(key) + "\"");
            if (i < n) {
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

   private void saveImageFile(Object img, Map<String,String> md, String path, String tiffFileName) {
      ImagePlus imp;
      try {
         ImageProcessor ip = null;
         int width = MDUtils.getWidth(md);
         int height = MDUtils.getHeight(md);
         String pixelType = MDUtils.getPixelType(md);
         if (pixelType.equals("GRAY8")) {
            ip = new ByteProcessor(width, height);
            ip.setPixels((byte[]) img);
            saveImageProcessor(ip, md, path, tiffFileName);
         } else if (pixelType.equals("GRAY16")) {
            ip = new ShortProcessor(width, height);
            ip.setPixels((short[]) img);
            saveImageProcessor(ip, md, path, tiffFileName);
         } else if (pixelType.equals("RGB32")) {
            byte[][] planes = ImageUtils.getColorPlanesFromRGB32((byte []) img);
            ColorProcessor cp = new ColorProcessor(width, height);
            cp.setRGB(planes[0],planes[1],planes[2]);
            saveImageProcessor(cp, md, path, tiffFileName);
         } else if (pixelType.equals("RGB64")) {
            short[][] planes = ImageUtils.getColorPlanesFromRGB64((short []) img);
            ImageStack stack = new ImageStack(width, height);
				stack.addSlice("Red", planes[0]);
				stack.addSlice("Green", planes[1]);
				stack.addSlice("Blue", planes[2]);
        		imp = new ImagePlus(path + "/" + tiffFileName, stack);
        		imp.setDimensions(3, 1, 1);
            imp = new CompositeImage(imp, CompositeImage.COLOR);
            saveImagePlus(imp, md, path, tiffFileName);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }


   private void saveImageProcessor(ImageProcessor ip, Map<String,String> md, String path, String tiffFileName) {
      if (ip != null) {
         ImagePlus imp = new ImagePlus(path + "/" + tiffFileName, ip);
         saveImagePlus(imp, md, path, tiffFileName);
      }
   }


   public void saveImagePlus(ImagePlus imp, Map<String, String> md, String path, String tiffFileName) {
      imp.setProperty("Info", metadataToYaml(md));
      FileSaver fs = new FileSaver(imp);
      fs.saveAsTiff(path + "/" + tiffFileName);
   }

   private void openNewDataSet() throws Exception, IOException {
      JavaUtils.createDirectory(dir_);
      firstElement_ = true;
      metadataStream_ = new BufferedWriter(new FileWriter(dir_ + "/metadata.txt"));
      metadataStream_.write("{" + "\r\n");
      writeMetadata(getSummaryMetadata(), "Summary");
      //writeMetadata(getSystemMetadata(), "SystemState");
   }

   public void finished() {
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
            for (String key:makeJsonIterableKeys(data)) {
               JSONObject chunk = data.getJSONObject(key);
               if (key.startsWith("FrameKey")) {
                  Map<String,String> md = jsonToMetadata(chunk);
                  filenameTable_.put(MDUtils.getLabel(md), MDUtils.getFileName(md));
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

   private Map<String,String> jsonToMetadata(final JSONObject data) {
      Map<String,String> md = new HashMap<String,String>();
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
   public Map<String,String> getSummaryMetadata() {
      return summaryMetadata_;
   }

   /**
    * @param summaryMetadata the summaryMetadata to set
    */
   public void setSummaryMetadata(Map<String,String> summaryMetadata) {
      this.summaryMetadata_ = summaryMetadata;
   }

   public void setComment(String text) {
      JavaUtils.writeTextFile(dir_ + "/Comments.txt", text);
   }

   public String getComment() {
      return JavaUtils.readTextFile(dir_ + "/Comments.txt");
   }


}
