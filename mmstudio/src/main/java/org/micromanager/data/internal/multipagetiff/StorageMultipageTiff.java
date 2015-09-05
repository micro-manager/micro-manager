///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Multipage TIFF
//-----------------------------------------------------------------------------
//
// COPYRIGHT:    University of California, San Francisco, 2012-2015
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
package org.micromanager.data.internal.multipagetiff;

import com.google.common.eventbus.Subscribe;

import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import mmcorej.TaggedImage;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenEvent;
import org.micromanager.data.Image;
import org.micromanager.data.NewImageEvent;
import org.micromanager.data.NewSummaryMetadataEvent;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.ProgressBar;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This class provides image storage in the form of a single TIFF file that
 * contains all image data in the dataset.
 * Adapted from the TaggedImageStorageMultipageTiff module.
 * TODO: extricate DisplaySettings from this code.
 */
public final class StorageMultipageTiff implements Storage {
   private static final String SHOULD_GENERATE_METADATA_FILE = "generate a metadata file when saving datasets as multipage TIFF files";
   private static final String SHOULD_USE_SEPARATE_FILES_FOR_POSITIONS = "generate a separate multipage TIFF file for each stage position";
   private static final HashSet<String> ALLOWED_AXES = new HashSet<String>(
         Arrays.asList(Coords.CHANNEL, Coords.TIME, Coords.Z,
            Coords.STAGE_POSITION));

   private DefaultDatastore store_;
   private DefaultSummaryMetadata summaryMetadata_ = (new DefaultSummaryMetadata.Builder()).build();
   private String summaryMetadataString_ = null;
   private boolean amInWriteMode_;
   private int lastFrameOpenedDataSet_ = -1;
   private String directory_;
   final private boolean separateMetadataFile_;
   private boolean splitByXYPosition_ = true;
   private volatile boolean finished_ = false;
   private OMEMetadata omeMetadata_;
   private int lastFrame_ = 0;
   private int lastAcquiredPosition_ = 0;
   private ThreadPoolExecutor writingExecutor_;
   private Image firstImage_;

   // Images that are currently being written. We keep them around until
   // writing completes, so that calls to getImage() mid-write can access
   // complete data rather than risking a call to
   // MultipageTiffReader.readImage().
   private ConcurrentHashMap<Coords, Image> coordsToPendingImage_ =
      new ConcurrentHashMap<Coords, Image>();

   //map of position indices to objects associated with each
   private HashMap<Integer, FileSet> positionToFileSet_;
   
   //Map of image labels to file 
   private TreeMap<Coords, MultipageTiffReader> coordsToReader_;
   // Keeps track of our maximum extent along each axis.
   private Coords maxIndices_;
  
   public StorageMultipageTiff(Datastore store, String dir, Boolean amInWriteMode)
         throws IOException {
      this(store, dir, amInWriteMode, getShouldGenerateMetadataFile(),
            getShouldSplitPositions());
   }
   
   /*
    * Constructor that doesn't make reference to MMStudio so it can be used
    * independently of MM GUI
    */
   public StorageMultipageTiff(Datastore store, String dir,
         boolean amInWriteMode, boolean separateMDFile,
         boolean separateFilesForPositions) throws IOException {
      store_ = (DefaultDatastore) store;
      // We must be notified of changes in the Datastore before everyone else,
      // so that others can read those changes out of the Datastore later.
      store_.registerForEvents(this, 0);
      separateMetadataFile_ = separateMDFile;
      splitByXYPosition_ = separateFilesForPositions;

      amInWriteMode_ = amInWriteMode;
      directory_ = dir;
      store_.setSavePath(directory_);
      coordsToReader_ = new TreeMap<Coords, MultipageTiffReader>();

      // TODO: throw error if no existing dataset
      if (!amInWriteMode_) {       
         openExistingDataSet();
      }
   }

   @Subscribe
   public void onNewSummaryMetadata(NewSummaryMetadataEvent event) {
      try {
         setSummaryMetadata(event.getSummaryMetadata());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error setting new summary metadata");
      }
   }
   
   public ThreadPoolExecutor getWritingExecutor() {
      return writingExecutor_;
   }

