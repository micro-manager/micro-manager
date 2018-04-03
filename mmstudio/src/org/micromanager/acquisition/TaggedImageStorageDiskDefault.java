
package org.micromanager.acquisition;

import org.micromanager.api.TaggedImageStorage;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.io.TiffDecoder;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.*;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.utils.*;

/**
 *
 * @author arthur
 */
public class TaggedImageStorageDiskDefault implements TaggedImageStorage {
   private final String dir_;
   private boolean firstElement_;
   private HashMap<Integer,Writer> metadataStreams_;
   private boolean newDataSet_;
   private JSONObject summaryMetadata_;
   private TreeMap<String,String> filenameTable_;
   private HashMap<String, JSONObject> metadataTable_ = null;
   private JSONObject displaySettings_;
   private int lastFrame_ = -1;
   private Thread shutdownHook_;
   private HashMap<Integer, String> positionNames_;

   public TaggedImageStorageDiskDefault(String dir) throws Exception {
      this(dir, false, null);
   }

   public TaggedImageStorageDiskDefault(String dir, Boolean newDataSet,
           JSONObject summaryMetadata) throws Exception {
      dir_ = dir;
      newDataSet_ = newDataSet;
      filenameTable_ = new TreeMap<String,String>(new ImageLabelComparator());
      metadataStreams_ = new HashMap<Integer,Writer>();
      metadataTable_ = new HashMap<String, JSONObject>();
      displaySettings_ = new JSONObject();
      positionNames_ = new HashMap<Integer,String>();
      setSummaryMetadata(summaryMetadata);
      
      // Note: this will throw an error if there is no existing data set
      if (!newDataSet_) {
         openExistingDataSet();
      }
      
      shutdownHook_ = new Thread() {
         @Override
         public void run() {
            writeDisplaySettings();
         }
      };
      
      Runtime.getRuntime().addShutdownHook(this.shutdownHook_);

   }

   @Override
   public int lastAcquiredFrame() {
      return lastFrame_;
   }

   private String getPosition(TaggedImage taggedImg) {
      return getPosition(taggedImg.tags);
   }
   
   private String getPosition(JSONObject tags) {
      try {
         String pos = MDUtils.getPositionName(tags);
         if (pos == null) {
            return "";
         }
         return pos;
      } catch (Exception e) {
         return "";
      }
   }

