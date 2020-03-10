///////////////////////////////////////////////////////////////////////////////
//FILE:          MagellanTaggedImageStorageMultipageTiff.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.magellan.internal.datasaving;

import org.micromanager.magellan.internal.imagedisplay.DisplaySettings;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadPoolExecutor;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.internal.misc.JavaUtils;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.misc.MD;
import org.micromanager.magellan.internal.misc.ProgressBar;

public final class TaggedImageStorageMultipageTiff {

   private ProgressBar savingFinishedProgressBar_;
   private JSONObject summaryMetadata_;
   private boolean newDataSet_;
   private String directory_;
   private boolean separateMetadataFile_;
   private boolean splitByXYPosition_ = false;
   private volatile boolean finished_ = false;
   private int lastAcquiredPosition_ = 0;
   private String summaryMetadataString_ = null;
   private ThreadPoolExecutor writingExecutor_;
   private int maxSliceIndex_ = 0, maxFrameIndex_ = 0, maxChannelIndex_ = 0, minSliceIndex_ = 0;
   // Images currently being written (need to keep around so that they can be
   // returned upon request via getImage()). The data structure must be
   // synchronized because the write completion is detected on a background
   // thread.
   private ConcurrentHashMap<String, TaggedImage> writePendingImages_
           = new ConcurrentHashMap<String, TaggedImage>();
   //map of position indices to objects associated with each
   private volatile HashMap<Integer, FileSet> fileSets_;
   //Map of image labels to file 
   private HashMap<String, MultipageTiffReader> tiffReadersByLabel_;
   private static boolean showProgressBars_ = true;
   private MultiResMultipageTiffStorage masterMultiResStorage_;

   public TaggedImageStorageMultipageTiff(String dir, boolean newDataSet, JSONObject summaryMetadata,
           ThreadPoolExecutor writingExecutor, MultiResMultipageTiffStorage masterMultiRes) throws IOException {
      masterMultiResStorage_ = masterMultiRes;
      writingExecutor_ = writingExecutor;
      separateMetadataFile_ = false;
      splitByXYPosition_ = false;

      newDataSet_ = newDataSet;
      directory_ = dir;
      tiffReadersByLabel_ = new HashMap<String, MultipageTiffReader>();
      setSummaryMetadata(summaryMetadata);

      if (!newDataSet_) {
         openExistingDataSet();
      }
   }

   /**
    * read summary metadata without reading index map
    *
    * @param dir
    * @return
    */
   public static JSONObject readSummaryMD(String dir) throws IOException {
      File directory = new File(dir);
      for (File f : directory.listFiles()) {
         if (f.getName().endsWith(".tif") || f.getName().endsWith(".TIF")) {
            //this is where fixing dataset code occurs
            return MultipageTiffReader.readSummaryMD(f.getAbsolutePath());
         }
      }
      throw new IOException("Couldn't find a vlid TIFF to read metadata from");
   }

   public int getNumChannels() {
      return maxChannelIndex_ + 1;
   }

   public ThreadPoolExecutor getWritingExecutor() {
      return writingExecutor_;
   }