   boolean slicesFirst() {
      ReportingUtils.logError("TODO: handle slicesFirst; returning false by default");
      return false;
   }

   boolean timeFirst() {
      ReportingUtils.logError("TODO: handle timeFirst; returning false by default");
      return false;
   }

   private void openExistingDataSet() {
      //Need to throw error if file not found
      MultipageTiffReader reader = null;
      File dir = new File(directory_);

      ProgressBar progressBar = null;
      // Allow operation in headless mode.
      if (!GraphicsEnvironment.isHeadless()) {
         progressBar = new ProgressBar("Reading " + directory_, 0, dir.listFiles().length);
      }
      int numRead = 0;
      if (progressBar != null) {
         progressBar.setProgress(numRead);
         progressBar.setVisible(true);
      }
      for (File f : dir.listFiles()) {
         if (f.getName().endsWith(".tif") || f.getName().endsWith(".TIF")) {
            try {
               //this is where fixing dataset code occurs
               reader = new MultipageTiffReader(f);
               Set<Coords> readerCoords = reader.getIndexKeys();
               for (Coords coords : readerCoords) {
                  coordsToReader_.put(coords, reader);
                  lastFrameOpenedDataSet_ = Math.max(coords.getTime(),
                        lastFrameOpenedDataSet_);
                  if (firstImage_ == null) {
                     firstImage_ = reader.readImage(coords);
                  }
               }
            } catch (IOException ex) {
               ReportingUtils.showError("Couldn't open file: " + f.toString());
            }
         }
         numRead++;
         if (progressBar != null) {
            progressBar.setProgress(numRead);
         }
      }
      if (progressBar != null) {
         progressBar.setVisible(false);
      }
      //reset this static variable to false so the prompt is delivered if a new data set is opened
      MultipageTiffReader.fixIndexMapWithoutPrompt_ = false;

      if (reader != null) {
         // TODO: coercing to DefaultSummaryMetadata here to match method
         // signature; see our setSummaryMetadata methods for more info.
         setSummaryMetadata((DefaultSummaryMetadata) reader.getSummaryMetadata(), true);
      }

      if (progressBar != null) {
         progressBar.setProgress(1);
         progressBar.setVisible(false);
      }
   }

   @Subscribe
   public void onNewImage(NewImageEvent event) {
      DefaultImage image = (DefaultImage) event.getImage();
      // Require images to only have time/channel/z/position axes.
      for (String axis : image.getCoords().getAxes()) {
         if (!ALLOWED_AXES.contains(axis)) {
            ReportingUtils.showError("Multipage TIFF storage cannot handle images with axis \"" + axis + "\". Allowed axes are " + ALLOWED_AXES);
            return;
         }
      }
      if (firstImage_ == null) {
         firstImage_ = image;
      }
      try {
         putImage(image, false);
      }
      catch (Exception e) {
         ReportingUtils.showError(e, "Failed to write image at " + image.getCoords());
      }
   }

   @Subscribe
   public void onDatastoreFrozen(DatastoreFrozenEvent event) {
      try {
         finished();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to finish saving");
      }
   }

   private void putImage(DefaultImage image, boolean waitForWritingToFinish) throws MMException, InterruptedException, ExecutionException, IOException {
      putImage(image);
      if (waitForWritingToFinish) {
         Future f = writingExecutor_.submit(new Runnable() {
            @Override
            public void run() {
            }
         });
         f.get();
      }
   }

   /**
    * This method is a wrapper around startWritingTask, that primarily concerns
    * itself with ensuring that coordsToPendingImage_ is kept up-to-date. That
    * structure, in turn, ensures that we do not have to rely on
    * MultipageTiffReader.readImage() returning a coherent (i.e.
    * finished-writing) image if our getImage() method is called before writing
    * is completed.
    */
   private void putImage(DefaultImage image) throws MMException, IOException {
      if (!amInWriteMode_) {
         ReportingUtils.showError("Tried to write image to a finished data set");
         throw new MMException("This ImageFileManager is read-only.");
      }

      final Coords coords = image.getCoords();
      synchronized(coordsToPendingImage_) {
         coordsToPendingImage_.put(coords, image);
      }

      startWritingTask(image);

      writingExecutor_.submit(new Runnable() {
         @Override
         public void run() {
            synchronized(coordsToPendingImage_) {
               coordsToPendingImage_.remove(coords);
            }
         }
      });
   };

