///////////////////////////////////////////////////////////////////////////////
//FILE:          TaggedImageStorageMultipageTiff.java
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
package org.micromanager.acquisition;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.imagedisplay.DisplaySettings;
import org.micromanager.utils.ImageLabelComparator;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ProgressBar;
import org.micromanager.utils.ReportingUtils;


public final class TaggedImageStorageMultipageTiff implements TaggedImageStorage {
   
   private static final int SPACE_FOR_PARTIAL_OME_MD = 2000; //this should be more than enough
   
   private JSONObject summaryMetadata_;
   private String summaryMetadataString_ = null;
   private JSONObject displayAndComments_;
   private boolean newDataSet_;
   private int lastFrameOpenedDataSet_ = -1;
   private String directory_;
   final public boolean omeTiff_;
   final private boolean separateMetadataFile_;
   private boolean splitByXYPosition_ = true;
   private volatile boolean finished_ = false;
   private boolean expectedImageOrder_ = true;
   private int numChannels_, numSlices_;
   private OMEMetadata omeMetadata_;
   private int lastFrame_ = 0;
   private boolean fixIndexMap_ = false;
   private final boolean fastStorageMode_;
   private int lastAcquiredPosition_ = 0;
   private ThreadPoolExecutor writingExecutor_;

   // Images currently being written (need to keep around so that they can be
   // returned upon request via getImage()). The data structure must be
   // synchronized because the write completion is detected on a background
   // thread.
   private ConcurrentHashMap<String, TaggedImage> writePendingImages_ =
      new ConcurrentHashMap<String, TaggedImage>();

   //map of position indices to objects associated with each
   private HashMap<Integer, FileSet> fileSets_;
   
   //Map of image labels to file 
   private TreeMap<String, MultipageTiffReader> tiffReadersByLabel_;
  
   public TaggedImageStorageMultipageTiff(String dir, Boolean newDataSet, JSONObject summaryMetadata) throws IOException {            
      this(dir, newDataSet, summaryMetadata, MMStudio.getInstance().getMetadataFileWithMultipageTiff(),
              MMStudio.getInstance().getSeparateFilesForPositionsMPTiff(),
              true);
   }
   
   /*
    * Constructor that doesn't make reference to MMStudio so it can be used independently of MM GUI
    */
   public TaggedImageStorageMultipageTiff(String dir, boolean newDataSet, JSONObject summaryMetadata, 
         boolean separateMDFile, boolean separateFilesForPositions, boolean fastStorageMode) throws IOException {
      fastStorageMode_ = fastStorageMode;
      omeTiff_ = true;
      separateMetadataFile_ = separateMDFile;
      splitByXYPosition_ = separateFilesForPositions;

      newDataSet_ = newDataSet;
      directory_ = dir;
      tiffReadersByLabel_ = new TreeMap<String, MultipageTiffReader>(new ImageLabelComparator());
      setSummaryMetadata(summaryMetadata);

      // TODO: throw error if no existing dataset
      if (!newDataSet_) {       
         openExistingDataSet();
      }    
      
   }
   
   private void processSummaryMD() {
      try {
         displayAndComments_ = DisplaySettings.getDisplaySettingsFromSummary(summaryMetadata_);    
      } catch (Exception ex) {
         ReportingUtils.logError(ex, "Problems setting displaySettings from Summery");
      }
      if (newDataSet_) {
         try {
            numChannels_ = MDUtils.getNumChannels(summaryMetadata_);
            numSlices_ = MDUtils.getNumSlices(summaryMetadata_);
         } catch (Exception ex) {
            ReportingUtils.logError("Error estimating total number of image planes");
         }
      }
   }
   
   public ThreadPoolExecutor getWritingExecutor() {
      return writingExecutor_;
   }
   
   boolean slicesFirst() {
      return ((ImageLabelComparator) tiffReadersByLabel_.comparator()).getSlicesFirst();
   }
   
