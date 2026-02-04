package org.micromanager.deskew;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.lightsheet.StackResampler;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.ndviewer.api.NDViewerAPI;
import org.micromanager.ndviewer.main.NDViewer;
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
   private NDTiffStorage storage_;
   private DeskewExploreDataSource dataSource_;
   private ExecutorService displayExecutor_;
   private ExecutorService acquisitionExecutor_;

   // Projected image dimensions (unknown until first deskew)
   private int projectedWidth_ = -1;
   private int projectedHeight_ = -1;
   private int bitDepth_ = 16;
   // Estimated tile dimensions based on camera size (for first click)
   private int estimatedTileWidth_ = 512;
   private int estimatedTileHeight_ = 512;

   // State tracking
   private volatile boolean exploring_ = false;
   private String storageDir_;
   private String acqName_;

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

         // Create executors
         displayExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Deskew Explore viewer communication"));
         acquisitionExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Deskew Explore acquisition"));

         // Create data source
         dataSource_ = new DeskewExploreDataSource(this);

         // Create temporary storage directory
         File tempDir = File.createTempFile("deskew_explore_", "");
         tempDir.delete();
         tempDir.mkdir();
         storageDir_ = tempDir.getAbsolutePath();
         acqName_ = "DeskewExplore_" + System.currentTimeMillis();

         // Get camera info for initial setup
         int imageWidth = (int) studio_.core().getImageWidth();
         int imageHeight = (int) studio_.core().getImageHeight();
         bitDepth_ = (int) studio_.core().getImageBitDepth();
         double pixelSizeUm = studio_.core().getPixelSizeUm();
         if (pixelSizeUm <= 0) {
            pixelSizeUm = 1.0;
         }

         // Estimate tile dimensions based on camera size
         // (actual size will be determined after first deskew)
         estimatedTileWidth_ = imageWidth;
         estimatedTileHeight_ = imageHeight;
         // Set initial estimated dimensions on data source for first-click support
         dataSource_.setTileDimensions(estimatedTileWidth_, estimatedTileHeight_);

         // Create summary metadata for storage and viewer
         JSONObject summaryMetadata = new JSONObject();
         summaryMetadata.put("Width", imageWidth);
         summaryMetadata.put("Height", imageHeight);
         summaryMetadata.put("PixelSize_um", pixelSizeUm);
         summaryMetadata.put("BitDepth", bitDepth_);
         summaryMetadata.put("PixelType", bitDepth_ <= 8 ? "GRAY8" : "GRAY16");
         summaryMetadata.put("GridPixelOverlapX", 0);
         summaryMetadata.put("GridPixelOverlapY", 0);

         // Initialize storage immediately so NDViewer has something to work with
         storage_ = new NDTiffStorage(storageDir_, acqName_, summaryMetadata,
                 0, 0, true, null, SAVING_QUEUE_SIZE, null, true);
         dataSource_.setStorage(storage_);

         // Create NDViewer
         viewer_ = new NDViewer(dataSource_, dataSource_, summaryMetadata, pixelSizeUm, false);
         viewer_.setWindowTitle("Deskew Explore - Right-click to select, Left-click to acquire");

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
    * Stops the explore session and cleans up resources.
    */
   public void stopExplore() {
      exploring_ = false;

      if (displayExecutor_ != null) {
         displayExecutor_.shutdown();
         displayExecutor_ = null;
      }

      if (acquisitionExecutor_ != null) {
         acquisitionExecutor_.shutdown();
         acquisitionExecutor_ = null;
      }

      if (storage_ != null) {
         if (!storage_.isFinished()) {
            storage_.finishedWriting();
         }
         storage_.close();
         storage_ = null;
      }

      if (dataSource_ != null) {
         dataSource_.setFinished(true);
         dataSource_ = null;
      }

      viewer_ = null;
   }

   /**
    * Called when the viewer is closed by the user.
    */
   public void onViewerClosed() {
      stopExplore();
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
            studio_.logs().logMessage("Deskew Explore: acquiring tile at row=" + row + ", col=" + col);

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
            frame_.getMutableSettings().putBoolean(DeskewFrame.EXPLORE_MODE, true);
            deskewFactory_.setSettings(frame_.getSettings());

            // Run acquisition in blocking mode - this ensures completion
            // shouldDisplayImages(false) prevents the normal acquisition display
            // and any pipeline processors from creating display windows
            Datastore testStore = studio_.acquisitions().runAcquisitionWithSettings(
                    acqSettings, true);  // blocking = true

            // Reset explore mode flag
            frame_.getMutableSettings().putBoolean(DeskewFrame.EXPLORE_MODE, false);
            deskewFactory_.setSettings(frame_.getSettings());

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
               }
               testStore.close();
               return;
            }

            // Process through our own deskew to get XY projection
            studio_.logs().logMessage("Deskew Explore: calling processStackThroughDeskew");
            Image projectedImage = processStackThroughDeskew(testStore);
            studio_.logs().logMessage("Deskew Explore: processStackThroughDeskew returned: "
                    + (projectedImage == null ? "null" : projectedImage.getWidth() + "x" + projectedImage.getHeight()));

            if (projectedImage == null) {
               studio_.logs().showError("Deskew processing failed.");
               try {
                  testStore.freeze();
               } catch (IOException ignored) {
               }
               testStore.close();
               return;
            }

            // If this is the first acquisition, update tile dimensions
            if (projectedWidth_ < 0) {
               projectedWidth_ = projectedImage.getWidth();
               projectedHeight_ = projectedImage.getHeight();
               dataSource_.setTileDimensions(projectedWidth_, projectedHeight_);
               studio_.logs().logMessage("Deskew Explore: projected dimensions = "
                       + projectedWidth_ + " x " + projectedHeight_);
            }

            // Store the projected image at the tile position
            storeProjectedImage(projectedImage, row, col);

            // Mark tile as acquired
            dataSource_.markTileAcquired(row, col);

            // Notify viewer of new image
            if (displayExecutor_ != null && viewer_ != null) {
               displayExecutor_.submit(() -> {
                  HashMap<String, Object> displayAxes = new HashMap<>();
                  viewer_.newImageArrived(displayAxes);
                  viewer_.update();
                  studio_.logs().logMessage("Deskew Explore: viewer notified of new image");
               });
            }

            // Clean up test store - freeze first to prevent "save" dialogs
            try {
               testStore.freeze();
            } catch (IOException ignored) {
            }
            testStore.close();

            studio_.logs().logMessage("Deskew Explore: tile acquired at row=" + row + ", col=" + col);

         } catch (Exception e) {
            studio_.logs().logError(e, "Deskew Explore: error acquiring tile");
         }
      });
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

   public void redrawOverlay() {
      if (viewer_ != null) {
         viewer_.redrawOverlay();
      }
   }

   public boolean isExploring() {
      return exploring_;
   }
}
