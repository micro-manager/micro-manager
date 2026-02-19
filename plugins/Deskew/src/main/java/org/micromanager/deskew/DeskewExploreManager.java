package org.micromanager.deskew;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.ndviewer2.AxesBridge;
import org.micromanager.display.ndviewer2.NDViewer2DataProvider;
import org.micromanager.display.ndviewer2.NDViewer2DataViewer;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.lightsheet.StackResampler;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.ndviewer.api.NDViewerAPI;
import org.micromanager.ndviewer.api.NDViewerAcqInterface;
import org.micromanager.ndviewer.overlay.Overlay;

/**
 * Manages the Deskew Explore session.
 * Coordinates between the GUI, NDViewer, storage, and acquisition.
 */
public class DeskewExploreManager {

   private static final int SAVING_QUEUE_SIZE = 30;

   private final Studio studio_;
   private final DeskewFrame frame_;
   private final DeskewFactory deskewFactory_;

   private NDViewerAPI viewer_;
   private NDViewer2DataViewer mm2Viewer_;
   private NDViewer2DataProvider mm2DataProvider_;
   private NDTiffStorage storage_;
   private DeskewExploreDataSource dataSource_;
   private ExecutorService displayExecutor_;
   private ExecutorService acquisitionExecutor_;

   // Projected image dimensions (unknown until first deskew), stored pre-rotation
   private int projectedWidth_ = -1;
   private int projectedHeight_ = -1;
   private int bitDepth_ = 16;
   // Estimated tile dimensions based on camera size (for first click), stored pre-rotation
   private int estimatedTileWidth_ = 512;
   private int estimatedTileHeight_ = 512;

   // State tracking
   private volatile boolean exploring_ = false;
   private volatile boolean viewerClosing_ = false;  // Guard for onViewerClosed re-entrancy
   private boolean loadedData_ = false;  // True when viewing a loaded dataset
   private String storageDir_;
   private String acqName_;

   // Stage position tracking for multi-tile acquisition
   private double initialStageX_ = 0;
   private double initialStageY_ = 0;
   private double pixelSizeUm_ = 1.0;

   public DeskewExploreManager(Studio studio, DeskewFrame frame, DeskewFactory deskewFactory) {
      studio_ = studio;
      frame_ = frame;
      deskewFactory_ = deskewFactory;
   }

   /**
    * Starts the explore session.
    * Creates the NDViewer and prepares for tile acquisition.
    */
   public void startExplore() {
      if (exploring_) {
         studio_.logs().showMessage("Explore session already running.");
         return;
      }

      try {
         exploring_ = true;
         viewerClosing_ = false;

         // Create executors
         displayExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Deskew Explore viewer communication"));
         acquisitionExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Deskew Explore acquisition"));

         // Create data source
         dataSource_ = new DeskewExploreDataSource(this);

         // Create temporary storage directory
         String tmpPathSetting = frame_.getSettings().getString(
                 DeskewFrame.EXPLORE_TMP_PATH, "").trim();
         Path tempDir;
         if (tmpPathSetting.isEmpty()) {
            tempDir = Files.createTempDirectory("deskew_explore_");
         } else {
            Path base = new File(tmpPathSetting).toPath();
            Files.createDirectories(base);
            tempDir = Files.createTempDirectory(base, "deskew_explore_");
         }
         storageDir_ = tempDir.toFile().getAbsolutePath();
         acqName_ = "DeskewExplore_" + LocalDateTime.now()
                 .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

         // Get camera info for initial setup
         final int imageWidth = (int) studio_.core().getImageWidth();
         final int imageHeight = (int) studio_.core().getImageHeight();
         bitDepth_ = (int) studio_.core().getImageBitDepth();
         pixelSizeUm_ = studio_.core().getPixelSizeUm();
         if (pixelSizeUm_ <= 0) {
            pixelSizeUm_ = 1.0;
         }

         // Store initial stage position for multi-tile acquisition
         initialStageX_ = studio_.core().getXPosition();
         initialStageY_ = studio_.core().getYPosition();
         studio_.logs().logMessage("Deskew Explore: initial stage position = ("
                 + initialStageX_ + ", " + initialStageY_ + ")");

         // Estimate tile dimensions based on camera size
         // (actual size will be determined after first deskew)
         estimatedTileWidth_ = imageWidth;
         estimatedTileHeight_ = imageHeight;
         // Set initial estimated dimensions on data source for first-click support
         updateTileDimensionsForRotation();

         // Create summary metadata for storage and viewer
         JSONObject summaryMetadata = new JSONObject();
         summaryMetadata.put("Width", imageWidth);
         summaryMetadata.put("Height", imageHeight);
         summaryMetadata.put("PixelSize_um", pixelSizeUm_);
         summaryMetadata.put("BitDepth", bitDepth_);
         summaryMetadata.put("PixelType", bitDepth_ <= 8 ? "GRAY8" : "GRAY16");
         summaryMetadata.put("GridPixelOverlapX", 0);
         summaryMetadata.put("GridPixelOverlapY", 0);

         // Initialize storage immediately so NDViewer has something to work with
         storage_ = new NDTiffStorage(storageDir_, acqName_, summaryMetadata,
                 0, 0, true, null, SAVING_QUEUE_SIZE, null, true);
         dataSource_.setStorage(storage_);

         // Create NDViewer2 (NDViewer + MM Inspector)
         AxesBridge axesBridge = new AxesBridge();
         mm2DataProvider_ = new NDViewer2DataProvider(
               storage_, axesBridge, acqName_);
         NDViewerAcqInterface acqInterface = createAcqInterface();
         mm2Viewer_ = new NDViewer2DataViewer(
               studio_, dataSource_, acqInterface, mm2DataProvider_, axesBridge,
               summaryMetadata, pixelSizeUm_, false);
         mm2Viewer_.setAccumulateStats(true);
         viewer_ = mm2Viewer_.getNDViewer();
         viewer_.setWindowTitle("Deskew Explore - Right-click to select, "
               + "Left-drag to extend, Left-click to acquire");

         // Set up overlayer and mouse listener
         viewer_.setOverlayerPlugin(dataSource_);
         viewer_.setCustomCanvasMouseListener(dataSource_);

         // Set metadata functions
         viewer_.setReadTimeMetadataFunction(tags -> {
            try {
               if (tags.has("ElapsedTime-ms")) {
                  return tags.getLong("ElapsedTime-ms");
               }
            } catch (Exception e) {
               // Ignore
            }
            return 0L;
         });
         viewer_.setReadZMetadataFunction(tags -> 0.0);

         // Set initial view offset
         viewer_.setViewOffset(0, 0);

         studio_.logs().logMessage("Deskew Explore started. "
                 + "Dimensions will be determined after first acquisition.");

      } catch (Exception e) {
         studio_.logs().showError(e, "Failed to start Deskew Explore.");
         stopExplore();
      }
   }