   /**
    * This method handles starting the process of writing images (which means
    * that it ultimately submits a task to writingExecutor_).
    */
   private void startWritingTask(DefaultImage image) throws MMException, IOException {
      // Update maxIndices_
      if (maxIndices_ == null) {
         maxIndices_ = image.getCoords().copy().build();
      }
      else {
         for (String axis : image.getCoords().getAxes()) {
            int pos = image.getCoords().getIndex(axis);
            if (pos > maxIndices_.getIndex(axis)) {
               maxIndices_ = maxIndices_.copy().index(axis, pos).build();
            }
         }
      }
      TaggedImage taggedImage = image.legacyToTaggedImage();

      // initialize writing executor
      if (writingExecutor_ == null) {
         writingExecutor_ = new ThreadPoolExecutor(1, 1, 0,
               TimeUnit.NANOSECONDS,
               new LinkedBlockingQueue<java.lang.Runnable>());
      }
      int fileSetIndex = 0;
      if (splitByXYPosition_) {
         fileSetIndex = image.getCoords().getStagePosition();
         if (fileSetIndex == -1) {
            // No position axis, so just default to 0.
            fileSetIndex = 0;
         }
      }

      if (positionToFileSet_ == null) {
         try {
            positionToFileSet_ = new HashMap<Integer, FileSet>();
            JavaUtils.createDirectory(directory_);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
          
      if (omeMetadata_ == null) {
         omeMetadata_ = new OMEMetadata(this);
      }

      if (!positionToFileSet_.containsKey(fileSetIndex)) {
         positionToFileSet_.put(fileSetIndex,
               new FileSet(taggedImage.tags, this, omeMetadata_,
                  splitByXYPosition_, separateMetadataFile_));
      }
      FileSet set = positionToFileSet_.get(fileSetIndex);

      try {
         set.writeImage(taggedImage);
         DefaultCoords coords = DefaultCoords.legacyFromJSON(taggedImage.tags);
         coordsToReader_.put(coords, set.getCurrentReader());
      } catch (IOException ex) {
        ReportingUtils.showError(ex, "Failed to write image to file.");
      }

         
      int frame;
      try {
         frame = MDUtils.getFrameIndex(taggedImage.tags);
      } catch (JSONException ex) {
         frame = 0;
      }
      lastFrameOpenedDataSet_ = Math.max(frame, lastFrameOpenedDataSet_);
   }

   public Set<Coords> imageKeys() {
      return coordsToReader_.keySet();
   }

   /**
    * Call this function when no more images are expected
    * Finishes writing the metadata file and closes it.
    * After calling this function, the imagestorage is read-only
    */
   public synchronized void finished() {
      if (finished_) {
         return;
      }
      amInWriteMode_ = false;
      if (positionToFileSet_ == null) {
         // Nothing to be done.
         finished_ = true;
         return;
      }
      ProgressBar progressBar = new ProgressBar("Finishing Files", 0, positionToFileSet_.size());
      try {
         int count = 0;
         progressBar.setProgress(count);
         progressBar.setVisible(true);
         for (FileSet p : positionToFileSet_.values()) {
            p.finishAbortedAcqIfNeeded();
         }
     
         try {
            //fill in missing tiffdata tags for OME meteadata--needed for acquisitions in which 
            //z and t arent the same for every channel
            for (int p = 0; p <= lastAcquiredPosition_; p++) {
               //set sizeT in case of aborted acq
               int currentFrame =  positionToFileSet_.get(splitByXYPosition_ ? p : 0).getCurrentFrame();
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
         for (FileSet p : positionToFileSet_.values()) {
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
            //in the rare case that no files have extra space to fit the full
            //block of OME XML, generate a file specifically for holding it
            //that all other files can point to simplest way to do this is to
            //make a .ome text file 
             filename = "OMEXMLMetadata.ome";
             uuid = "urn:uuid:" + UUID.randomUUID().toString();
             PrintWriter pw = new PrintWriter(directory_ + File.separator + filename);
             pw.print(fullOMEXMLMetadata);
             pw.close();
         }
         
         String partialOME = OMEMetadata.getOMEStringPointerToMasterFile(filename, uuid);

         for (FileSet p : positionToFileSet_.values()) {
            if (p == master) {
               continue;
            }
            p.finished(partialOME);
            count++;
            progressBar.setProgress(count);
         }            
         //shut down writing executor--pause here until all tasks have finished
         //writing so that no attempt is made to close the dataset (and thus
         //the FileChannel) before everything has finished writing mkae sure
         //all images have finished writing if they are on seperate thread 
         if (writingExecutor_ != null && !writingExecutor_.isShutdown()) {
            writingExecutor_.shutdown();
            try {
               // Wait for tasks to finish.
               int i = 0;
               while (!writingExecutor_.awaitTermination(4, TimeUnit.SECONDS)) {
                  ReportingUtils.logMessage("Waiting for image stack to finish writing (" + i + ")...");
                  i++;
               }
            }
            catch (InterruptedException e) {
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
   public void close() {
      for (MultipageTiffReader r : new HashSet<MultipageTiffReader>(coordsToReader_.values())) {
         try {
            r.close();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   public boolean isFinished() {
      return !amInWriteMode_;
   }

   /**
    * TODO: disallowing non-DefaultSummaryMetadata instances here because
    * we rely on DefaultSummaryMetadata's JSON conversion functions.
    * TODO: subscribe to the Datastore to get notified of newly-created
    * images.
    */
   public void setSummaryMetadata(SummaryMetadata summary) {
      setSummaryMetadata((DefaultSummaryMetadata) summary, false);
   }
   
   private void setSummaryMetadata(DefaultSummaryMetadata summary,
         boolean showProgress) {
      summaryMetadata_ = summary;
      // HACK: ensure the metadata version is valid.
      if (summaryMetadata_.getMetadataVersion() == null) {
         summaryMetadata_ = (DefaultSummaryMetadata) summaryMetadata_.copy()
            .metadataVersion(DefaultSummaryMetadata.METADATA_VERSION).build();
      }
      JSONObject summaryJSON = summary.toJSON();
      if (summaryJSON != null) {
         summaryMetadataString_ = summaryJSON.toString();
         boolean slicesFirst = summaryJSON.optBoolean("SlicesFirst", true);
         boolean timeFirst = summaryJSON.optBoolean("TimeFirst", false);
         TreeMap<Coords, MultipageTiffReader> oldImageMap = coordsToReader_;
         coordsToReader_ = new TreeMap<Coords, MultipageTiffReader>();
         if (showProgress && !GraphicsEnvironment.isHeadless()) {
            ProgressBar progressBar = new ProgressBar("Building image location map", 0, oldImageMap.keySet().size());
            progressBar.setProgress(0);
            progressBar.setVisible(true);
            int i = 1;
            for (Coords coords : oldImageMap.keySet()) {
               coordsToReader_.put(coords, oldImageMap.get(coords));
               progressBar.setProgress(i);
               i++;
            }
            progressBar.setVisible(false);
         } else {
            coordsToReader_.putAll(oldImageMap);
         }
      }
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return summaryMetadata_;
   }

   public String getSummaryMetadataString() {
      return summaryMetadataString_;
   }

   public boolean getSplitByStagePosition() {
      return splitByXYPosition_;
   }

   public void writeDisplaySettings() {
      for (MultipageTiffReader r : new HashSet<MultipageTiffReader>(coordsToReader_.values())) {
         try {
            r.rewriteDisplaySettings(DefaultDisplaySettings.getStandardSettings());
            r.rewriteComments(summaryMetadata_.getComments());
         } catch (JSONException ex) {
            ReportingUtils.logError("Error writing display settings");
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   public String getDiskLocation() {
      return directory_;
   }

   public int lastAcquiredFrame() {
      if (amInWriteMode_) {
         return lastFrame_;
      } else {
         return lastFrameOpenedDataSet_;
      }
   }

   public void updateLastFrame(int frame) {
      lastFrame_ = Math.max(frame, lastFrame_);
   }

   public void updateLastPosition(int pos) {
      lastAcquiredPosition_ = Math.max(pos, lastAcquiredPosition_);
   }

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

   @Override
   public int getNumImages() {
      return coordsToReader_.keySet().size();
   }

   @Override
   public Coords getMaxIndices() {
      // TODO: this method is pretty poorly-implemented at current.
      if (maxIndices_ == null) {
         // Calculate max indices by examining all registered Readers.
         HashMap<String, Integer> maxIndices = new HashMap<String, Integer>();
         for (Coords coords : coordsToReader_.keySet()) {
            for (String axis : coords.getAxes()) {
               if (!maxIndices.containsKey(axis) ||
                     coords.getIndex(axis) > maxIndices.get(axis)) {
                  maxIndices.put(axis, coords.getIndex(axis));
               }
            }
         }
         DefaultCoords.Builder builder = new DefaultCoords.Builder();
         for (String axis : maxIndices.keySet()) {
            builder.index(axis, maxIndices.get(axis));
         }
         maxIndices_ = builder.build();
      }
      return maxIndices_;
   }

   /**
    * TODO: in future there will probably be a cleaner way to implement this.
    */
   @Override
   public List<String> getAxes() {
      return getMaxIndices().getAxes();
   }

   @Override
   public Integer getMaxIndex(String axis) {
      return getMaxIndices().getIndex(axis);
   }

   // Convenience function.
   public int getAxisLength(String axis) {
      return getMaxIndex(axis) + 1;
   }

   public Integer getIntendedSize(String axis) {
      if (summaryMetadata_.getIntendedDimensions() == null) {
         // Return the current size instead.
         return getAxisLength(axis);
      }
      return summaryMetadata_.getIntendedDimensions().getIndex(axis);
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) {
      HashSet<Image> result = new HashSet<Image>();
      synchronized(coordsToPendingImage_) {
         for (Coords imageCoords : coordsToPendingImage_.keySet()) {
            if (imageCoords.matches(coords)) {
               result.add(coordsToPendingImage_.get(imageCoords));
            }
         }
      }
      for (Coords imageCoords : coordsToReader_.keySet()) {
         if (imageCoords.matches(coords)) {
            result.add(coordsToReader_.get(imageCoords).readImage(imageCoords));
         }
      }
      return new ArrayList<Image>(result);
   }

   @Override
   public Image getImage(Coords coords) {
      synchronized(coordsToPendingImage_) {
         if (coordsToPendingImage_.containsKey(coords)) {
            return coordsToPendingImage_.get(coords);
         }
      }
      if (!coordsToReader_.containsKey(coords)) {
         ReportingUtils.logError("Asked for image at " + coords + " that doesn't exist");
         return null;
      }
      return coordsToReader_.get(coords).readImage(coords);
   }

   @Override
   public Image getAnyImage() {
      return firstImage_;
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      return coordsToReader_.keySet();
   }

   public static boolean getShouldGenerateMetadataFile() {
      return DefaultUserProfile.getInstance().getBoolean(
            StorageMultipageTiff.class, SHOULD_GENERATE_METADATA_FILE, false);
   }

   public static void setShouldGenerateMetadataFile(boolean shouldGen) {
      DefaultUserProfile.getInstance().setBoolean(
            StorageMultipageTiff.class,
            SHOULD_GENERATE_METADATA_FILE, shouldGen);
   }

   public static boolean getShouldSplitPositions() {
      return DefaultUserProfile.getInstance().getBoolean(
            StorageMultipageTiff.class,
            SHOULD_USE_SEPARATE_FILES_FOR_POSITIONS, true);
   }

   public static void setShouldSplitPositions(boolean shouldSplit) {
      DefaultUserProfile.getInstance().setBoolean(
            StorageMultipageTiff.class,
            SHOULD_USE_SEPARATE_FILES_FOR_POSITIONS, shouldSplit);
   }
}