   private void openExistingDataSet() {
      //Need to throw error if file not found
      MultipageTiffReader reader = null;
      File dir = new File(directory_);
      int numFiles = dir.listFiles().length;

      ProgressBar progressBar = null;
      try {
         progressBar = new ProgressBar("Reading " + directory_, 0, numFiles);
      } catch (Exception e) {
         //on a system that doesnt have support for graphics
         showProgressBars_ = false;
      }
      int numRead = 0;
      if (showProgressBars_) {
         progressBar.setProgress(numRead);
         progressBar.setVisible(true);
      }
      for (File f : dir.listFiles()) {
         if (f.getName().endsWith(".tif") || f.getName().endsWith(".TIF")) {
            try {
               //this is where fixing dataset code occurs
               reader = new MultipageTiffReader(f);
               Set<String> labels = reader.getIndexKeys();
               for (String label : labels) {
                  tiffReadersByLabel_.put(label, reader);
                  maxChannelIndex_ = Math.max(maxChannelIndex_, Integer.parseInt(label.split("_")[0]));
                  maxSliceIndex_ = Math.max(maxSliceIndex_, Integer.parseInt(label.split("_")[1]));
                  minSliceIndex_ = Math.min(minSliceIndex_, Integer.parseInt(label.split("_")[1]));
                  maxFrameIndex_ = Math.max(maxFrameIndex_, Integer.parseInt(label.split("_")[2]));
               }
               if (reader.getDisplaySettings() != null) {
                  masterMultiResStorage_.setDisplaySettings(reader.getDisplaySettings());
               }
            } catch (IOException ex) {
               ex.printStackTrace();
               Log.log("Couldn't open file: " + f.toString());
            }
         }
         numRead++;
         if (showProgressBars_) {
            progressBar.setProgress(numRead);
         }
      }
      if (showProgressBars_) {
         progressBar.setVisible(false);
      }

      if (reader != null) {
         setSummaryMetadata(reader.getSummaryMetadata(), true);
      }
      if (showProgressBars_) {
         progressBar.setProgress(1);
         progressBar.setVisible(false);
      }
   }

   public int getMaxFrameIndexOpenedDataset() {
      return maxFrameIndex_;
   }

   public int getMaxSliceIndexOpenedDataset() {
      return maxSliceIndex_;
   }