   /**
    * Opens an existing NDTiff explore dataset for viewing.
    * The viewer shows the previously acquired tiles and allows acquiring new ones
    * (which requires the stage to be at the same position as the original acquisition).
    *
    * @param dir Path to the NDTiff dataset directory
    */
   public void openExplore(String dir) {
      if (exploring_) {
         studio_.logs().showMessage("Explore session already running.");
         return;
      }

      try {
         exploring_ = true;
         viewerClosing_ = false;
         loadedData_ = true;

         // Create executors
         displayExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Deskew Explore viewer communication"));
         acquisitionExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Deskew Explore acquisition"));

         // Create data source
         dataSource_ = new DeskewExploreDataSource(this);

         // Open existing storage read-only
         storage_ = new NDTiffStorage(dir);
         storageDir_ = new File(dir).getParent();
         acqName_ = new File(dir).getName();
         dataSource_.setStorage(storage_);

         // Read summary metadata from existing storage
         JSONObject summaryMetadata = storage_.getSummaryMetadata();
         final int imageWidth = summaryMetadata.optInt("Width", 512);
         final int imageHeight = summaryMetadata.optInt("Height", 512);
         bitDepth_ = summaryMetadata.optInt("BitDepth", 16);
         pixelSizeUm_ = summaryMetadata.optDouble("PixelSize_um", 1.0);
         if (pixelSizeUm_ <= 0) {
            pixelSizeUm_ = 1.0;
         }

         // Store current stage position for potential new acquisitions
         initialStageX_ = studio_.core().getXPosition();
         initialStageY_ = studio_.core().getYPosition();

         // Determine tile dimensions from the first stored image
         for (HashMap<String, Object> axes : storage_.getAxesSet()) {
            EssentialImageMetadata meta = storage_.getEssentialImageMetadata(axes);
            projectedWidth_ = meta.width;
            projectedHeight_ = meta.height;
            break;
         }
         if (projectedWidth_ <= 0 || projectedHeight_ <= 0) {
            projectedWidth_ = imageWidth;
            projectedHeight_ = imageHeight;
         }
         // Stored images are already in their final (post-rotation) orientation,
         // so use their dimensions directly without re-applying rotation.
         dataSource_.setTileDimensions(projectedWidth_, projectedHeight_);

         // Mark existing tiles as acquired
         for (HashMap<String, Object> axes : storage_.getAxesSet()) {
            Object rowObj = axes.get("row");
            Object colObj = axes.get("column");
            if (rowObj instanceof Number && colObj instanceof Number) {
               dataSource_.markTileAcquired(
                       ((Number) rowObj).intValue(),
                       ((Number) colObj).intValue());
            }
         }

         // Create NDViewer2 (NDViewer + MM Inspector)
         AxesBridge axesBridge = new AxesBridge();
         mm2DataProvider_ = new NDViewer2DataProvider(
               storage_, axesBridge, acqName_);
         NDViewerAcqInterface acqInterface = createAcqInterface();
         mm2Viewer_ = new NDViewer2DataViewer(
               studio_, dataSource_, acqInterface, mm2DataProvider_, axesBridge,
               summaryMetadata, pixelSizeUm_, false);
         mm2Viewer_.setAccumulateStats(true);
         viewer_ = mm2Viewer_.getNDViewer();
         viewer_.setWindowTitle("Deskew Explore - " + acqName_);

         // Set up overlayer and mouse listener
         viewer_.setOverlayerPlugin(dataSource_);
         viewer_.setCustomCanvasMouseListener(dataSource_);

         // Set metadata functions
         viewer_.setReadTimeMetadataFunction(tags -> {
            try {
               if (tags.has("ElapsedTime-ms")) {
                  return tags.getLong("ElapsedTime-ms");
               }
            } catch (Exception e) {
               // Ignore
            }
            return 0L;
         });
         viewer_.setReadZMetadataFunction(tags -> 0.0);

         viewer_.setViewOffset(0, 0);

         // Trigger initial display
         if (displayExecutor_ != null && viewer_ != null) {
            displayExecutor_.submit(() -> {
               HashMap<String, Object> displayAxes = new HashMap<>();
               viewer_.newImageArrived(displayAxes);
               viewer_.update();
            });
         }

         studio_.logs().logMessage("Deskew Explore: opened dataset from " + dir);

      } catch (Exception e) {
         studio_.logs().showError(e, "Failed to open Deskew Explore dataset.");
         stopExplore();
      }
   }