   @Override
   public void putImage(TaggedImage taggedImg) throws MMException {
      try {
         if (!newDataSet_) {
            throw new MMException("This ImageFileManager is read-only.");
         }
         if (!metadataStreams_.containsKey(MDUtils.getPositionIndex(taggedImg.tags))) {
            try {
               openNewDataSet(taggedImg);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
         JSONObject md = taggedImg.tags;
         Object img = taggedImg.pix;
         String tiffFileName = createFileName(md);
         MDUtils.setFileName(md, tiffFileName);
         String posName;
         String fileName = tiffFileName;
         try {
            posName = positionNames_.get(MDUtils.getPositionIndex(md));
            if (posName != null && posName.length() > 0 && 
                  !posName.contentEquals("null")) {
               JavaUtils.createDirectory(dir_ + "/" + posName);
               fileName = posName + "/" + tiffFileName;
            } else {
               fileName = tiffFileName;
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }         

         File saveFile = new File(dir_, fileName);
         if (saveFile.exists()) {
            MMStudio.getInstance().stopAllActivity();
            throw new IOException("Image saving failed: " + saveFile.getAbsolutePath());
         }
         
         saveImageFile(img, md, dir_, fileName);
         writeFrameMetadata(md);
         String label = MDUtils.getLabel(md);
         filenameTable_.put(label, fileName);
      } catch (Exception ex) {
         ReportingUtils.showError(ex);
      }
   }

   @Override
   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      String label = MDUtils.generateLabel(channel, slice, frame, position);
      if (filenameTable_.get(label) == null) {
         return null;
      }
      ImagePlus imp = new Opener().openImage(dir_ + "/" + filenameTable_.get(label));
      if (imp != null) {
         try {
            ImageProcessor proc = imp.getProcessor();
            JSONObject md = null;
            try {
               if (imp.getProperty("Info") != null) {
                  md = new JSONObject((String) imp.getProperty("Info"));
               } else {
                 md = metadataTable_.get(label);
               }
            } catch (Exception e) {
               if (metadataTable_.size() > 0) {
                  md = metadataTable_.get(label);
                  return null;
               }
            }
            String pixelType = MDUtils.getPixelType(md);
            Object img;
            if (pixelType.contentEquals("GRAY8") || pixelType.contentEquals("GRAY16")) {
               img = proc.getPixels();
            } else if (pixelType.contentEquals("RGB32")) {
               img = proc.getPixels();
               img = ImageUtils.convertRGB32IntToBytes((int[]) img);
            } else if (pixelType.contentEquals("RGB64")) {
               ImageStack stack = ((CompositeImage) imp).getStack();
               short[] r = (short[]) stack.getProcessor(1).getPixels();
               short[] g = (short[]) stack.getProcessor(2).getPixels();
               short[] b = (short[]) stack.getProcessor(3).getPixels();
               short[][] planes = {r, g, b};
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

   @Override
   public JSONObject getImageTags(int channel, int slice, int frame, int position) {
      String label = MDUtils.generateLabel(channel, slice, frame, position);
      TiffDecoder td = new TiffDecoder(dir_, filenameTable_.get(label));
      try {
         return new JSONObject(td.getTiffInfo()[0].info);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   @Override
   public Set<String> imageKeys() {
      return filenameTable_.keySet();
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

   private void writeFrameMetadata(JSONObject md) {
      try {    
         String title = "FrameKey-" + MDUtils.getFrameIndex(md) + "-" + MDUtils.getChannelIndex(md) + "-" + MDUtils.getSliceIndex(md);
         int pos = MDUtils.getPositionIndex(md);
         writeMetadata(pos, md, title);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void writeMetadata(int pos, JSONObject md, String title) {
      try {
         Writer metadataStream = metadataStreams_.get(pos);
         if (!firstElement_) {
            metadataStream.write(",\n");
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
         ImageProcessor ip;
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
         } else if (pixelType.equals("GRAY32")) {
            ip = new FloatProcessor(width, height);
            ip.setPixels((float[]) img);
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
         applyPixelSizeCalibration(imp);
         saveImagePlus(imp, md, path, tiffFileName);
      }
   }
   
   private void applyPixelSizeCalibration(final ImagePlus ip) {
      try {
         JSONObject summary = getSummaryMetadata();
         double pixSizeUm = summary.getDouble("PixelSize_um");
         if (pixSizeUm > 0) {
            ij.measure.Calibration cal = new ij.measure.Calibration();
            cal.setUnit("um");
            cal.pixelWidth = pixSizeUm;
            cal.pixelHeight = pixSizeUm;
            String intMs = "Interval_ms";
            if (summary.has(intMs)) {
               cal.frameInterval = summary.getDouble(intMs) / 1000.0;
            }
            String zStepUm = "z-step_um";
            if (summary.has(zStepUm)) {
               cal.pixelDepth = summary.getDouble(zStepUm);
            }
            ip.setCalibration(cal);
         }
      } catch (JSONException ex) {
         // no pixelsize defined.  Nothing to do
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

   private void openNewDataSet(TaggedImage firstImage) throws IOException, Exception {
      String time = MDUtils.getImageTime(firstImage.tags);
      int pos;
      String posName = getPosition(firstImage);
      
      try {
         pos = MDUtils.getPositionIndex(firstImage.tags);
      } catch (JSONException e) {
         pos = 0;
         posName = "";
      }

      if (positionNames_.containsKey(pos)
              && positionNames_.get(pos) != null
              && !positionNames_.get(pos).contentEquals(posName)) {
         throw new IOException ("Position name changed during acquisition.");
      }

      positionNames_.put(pos, posName);
      JavaUtils.createDirectory(dir_ + "/" + posName);
      firstElement_ = true;
      Writer metadataStream = new BufferedWriter(new FileWriter(dir_ + "/" + posName + "/metadata.txt"));
      metadataStreams_.put(pos, metadataStream);
      metadataStream.write("{" + "\n");
      JSONObject summaryMetadata = getSummaryMetadata();
      summaryMetadata.put("Time", time);
      summaryMetadata.put("Date", time.split(" ")[0]);
      summaryMetadata.put("PositionIndex", MDUtils.getPositionIndex(firstImage.tags));
      writeMetadata(pos, summaryMetadata, "Summary");
   }

   @Override
   public void finished() {
      closeMetadataStreams();
      newDataSet_ = false;
   }

   @Override
   public boolean isFinished() {
      return !newDataSet_;
   }

   private void closeMetadataStreams() {
      if (newDataSet_) {
         try {
            for (Writer metadataStream:metadataStreams_.values()) {
               metadataStream.write("\n}\n");
               metadataStream.close();
            }
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   private void openExistingDataSet() throws Exception {
      File metadataFile = new File(dir_ + "/metadata.txt");
      ArrayList<String> positions = new ArrayList<String>();
      if (metadataFile.exists()) {
         positions.add("");
      } else {
         for (File f : new File(dir_).listFiles()) {
            if (f.isDirectory()) {
               positions.add(f.getName());
            }
         }
      }

      for (int positionIndex = 0; positionIndex < positions.size(); ++positionIndex) {
         String position = positions.get(positionIndex);
         JSONObject data = readJsonMetadata(position);
         if (data != null) {
            try {
               summaryMetadata_ = jsonToMetadata(data.getJSONObject("Summary"));
               int metadataVersion = 0;
               try {
                  metadataVersion = summaryMetadata_.getInt("MetadataVersion");
               } catch (JSONException ex) {
               }
               for (String key:makeJsonIterableKeys(data)) {
                  JSONObject chunk = data.getJSONObject(key);
                  if (key.startsWith("FrameKey")) {
                     JSONObject md = jsonToMetadata(chunk);
                     try {
                        if (!md.has("ChannelIndex"))
                           md.put("ChannelIndex", getChannelIndex(MDUtils.getChannelName(md)));
                        if (!md.has("PositionIndex"))
                           md.put("PositionIndex", positionIndex);
                        if (!md.has("PixelType") && !md.has("IJType")) {
                           md.put("PixelType", MDUtils.getPixelType(summaryMetadata_));
                        }
                        lastFrame_ = Math.max(MDUtils.getFrameIndex(md), lastFrame_);
                        String fileName = MDUtils.getFileName(md);
                        if (fileName == null) {
                           fileName = "img_" + String.format("%9d", MDUtils.getFrameIndex(md))
                                   + "_" + MDUtils.getChannelName(md)
                                   + "_" + String.format("%3d", MDUtils.getSliceIndex(md));
                        }
                        if (position.length() > 0)
                           fileName = position + "/" + fileName;
                        
                        filenameTable_.put(MDUtils.getLabel(md), fileName);
                        if (metadataVersion < 10)
                           metadataTable_.put(MDUtils.getLabel(md), md);
                        
                     } catch (Exception ex) {
                        ReportingUtils.showError(ex);
                     }
                  }
               }
            } catch (JSONException ex) {
               ReportingUtils.showError(ex);
            }
         } else {
            throw (new IOException("No metadata file found"));
         }
        
      }
      readDisplaySettings();
   }

   private int getChannelIndex(String channelName) {
      try {
         JSONArray channelNames;
         Object tmp;
         try {
            tmp = getSummaryMetadata().get("ChNames");
            if (tmp instanceof String) {
               channelNames = new JSONArray((String) tmp);
            } else {
               channelNames = (JSONArray) tmp;
            }
            for (int i=0;i<channelNames.length();++i) {
               if (channelNames.getString(i).contentEquals(channelName))
                  return i;
            }
            // older metadata versions may not have ChNames
            // create it here from the data we read from the FrameKeys
            channelNames.put(channelName);
            return channelNames.length();
         } catch (JSONException ex) {
            channelNames = new JSONArray();
            channelNames.put(channelName);
            getSummaryMetadata().put("ChNames", channelNames);
            return 0;
         }
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
         return 0;
      }
   }

   private Iterable<String> makeJsonIterableKeys(final JSONObject data) {
      return new Iterable<String>() {
            @Override
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
            md.put(key, data.getString(key));
         }
      } catch (JSONException ex) {
         ReportingUtils.showError(ex);
      }

      return md;

   }

   private JSONObject readJsonMetadata(String pos) throws Exception {
      String fileStr;
      fileStr = TextUtils.readTextFile(dir_ + "/" + pos + "/metadata.txt");
      try {
         return new JSONObject(fileStr);
      } catch (JSONException ex) {
         return new JSONObject(fileStr.concat("}"));
      }
   }

   /**
    * @return the summaryMetadata_
    */
   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   /**
    * @param summaryMetadata the summaryMetadata to set
    */
   @Override
   public final void setSummaryMetadata(JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      if (summaryMetadata_ != null) {
         boolean slicesFirst = summaryMetadata_.optBoolean("SlicesFirst", true);
         boolean timeFirst = summaryMetadata_.optBoolean("TimeFirst", false);
         TreeMap<String, String> oldFilenameTable = filenameTable_;
         filenameTable_ = new TreeMap<String, String>(new ImageLabelComparator(slicesFirst, timeFirst));
         filenameTable_.putAll(oldFilenameTable);
      }
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      displaySettings_ = settings;
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return displaySettings_;
   }

   @Override
   public void writeDisplaySettings() {
      if (displaySettings_ == null)
         return;
      if (! new File(dir_).exists()) {
         try {
            JavaUtils.createDirectory(dir_);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
      File displayFile = new File(dir_ + "/" + "display_and_comments.txt");
      try {
         Writer displayFileWriter = new FileWriter(displayFile);
         displayFileWriter.append(displaySettings_.toString(2));
         displayFileWriter.close();
      } catch (Exception e) {
         ReportingUtils.showError(e);
      }
   }

   private void readDisplaySettings() {
      displaySettings_ = null;
      String path = dir_ + "/" + "display_and_comments.txt";
      try {
         String jsonText = JavaUtils.readTextFile(path);
         if (jsonText != null) {
            JSONObject tmp = new JSONObject(jsonText);
            JSONArray channels = (JSONArray) tmp.get("Channels");
            if (channels != null)
               displaySettings_ = tmp;
            else
               displaySettings_.put("Comments", tmp.get("Comments"));
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   @Override
   public void close() {
      try {
         writeDisplaySettings();
         if (shutdownHook_ != null) {
            Runtime.getRuntime().removeShutdownHook(shutdownHook_);
         }
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   @Override
   public String getDiskLocation() {
      return dir_;
   }

   @Override
   public void finalize() throws Throwable {
      close();
      super.finalize();
   }

   @Override
   public long getDataSetSize() {
      File[] files = new File(dir_).listFiles(new FileFilter() {
         @Override
         public boolean accept(File pathname) {
            return pathname.getName().toLowerCase().endsWith(".tif");
         }
      });
      int numTiffFiles = files.length;
      long tiffSize = (numTiffFiles > 0) ? files[0].length() : 0;
      return numTiffFiles * tiffSize;
   }

}