   public int getMinSliceIndexOpenedDataset() {
      return minSliceIndex_;
   }

   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MD.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);

      TaggedImage image = writePendingImages_.get(label);
      if (image != null) {
         return image;
      }

      MultipageTiffReader reader = tiffReadersByLabel_.get(label);
      if (reader == null) {
         return null;
      }
      return reader.readImage(label);
   }

   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      TaggedImage image = getImage(channelIndex, sliceIndex, frameIndex, positionIndex);
      if (image == null) {
         return null;
      }
      return image.tags;
   }

   /*
    * Method that allows overwrting of pixels but not MD or TIFF tags
    * so that low res stitched images can be written tile by tile
    */
   public List<Future> overwritePixels(Object pix, int channel, int slice, int frame, int position) throws IOException {
      //asumes only one position
      int fileSetIndex = 0;
      if (splitByXYPosition_) {
         fileSetIndex = position;
      }
      return fileSets_.get(fileSetIndex).overwritePixels(pix, channel, slice, frame, position);
   }

   public Future putImage(TaggedImage MagellanTaggedImage) throws IOException {
      final String label = MD.getLabel(MagellanTaggedImage.tags);
      // Now, we must hold on to MagellanTaggedImage, so that we can return it if
      // somebody calls getImage() before the writing is finished.
      // There is a data race if the MagellanTaggedImage is modified by other code, but
      // that would be a bad thing to do anyway (will break the writer) and is
      // considered forbidden.
      maxChannelIndex_ = Math.max(maxChannelIndex_, Integer.parseInt(label.split("_")[0]));
      writePendingImages_.put(label, MagellanTaggedImage);
      Future f = startWritingTask(label, MagellanTaggedImage);

      writingExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            writePendingImages_.remove(label);
         }
      });
      return f;
   }

   /*
    * Sets up and kicks off the writing of a new image. This, in an indirect
    * way, ends up submitting the writing task to writingExecutor_.
    */
   private Future startWritingTask(String label, TaggedImage ti)
           throws IOException {
      if (!newDataSet_) {
         Log.log("Tried to write image to a finished data set");
         throw new RuntimeException("This Dataset is read-only.");
      }

      int fileSetIndex = 0;
      if (splitByXYPosition_) {
         fileSetIndex = MD.getPositionIndex(ti.tags);
      }
      if (fileSets_ == null) {
         try {
            fileSets_ = new HashMap<Integer, FileSet>();
            JavaUtils.createDirectory(directory_);
         } catch (Exception ex) {
            Log.log(ex);
         }
      }

      if (fileSets_.get(fileSetIndex) == null) {
         fileSets_.put(fileSetIndex, new FileSet(ti.tags, this));
      }
      FileSet set = fileSets_.get(fileSetIndex);
      try {
         Future f = set.writeImage(ti);
         tiffReadersByLabel_.put(label, set.getCurrentReader());
         return f;
      } catch (IOException ex) {
         Log.log("problem writing image to file");
         throw new RuntimeException(ex);
      }
   }

   public Set<String> imageKeys() {
      return tiffReadersByLabel_.keySet();
   }

   /**
    * Call this function when no more images are expected Finishes writing the
    * metadata file and closes it. After calling this function, the imagestorage
    * is read-only
    */
   public synchronized void finished() {
      if (finished_) {
         return;
      }
      newDataSet_ = false;
      if (fileSets_ == null) {
         // Nothing to be done.
         finished_ = true;
         return;
      }

      //Initialize progress bar on EDT
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            if (fileSets_ == null) {
               //its already done
               return;
            }
            savingFinishedProgressBar_ = new ProgressBar("Finishing Files", 0, fileSets_.size());
            savingFinishedProgressBar_.setProgress(0);
            savingFinishedProgressBar_.setVisible(true);
         }
      });

      int count = 0;
      for (FileSet p : fileSets_.values()) {
         try {
            p.finished();
         } catch (Exception ex) {
            throw new RuntimeException(ex);
         }
         count++;
         final int currentCount = count;
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               if (savingFinishedProgressBar_ == null) {
                  return;
               }
               savingFinishedProgressBar_.setProgress(currentCount);
            }
         });
      }
      fileSets_.clear();
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            if (savingFinishedProgressBar_ == null) {
               return;
            }
            savingFinishedProgressBar_.close();
            savingFinishedProgressBar_ = null;
            fileSets_ = null;
         }
      });

      finished_ = true;
   }

   /**
    * Disposes of the tagged images in the imagestorage
    */
   public void close() {
      for (MultipageTiffReader r : new HashSet<MultipageTiffReader>(tiffReadersByLabel_.values())) {
         try {
            r.close();
         } catch (IOException ex) {
            Log.log(ex);
         }
      }
      tiffReadersByLabel_ = null;
   }

   public boolean isFinished() {
      return !newDataSet_;
   }

   public void setSummaryMetadata(JSONObject md) {
      setSummaryMetadata(md, false);
   }

   private void setSummaryMetadata(JSONObject md, boolean showProgress) {
      summaryMetadata_ = md;
      summaryMetadataString_ = null;
      if (summaryMetadata_ != null) {
         summaryMetadataString_ = md.toString();
         HashMap<String, MultipageTiffReader> oldImageMap = tiffReadersByLabel_;
         tiffReadersByLabel_ = new HashMap<String, MultipageTiffReader>();
         if (showProgress && showProgressBars_) {
            ProgressBar progressBar = new ProgressBar("Building image location map", 0, oldImageMap.keySet().size());
            progressBar.setProgress(0);
            progressBar.setVisible(true);
            int i = 1;
            for (String label : oldImageMap.keySet()) {
               tiffReadersByLabel_.put(label, oldImageMap.get(label));
               progressBar.setProgress(i);
               i++;
            }
            progressBar.setVisible(false);
         } else {
            tiffReadersByLabel_.putAll(oldImageMap);
         }

      }
   }

   public String getSummaryMetadataString() {
      return summaryMetadataString_;
   }

   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   public String getDiskLocation() {
      return directory_;
   }

   public long getDataSetSize() {
      File dir = new File(directory_);
      LinkedList<File> list = new LinkedList<File>();
      for (File f : dir.listFiles()) {
         if (f.isDirectory()) {
            for (File fi : f.listFiles()) {
               list.add(f);
            }
         } else {
            list.add(f);
         }
      }
      long size = 0;
      for (File f : list) {
         size += f.length();
      }
      return size;
   }

   public boolean hasExpectedImageOrder() {
      return false;
//      return expectedImageOrder_;
   }

   public JSONObject getDisplaySettings() {
      return masterMultiResStorage_.getDisplaySettings();
   }

   //Class encapsulating a single File (or series of files)
   //Default is one file series per xy posititon
   private class FileSet {

      private LinkedList<MultipageTiffWriter> tiffWriters_;
      private FileWriter mdWriter_;
      private String baseFilename_;
      private String currentTiffFilename_;
      private String currentTiffUUID_;
      ;
      private String metadataFileFullPath_;
      private boolean finished_ = false;
      private TaggedImageStorageMultipageTiff mpTiff_;
      int nextExpectedChannel_ = 0, nextExpectedSlice_ = 0, nextExpectedFrame_ = 0;
      int currentFrame_ = 0;

      public FileSet(JSONObject firstImageTags, TaggedImageStorageMultipageTiff mpt) throws IOException {
         tiffWriters_ = new LinkedList<MultipageTiffWriter>();
         mpTiff_ = mpt;

         //get file path and name
         baseFilename_ = createBaseFilename(firstImageTags);
         currentTiffFilename_ = baseFilename_ + ".tif";
         currentTiffUUID_ = "urn:uuid:" + UUID.randomUUID().toString();
         //make first writer
         tiffWriters_.add(new MultipageTiffWriter(directory_, currentTiffFilename_, summaryMetadata_, mpt, splitByXYPosition_, writingExecutor_, true));

         try {
            if (separateMetadataFile_) {
               startMetadataFile();
            }
         } catch (JSONException ex) {
            Log.log("Problem with summary metadata");
         }
      }

      public String getCurrentUUID() {
         return currentTiffUUID_;
      }

      public String getCurrentFilename() {
         return currentTiffFilename_;
      }

      public void finished() throws IOException, ExecutionException, InterruptedException {
         if (finished_) {
            return;
         }

         if (separateMetadataFile_) {
            try {
               finishMetadataFile();
            } catch (JSONException ex) {
               Log.log("Problem finishing metadata.txt", true);
            }
         }

         //only need to finish last one here because previous ones in set are finished as they fill up with images
         tiffWriters_.getLast().finishedWriting();
         //close all
         for (MultipageTiffWriter w : tiffWriters_) {
            w.close();
         }
         tiffWriters_ = null;
         finished_ = true;
      }

      public MultipageTiffReader getCurrentReader() {
         return tiffWriters_.getLast().getReader();
      }

      public List<Future> overwritePixels(Object pixels, int channel, int slice, int frame, int position) throws IOException {
         ArrayList<Future> list = new ArrayList<Future>();
         for (MultipageTiffWriter w : tiffWriters_) {
            if (w.getIndexMap().containsKey(MD.generateLabel(channel, slice, frame, position))) {
               list.add(w.overwritePixels(pixels, channel, slice, frame, position));
            }
         }
         return list;
      }

      public int getCurrentFrame() {
         return currentFrame_;
      }

      public Future writeImage(TaggedImage img) throws IOException {
         //check if current writer is out of space, if so, make a new one
         if (!tiffWriters_.getLast().hasSpaceToWrite(img)) {
            try {
               //write index map here but still need to call close() at end of acq
               tiffWriters_.getLast().finishedWriting();
            } catch (Exception ex) {
               throw new RuntimeException(ex);
            }

            currentTiffFilename_ = baseFilename_ + "_" + tiffWriters_.size() + ".tif";
            currentTiffUUID_ = "urn:uuid:" + UUID.randomUUID().toString();
            tiffWriters_.add(new MultipageTiffWriter(directory_, currentTiffFilename_, summaryMetadata_, mpTiff_, splitByXYPosition_, writingExecutor_, false));
         }

         //Add filename to image tags
         try {
            img.tags.put("FileName", currentTiffFilename_);
         } catch (JSONException ex) {
            Log.log("Error adding filename to metadata", true);
         }

         //write image
         Future f = tiffWriters_.getLast().writeImage(img);

         int frame = MD.getFrameIndex(img.tags);
         int pos = MD.getPositionIndex(img.tags);
         lastAcquiredPosition_ = Math.max(pos, lastAcquiredPosition_);

         try {
            if (separateMetadataFile_) {
               writeToMetadataFile(img.tags);
            }
         } catch (JSONException ex) {
            Log.log("Problem with image metadata", true);
         }
         return f;
      }

      private void writeToMetadataFile(JSONObject md) throws JSONException {
         try {
            mdWriter_.write(",\n\"FrameKey-" + MD.getFrameIndex(md)
                    + "-" + MD.getChannelIndex(md) + "-" + MD.getSliceIndex(md) + "\": ");
            mdWriter_.write(md.toString(2));
         } catch (IOException ex) {
            Log.log("Problem writing to metadata.txt file", true);
         }
      }

      private void startMetadataFile() throws JSONException {
         metadataFileFullPath_ = directory_ + "/" + baseFilename_ + "_metadata.txt";
         try {
            mdWriter_ = new FileWriter(metadataFileFullPath_);
            mdWriter_.write("{" + "\n");
            mdWriter_.write("\"Summary\": ");
            mdWriter_.write(summaryMetadata_.toString(2));
         } catch (IOException ex) {
            Log.log("Problem creating metadata.txt file", true);
         }
      }

      private void finishMetadataFile() throws JSONException {
         try {
            mdWriter_.write("\n}\n");
            mdWriter_.close();
         } catch (IOException ex) {
            Log.log("Problem creating metadata.txt file", true);
         }
      }

      private String createBaseFilename(JSONObject firstImageTags) {
         String baseFilename;
         try {
            String prefix = summaryMetadata_.getString("Prefix");
            if (prefix.length() == 0) {
               baseFilename = "MagellanStack";
            } else {
               baseFilename = prefix + "_MagellanStack";
            }
         } catch (JSONException ex) {
            Log.log("Can't find Prefix in summary metadata", true);
            baseFilename = "MagellanStack";
         }

         if (splitByXYPosition_) {
            baseFilename += "_" + MD.getPositionName(firstImageTags);
         }
         return baseFilename;
      }
   }

   class ImageLabelComparator implements Comparator<String> {

      private final boolean slicesFirst_;
      private final boolean timeFirst_;

      public ImageLabelComparator() {
         this(false, false);
      }

      public ImageLabelComparator(boolean slicesFirst, boolean timeFirst) {
         super();
         slicesFirst_ = slicesFirst;
         timeFirst_ = timeFirst;
      }

      public boolean getSlicesFirst() {
         return slicesFirst_;
      }

      public boolean getTimeFirst() {
         return timeFirst_;
      }

      @Override
      public int compare(String s1, String s2) {
         //c_s_f_p
         String[] indices1 = s1.split("_");
         String[] indices2 = s2.split("_");
         if (timeFirst_) {
            int position1 = Integer.parseInt(indices1[3]), position2 = Integer.parseInt(indices2[3]);
            if (position1 != position2) {
               return position1 - position2;
            }
            int frame1 = Integer.parseInt(indices1[2]), frame2 = Integer.parseInt(indices2[2]);
            if (frame1 != frame2) {
               return frame1 - frame2;
            }
         } else {
            int frame1 = Integer.parseInt(indices1[2]), frame2 = Integer.parseInt(indices2[2]);
            if (frame1 != frame2) {
               return frame1 - frame2;
            }
            int position1 = Integer.parseInt(indices1[3]), position2 = Integer.parseInt(indices2[3]);
            if (position1 != position2) {
               return position1 - position2;
            }
         }
         if (slicesFirst_) {
            int channel1 = Integer.parseInt(indices1[0]), channel2 = Integer.parseInt(indices2[0]);
            if (channel1 != channel2) {
               return channel1 - channel2;
            }
            return Integer.parseInt(indices1[1]) - Integer.parseInt(indices2[1]);
         } else {
            int slice1 = Integer.parseInt(indices1[1]), slice2 = Integer.parseInt(indices2[1]);
            if (slice1 != slice2) {
               return slice1 - slice2;
            }
            return Integer.parseInt(indices1[0]) - Integer.parseInt(indices2[0]);
         }
      }
   }
}