   /**
    * Create an NDViewerAcqInterface for the explore session.
    */
   private NDViewerAcqInterface createAcqInterface() {
      return new NDViewerAcqInterface() {
         @Override
         public boolean isFinished() {
            // Always report finished so NDViewer does not show
            // "Finish Acquisition?" dialog on close.
            return true;
         }

         @Override
         public void abort() {
            // Don't call stopExplore() here — NDViewer calls abort()
            // before dataSource.close(), which triggers onViewerClosed().
            // If we called stopExplore() here, it would set exploring_=false
            // and onViewerClosed() would skip the save prompt.
         }

         @Override
         public void setPaused(boolean paused) {
            // Not applicable for Explore mode
         }

         @Override
         public boolean isPaused() {
            return false;
         }

         @Override
         public void waitForCompletion() {
            // Not applicable for Explore mode
         }
      };
   }

   /**
    * Stops the explore session and cleans up resources.
    * Deletes temporary storage by default.
    */
   public void stopExplore() {
      stopExplore(true);
   }

   /**
    * Stops the explore session and cleans up resources.
    *
    * @param deleteTempFiles If true, deletes the temporary storage directory
    */
   private void stopExplore(boolean deleteTempFiles) {
      if (!exploring_) {
         return; // Already stopped (re-entrant call)
      }
      exploring_ = false;
      loadedData_ = false;

      if (displayExecutor_ != null) {
         displayExecutor_.shutdownNow();
         displayExecutor_ = null;
      }

      if (acquisitionExecutor_ != null) {
         acquisitionExecutor_.shutdownNow();
         acquisitionExecutor_ = null;
      }

      // Close viewers BEFORE storage to avoid NPEs from pending repaints
      if (mm2Viewer_ != null) {
         mm2Viewer_.close();
         mm2Viewer_ = null;
      }
      mm2DataProvider_ = null;
      viewer_ = null;

      if (dataSource_ != null) {
         dataSource_.setFinished(true);
         dataSource_ = null;
      }

      // Close storage and optionally delete temp files on a background thread.
      // NDViewer's close runs asynchronously on its own thread ("NDViewer closing
      // thread"), so we must wait for it to finish before closing the storage or
      // deleting files it may still be accessing.
      final NDTiffStorage storageToClose = storage_;
      final boolean doDelete = deleteTempFiles;
      storage_ = null;
      if (storageToClose != null || doDelete) {
         new Thread(() -> {
            // Wait for NDViewer's async close thread ("NDViewer closing thread")
            // to finish before touching storage. NDViewer does not provide a
            // join/callback, so we use a best-effort delay. 500ms is sufficient
            // in practice, but if NDViewer adds a close-completion callback
            // this sleep should be replaced.
            try {
               Thread.sleep(500);
            } catch (InterruptedException ignored) {
               Thread.currentThread().interrupt();
            }
            if (storageToClose != null) {
               try {
                  if (!storageToClose.isFinished()) {
                     storageToClose.finishedWriting();
                  }
                  storageToClose.close();
               } catch (Exception e) {
                  studio_.logs().logError(e, "Error closing storage");
               }
            }
            if (doDelete) {
               deleteTempStorage();
            }
         }, "Deskew Explore cleanup").start();
      }
   }

