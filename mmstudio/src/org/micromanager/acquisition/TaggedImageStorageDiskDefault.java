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
import java.io.Writer;
import java.util.HashMap;
import java.util.Iterator;
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
   private HashMap<String,Writer> metadataStreams_;
   private boolean newDataSet_;
   private JSONObject summaryMetadata_;
   private HashMap<String,String> filenameTable_;


   TaggedImageStorageDiskDefault(String dir) {
      this(dir, false, null);
   }

   TaggedImageStorageDiskDefault(String dir, boolean newDataSet,
           JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      dir_ = dir;
      newDataSet_ = newDataSet;
      filenameTable_ = new HashMap<String,String>();
      metadataStreams_ = new HashMap<String,Writer>();

      try {
         if (!newDataSet_) {
            openExistingDataSet();
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private String getPosition(TaggedImage taggedImg) {
      return getPosition(taggedImg.tags);
   }
   
   private String getPosition(JSONObject tags) {
      try {
         return MDUtils.getPositionName(tags);
      } catch (Exception e) {
         return "";
      }
   }

   public String putImage(TaggedImage taggedImg) throws MMException {
      try {
         if (newDataSet_ == false) {
            throw new MMException("This ImageFileManager is read-only.");
         }
         if (!metadataStreams_.containsKey(getPosition(taggedImg))) {
            try {
               openNewDataSet(taggedImg);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
         JSONObject md = taggedImg.tags;
         Object img = taggedImg.pix;
         String tiffFileName = createFileName(md);
         String posName = "";
         try {
            posName = getPosition(md);
            JavaUtils.createDirectory(dir_ + "/" + posName);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }

         MDUtils.setFileName(md, tiffFileName);
         saveImageFile(img, md, dir_ +"/" + posName, tiffFileName);
         writeFrameMetadata(md, tiffFileName);
         String label = MDUtils.getLabel(md);
         filenameTable_.put(label, tiffFileName);
         return MDUtils.getLabel(md);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      String label = MDUtils.generateLabel(channel, slice, frame, position);
      ImagePlus imp = new Opener().openImage(dir_ + "/" + filenameTable_.get(label));
      if (imp != null) {
         try {
            ImageProcessor proc = imp.getProcessor();
            JSONObject md = new JSONObject((String) imp.getProperty("Info"));
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

   private String createFileName(JSONObject md) {
      try {
         int frame;
         int slice;
         String channel;
         try {
            frame = MDUtils.getFrameIndex(md);
         } catch (Exception e) {
            frame = 0;
         }
         try {
            channel = MDUtils.getChannelName(md);
         } catch (Exception e) {
            channel = "";
         }
         try {
            slice = MDUtils.getSliceIndex(md);
         } catch (Exception e) {
            slice = 0;
         }


         return String.format("img_%09d_%s_%03d.tif",
                 frame,
                 channel,
                 slice);
      } catch (Exception e) {
         ReportingUtils.logError(e);
         return "";
      }
   }

   private void writeFrameMetadata(JSONObject md, String fileName) {
      try {    
         String title = "FrameKey-" + MDUtils.getFrameIndex(md) + "-" + MDUtils.getChannelIndex(md) + "-" + MDUtils.getSliceIndex(md);
         String pos = getPosition(md);
         writeMetadata(pos, md, title);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void writeMetadata(String pos, JSONObject md, String title) {
      try {
         Writer metadataStream = metadataStreams_.get(pos);
         if (!firstElement_) {
            metadataStream.write(",\r\n");
         }
         metadataStream.write("\"" + title + "\": ");
         metadataStream.write(md.toString(2));
         metadataStream.flush();
         firstElement_ = false;
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private void saveImageFile(Object img, JSONObject md, String path, String tiffFileName) {
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


   private void saveImageProcessor(ImageProcessor ip, JSONObject md, String path, String tiffFileName) {
      if (ip != null) {
         ImagePlus imp = new ImagePlus(path + "/" + tiffFileName, ip);
         saveImagePlus(imp, md, path, tiffFileName);
      }
   }


   public void saveImagePlus(ImagePlus imp, JSONObject md, String path, String tiffFileName) {
      try {
         imp.setProperty("Info", md.toString(2));
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
      FileSaver fs = new FileSaver(imp);
      fs.saveAsTiff(path + "/" + tiffFileName);
   }

   private void openNewDataSet(TaggedImage firstImage) throws Exception, IOException {
      String time = firstImage.tags.getString("Time");
      String pos = getPosition(firstImage);
      JavaUtils.createDirectory(dir_ + "/" + pos);
      firstElement_ = true;
      Writer metadataStream = new BufferedWriter(new FileWriter(dir_ + "/" + pos + "/metadata.txt"));
      metadataStreams_.put(pos, metadataStream);
      metadataStream.write("{" + "\r\n");
      JSONObject summaryMetadata = getSummaryMetadata();
      summaryMetadata.put("Time", time);
      summaryMetadata.put("Date", time.split(" ")[0]);
      writeMetadata(pos, getSummaryMetadata(), "Summary");
   }

   public void finished() {
      closeMetadataStreams();
      newDataSet_ = false;
   }

   private void closeMetadataStreams() {
      if (newDataSet_) {
         try {
            for (Writer metadataStream:metadataStreams_.values()) {
               metadataStream.write("\r\n}\r\n");
               metadataStream.close();
            }
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
                  JSONObject md = jsonToMetadata(chunk);
                  try {
                     filenameTable_.put(MDUtils.getLabel(md), MDUtils.getFileName(md));
                  } catch (Exception ex) {
                     ReportingUtils.showError(ex);
                  }
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

   private JSONObject jsonToMetadata(final JSONObject data) {
      JSONObject md = new JSONObject();
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
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   /**
    * @param summaryMetadata the summaryMetadata to set
    */
   public void setSummaryMetadata(JSONObject summaryMetadata) {
      this.summaryMetadata_ = summaryMetadata;
   }

   public void setComment(String text) {
      try {
         getSummaryMetadata().put("Comment", text);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      if (text != null)
         JavaUtils.writeTextFile(dir_ + "/comments.txt", text);
   }

   public String getComment() {
      return JavaUtils.readTextFile(dir_ + "/comments.txt");
   }

   public void setDisplaySettings(JSONObject settings) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public JSONObject getDisplaySettings() {
      throw new UnsupportedOperationException("Not supported yet.");
   }


}
