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
import ij.ImageJ;
import java.awt.Component;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import javax.swing.JOptionPane;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.MMException;
import org.micromanager.internal.utils.ProgressBar;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.data.DataProviderHasNewSummaryMetadataEvent;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;


/**
 * This class provides image storage in the form of a single TIFF file that
 * contains all image data in the dataset.
 * Adapted from the TaggedImageStorageMultipageTiff module.
 * 
 * TODO: The org.micromanager.data package should not depend on internal code
 * (or any code outside of the packae) so that it can be re-used elsewhere.
 * 
 * 
 */
public final class StorageMultipageTiff implements Storage {
   private static final String SHOULD_GENERATE_METADATA_FILE = 
           "generate a metadata file when saving datasets as multipage TIFF files";
   private static final String SHOULD_USE_SEPARATE_FILES_FOR_POSITIONS = 
           "generate a separate multipage TIFF file for each stage position";
   private static final HashSet<String> ALLOWED_AXES = new HashSet<String>(
         Arrays.asList(Coords.CHANNEL, Coords.T, Coords.Z,
            Coords.STAGE_POSITION));

   private DefaultDatastore store_;
   private Component parent_;
   private DefaultSummaryMetadata summaryMetadata_ = (new DefaultSummaryMetadata.Builder()).build();
   private String summaryMetadataString_ = NonPropertyMapJSONFormats.
         summaryMetadata().toJSON(summaryMetadata_.toPropertyMap());
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
   private final ConcurrentHashMap<Coords, Image> coordsToPendingImage_ =
            new ConcurrentHashMap<>();

   //map of position indices to objects associated with each
   private HashMap<Integer, FileSet> positionToFileSet_;
   
   //Map of image labels to file 
   private Map<Coords, MultipageTiffReader> coordsToReader_;
   // Keeps track of our maximum extent along each axis.
   private Coords maxIndices_;

  
   public StorageMultipageTiff(Component parent, Datastore store, String dir, 
           Boolean amInWriteMode)
         throws IOException {
      this(parent, store, dir, amInWriteMode, getShouldGenerateMetadataFile(),
            getShouldSplitPositions());
   }
   
   /**
    * Constructor that doesn't make reference to MMStudio so it can be used
    * independently of MM GUI
    * 
    * @param parent  GUI element on top of which a ProgressBar (or other things) can be displayed
    * @param store   Datastore to be saved
    * @param dir     Directory in which to store the data
    * @param amInWriteMode 
    * @param separateMDFile   Whether or not to write a separate file with the MM metadata
    * @param separateFilesForPositions If true, will store positions in separate files,
    *             otherwise all data will go into a single file
    * @throws java.io.IOException
    */
   public StorageMultipageTiff(Component parent, Datastore store, String dir,
         boolean amInWriteMode, boolean separateMDFile,
         boolean separateFilesForPositions) throws IOException {
      store_ = (DefaultDatastore) store;
      parent_ = parent;
      // We must be notified of changes in the Datastore before everyone else,
      // so that others can read those changes out of the Datastore later.
      store_.registerForEvents(this, 0);
      separateMetadataFile_ = separateMDFile;
      splitByXYPosition_ = separateFilesForPositions;

      amInWriteMode_ = amInWriteMode;
      directory_ = dir;
      store_.setSavePath(directory_);
      store_.setName(new File(directory_).getName());
      coordsToReader_ = new HashMap<>();

      if (amInWriteMode_) {
         positionToFileSet_ = new HashMap<>();
         // Create the directory now, even though we have nothing to write to
         // it, so we can detect e.g. permissions errors that would cause
         // problems later.
         File dirFile = new File(directory_);
         if (dirFile.exists()) {
            // No overwriting existing datastores.
            throw new IOException("Data at " + dirFile + " already exists");
         }
         dirFile.mkdirs();
         if (!dirFile.canWrite()) {
            throw new IOException("Insufficient permission to write to " + dirFile);
         }
      }
      else {
         openExistingDataSet();
      }
   }