   boolean timeFirst() {
      return ((ImageLabelComparator) tiffReadersByLabel_.comparator()).getTimeFirst();
   }
   
   public boolean getFixIndexMap() {
      return fixIndexMap_;
   }
   
   public void setFixIndexMap() {
      fixIndexMap_ = true;
   }

   private void openExistingDataSet() {
      //Need to throw error if file not found
      MultipageTiffReader reader = null;
      File dir = new File(directory_);

      ProgressBar progressBar = new ProgressBar("Reading " + directory_, 0, dir.listFiles().length);
      int numRead = 0;
      progressBar.setProgress(numRead);
      progressBar.setVisible(true);
      for (File f : dir.listFiles()) {
         if (f.getName().endsWith(".tif") || f.getName().endsWith(".TIF")) {
            reader = loadFile(f);
         }
         numRead++;
         progressBar.setProgress(numRead);
      }
      progressBar.setVisible(false);

      if (reader != null) {
         setSummaryMetadata(reader.getSummaryMetadata(), true);
         displayAndComments_ = reader.getDisplayAndComments();
      }

      progressBar.setProgress(1);
      progressBar.setVisible(false);
   }

   private MultipageTiffReader loadFile(File f) {
      MultipageTiffReader reader = null;
      try {
         try {
            reader = new MultipageTiffReader(f);
         }
         catch (InvalidIndexMapException e) {
            // Prompt to repair it.
            int choice = JOptionPane.showConfirmDialog(null,
                  "This file cannot be opened bcause it appears to have \n" +
                  "been improperly saved. Would you like Micro-Manger to attempt " +
                  "to fix it?",
                  "Micro-Manager", JOptionPane.YES_NO_OPTION);
            if (choice != JOptionPane.YES_OPTION) {
               return null;
            }
            // Attempt to repair it. This constructor automatically invokes
            // the fixIndexMap method (and is the only constructor that
            // opens files in read/write mode).
            reader = new MultipageTiffReader(f, true);
            reader.close();
            // Open the file normally.
            reader = new MultipageTiffReader(f);
         }
         Set<String> labels = reader.getIndexKeys();
         for (String label : labels) {
            tiffReadersByLabel_.put(label, reader);
            int frameIndex = Integer.parseInt(label.split("_")[2]);
            lastFrameOpenedDataSet_ = Math.max(frameIndex, lastFrameOpenedDataSet_);
         }
      } catch (IOException ex) {
         ReportingUtils.showError("Couldn't open file: " + f.toString());
      }
      return reader;
   }

   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);

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

   @Override
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
    * Not used by the TaggedImageStorage API, but can be useful for special applicaitons
    * of this class (e.g. Navigator plugin)
    */
   public void overwritePixels(Object pix, int channel, int slice, int frame, int position) throws IOException {
      //asumes only one position
      fileSets_.get(position).overwritePixels(pix, channel, slice, frame, position); 
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException, IOException {
      final String label = MDUtils.getLabel(taggedImage.tags);
      startWritingTask(label, taggedImage);

      // Now, we must hold on to taggedImage, so that we can return it if
      // somebody calls getImage() before the writing is finished.
      // There is a data race if the taggedImage is modified by other code, but
      // that would be a bad thing to do anyway (will break the writer) and is
      // considered forbidden.

      // We are here depending on the fact that writingExecutor_ is a
      // single-thread ThreadPoolExecutor, and that submitted tasks are
      // executed in order. A better implementation might use Guava's
      // ListenableFuture.
      // Also note that the image will be dropped if the writing fails due to
      // any error. This is acceptable for disk-backed storage.
      writePendingImages_.put(label, taggedImage);
      writingExecutor_.submit(new Runnable() {
         @Override public void run() {
            writePendingImages_.remove(label);
         }
      });
   }

   /*
    * Sets up and kicks off the writing of a new image. This, in an indirect
    * way, ends up submitting the writing task to writingExecutor_.
    */
   private void startWritingTask(String label, TaggedImage taggedImage)
      throws MMException, IOException
   {
      if (!newDataSet_) {
         ReportingUtils.showError("Tried to write image to a finished data set");
         throw new MMException("This ImageFileManager is read-only.");
      }
      //initialize writing executor
      if (fastStorageMode_ && writingExecutor_ == null) {
         // Note: Code elsewhere assumes that the writing task is performed on
         // a _single_ background thread.
         writingExecutor_ = new ThreadPoolExecutor(1, 1, 0, TimeUnit.NANOSECONDS,
                 new LinkedBlockingQueue<java.lang.Runnable>());
      }
      int fileSetIndex = 0;
      if (splitByXYPosition_) {
         try {
            fileSetIndex = MDUtils.getPositionIndex(taggedImage.tags);
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
      }
      if (fileSets_ == null) {
         try {
            fileSets_ = new HashMap<Integer, FileSet>();
            JavaUtils.createDirectory(directory_);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
          
      if (omeTiff_) {
         if (omeMetadata_ == null) {
            omeMetadata_ = new OMEMetadata(this);
         }
      }
      
      if (fileSets_.get(fileSetIndex) == null) {
         fileSets_.put(fileSetIndex, new FileSet(taggedImage.tags, this));
      }
      FileSet set = fileSets_.get(fileSetIndex);
      try {
         set.writeImage(taggedImage);
         tiffReadersByLabel_.put(label, set.getCurrentReader());
      } catch (IOException ex) {
        ReportingUtils.showError("problem writing image to file");
      }

         
      int frame;
      try {
         frame = MDUtils.getFrameIndex(taggedImage.tags);
      } catch (JSONException ex) {
         frame = 0;
      }
      lastFrameOpenedDataSet_ = Math.max(frame, lastFrameOpenedDataSet_);
   }

   @Override
   public Set<String> imageKeys() {
      return tiffReadersByLabel_.keySet();
   }

   /**
    * Call this function when no more images are expected
    * Finishes writing the metadata file and closes it.
    * After calling this function, the imagestorage is read-only
    */
   @Override
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
      ProgressBar progressBar = new ProgressBar("Finishing Files", 0, fileSets_.size());
      try {
         int count = 0;
         progressBar.setProgress(count);
         progressBar.setVisible(true);
         for (FileSet p : fileSets_.values()) {
            p.finishAbortedAcqIfNeeded();
         }
     
         try {
            //fill in missing tiffdata tags for OME meteadata--needed for acquisitions in which 
            //z and t arent the same for every channel
            for (int p = 0; p <= lastAcquiredPosition_; p++) {
               //set sizeT in case of aborted acq
               int currentFrame =  fileSets_.get(splitByXYPosition_ ? p : 0).getCurrentFrame();
               omeMetadata_.setNumFrames(p, currentFrame + 1);
               omeMetadata_.fillInMissingTiffDatas(lastAcquiredFrame(), p);
            }
         } catch (Exception e) {
            //don't want errors in this code to trip up correct file finishing
            ReportingUtils.logError("Couldn't fill in missing frames in OME");
         }

         //figure out where the full string of OME metadata can be stored 
         String fullOMEXMLMetadata = omeMetadata_.toString();
         int length = fullOMEXMLMetadata.length();
         String uuid = null, filename = null;
         FileSet master = null;
         for (FileSet p : fileSets_.values()) {
            if (p.hasSpaceForFullOMEXML(length)) {
               uuid = p.getCurrentUUID();
               filename = p.getCurrentFilename();
               p.finished(fullOMEXMLMetadata);
               master = p;
               count++;
               progressBar.setProgress(count);
               break;
            }
         }
         
         if (uuid == null) {
            //in the rare case that no files have extra space to fit the full block of OME XML,
            //generate a file specifically for holding it that all other files can point to
            //simplest way to do this is to make a .ome text file 
             filename = "OMEXMLMetadata.ome";
             uuid = "urn:uuid:" + UUID.randomUUID().toString();
             PrintWriter pw = new PrintWriter(directory_ + File.separator + filename);
             pw.print(fullOMEXMLMetadata);
             pw.close();
         }
         
         String partialOME = OMEMetadata.getOMEStringPointerToMasterFile(filename, uuid);

         for (FileSet p : fileSets_.values()) {
            if (p == master) {
               continue;
            }
            p.finished(partialOME);
            count++;
            progressBar.setProgress(count);
         }            
         //shut down writing executor--pause here until all tasks have finished writing
         //so that no attempt is made to close the dataset (and thus the FileChannel)
         //before everything has finished writing
         //mkae sure all images have finished writing if they are on seperate thread 
         if (writingExecutor_ != null && !writingExecutor_.isShutdown()) {
            writingExecutor_.shutdown();
            try {
               //now that shutdown has been called, need to wait for tasks to finish
               while (!writingExecutor_.awaitTermination(4, TimeUnit.SECONDS)) {
                  ReportingUtils.logMessage("Waiting for image stack file finishing to complete");
               }
            } catch (InterruptedException e) {
               ReportingUtils.logError("File finishing thread interrupted");
               Thread.interrupted();
            }
         }
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
      finally {
         progressBar.setVisible(false);
      }
      finished_ = true;
   }

   /**
    * Disposes of the tagged images in the imagestorage
    */
   @Override
   public void close() {
      for (MultipageTiffReader r : new HashSet<MultipageTiffReader>(tiffReadersByLabel_.values())) {
         try {
            r.close();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   @Override
   public boolean isFinished() {
      return !newDataSet_;
   }

   @Override
   public void setSummaryMetadata(JSONObject md) {
      setSummaryMetadata(md, false);
   }
   
   private void setSummaryMetadata(JSONObject md, boolean showProgress) {
      summaryMetadata_ = md;
      summaryMetadataString_ = null;
      if (summaryMetadata_ != null) {
         summaryMetadataString_ = md.toString();
         boolean slicesFirst = summaryMetadata_.optBoolean("SlicesFirst", true);
         boolean timeFirst = summaryMetadata_.optBoolean("TimeFirst", false);
         TreeMap<String, MultipageTiffReader> oldImageMap = tiffReadersByLabel_;
         tiffReadersByLabel_ = new TreeMap<String, MultipageTiffReader>(new ImageLabelComparator(slicesFirst, timeFirst));
         if (showProgress) {
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
         if (summaryMetadata_ != null && summaryMetadata_.length() > 0) {
            processSummaryMD();
         }
      }
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   public String getSummaryMetadataString() {
      return summaryMetadataString_;
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return displayAndComments_;
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      displayAndComments_ = settings;
   }
          
   @Override   
   public void writeDisplaySettings() {
      for (MultipageTiffReader r : new HashSet<MultipageTiffReader>(tiffReadersByLabel_.values())) {
         try {
            r.rewriteDisplaySettings(displayAndComments_.getJSONArray("Channels"));
            r.rewriteComments(displayAndComments_.getJSONObject("Comments"));
         } catch (JSONException ex) {
            ReportingUtils.logError("Error writing display settings");
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   @Override
   public String getDiskLocation() {
      return directory_;
   }

   @Override
   public int lastAcquiredFrame() {
      if (newDataSet_) {
         return lastFrame_;
      } else {
         return lastFrameOpenedDataSet_;
      }
   }

   @Override
   public long getDataSetSize() {
      File dir = new File (directory_);
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

   //Class encapsulating a single File (or series of files)
   //Default is one file series per xy posititon
   private class FileSet {
      private LinkedList<MultipageTiffWriter> tiffWriters_;
      private FileWriter mdWriter_;
      private String baseFilename_;
      private String currentTiffFilename_;
      private String currentTiffUUID_;;
      private String metadataFileFullPath_;
      private boolean finished_ = false;
      private int ifdCount_ = 0;
      private TaggedImageStorageMultipageTiff mpTiff_;
      int nextExpectedChannel_ = 0, nextExpectedSlice_ = 0, nextExpectedFrame_ = 0;
      int currentFrame_ = 0;

      
      public FileSet(JSONObject firstImageTags, TaggedImageStorageMultipageTiff mpt) throws IOException {
         tiffWriters_ = new LinkedList<MultipageTiffWriter>();  
         mpTiff_ = mpt;
         
         //get file path and name
         baseFilename_ = createBaseFilename(firstImageTags);
         currentTiffFilename_ = baseFilename_ + (omeTiff_ ? ".ome.tif" : ".tif");
         currentTiffUUID_ = "urn:uuid:" + UUID.randomUUID().toString();
         //make first writer
         tiffWriters_.add(new MultipageTiffWriter(directory_, currentTiffFilename_, summaryMetadata_, mpt,
                 fastStorageMode_, splitByXYPosition_));
   
         try {
            if (separateMetadataFile_) {
               startMetadataFile();
            }
         } catch (JSONException ex) {
            ReportingUtils.showError("Problem with summary metadata");
         }
      }

      public String getCurrentUUID() {
         return currentTiffUUID_;
      }
      
      public String getCurrentFilename() {
         return currentTiffFilename_;
      }
      
      public boolean hasSpaceForFullOMEXML(int mdLength) {
         return tiffWriters_.getLast().hasSpaceForFullOMEMetadata(mdLength);
      }
      
      public void finished(String omeXML) throws IOException {
         if (finished_) {
            return;
         }
         
         if (separateMetadataFile_) {
            try {
               finishMetadataFile();
            } catch (JSONException ex) {
               ReportingUtils.logError("Problem finishing metadata.txt");
            }
         }
         
         //only need to finish last one here because previous ones in set are finished as they fill up with images
         tiffWriters_.getLast().finish();
         //close all
         for (MultipageTiffWriter w : tiffWriters_) {
            w.close(omeXML);
         }
         finished_ = true;
      }

      public MultipageTiffReader getCurrentReader() {
         return tiffWriters_.getLast().getReader();
      }
      
      public void overwritePixels(Object pixels, int channel, int slice, int frame, int position) throws IOException {
         for (MultipageTiffWriter w : tiffWriters_) {
            if (w.getIndexMap().containsKey(MDUtils.generateLabel(channel, slice, frame, position))) {
               w.overwritePixels(pixels, channel, slice, frame, position);
            }
         }
      }
      
      public int getCurrentFrame() {
         return currentFrame_;
      }
      
      public void writeImage(TaggedImage img) throws IOException {
         //check if current writer is out of space, if so, make a new one
         if (!tiffWriters_.getLast().hasSpaceToWrite(img, omeTiff_ ?  SPACE_FOR_PARTIAL_OME_MD : 0  )) {
            //write index map here but still need to call close() at end of acq
            tiffWriters_.getLast().finish();          
            
            currentTiffFilename_ = baseFilename_ + "_" + tiffWriters_.size() + (omeTiff_ ? ".ome.tif" : ".tif");
            currentTiffUUID_ = "urn:uuid:" + UUID.randomUUID().toString();
            ifdCount_ = 0;
            tiffWriters_.add(new MultipageTiffWriter(directory_ ,currentTiffFilename_, summaryMetadata_, mpTiff_,
                    fastStorageMode_, splitByXYPosition_));
         }      

         //Add filename to image tags
         try {
            img.tags.put("FileName", currentTiffFilename_);
         } catch (JSONException ex) {
            ReportingUtils.logError("Error adding filename to metadata");
         }

         //write image
         tiffWriters_.getLast().writeImage(img);  
                         
         if (expectedImageOrder_) {
            if (splitByXYPosition_) {
               checkForExpectedImageOrder(img.tags);
            } else {
               expectedImageOrder_ = false;
            }
         }

         //write metadata
         if (omeTiff_) {
            try {
               //Check if missing planes need to be added OME metadata
               int frame = MDUtils.getFrameIndex(img.tags);
               int position;
               try {
                  position = MDUtils.getPositionIndex(img.tags);
               } catch (Exception e) {
                  position = 0;
               }
               if (frame > currentFrame_) {
                  //check previous frame for missing IFD's in OME metadata
                  omeMetadata_.fillInMissingTiffDatas(currentFrame_, position);
               }
               //reset in case acquisitin order is position then time and all files not split by position
               currentFrame_ = frame;
               
               omeMetadata_.addImageTagsToOME(img.tags, ifdCount_, baseFilename_, currentTiffFilename_, currentTiffUUID_);
            } catch (Exception ex) {
               ReportingUtils.logError("Problem writing OME metadata");
            }
         }
         
         try {
            int frame = MDUtils.getFrameIndex(img.tags);
            lastFrame_ = Math.max(frame, lastFrame_);
         } catch (JSONException ex) {
            ReportingUtils.showError("Couldn't find frame index in image tags");
         }   
         try {
            int pos = MDUtils.getPositionIndex(img.tags);
            lastAcquiredPosition_ = Math.max(pos, lastAcquiredPosition_);
         } catch (JSONException ex) {
            ReportingUtils.showError("Couldn't find position index in image tags");
         }  
         
         
         try {
            if (separateMetadataFile_) {
               writeToMetadataFile(img.tags);
            }
         } catch (JSONException ex) {
            ReportingUtils.logError("Problem with image metadata");
         }
         ifdCount_++;
      }

      private void writeToMetadataFile(JSONObject md) throws JSONException {
         try {
            mdWriter_.write(",\n\"FrameKey-" + MDUtils.getFrameIndex(md)
                    + "-" + MDUtils.getChannelIndex(md) + "-" + MDUtils.getSliceIndex(md) + "\": ");
            mdWriter_.write(md.toString(2));
         } catch (IOException ex) {
            ReportingUtils.logError("Problem writing to metadata.txt file");
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
               ReportingUtils.logError("Problem creating metadata.txt file");
            }
      }

      private void finishMetadataFile() throws JSONException {
         try {
            mdWriter_.write("\n}\n");
            mdWriter_.close();
         } catch (IOException ex) {
            ReportingUtils.logError("Problem creating metadata.txt file");
         }
      }

      private String createBaseFilename(JSONObject firstImageTags) {
         String baseFilename;
         try {
            String prefix = summaryMetadata_.getString("Prefix");
            if (prefix.length() == 0) {
               baseFilename = "MMStack";
            } else {
               baseFilename = prefix + "_MMStack";
            }
         } catch (JSONException ex) {
            ReportingUtils.logError("Can't find Prefix in summary metadata");
            baseFilename = "MMStack";
         }

         if (splitByXYPosition_) {
            try {
               if (MDUtils.hasPositionName(firstImageTags)) {
                  baseFilename += "_" + MDUtils.getPositionName(firstImageTags);
               }
               else {
                  baseFilename += "_" + "Pos" + MDUtils.getPositionIndex(firstImageTags);
               }
            } catch (JSONException ex) {
               ReportingUtils.showError("No position name or index in metadata");
            }
         }
         return baseFilename;
      }

      /**
       * Generate all expected labels for the last frame, and write dummy images for ones 
       * that weren't written. Modify ImageJ and OME max number of frames as appropriate.
       * This method only works if xy positions are split across separate files
       */
      private void finishAbortedAcqIfNeeded() {
         if (expectedImageOrder_ && splitByXYPosition_ && !timeFirst()) {
            try {
               //One position may be on the next frame compared to others. Complete each position
               //with blank images to fill this frame
               completeFrameWithBlankImages(lastAcquiredFrame());
            } catch (Exception e) {
               ReportingUtils.logError("Problem finishing aborted acq with blank images");
            }
         }
      }

      /*
       * Completes the current time point of an aborted acquisition with blank images, 
       * so that it can be opened correctly by ImageJ/BioForamts
       */
      private void completeFrameWithBlankImages(int frame) throws JSONException, MMScriptException {
         
         int numFrames = MDUtils.getNumFrames(summaryMetadata_);
         int numSlices = MDUtils.getNumSlices(summaryMetadata_);
         int numChannels = MDUtils.getNumChannels(summaryMetadata_);
         if (numFrames > frame + 1 ) {
            TreeSet<String> writtenImages = new TreeSet<String>();
            for (MultipageTiffWriter w : tiffWriters_) {
               writtenImages.addAll(w.getIndexMap().keySet());
               w.setAbortedNumFrames(frame + 1);
            }
            int positionIndex = MDUtils.getIndices(writtenImages.first())[3];
            if (omeTiff_) {
               omeMetadata_.setNumFrames(positionIndex, frame + 1);
            }
            TreeSet<String> lastFrameLabels = new TreeSet<String>();
            for (int c = 0; c < numChannels; c++) {
               for (int z = 0; z < numSlices; z++) {
                  lastFrameLabels.add(MDUtils.generateLabel(c, z, frame, positionIndex));
               }
            }
            lastFrameLabels.removeAll(writtenImages);
            try {
               for (String label : lastFrameLabels) {
                  tiffWriters_.getLast().writeBlankImage(label);
                  if (omeTiff_) {
                     JSONObject dummyTags = new JSONObject();
                     int channel = Integer.parseInt(label.split("_")[0]);
                     int slice = Integer.parseInt(label.split("_")[1]);
                     MDUtils.setChannelIndex(dummyTags, channel);
                     MDUtils.setFrameIndex(dummyTags, frame);
                     MDUtils.setSliceIndex(dummyTags, slice);
                     omeMetadata_.addImageTagsToOME(dummyTags, ifdCount_, baseFilename_, currentTiffFilename_, currentTiffUUID_);
                  }
               }
            } catch (IOException ex) {
               ReportingUtils.logError("problem writing dummy image");
            }
         }
      }
      
      void checkForExpectedImageOrder(JSONObject tags) {
         try {
            //Determine next expected indices
            int channel = MDUtils.getChannelIndex(tags), frame = MDUtils.getFrameIndex(tags),
                    slice = MDUtils.getSliceIndex(tags);
            if (slice != nextExpectedSlice_ || channel != nextExpectedChannel_ ||
                    frame != nextExpectedFrame_) {
               expectedImageOrder_ = false;
            }
            //Figure out next expected indices
            if (slicesFirst()) {
               nextExpectedSlice_ = slice + 1;
               if (nextExpectedSlice_ == numSlices_) {
                  nextExpectedSlice_ = 0;
                  nextExpectedChannel_ = channel + 1;
                  if (nextExpectedChannel_ == numChannels_) {
                     nextExpectedChannel_ = 0;
                     nextExpectedFrame_ = frame + 1;
                  }
               }
            } else {
               nextExpectedChannel_ = channel + 1;
               if (nextExpectedChannel_ == numChannels_) {
                  nextExpectedChannel_ = 0;
                  nextExpectedSlice_ = slice + 1;
                  if (nextExpectedSlice_ == numSlices_) {
                     nextExpectedSlice_ = 0;
                     nextExpectedFrame_ = frame + 1;
                  }
               }
            }
         } catch (JSONException ex) {
            ReportingUtils.logError("Couldnt find channel, slice, or frame index in Image tags");
            expectedImageOrder_ = false;
         }
      }
 
   }    
}