   /**
    * Called when the viewer is closed by the user.
    * Prompts to save data if any tiles were acquired (only for new sessions, not loaded data).
    */
   public void onViewerClosed() {
      // Guard against re-entrant calls:
      // - stopExplore() closes the viewer, which triggers dataSource.close() → here
      // - mm2Viewer_.close() below calls ndViewer_.close() → dataSource.close() → here
      if (!exploring_ || viewerClosing_) {
         return;
      }
      viewerClosing_ = true;

      // The viewer is already closing (NDViewer triggered this via dataSource.close()).
      // Only shut down MM2-specific resources (Inspector detach, stats queue) without
      // calling ndViewer_.close() again — a second close would queue EDT runnables
      // that NPE on NDViewer's partially-torn-down internal state.
      if (mm2Viewer_ != null) {
         mm2Viewer_.closeWithoutNDViewer();
      }
      // Null out viewer references so stopExplore() doesn't try to close them again.
      mm2Viewer_ = null;
      mm2DataProvider_ = null;
      viewer_ = null;

      // If we opened an existing dataset, just close without prompting
      if (loadedData_) {
         stopExplore(false);  // Don't delete the user's data
         return;
      }

      // Check if there's any data to save
      boolean hasData = false;
      try {
         hasData = storage_ != null && !storage_.isFinished() && !storage_.getAxesSet().isEmpty();
      } catch (Exception e) {
         // Storage may already be in a bad state
         studio_.logs().logMessage("Deskew Explore: could not check storage state: "
                  + e.getMessage());
      }

      if (hasData) {
         // Ask user what to do with the data
         int choice = JOptionPane.showOptionDialog(
                 null,
                 "Save the acquired Deskew Explore data?",
                 "Save Explore Data",
                 JOptionPane.YES_NO_CANCEL_OPTION,
                 JOptionPane.QUESTION_MESSAGE,
                 null,
                 new String[]{"Save", "Discard", "Cancel"},
                 "Save");

         if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
            // Cancel - don't close (but we can't really prevent NDViewer from closing)
            // At least don't delete the data
            studio_.logs().logMessage("Deskew Explore: close cancelled, data remains in: "
                     + storageDir_);
            stopExplore(false);  // Don't delete temp files
            return;
         } else if (choice == 0) {
            // Save - let user choose location
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Deskew Explore Data");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            chooser.setSelectedFile(new File(acqName_));

            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
               File destDir = chooser.getSelectedFile();
               saveDataTo(destDir);
            } else {
               // User cancelled save dialog - keep data in temp
               studio_.logs().logMessage("Deskew Explore: save cancelled, data remains in: "
                        + storageDir_);
               stopExplore(false);
               return;
            }
         }
         // choice == 1 (Discard) falls through to delete
      }

      stopExplore(true);  // Delete temp files
   }

   /**
    * Saves the explore data to the specified directory.
    */
   private void saveDataTo(File destDir) {
      if (storageDir_ == null) {
         return;
      }

      try {
         // Finish writing to storage if it's still open
         if (storage_ != null) {
            try {
               if (!storage_.isFinished()) {
                  storage_.finishedWriting();
               }
               storage_.close();
            } catch (Exception e) {
               studio_.logs().logError(e, "Error closing storage before save");
            }
            storage_ = null;
         }

         // Source directory contains the NDTiff data
         File sourceDir = new File(storageDir_, acqName_);
         if (!sourceDir.exists()) {
            sourceDir = new File(storageDir_);
         }

         // Create destination if it doesn't exist
         if (!destDir.exists()) {
            destDir.mkdirs();
         }

         // Copy all files from source to destination
         Path sourcePath = sourceDir.toPath();
         Path destPath = destDir.toPath();

         final int[] copyErrors = {0};
         try (Stream<Path> stream = Files.walk(sourcePath)) {
            stream.forEach(source -> {
               try {
                  Path dest = destPath.resolve(sourcePath.relativize(source));
                  if (Files.isDirectory(source)) {
                     if (!Files.exists(dest)) {
                        Files.createDirectories(dest);
                     }
                  } else {
                     Files.copy(source, dest, StandardCopyOption.REPLACE_EXISTING);
                  }
               } catch (IOException e) {
                  copyErrors[0]++;
                  studio_.logs().logError(e, "Failed to copy file: " + source);
               }
            });
         }

         if (copyErrors[0] > 0) {
            studio_.logs().showMessage("Data saved to " + destDir.getAbsolutePath()
                  + " with " + copyErrors[0] + " file(s) failed to copy. "
                  + "Check CoreLog for details.");
         } else {
            studio_.logs().logMessage(
                  "Deskew Explore: data saved to " + destDir.getAbsolutePath());
            studio_.logs().showMessage("Data saved to: " + destDir.getAbsolutePath());
         }

      } catch (Exception e) {
         studio_.logs().showError(e, "Failed to save Deskew Explore data");
      }
   }

   /**
    * Deletes the temporary storage directory and all its contents.
    */
   private void deleteTempStorage() {
      if (storageDir_ == null) {
         return;
      }

      try {
         File dir = new File(storageDir_);
         if (dir.exists()) {
            // Delete directory recursively
            try (Stream<Path> stream = Files.walk(dir.toPath())) {
               stream.sorted(Comparator.reverseOrder())
                       .forEach(path -> {
                          try {
                             Files.delete(path);
                          } catch (IOException e) {
                             studio_.logs().logError(e,
                                   "Failed to delete: " + path);
                          }
                       });
            }
            studio_.logs().logMessage("Deskew Explore: deleted temp storage at " + storageDir_);
         }
      } catch (IOException e) {
         studio_.logs().logError(e, "Failed to delete temp storage: " + storageDir_);
      }
   }

   /**
    * Acquires a tile at the specified row and column.
    * Runs a Test Acquisition, processes through deskew, and stores the XY projection.
    */
   public void acquireTile(int row, int col) {
      if (!exploring_ || acquisitionExecutor_ == null) {
         return;
      }

      acquisitionExecutor_.submit(() -> {
         try {
            studio_.logs().logMessage("Deskew Explore: acquiring tile at row=" + row + ", col="
                     + col);

            // Get Z settings from MDA
            SequenceSettings settings = studio_.acquisitions().getAcquisitionSettings();
            SequenceSettings.Builder sb = settings.copyBuilder();
            sb.useFrames(false)
                    .usePositionList(false)
                    .save(false)
                    .shouldDisplayImages(false)  // Prevent display windows from appearing
                    .isTestAcquisition(true);

            if (!settings.useSlices()) {
               studio_.logs().showError("Deskew Explore requires Z-stack acquisition settings.");
               return;
            }

            SequenceSettings acqSettings = sb.build();

            // Set explore mode flag so DeskewFactory creates a pass-through processor
            // This ensures raw images flow through to the test datastore even when
            // "Keep Original Image Files" is unchecked
            Datastore testStore;
            frame_.getMutableSettings().putBoolean(DeskewFrame.EXPLORE_MODE, true);
            deskewFactory_.setSettings(frame_.getSettings());
            try {
               // Run acquisition in blocking mode - this ensures completion
               // shouldDisplayImages(false) prevents the normal acquisition display
               // and any pipeline processors from creating display windows
               testStore = studio_.acquisitions().runAcquisitionWithSettings(
                       acqSettings, true);  // blocking = true
            } finally {
               // Always reset explore mode flag, even if acquisition throws
               frame_.getMutableSettings().putBoolean(DeskewFrame.EXPLORE_MODE, false);
               deskewFactory_.setSettings(frame_.getSettings());
            }

            if (testStore == null) {
               studio_.logs().showError("Test acquisition failed.");
               return;
            }

            // The store should now have all images
            int numImages = testStore.getNumImages();
            studio_.logs().logMessage("Deskew Explore: test store has " + numImages + " images");

            if (numImages == 0) {
               studio_.logs().showError("Test acquisition produced no images.");
               try {
                  testStore.freeze();
               } catch (IOException ignored) {
                  studio_.logs().logError("Ignoring IO Exception in DeskewExploreManager");
               }
               testStore.close();
               return;
            }

            // Process through our own deskew to get XY projection
            studio_.logs().logMessage("Deskew Explore: calling processStackThroughDeskew");
            Image projectedImage = processStackThroughDeskew(testStore);
            studio_.logs().logMessage("Deskew Explore: processStackThroughDeskew returned: "
                    + (projectedImage == null ? "null" : projectedImage.getWidth() + "x"
                     + projectedImage.getHeight()));

            if (projectedImage == null) {
               studio_.logs().showError("Deskew processing failed.");
               try {
                  testStore.freeze();
               } catch (IOException ignored) {
                  studio_.logs().logError("Ignoring IO Exception in DeskewExploreManager");
               }
               testStore.close();
               return;
            }

            // If this is the first acquisition, update tile dimensions
            if (projectedWidth_ < 0) {
               // Store the pre-rotation dimensions so updateTileDimensionsForRotation
               // can correctly swap them when rotation is 90° or 270°.
               int rotateDegrees = frame_.getSettings().getInteger(
                       DeskewFrame.EXPLORE_ROTATE, 0);
               if (rotateDegrees == 90 || rotateDegrees == 270) {
                  projectedWidth_ = projectedImage.getHeight();
                  projectedHeight_ = projectedImage.getWidth();
               } else {
                  projectedWidth_ = projectedImage.getWidth();
                  projectedHeight_ = projectedImage.getHeight();
               }
               updateTileDimensionsForRotation();
               studio_.logs().logMessage("Deskew Explore: projected dimensions = "
                       + projectedWidth_ + " x " + projectedHeight_);
            }

            // Store the projected image at the tile position
            storeProjectedImage(projectedImage, row, col);

            // Mark tile as acquired
            dataSource_.markTileAcquired(row, col);

            // Notify viewer of new image
            if (displayExecutor_ != null && viewer_ != null) {
               final int tileRow = row;
               final int tileCol = col;
               final Image tileImage = projectedImage;
               displayExecutor_.submit(() -> {
                  HashMap<String, Object> displayAxes = new HashMap<>();
                  displayAxes.put("row", tileRow);
                  displayAxes.put("column", tileCol);
                  if (mm2DataProvider_ != null) {
                     mm2DataProvider_.newImageArrived(tileImage, displayAxes);
                  }
                  if (mm2Viewer_ != null) {
                     mm2Viewer_.newImageArrived(tileImage);
                  }
                  viewer_.newImageArrived(displayAxes);
                  viewer_.update();
                  studio_.logs().logMessage("Deskew Explore: viewer notified of new image");
               });
            }

            // Clean up test store - freeze first to prevent "save" dialogs
            try {
               testStore.freeze();
            } catch (IOException ignored) {
               studio_.logs().logError("Ignoring IO Exception in DeskewExploreManager");
            }
            testStore.close();

            studio_.logs().logMessage("Deskew Explore: tile acquired at row=" + row + ", col="
                     + col);

         } catch (Exception e) {
            studio_.logs().logError(e, "Deskew Explore: error acquiring tile");
         }
      });
   }

   /**
    * Acquires multiple tiles sequentially, moving the stage between positions.
    * Each tile position is calculated relative to the initial stage position when explore started.
    *
    * @param tiles List of tile positions as (row, col) Points
    */
   public void acquireMultipleTiles(List<Point> tiles) {
      if (!exploring_ || acquisitionExecutor_ == null || tiles.isEmpty()) {
         return;
      }

      acquisitionExecutor_.submit(() -> {
         dataSource_.setAcquisitionInProgress(true);
         redrawOverlay();

         try {
            studio_.logs().logMessage("Deskew Explore: starting multi-tile acquisition of "
                    + tiles.size() + " tiles");

            // Get the projected tile dimensions (use estimated if not yet determined)
            int tileWidth = projectedWidth_ > 0 ? projectedWidth_ : estimatedTileWidth_;
            int tileHeight = projectedHeight_ > 0 ? projectedHeight_ : estimatedTileHeight_;

            // Calculate the stage movement per tile in microns
            // Note: tile coordinates are in projected pixel space
            double tileWidthUm = tileWidth * pixelSizeUm_;
            double tileHeightUm = tileHeight * pixelSizeUm_;

            studio_.logs().logMessage("Deskew Explore: tile size = " + tileWidth + "x" + tileHeight
                    + " pixels, " + tileWidthUm + "x" + tileHeightUm + " um");

            for (int i = 0; i < tiles.size(); i++) {
               Point tile = tiles.get(i);
               int row = tile.x;
               int col = tile.y;

               studio_.logs().logMessage("Deskew Explore: acquiring tile " + (i + 1) + "/"
                        + tiles.size()
                       + " at row=" + row + ", col=" + col);

               // Calculate target stage position relative to initial position
               // Tile (0,0) is at the initial position
               // Positive col -> move stage in +X direction
               // Positive row -> move stage in +Y direction
               double targetX = initialStageX_ + col * tileWidthUm;
               double targetY = initialStageY_ + row * tileHeightUm;

               // Move stage to target position
               studio_.logs().logMessage("Deskew Explore: moving stage to ("
                       + targetX + ", " + targetY + ")");
               studio_.core().setXYPosition(targetX, targetY);
               studio_.core().waitForDevice(studio_.core().getXYStageDevice());

               // Brief settle time after stage move. Most stages report
               // "ready" before vibrations fully dampen; 100ms is a
               // conservative default for piezo and motorized stages.
               Thread.sleep(100);

               // Acquire this tile (inline the acquisition logic to avoid executor issues)
               acquireSingleTileBlocking(row, col);
            }

            studio_.logs().logMessage("Deskew Explore: multi-tile acquisition complete");

         } catch (Exception e) {
            studio_.logs().logError(e, "Deskew Explore: error during multi-tile acquisition");
         } finally {
            dataSource_.setAcquisitionInProgress(false);
         }
      });
   }

   /**
    * Acquires a single tile synchronously (blocking).
    * This is called from within the acquisition executor.
    */
   private void acquireSingleTileBlocking(int row, int col) {
      try {
         // Get Z settings from MDA
         SequenceSettings settings = studio_.acquisitions().getAcquisitionSettings();
         SequenceSettings.Builder sb = settings.copyBuilder();
         sb.useFrames(false)
                 .usePositionList(false)
                 .save(false)
                 .shouldDisplayImages(false)
                 .isTestAcquisition(true);

         if (!settings.useSlices()) {
            studio_.logs().showError("Deskew Explore requires Z-stack acquisition settings.");
            return;
         }

         SequenceSettings acqSettings = sb.build();

         // Set explore mode flag so DeskewFactory creates a pass-through processor
         Datastore testStore;
         frame_.getMutableSettings().putBoolean(DeskewFrame.EXPLORE_MODE, true);
         deskewFactory_.setSettings(frame_.getSettings());
         try {
            // Run acquisition in blocking mode
            testStore = studio_.acquisitions().runAcquisitionWithSettings(
                    acqSettings, true);
         } finally {
            // Always reset explore mode flag, even if acquisition throws
            frame_.getMutableSettings().putBoolean(DeskewFrame.EXPLORE_MODE, false);
            deskewFactory_.setSettings(frame_.getSettings());
         }

         if (testStore == null) {
            studio_.logs().showError("Test acquisition failed at row=" + row + ", col=" + col);
            return;
         }

         int numImages = testStore.getNumImages();
         studio_.logs().logMessage("Deskew Explore: test store has " + numImages + " images");

         if (numImages == 0) {
            studio_.logs().showError("Test acquisition produced no images at row=" + row + ", col="
                     + col);
            try {
               testStore.freeze();
            } catch (IOException ignored) {
               studio_.logs().logError("IOException ignored in DeskewExploreManager");
            }
            testStore.close();
            return;
         }

         // Process through deskew to get XY projection
         Image projectedImage = processStackThroughDeskew(testStore);

         if (projectedImage == null) {
            studio_.logs().showError("Deskew processing failed at row=" + row + ", col=" + col);
            try {
               testStore.freeze();
            } catch (IOException ignored) {
               studio_.logs().logError("IOException ignored in DeskewExploreManager");
            }
            testStore.close();
            return;
         }

         // Update tile dimensions if this is first acquisition
         if (projectedWidth_ < 0) {
            projectedWidth_ = projectedImage.getWidth();
            projectedHeight_ = projectedImage.getHeight();
            dataSource_.setTileDimensions(projectedWidth_, projectedHeight_);
            studio_.logs().logMessage("Deskew Explore: projected dimensions = "
                    + projectedWidth_ + " x " + projectedHeight_);
         }

         // Store the projected image
         storeProjectedImage(projectedImage, row, col);

         // Mark tile as acquired
         dataSource_.markTileAcquired(row, col);

         // Notify viewer
         if (displayExecutor_ != null && viewer_ != null) {
            final int tileRow = row;
            final int tileCol = col;
            final Image tileImage = projectedImage;
            displayExecutor_.submit(() -> {
               HashMap<String, Object> displayAxes = new HashMap<>();
               displayAxes.put("row", tileRow);
               displayAxes.put("column", tileCol);
               if (mm2DataProvider_ != null) {
                  mm2DataProvider_.newImageArrived(tileImage, displayAxes);
               }
               if (mm2Viewer_ != null) {
                  mm2Viewer_.newImageArrived(tileImage);
               }
               viewer_.newImageArrived(displayAxes);
               viewer_.update();
            });
         }

         // Clean up
         try {
            testStore.freeze();
         } catch (IOException ignored) {
            studio_.logs().logError("IOException ignored in DeskewExploreManager");
         }
         testStore.close();

         studio_.logs().logMessage("Deskew Explore: tile acquired at row=" + row + ", col=" + col);

      } catch (Exception e) {
         studio_.logs().logError(e, "Deskew Explore: error acquiring tile at row=" + row + ", col="
                  + col);
      }
   }

   /**
    * Processes a Z-stack through the deskew pipeline to produce an XY projection.
    */
   private Image processStackThroughDeskew(Datastore source) {
      try {
         studio_.logs().logMessage("Deskew Explore: starting deskew processing");

         // Get deskew settings from frame
         double theta = Math.toRadians(NumberUtils.displayStringToDouble(
                 frame_.getSettings().getString(DeskewFrame.DEGREE, "20.0")));
         studio_.logs().logMessage("Deskew Explore: theta = " + Math.toDegrees(theta) + " degrees");

         SummaryMetadata summaryMetadata = source.getSummaryMetadata();
         int nSlices = summaryMetadata.getIntendedDimensions().getZ();
         studio_.logs().logMessage("Deskew Explore: nSlices from metadata = " + nSlices);

         // Get first image for dimensions - use the actual first coords, don't modify z
         Coords firstCoords = source.getUnorderedImageCoords().iterator().next();
         studio_.logs().logMessage("Deskew Explore: firstCoords = " + firstCoords);
         Image firstImage = source.getImage(firstCoords);
         studio_.logs().logMessage("Deskew Explore: firstImage size = "
                 + firstImage.getWidth() + "x" + firstImage.getHeight());

         double pixelSizeUm = firstImage.getMetadata().getPixelSizeUm();
         double zStepUm = summaryMetadata.getZStepUm();
         studio_.logs().logMessage("Deskew Explore: pixelSize = " + pixelSizeUm
                 + ", zStep = " + zStepUm);

         // Create resampler for XY projection only
         studio_.logs().logMessage("Deskew Explore: creating StackResampler");
         StackResampler resampler;
         try {
            resampler = new StackResampler(
                    StackResampler.YX_PROJECTION,
                    true, // Max projection
                    theta,
                    pixelSizeUm,
                    zStepUm,
                    nSlices,
                    firstImage.getHeight(),
                    firstImage.getWidth());
            studio_.logs().logMessage("Deskew Explore: StackResampler created");
         } catch (Exception e) {
            studio_.logs().logError(e, "Deskew Explore: failed to create StackResampler");
            throw e;
         }

         resampler.initializeProjections();
         studio_.logs().logMessage("Deskew Explore: projections initialized");

         // Start processing in background thread - it will wait for images
         Runnable processing = resampler.startStackProcessing();
         Thread processingThread = new Thread(processing, "Deskew Explore Processing");
         processingThread.start();
         studio_.logs().logMessage("Deskew Explore: stack processing thread started");

         // Feed all Z slices to the resampler
         Iterable<Coords> allCoords = source.getUnorderedImageCoords();
         List<Image> zStack = new ArrayList<>();
         for (Coords c : allCoords) {
            zStack.add(source.getImage(c));
         }
         studio_.logs().logMessage("Deskew Explore: loaded " + zStack.size() + " images");

         // Sort by Z
         zStack.sort((a, b) -> Integer.compare(a.getCoords().getZ(), b.getCoords().getZ()));

         for (int z = 0; z < zStack.size(); z++) {
            Image img = zStack.get(z);
            resampler.addToProcessImageQueue((short[]) img.getRawPixels(), z);
         }
         studio_.logs().logMessage("Deskew Explore: all images added to queue");

         // Wait for processing to complete
         processingThread.join();
         studio_.logs().logMessage("Deskew Explore: processing thread finished");

         resampler.finalizeProjections();
         studio_.logs().logMessage("Deskew Explore: projections finalized");

         // Get the YX projection
         short[] projectionPixels = resampler.getYXProjection();
         int width = resampler.getResampledShapeX();
         int height = resampler.getResampledShapeY();
         studio_.logs().logMessage("Deskew Explore: projection size = " + width + "x" + height);

         // Apply optional mirror and rotate transformations
         boolean doMirror = frame_.getSettings().getBoolean(DeskewFrame.EXPLORE_MIRROR, false);
         int rotateDegrees = frame_.getSettings().getInteger(DeskewFrame.EXPLORE_ROTATE, 0);

         if (doMirror) {
            for (int y = 0; y < height; y++) {
               int rowStart = y * width;
               for (int x = 0; x < width / 2; x++) {
                  short tmp = projectionPixels[rowStart + x];
                  projectionPixels[rowStart + x] = projectionPixels[rowStart + width - 1 - x];
                  projectionPixels[rowStart + width - 1 - x] = tmp;
               }
            }
         }
         if (rotateDegrees == 90) {
            // Rotate 90° clockwise: new[x][height-1-y] = old[y][x]
            short[] rotated = new short[projectionPixels.length];
            int newWidth = height;
            int newHeight = width;
            for (int y = 0; y < height; y++) {
               for (int x = 0; x < width; x++) {
                  rotated[x * newWidth + (newWidth - 1 - y)] = projectionPixels[y * width + x];
               }
            }
            projectionPixels = rotated;
            width = newWidth;
            height = newHeight;
         } else if (rotateDegrees == 180) {
            for (int i = 0, j = projectionPixels.length - 1; i < j; i++, j--) {
               short tmp = projectionPixels[i];
               projectionPixels[i] = projectionPixels[j];
               projectionPixels[j] = tmp;
            }
         } else if (rotateDegrees == 270) {
            // Rotate 270° clockwise: new[height-1-x][y] = old[y][x]
            short[] rotated = new short[projectionPixels.length];
            int newWidth = height;
            int newHeight = width;
            for (int y = 0; y < height; y++) {
               for (int x = 0; x < width; x++) {
                  rotated[(newHeight - 1 - x) * newWidth + y] = projectionPixels[y * width + x];
               }
            }
            projectionPixels = rotated;
            width = newWidth;
            height = newHeight;
         }

         // Create output image
         Image result = studio_.data().createImage(
                 projectionPixels,
                 width,
                 height,
                 2, // bytes per pixel for 16-bit
                 1, // number of components
                 firstImage.getCoords().copyBuilder().z(0).build(),
                 firstImage.getMetadata());
         studio_.logs().logMessage("Deskew Explore: created result image");
         return result;

      } catch (ParseException e) {
         studio_.logs().logError(e, "Failed to parse deskew angle");
         return null;
      } catch (IOException e) {
         studio_.logs().logError(e, "Failed to read image from datastore");
         return null;
      } catch (Exception e) {
         studio_.logs().logError(e, "Unexpected error in deskew processing");
         return null;
      }
   }

   /**
    * Stores a projected image at the specified tile position.
    */
   private void storeProjectedImage(Image image, int row, int col) {
      if (storage_ == null) {
         studio_.logs().logError("Deskew Explore: storage is null, cannot store image");
         return;
      }

      try {
         studio_.logs().logMessage("Deskew Explore: storing image " + image.getWidth() + "x"
                 + image.getHeight() + " at row=" + row + ", col=" + col);

         // Create image metadata with row/col axes
         JSONObject tags = new JSONObject();
         tags.put("ElapsedTime-ms", System.currentTimeMillis());
         tags.put("Width", image.getWidth());
         tags.put("Height", image.getHeight());
         tags.put("BitDepth", bitDepth_);
         tags.put("PixelType", bitDepth_ <= 8 ? "GRAY8" : "GRAY16");

         // Set up axes using AcqEngMetadata
         AcqEngMetadata.createAxes(tags);
         AcqEngMetadata.setAxisPosition(tags, "row", row);
         AcqEngMetadata.setAxisPosition(tags, "column", col);

         HashMap<String, Object> axes = AcqEngMetadata.getAxes(tags);
         studio_.logs().logMessage("Deskew Explore: axes = " + axes);

         // Store with multi-resolution pyramid
         Future<?> future = storage_.putImageMultiRes(
                 image.getRawPixels(),
                 tags,
                 axes,
                 false, // not RGB
                 bitDepth_,
                 image.getHeight(),
                 image.getWidth());

         // Wait for storage to complete
         future.get();
         // Pre-build pyramid levels for smooth zoom-out display.
         // increaseMaxResolutionLevel is non-blocking (just enqueues work).
         storage_.increaseMaxResolutionLevel(4);
         if (dataSource_ != null) {
            dataSource_.invalidateImageKeysCache();
         }

         studio_.logs().logMessage("Deskew Explore: image stored, storage now has "
                 + storage_.getAxesSet().size() + " images");

      } catch (Exception e) {
         studio_.logs().logError(e, "Failed to store projected image");
      }
   }

   // ===================== Viewer interaction methods =====================

   public void pan(int dx, int dy) {
      if (viewer_ != null) {
         viewer_.pan(dx, dy);
      }
   }

   public void zoom(double factor, Point mouseLocation) {
      if (viewer_ != null) {
         viewer_.zoom(factor, mouseLocation);
      }
   }

   public Point2D.Double getViewOffset() {
      if (viewer_ != null) {
         return viewer_.getViewOffset();
      }
      return new Point2D.Double(0, 0);
   }

   public double getMagnification() {
      if (viewer_ != null) {
         return viewer_.getMagnification();
      }
      return 1.0;
   }

   public void setOverlay(Overlay overlay) {
      if (viewer_ != null) {
         viewer_.setOverlay(overlay);
      }
   }

   /**
    * Recomputes the tile dimensions shown in the overlay based on the current
    * rotation setting, then redraws the overlay. Call this whenever the
    * rotation setting changes (or after the first image is acquired).
    * Uses pre-rotation projectedWidth_/Height_ (or estimated dims if not yet
    * acquired) and swaps them for 90°/270° rotations.
    */
   public void updateTileDimensionsForRotation() {
      int baseW = projectedWidth_ > 0 ? projectedWidth_ : estimatedTileWidth_;
      int baseH = projectedHeight_ > 0 ? projectedHeight_ : estimatedTileHeight_;
      int rotateDegrees = frame_.getSettings().getInteger(DeskewFrame.EXPLORE_ROTATE, 0);
      if (rotateDegrees == 90 || rotateDegrees == 270) {
         dataSource_.setTileDimensions(baseH, baseW);
      } else {
         dataSource_.setTileDimensions(baseW, baseH);
      }
      redrawOverlay();
   }

   public void redrawOverlay() {
      if (viewer_ != null) {
         viewer_.redrawOverlay();
      }
   }

   public boolean isExploring() {
      return exploring_;
   }

   /**
    * Moves the stage so that the given pixel position (in the projected/tile coordinate
    * space) becomes the center of the field of view.
    *
    * <p>The coordinate system is:
    * - Tile (0,0) has its center at pixel (tileWidth/2, tileHeight/2)
    * - Tile (0,0) was acquired with stage at (initialStageX_, initialStageY_)
    * - So pixel (tileWidth/2, tileHeight/2) corresponds to stage (initialStageX_, initialStageY_)
    *
    * @param pixelX X coordinate in full-resolution projected pixel space
    * @param pixelY Y coordinate in full-resolution projected pixel space
    */
   public void moveStageToPixelPosition(double pixelX, double pixelY) {
      if (!exploring_ || acquisitionExecutor_ == null) {
         return;
      }

      acquisitionExecutor_.submit(() -> {
         try {
            // Get tile dimensions
            int tileWidth = projectedWidth_ > 0 ? projectedWidth_ : estimatedTileWidth_;
            int tileHeight = projectedHeight_ > 0 ? projectedHeight_ : estimatedTileHeight_;

            // The center of tile (0,0) is at pixel (tileWidth/2, tileHeight/2)
            // and corresponds to stage (initialStageX_, initialStageY_)
            // So we need to offset by half a tile
            double offsetPixelX = pixelX - tileWidth / 2.0;
            double offsetPixelY = pixelY - tileHeight / 2.0;

            double targetX = initialStageX_ + offsetPixelX * pixelSizeUm_;
            double targetY = initialStageY_ + offsetPixelY * pixelSizeUm_;

            studio_.logs().logMessage("Deskew Explore: moving stage to pixel ("
                    + pixelX + ", " + pixelY + ") -> stage ("
                    + targetX + ", " + targetY + ")");

            studio_.core().setXYPosition(targetX, targetY);
            studio_.core().waitForDevice(studio_.core().getXYStageDevice());

         } catch (Exception e) {
            studio_.logs().logError(e, "Deskew Explore: error moving stage");
         }
      });
   }
}
