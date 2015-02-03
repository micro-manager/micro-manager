package org.micromanager.data.internal;

import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.io.FileSaver;
import ij.io.Opener;
import ij.io.TiffDecoder;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import java.awt.Dimension;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileFilter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import mmcorej.TaggedImage;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Coords;
import org.micromanager.data.DatastoreLockedEvent;
import org.micromanager.data.DatastoreLockedException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.NewImageEvent;
import org.micromanager.data.NewSummaryMetadataEvent;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.TextUtils;

/**
 * This class provides Image storage backed by a file system in which each
 * file contains a single 2D image plane. It descends from the old
 * TaggedImageStorageDiskDefault class.
 */
public class StorageSinglePlaneTiffSeries implements Storage {
   private DefaultDatastore store_;
   private final String dir_;
   private boolean firstElement_;
   private boolean amLoading_;
   private HashMap<Integer, Writer> metadataStreams_;
   private boolean isDatasetWritable_;
   private SummaryMetadata summaryMetadata_;
   private HashMap<Coords, String> coordsToFilename_;
   private HashMap<Coords, Dimension> coordsToImageDims_;
   private HashMap<Integer, String> positionIndexToName_;
   private Coords maxIndices_;

   public StorageSinglePlaneTiffSeries(DefaultDatastore store,
         String directory, boolean newDataSet) {
      store_ = store;
      dir_ = directory;
      isDatasetWritable_ = newDataSet;
      // Must be informed of events before traditional consumers, so that we
      // can provide images on request.
      store_.registerForEvents(this, 0);
      coordsToFilename_ = new HashMap<Coords, String>();
      metadataStreams_ = new HashMap<Integer, Writer>();
      coordsToImageDims_ = new HashMap<Coords, Dimension>();
      positionIndexToName_ = new HashMap<Integer, String>();
      maxIndices_ = new DefaultCoords.Builder().build();
      amLoading_ = false;

      // Note: this will throw an error if there is no existing data set
      if (!isDatasetWritable_) {
         openExistingDataSet();
      }
   }

   @Subscribe
   public void onNewImage(NewImageEvent event) {
      try {
         addImage(event.getImage());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to add image to storage");
      }
   }