   @Subscribe
   public void onNewSummaryMetadata(DataProviderHasNewSummaryMetadataEvent event) {
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

   /**
    * Indicator of Acquisition order.  This function is difficult to name.
    * "First" means that the axis comes before another axis in the ordered axes
    * list.  Note, however, that the list of ordered axes is exactly the inverse 
    * of the Acquisition Order as presented to the user
    * 
    * @return true if slices come before channels in the list of ordered axis
    */
   boolean slicesFirst() {
      List<String> orderList = summaryMetadata_.getOrderedAxes();
      if (orderList == null) {
         // HACK: default to true.
         return true;
      }
      int sliceIndex = orderList.indexOf(Coords.Z);
      int channelIndex = orderList.indexOf(Coords.CHANNEL);
      return (sliceIndex < channelIndex);
   }

   /**
    * Indicator of Acquisition order.  This function is difficult to name.
    * "First" means that the axis comes before another axis in the ordered axes
    * list.  Note, however, that the list of ordered axes is exactly the inverse 
    * of the Acquisition Order as presented to the user
    * 
    * @return true if time comes before positions in the list of ordered axis
    */
   boolean timeFirst() {
      List<String> orderList = summaryMetadata_.getOrderedAxes();
      if (orderList == null) {
         // HACK: default to false.
         return false;
      }
      int timeIndex = orderList.indexOf(Coords.T);
      int positionIndex = orderList.indexOf(Coords.STAGE_POSITION);
      return (timeIndex < positionIndex);
   }

   private void openExistingDataSet() {
      //Need to throw error if file not found
      MultipageTiffReader reader = null;
      File dir = new File(directory_);

      ProgressBar progressBar = null;
      // Allow operation in headless mode.
      if (!GraphicsEnvironment.isHeadless()) {
         progressBar = new ProgressBar(parent_, "Reading " + directory_, 0, 
                 dir.listFiles().length);
      }
      int numRead = 0;
      if (progressBar != null) {
         progressBar.setProgress(numRead);
         progressBar.setVisible(true);
      }
      for (File f : dir.listFiles()) {
         // Heuristics to only read tiff files, and not files created by the
         // OS (starting with ".")
         String fileName = f.getName();
         if (fileName.endsWith(".tif") || fileName.endsWith(".TIF") ) {
            if (  !fileName.startsWith("._") ) {
               reader = loadFile(f);
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

   private MultipageTiffReader loadFile(File f) {
      MultipageTiffReader reader = null;
      try {
         try {
            reader = new MultipageTiffReader(this, f);
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
            reader = new MultipageTiffReader(f);
            reader.close();
            // Open the file normally.
            reader = new MultipageTiffReader(this, f);
         }
         Set<Coords> readerCoords = reader.getIndexKeys();
         for (Coords coords : readerCoords) {
            coordsToReader_.put(coords, reader);
            lastFrameOpenedDataSet_ = Math.max(coords.getT(),
                  lastFrameOpenedDataSet_);
            if (firstImage_ == null) {
               firstImage_ = reader.readImage(coords);
            }
         }
      } catch (IOException ex) {
         ReportingUtils.showError(ex, "There was an error reading the file: " + f.toString());
      }
      return reader;
   }

   @Override
   public void putImage(Image newImage) {
      DefaultImage image = (DefaultImage) newImage;
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
         writeImage(image, false);
      }
      catch (MMException | InterruptedException | ExecutionException | IOException e) {
         ReportingUtils.showError(e, "Failed to write image at " + image.getCoords());
      }
   }

   @Override
   public void freeze() {
      finished();
   }

   private void writeImage(DefaultImage image, boolean waitForWritingToFinish) 
           throws MMException, InterruptedException, ExecutionException, IOException {
      writeImage(image);
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
   private void writeImage(DefaultImage image) throws MMException, IOException {
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
         maxIndices_ = image.getCoords().copyBuilder().build();
      }
      else {
         for (String axis : image.getCoords().getAxes()) {
            int pos = image.getCoords().getIndex(axis);
            if (pos > maxIndices_.getIndex(axis)) {
               maxIndices_ = maxIndices_.copyBuilder().index(axis, pos).build();
            }
         }
      }

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

      if (omeMetadata_ == null) {
         omeMetadata_ = new OMEMetadata(this);
      }

      if (!positionToFileSet_.containsKey(fileSetIndex)) {
         positionToFileSet_.put(fileSetIndex,
               new FileSet(image, this, omeMetadata_,
                  splitByXYPosition_, separateMetadataFile_));
      }
      FileSet set = positionToFileSet_.get(fileSetIndex);

      try {
         set.writeImage(image);
         Coords coords = image.getCoords();
         coordsToReader_.put(coords, set.getCurrentReader());
      } catch (IOException ex) {
        ReportingUtils.showError(ex, "Failed to write image to file.");
      }

      int frame = image.getCoords().getTimePoint();
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
      ProgressBar progressBar = null;
      if (!GraphicsEnvironment.isHeadless()) {
         progressBar = new ProgressBar(parent_, "Finishing Files", 0, 
                 positionToFileSet_.size());
      }
      try {
         int count = 0;
         if (progressBar != null) {
            progressBar.setProgress(count);
            progressBar.setVisible(true);
         }
         for (FileSet p : positionToFileSet_.values()) {
            p.finishAbortedAcqIfNeeded();
         }

         try {
            //fill in missing tiffdata tags for OME meteadata--needed for
            //acquisitions in which z and t arent the same for every channel
            for (int p = 0; p <= lastAcquiredPosition_; p++) {
               //set sizeT in case of aborted acq
               int currentFrame = positionToFileSet_.get(
                     splitByXYPosition_ ? p : 0).getCurrentFrame();
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
         String ijDescription = getIJDescriptionString();
         for (FileSet p : positionToFileSet_.values()) {
            if (p.hasSpaceForFullOMEXML(length)) {
               uuid = p.getCurrentUUID();
               filename = p.getCurrentFilename();
               p.finished(fullOMEXMLMetadata, ijDescription);
               master = p;
               count++;
               if (progressBar != null) {
                  progressBar.setProgress(count);
               }
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
            p.finished(partialOME, ijDescription);
            count++;
            if (progressBar != null) {
               progressBar.setProgress(count);
            }
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
         if (progressBar != null) {
            progressBar.setVisible(false);
         }
      }
      finished_ = true;
   }

   public boolean isFinished() {
      return !amInWriteMode_;
   }

   public void setSummaryMetadata(SummaryMetadata summary) {
      setSummaryMetadata((DefaultSummaryMetadata) summary, false);
   }

   private void setSummaryMetadata(DefaultSummaryMetadata summary,
         boolean showProgress) {
      summaryMetadata_ = summary;
      summaryMetadataString_ = NonPropertyMapJSONFormats.summaryMetadata().
            toJSON(summary.toPropertyMap());

      // TODO What does the following have to do with summary metadata?
      Map<Coords, MultipageTiffReader> oldImageMap = coordsToReader_;
      coordsToReader_ = new HashMap<>();
      if (showProgress && !GraphicsEnvironment.isHeadless()) {
         ProgressBar progressBar = new ProgressBar(parent_, 
                 "Building image location map", 0, oldImageMap.keySet().size());
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
   
    /**
    * This function provides the ImageJ "Properties" information when opening 
    * in ImageJ/Fiji.   
    * 
    * It also defines the image orde when creating Hyperstacks from sequences
    * of images
    * 
    * @return 
    */
   private String getIJDescriptionString() {
      StringBuffer sb = new StringBuffer();
      sb.append("ImageJ=" + ImageJ.VERSION + "\n");
      int numChannels = getIntendedSize(Coords.CHANNEL);
      int numFrames = getIntendedSize(Coords.TIME_POINT);
      int numSlices = getIntendedSize(Coords.Z_SLICE);
      if (numChannels > 1) {
         sb.append("channels=").append(numChannels).append("\n");
      }
      if (numSlices > 1) {
         sb.append("slices=").append(numSlices).append("\n");
      }
      if (numFrames > 1) {
         sb.append("frames=").append(numFrames).append("\n");
      }
      if (numFrames > 1 || numSlices > 1 || numChannels > 1) {
         sb.append("hyperstack=true\n");
                   
         // It loooks like ImageJ ignores the order and always assumes it to be
         // tcz (which it calls "xyctz".  This means that datasets acquired in 
         // a different order will open incorrectly in ImageJ.        
         // order - hyperstack order ("default", "xyctz", "xyzct", "xyztc", "xytcz" or "xytzc")
         
         /*
          * This was an attempt to tell ImageJ about image order, but I see no 
          * evidence (either in practice or in the code) that it looks at the "order" tag
         
         sb.append("order=xyctz\n");
         
         if (numFrames < 1) {
            if (numChannels > 1 && numSlices > 1) {
               if (!slicesFirst()) {
                  sb.append("order=zc\n");
               } else {
                  sb.append("order=cz\n");
               }
            }
         } else { // we have a time axis.  Currently, time always comes first
            sb.append("order=");
            if (numChannels > 1 && numSlices > 1) {
               if (!slicesFirst()) {
                  sb.append("zc");
               } else {
                  sb.append("cz");
               }
            } else if (numChannels > 1) {
               sb.append("c");
            } else if (numSlices > 1) {
               sb.append("z");
            }
            sb.append("t\n");         
         }
         */
      }
      //cm so calibration unit is consistent with units used in Tiff tags
      sb.append("unit=um\n");
      if (numSlices > 1) {
         double zStepUm = 0.0;
         if (getSummaryMetadata().getZStepUm() != null) {
            zStepUm = getSummaryMetadata().getZStepUm();
         }
         sb.append("spacing=").append(zStepUm).append("\n");
      }
      // write single channel contrast settings or display mode if multi channel
      // it would be nice to have the display settings of the current viewer,
      // but we don't, and do not want to couple Display Settings to Data storage,
      // so come up with reasonable defaults
      // DisplaySettings settings = DefaultDisplaySettings.builder().build();
      DisplaySettings settings = getDisplaySettings();
      
      if (numChannels == 1) {
         sb.append("mode=gray\n");
         if (settings != null) {
            sb.append("min=").append(settings.getSafeContrastMin(0, 0, 0)).append("\n");
            sb.append("max=").append(settings.getSafeContrastMax(0, 0, 0)).append("\n");
         }
      } else {
         // multiple channels?  go for composite display
         if (settings != null) {
            DisplaySettings.ColorMode mode = settings.getChannelColorMode();
            if (null != mode) switch (mode) {
               case COMPOSITE:
                  sb.append("mode=composite\n");
                  break;
               case COLOR:
                  sb.append("mode=color\n");
                  break;
               case GRAYSCALE:
                  sb.append("mode=gray\n");
                  break;
               default:
                  break;
            }
         } else {
            sb.append("mode=composite\n");
         }
      }


      sb.append((char) 0);
      return new String(sb);
   }
   
   /**
    * Since we do no longer want to pass through DisplaySettings to the saving code,
    * but still need access to the, to enable saving metadata used by 3rdparty
    * applications (such as ImageJ/Fiji) to display the data the same way we 
    * did in MM, we need some ugly heuristics to figure out what DisplaySettings 
    * were used.
    * 
    * @return DisplaySettings of a DataViewer that used this store for data.
    *          Will be null when no such DataViewer was found.
    */
   DisplaySettings getDisplaySettings() {
      MMStudio studio = MMStudio.getInstance();
      DataViewer activeDataViewer = studio.displays().getActiveDataViewer();
      try {
         if (activeDataViewer != null && isViewingOurStore(activeDataViewer)) {
            
            return activeDataViewer.getDisplaySettings();
         } else {
            List<DataViewer> allDataViewers = studio.displays().getAllDataViewers();
            for (DataViewer dv : allDataViewers) {
               if (dv != null && isViewingOurStore(dv)) {
                  return dv.getDisplaySettings();
               }
            }
         }
      } catch (IOException ioe) {
         // TODO, handle nicely
      }
      return null;
   }

   /** 
    * We can not simply test if the viewer's store is the same as ours, 
    * since we may be saving a "copy" (i.e., saving a RAMM data set on disk)
    * This is bad, but seems to work alright for now.
    */
   private boolean isViewingOurStore  (DataViewer dv) throws IOException{
      DataProvider dp = dv.getDataProvider();
      if (dp != null) {
         Image dpImg = dp.getAnyImage();
         Image ourImg = store_.getAnyImage();
         if (dpImg.getWidth() != ourImg.getWidth() || 
                 dpImg.getHeight() != ourImg.getHeight() ||
                 dpImg.getBytesPerPixel() != ourImg.getBytesPerPixel()) {
            return false;
         }
         if (dp.getAxes().size() != store_.getAxes().size()) {
            return false;
         }
         for (String axis : dp.getAxes() ) {
            if (store_.getAxisLength(axis) != dp.getAxisLength(axis)) {
               return false;
            }
         }
         return true;
         
      }
      return false;
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
      List<File> list = new LinkedList<>();
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
         HashMap<String, Integer> maxIndices = new HashMap<>();
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
    * TODO: Check that summaryMetadata is a reliable source for this information
    */
   @Override
   public List<String> getAxes() {
      return summaryMetadata_.getOrderedAxes();
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
      // The SummaryMetadata will return -1 for axes that are not present, but
      // all of our uses of this method require a minimum size of 1.
      return Math.max(summaryMetadata_.getIntendedDimensions().getIndex(axis),
            1);
   }

   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) {
      HashSet<Image> result = new HashSet<>();
      synchronized(coordsToPendingImage_) {
         for (Coords imageCoords : coordsToPendingImage_.keySet()) {
            if (imageCoords.isSubspaceCoordsOf(coords)) {
               result.add(coordsToPendingImage_.get(imageCoords));
            }
         }
      }
      for (Coords imageCoords : coordsToReader_.keySet()) {
         if (imageCoords.isSubspaceCoordsOf(coords)) {
            try {
               result.add(coordsToReader_.get(imageCoords).readImage(imageCoords));
            }
            catch (IOException ex) {
               ReportingUtils.logError("Failed to read image at " + imageCoords);
            }
         }
      }
      return new ArrayList<>(result);
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
      try {
         return coordsToReader_.get(coords).readImage(coords);
      }
      catch (IOException ex) {
         ReportingUtils.logError(ex, "Failed to read image at " + coords);
         return null;
      }
   }

   @Override
   public Image getAnyImage() {
      return firstImage_;
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      return coordsToReader_.keySet();
   }

   @Override
   public boolean hasImage(Coords coords) {
      return coordsToPendingImage_.containsKey(coords) ||
         coordsToReader_.containsKey(coords);
   }

   /**
    * Remove open file descriptors.
    */
   @Override
   public void close() {
      // For files we wrote ourselves.
      if (positionToFileSet_ != null) {
         for (FileSet fileset : positionToFileSet_.values()) {
            fileset.closeFileDescriptors();
         }
      }
      // For files we read from disk.
      for (MultipageTiffReader reader : coordsToReader_.values()) {
         try {
            reader.close();
         }
         catch (IOException e) {
            ReportingUtils.logError(e, "Error cleaning up open file descriptor");
         }
      }
   }

   public static boolean getShouldGenerateMetadataFile() {
      return MMStudio.getInstance().profile().getSettings(StorageMultipageTiff.class).
              getBoolean(SHOULD_GENERATE_METADATA_FILE, false);
   }

   public static void setShouldGenerateMetadataFile(boolean shouldGen) {
      MMStudio.getInstance().profile().getSettings(StorageMultipageTiff.class).
              putBoolean(SHOULD_GENERATE_METADATA_FILE, shouldGen);
   }

   public static boolean getShouldSplitPositions() {
      return MMStudio.getInstance().profile().getSettings(StorageMultipageTiff.class).
              getBoolean(SHOULD_USE_SEPARATE_FILES_FOR_POSITIONS, true);
   }

   public static void setShouldSplitPositions(boolean shouldSplit) {
      MMStudio.getInstance().profile().getSettings(StorageMultipageTiff.class).
              putBoolean(SHOULD_USE_SEPARATE_FILES_FOR_POSITIONS, shouldSplit);
   }
}