   private void addImage(Image image) {
      if (!isDatasetWritable_ && !amLoading_) {
         // This should never happen!
         ReportingUtils.logError("Attempted to add an image to a read-only fileset");
         return;
      }
      // If we're in the middle of loading a file, then the code that writes
      // stuff to disk should not run; we only need to update our internal
      // records.
      String fileName = createFileName(image.getCoords());
      if (!amLoading_) {
         int imagePos = image.getCoords().getPositionAt(Coords.STAGE_POSITION);
         if (!metadataStreams_.containsKey(imagePos)) {
            // No metadata for image at this location, means we haven't
            // written to its location before.
            try {
               openNewDataSet(image);
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
         String posName = image.getMetadata().getPositionName();
         if (posName != null && posName.length() > 0 && 
               !posName.contentEquals("null")) {
            // Create a directory to hold images for this stage position.
            String dirName = dir_ + "/" + posName;
            try {
               JavaUtils.createDirectory(dirName);
            }
            catch (Exception e) {
               ReportingUtils.showError("Unable to create save directory " + dirName);
            }
            fileName = posName + "/" + fileName;
         }

         File saveFile = new File(dir_, fileName);
         if (saveFile.exists()) {
            MMStudio.getInstance().stopAllActivity();
            ReportingUtils.showError(
                  "Image saving failed: file already exists: " +
                  saveFile.getAbsolutePath());
         }

         saveImageFile(image, dir_, fileName);
         writeFrameMetadata(image);
      }

      Coords coords = image.getCoords();
      // TODO: is this in fact always the correct fileName? What if it isn't?
      // See the above code that branches based on amLoading_.
      coordsToFilename_.put(coords, fileName);
      // Update our tracking of the max position along each axis.
      for (String axis : coords.getAxes()) {
         if (coords.getPositionAt(axis) > maxIndices_.getPositionAt(axis)) {
            maxIndices_ = maxIndices_.copy().position(axis, coords.getPositionAt(axis)).build();
         }
      }
   }

   // TODO: consider caching frequently-requested images to cut down on
   // disk accesses.
   @Override
   public Image getImage(Coords coords) {
      if (coordsToFilename_.get(coords) == null) {
         // We don't have that image.
         return null;
      }
      ImagePlus imp = new Opener().openImage(dir_ + "/" + coordsToFilename_.get(coords));
      if (imp == null) {
         // Loading failed.
         return null;
      }
      try {
         // Assemble an Image out of the pixels and JSON-ified metadata.
         ImageProcessor proc = imp.getProcessor();
         Metadata metadata = null;
         int width = -1;
         int height = -1;
         if (imp.getProperty("Info") != null) {
            try {
               JSONObject jsonMeta = new JSONObject((String) imp.getProperty("Info"));
               ReportingUtils.logError("Loaded metadata\n" + jsonMeta.toString(2));
               metadata = DefaultMetadata.legacyFromJSON(jsonMeta);
               width = MDUtils.getWidth(jsonMeta);
               height = MDUtils.getHeight(jsonMeta);
            }
            catch (Exception e) {
               ReportingUtils.logError(e, "Unable to extract image dimensions from JSON metadata");
               return null;
            }
         }
         else {
            ReportingUtils.logError("Unable to reconstruct metadata for image at " + coords);
         }

         Object pixels = proc.getPixels();
         int bytesPerPixel = -1;
         int numComponents = -1;
         if (proc instanceof ByteProcessor) {
            bytesPerPixel = 1;
            numComponents = 1;
         }
         else if (proc instanceof ShortProcessor) {
            bytesPerPixel = 2;
            numComponents = 1;
         }
         else if (proc instanceof ColorProcessor) {
            bytesPerPixel = 4;
            numComponents = 3;
         }
         else {
            ReportingUtils.logError("Received an ImageProcessor of unrecognized type " + proc);
            return null;
         }
         DefaultImage result = new DefaultImage(pixels, width, height,
               bytesPerPixel, numComponents, coords, metadata);
         return result;
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   @Override
   public Image getAnyImage() {
      if (coordsToFilename_.size() == 0) {
         return null;
      }
      Coords coords = new ArrayList<Coords>(coordsToFilename_.keySet()).get(0);
      return getImage(coords);
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      return coordsToFilename_.keySet();
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) {
      ArrayList<Image> result = new ArrayList<Image>();
      for (Coords altCoords : coordsToFilename_.keySet()) {
         boolean canUse = true;
         for (String axis : coords.getAxes()) {
            if (coords.getPositionAt(axis) != altCoords.getPositionAt(axis)) {
               canUse = false;
               break;
            }
         }
         if (canUse) {
            result.add(getImage(altCoords));
         }
      }
      return result;
   }

   @Override
   public Integer getMaxIndex(String axis) {
      return maxIndices_.getPositionAt(axis);
   }

   @Override
   public List<String> getAxes() {
      return maxIndices_.getAxes();
   }

   @Override
   public Coords getMaxIndices() {
      return maxIndices_;
   }

   @Override
   public int getNumImages() {
      return coordsToFilename_.size();
   }

   /**
    * Generate a filename based on the coordinates of an image.
    */
   private String createFileName(Coords coords) {
      List<String> axes = coords.getAxes();
      java.util.Collections.sort(axes);
      String filename = "img";
      for (String axis : axes) {
         // HACK: more precision for the time axis.
         String precision = "%03d";
         if (axis.contentEquals(Coords.TIME)) {
            precision = "%09d";
         }
         filename += String.format("_%s" + precision, axis,
               coords.getPositionAt(axis));
      }
      return filename + ".tif";
   }

   private void writeFrameMetadata(Image image) {
      try {
         String title = "Coords-" + createFileName(image.getCoords());
         // Use 0 for situations where there's no position information.
         int pos = Math.max(0, image.getCoords().getStagePosition());
         JSONObject coords = new JSONObject();
         for (String axis : image.getCoords().getAxes()) {
            coords.put(axis, image.getCoords().getPositionAt(axis));
         }
         writeJSONMetadata(pos, coords, title);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void writeJSONMetadata(int pos, JSONObject metadata, String title) {
      try {
         Writer metadataStream = metadataStreams_.get(pos);
         if (metadataStream == null) {
            ReportingUtils.logError("Failed to make a stream for location " + pos);
         }
         if (!firstElement_) {
            metadataStream.write(",\n");
         }
         metadataStream.write("\"" + title + "\": ");
         metadataStream.write(metadata.toString(2));
         metadataStream.flush();
         firstElement_ = false;
      } catch (Exception e) {
         ReportingUtils.logError(e);
      }
   }

   private void saveImageFile(Image image, String path, String tiffFileName) {
      ImagePlus imp;
      try {
         ImageProcessor ip;
         int width = image.getWidth();
         int height = image.getHeight();
         Object pixels = image.getRawPixels();
         int bytesPerPixel = image.getBytesPerPixel();
         int numComponents = image.getNumComponents();
         ImageProcessor proc;
         if (numComponents == 3 && bytesPerPixel == 4) {
            // 32-bit RGB
            // TODO: can we just use setPixels() here?
            proc = new ColorProcessor(width, height);
            ((ColorProcessor) proc).setRGB(
                  (byte[]) image.getRawPixelsForComponent(0),
                  (byte[]) image.getRawPixelsForComponent(1),
                  (byte[]) image.getRawPixelsForComponent(2));
         }
         else if (numComponents == 1 && bytesPerPixel == 1) {
            // Byte
            proc = new ByteProcessor(width, height);
            proc.setPixels((byte[]) pixels);
         }
         else if (numComponents == 1 && bytesPerPixel == 2) {
            // Short
            proc = new ShortProcessor(width, height);
            proc.setPixels((short[]) pixels);
         }
         else {
            throw new IllegalArgumentException(String.format("Unexpected image format with %d bytes per pixel and %d components", bytesPerPixel, numComponents));
         }
         saveImageProcessor(proc, image, path, tiffFileName);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }


   private void saveImageProcessor(ImageProcessor ip, Image image,
         String path, String tiffFileName) {
      // TODO: why is this check here?
      if (ip == null) {
         return;
      }
      ImagePlus imp = new ImagePlus(path + "/" + tiffFileName, ip);
      applyPixelSizeCalibration(imp, image.getMetadata());
      saveImagePlus(imp, image, path, tiffFileName);
   }

   private void applyPixelSizeCalibration(final ImagePlus ip,
         Metadata metadata) {
      Double pixSizeUm = metadata.getPixelSizeUm();
      if (pixSizeUm != null && pixSizeUm > 0) {
         ij.measure.Calibration cal = new ij.measure.Calibration();
         cal.setUnit("um");
         cal.pixelWidth = pixSizeUm;
         cal.pixelHeight = pixSizeUm;
         String intMs = "Interval_ms";
         if (summaryMetadata_.getWaitInterval() != null) {
            cal.frameInterval = summaryMetadata_.getWaitInterval() / 1000.0;
         }
         String zStepUm = "z-step_um";
         if (summaryMetadata_.getZStepUm() != null) {
            cal.pixelDepth = summaryMetadata_.getZStepUm();
         }
         ip.setCalibration(cal);
      }
   }


   public void saveImagePlus(ImagePlus imp, Image image,
         String path, String tiffFileName) {
      try {
         JSONObject imageJSON = image.getMetadata().legacyToJSON();
         // Augment the JSON with image property info.
         imageJSON.put("Width", image.getWidth());
         imageJSON.put("Height", image.getHeight());
         imp.setProperty("Info", imageJSON.toString(2));
         ReportingUtils.logError("Setting metadata\n" + imageJSON.toString(2));
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
      FileSaver fs = new FileSaver(imp);
      fs.saveAsTiff(path + "/" + tiffFileName);
   }

   private void openNewDataSet(Image image) throws IOException, Exception {
      String posName = image.getMetadata().getPositionName();

      int pos = image.getCoords().getStagePosition();
      if (pos == -1) {
         // No stage position axis.
         pos = 0;
         posName = "";
      }

      if (positionIndexToName_.containsKey(pos)
              && positionIndexToName_.get(pos) != null
              && !positionIndexToName_.get(pos).contentEquals(posName)) {
         throw new IOException ("Position name changed during acquisition.");
      }

      positionIndexToName_.put(pos, posName);
      JavaUtils.createDirectory(dir_ + "/" + posName);
      firstElement_ = true;
      Writer metadataStream = new BufferedWriter(new FileWriter(dir_ + "/" + posName + "/metadata.txt"));
      metadataStreams_.put(pos, metadataStream);
      metadataStream.write("{" + "\n");
      // TODO: this method of extracting the date is extremely hacky and
      // potentially locale-dependent.
      String time = image.getMetadata().getReceivedTime();
      // TODO: should we log if the date isn't available?
      SummaryMetadata summary = summaryMetadata_;
      if (time != null) {
         summary = summary.copy().startDate(time.split(" ")[0]).build();
      }
      writeJSONMetadata(pos, summary.legacyToJSON(), "Summary");
   }

   @Subscribe
   public void onDatastoreLocked(DatastoreLockedEvent event) {
      finish();
   }

   private void finish() {
      closeMetadataStreams();
      isDatasetWritable_ = false;
   }

   private void closeMetadataStreams() {
      if (isDatasetWritable_) {
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

   private void openExistingDataSet() {
      amLoading_ = true;
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
         ReportingUtils.logError("Loading files for position " + positionIndex);
         String position = positions.get(positionIndex);
         JSONObject data = readJSONMetadata(position);
         if (data == null) {
            ReportingUtils.logError("Couldn't load metadata for position " + position + " in directory " + dir_);
            continue;
         }
         try {
            summaryMetadata_ = DefaultSummaryMetadata.legacyFromJSON(
                  data.getJSONObject("Summary"));
            ReportingUtils.logError("Made summary metadata " + summaryMetadata_);
            for (String key : makeJsonIterableKeys(data)) {
               if (!key.contains("Coords-")) {
                  continue;
               }
               JSONObject chunk = data.getJSONObject(key);
               try {
                  DefaultCoords.Builder builder = new DefaultCoords.Builder();
                  for (String axis : makeJsonIterableKeys(chunk)) {
                     builder.position(axis, chunk.getInt(axis));
                  }
                  // TODO: omitting pixel type information.

                  // Reconstruct the filename from the coordinates.
                  DefaultCoords coords = builder.build();
                  ReportingUtils.logError("Should be an image at " + coords);
                  String fileName = createFileName(coords);
                  if (position.length() > 0) {
                     // File is in a subdirectory.
                     fileName = position + "/" + fileName;
                  }
                  // This will update our internal records without touching
                  // the disk, as amLoading_ is true.
                  coordsToFilename_.put(coords, fileName);
                  store_.putImage(getImage(coords));
               } catch (Exception ex) {
                  ReportingUtils.showError(ex);
               }
            }
         } catch (JSONException ex) {
            ReportingUtils.showError(ex);
         }
      }
      amLoading_ = false;
   }

   private int getChannelIndex(String channelName) {
      if (summaryMetadata_.getChannelNames() == null) {
         return 0;
      }
      int result = Arrays.asList(summaryMetadata_.getChannelNames()).indexOf(channelName);
      if (result == -1) {
         // Nothing for this channel.
         result = 0;
      }
      return result;
   }

   private Iterable<String> makeJsonIterableKeys(final JSONObject data) {
      return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
               return data.keys();
            }
         };
   }

   private JSONObject readJSONMetadata(String pos) {
      String fileStr;
      String path = new File(new File(dir_, pos), "metadata.txt").getPath();
      try {
         fileStr = TextUtils.readTextFile(path);
      }
      catch (IOException e) {
         ReportingUtils.logError(e, "Unable to read text file at " + path);
         return null;
      }
      try {
         return new JSONObject(fileStr);
      }
      catch (JSONException e) {
         // HACK: try again with a manually-added curly brace.
         // TODO: why do we do this?
         try {
            return new JSONObject(fileStr.concat("}"));
         }
         catch (JSONException e2) {
            // Give up.
            return null;
         }
      }
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Subscribe
   public void onNewSummaryMetadata(NewSummaryMetadataEvent event) {
      summaryMetadata_ = event.getSummaryMetadata();
   }
}
