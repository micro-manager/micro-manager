package org.micromanager.explorer;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.RememberedDisplaySettings;
import org.micromanager.events.PixelSizeAffineChangedEvent;
import org.micromanager.events.PixelSizeChangedEvent;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.imageprocessing.ImageTransformUtils;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.ColorPalettes;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.tileddataprovider.NDTiffProviderAdapter;
import org.micromanager.tileddataviewer.TiledDataViewerAPI;
import org.micromanager.tileddataviewer.TiledDataViewerAcqInterface;
import org.micromanager.tileddataviewer.TiledDataViewerDataProviderAPI;
import org.micromanager.tileddataviewer.TiledDataViewerDataViewerAPI;
import org.micromanager.tileddataviewer.TiledDataViewerFactory;

/**
 * Manages the Explorer session.
 * Coordinates between the GUI, NDViewer, storage, and acquisition.
 *
 * <p>Key difference from DeskewExploreManager: images pass through the user's active
 * MM application pipeline (applied automatically by runAcquisitionWithSettings) instead
 * of a deskew/projection step. The plugin itself uses a PassThroughProcessor so it does
 * not add a second processing step.
 *
 * <p>Two separate size concepts are tracked:
 * <ul>
 *   <li>{@code stageTileWidthUm_}/{@code stageTileHeightUm_} — physical stage step in microns
 *       (camera FOV × pixel size, fixed for the session).</li>
 *   <li>{@code tileWidth_}/{@code tileHeight_} (pixels) — display/storage tile size derived
 *       from the pipeline-processed image; set after the first tile is acquired.</li>
 * </ul>
 */
public class ExplorerManager {

   private static final int SAVING_QUEUE_SIZE = 30;
   private static final String MM_DISPLAY_SETTINGS_FILE = "mm_display_settings.json";
   private static final String VIEW_STATE_FILE = "view_state.json";
   private static final java.time.format.DateTimeFormatter RECEIVED_TIME_FORMAT =
         java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS Z")
               .withZone(java.time.ZoneId.systemDefault());

   private final Studio studio_;
   private final ExplorerFrame frame_;

   private TiledDataViewerAPI viewer_;
   private TiledDataViewerDataViewerAPI mm2Viewer_;
   private TiledDataViewerDataProviderAPI mm2DataProvider_;
   private NDTiffStorage storage_;
   private ExplorerDataSource dataSource_;
   private ExecutorService displayExecutor_;
   private ExecutorService acquisitionExecutor_;
   private ScheduledExecutorService stagePollingExecutor_;

   // Tracks which per-image metadata warnings have already been logged (once per session)
   private final Set<String> loggedMetadataWarnings_ = new HashSet<>();

   // Display tile dimensions (from pipeline output; set after first tile)
   private int tileWidth_ = -1;
   private int tileHeight_ = -1;
   private int bitDepth_ = 16;

   // Stage step size in microns (camera FOV; fixed for session)
   private double stageTileWidthUm_ = 0;
   private double stageTileHeightUm_ = 0;
   // Camera pixel dimensions used for initial tile-grid estimate
   private int cameraWidth_ = 512;
   private int cameraHeight_ = 512;

   // State tracking
   private volatile boolean exploring_ = false;
   private volatile boolean viewerClosing_ = false;
   private volatile boolean shutdownInProgress_ = false;
   private boolean loadedData_ = false;
   private DisplaySettings pendingMMDisplaySettings_ = null;
   private JSONObject pendingViewState_ = null;
   private String storageDir_;
   private String acqName_;

   private double initialStageX_ = 0;
   private double initialStageY_ = 0;
   private double pixelSizeUm_ = 1.0;
   // Affine: camera-pixel delta → stage-micron delta. Null = use scalar fallback.
   private AffineTransform pixelSizeAffine_ = null;
   // Pre-computed inverse: stage-micron delta → camera-pixel delta.
   private AffineTransform pixelSizeAffineInverse_ = null;
   // Session-start affine and its inverse, kept fixed so position calculations stay in
   // the tile-grid coordinate system even after a pixel-size change.
   private AffineTransform initialPixelSizeAffine_ = null;
   private AffineTransform initialPixelSizeAffineInverse_ = null;
   private double overlapPercentage_ = 10.0;
   private volatile boolean acquisitionInterrupted_ = false;
   private final AtomicInteger pendingBatches_ = new AtomicInteger(0);
   // True when pixel size or image dimensions differ from session initial values.
   // Blocks new tile acquisition until settings return to initial values.
   private volatile boolean settingsMismatch_ = false;
   // Active mismatch alert; dismissed when settings return to initial values.
   private org.micromanager.alerts.Alert mismatchAlert_ = null;
   // Initial values recorded at session start (for mismatch detection).
   private double initialPixelSizeUm_ = 1.0;
   private int initialCameraWidth_ = 512;
   private int initialCameraHeight_ = 512;
   private boolean isRGB_ = false;

   // Camera orientation / Image Flipper correction
   private int sessionCorrectionRotation_ = 0;    // 0/90/180/270
   private boolean sessionCorrectionMirror_ = false; // horizontal mirror
   private boolean flipperInPipeline_ = false;    // Flipper already corrected images
   // Raw (pre-correction) affines so Flipper override can restart from them
   private AffineTransform rawPixelSizeAffine_ = null;
   private AffineTransform rawInitialPixelSizeAffine_ = null;

   public ExplorerManager(Studio studio, ExplorerFrame frame) {
      studio_ = studio;
      frame_ = frame;
   }

   /**
    * Starts the explore session.
    */
   public void startExplore() {
      if (exploring_) {
         studio_.logs().showMessage("Explorer session already running.");
         return;
      }

      try {
         exploring_ = true;
         viewerClosing_ = false;

         displayExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Explorer viewer communication"));
         acquisitionExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Explorer acquisition"));
         stagePollingExecutor_ = Executors.newSingleThreadScheduledExecutor(r ->
                 new Thread(r, "Explorer stage polling"));

         dataSource_ = new ExplorerDataSource(this);

         String tmpPathSetting = frame_.getSettings().getString(
                 ExplorerFrame.EXPLORE_TMP_PATH, "").trim();
         Path tempDir;
         if (tmpPathSetting.isEmpty()) {
            tempDir = Files.createTempDirectory("explorer_");
         } else {
            Path base = new File(tmpPathSetting).toPath();
            Files.createDirectories(base);
            tempDir = Files.createTempDirectory(base, "explorer_");
         }
         storageDir_ = tempDir.toFile().getAbsolutePath();
         acqName_ = "Explorer_" + LocalDateTime.now()
                 .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));

         cameraWidth_ = (int) studio_.core().getImageWidth();
         cameraHeight_ = (int) studio_.core().getImageHeight();
         bitDepth_ = (int) studio_.core().getImageBitDepth();
         try {
            isRGB_ = studio_.core().getNumberOfComponents() > 1;
         } catch (Exception e) {
            studio_.logs().logError(e, "Explorer: could not determine camera component count");
            isRGB_ = false;
         }
         pixelSizeUm_ = studio_.core().getPixelSizeUm();
         if (pixelSizeUm_ <= 0) {
            pixelSizeUm_ = 1.0;
         }
         initialPixelSizeUm_ = pixelSizeUm_;
         initialCameraWidth_ = cameraWidth_;
         initialCameraHeight_ = cameraHeight_;
         settingsMismatch_ = false;

         initialStageX_ = studio_.core().getXPosition();
         initialStageY_ = studio_.core().getYPosition();
         overlapPercentage_ = studio_.profile().getSettings(ExplorerFrame.class)
                 .getInteger(ExplorerFrame.EXPLORE_OVERLAP_PERCENT, 10);

         // Stage step in microns = camera FOV × pixel size
         stageTileWidthUm_ = cameraWidth_ * pixelSizeUm_;
         stageTileHeightUm_ = cameraHeight_ * pixelSizeUm_;
         loadPixelSizeAffine();
         initialPixelSizeAffine_ = pixelSizeAffine_;
         initialPixelSizeAffineInverse_ = pixelSizeAffineInverse_;

         // Keep uncorrected copies so the Flipper-detection path can restart cleanly.
         rawPixelSizeAffine_        = pixelSizeAffine_;
         rawInitialPixelSizeAffine_ = pixelSizeAffine_;
         sessionCorrectionRotation_ = 0;
         sessionCorrectionMirror_   = false;
         flipperInPipeline_         = false;

         // Issue 1: detect camera mirror from the raw affine (det < 0 → mirror encoded).
         // Rotation ≠ 0 is only logged; use the Image Flipper plugin for rotation correction.
         if (pixelSizeAffine_ != null) {
            int[] corr = ImageTransformUtils.correctionFromAffine(pixelSizeAffine_);
            if (corr != null && (corr[0] != 0 || corr[1] != 0)) {
               if (corr[0] != 0) {
                  studio_.logs().logMessage("Explorer: camera rotation detected ("
                        + corr[0] + "°); use the Image Flipper plugin for rotation correction");
               } else {
                  sessionCorrectionMirror_ = (corr[1] != 0);
                  if (sessionCorrectionMirror_) {
                     applyOrientationCorrectionToAffines(0, true);
                     studio_.logs().logMessage("Explorer: camera mirror detected;"
                           + " mirror correction applied to canvas affine");
                  }
               }
            }
         }

         // Initial tile-grid estimate uses camera dimensions (corrected after first acq)
         dataSource_.setTileDimensions(cameraWidth_, cameraHeight_);

         // Build summary metadata for storage
         SummaryMetadata summaryMetadata = buildSummaryMetadata(cameraWidth_, cameraHeight_);
         JSONObject summaryMetadataJson = summaryMetadataToJSON(summaryMetadata);

         int overlapX = (int) Math.round(cameraWidth_ * overlapPercentage_ / 100.0);
         int overlapY = (int) Math.round(cameraHeight_ * overlapPercentage_ / 100.0);
         summaryMetadataJson.put("GridPixelOverlapX", overlapX);
         summaryMetadataJson.put("GridPixelOverlapY", overlapY);
         storage_ = new NDTiffStorage(storageDir_, acqName_, summaryMetadataJson,
                 overlapX, overlapY, true, null,
                 SAVING_QUEUE_SIZE, null, true);
         dataSource_.setStorage(storage_);

         mm2DataProvider_ = TiledDataViewerFactory.createDataProvider(
                 studio_.data(), new NDTiffProviderAdapter(storage_), acqName_, summaryMetadata);
         TiledDataViewerAcqInterface acqInterface = createAcqInterface();
         mm2Viewer_ = TiledDataViewerFactory.createDataViewer(
               studio_, dataSource_, acqInterface, mm2DataProvider_,
               summaryMetadataJson, pixelSizeUm_, isRGB_);
         mm2Viewer_.setAccumulateStats(true);

         initDisplaySettings(summaryMetadataJson);

         viewer_ = mm2Viewer_.getTiledDataViewer();
         dataSource_.setViewer(viewer_);
         viewer_.setWindowTitle("Explorer - Right-click/drag to select, "
               + "Left-drag to pan, Left-click to acquire");

         mm2Viewer_.setOverlayerPlugin(dataSource_);
         viewer_.setCustomCanvasMouseListener(dataSource_);

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
         viewer_.setReadZMetadataFunction(tags -> tags.optDouble("ZPositionUm", 0.0));
         viewer_.setViewOffset(0, 0);

         startStagePositionPolling();
         studio_.events().registerForEvents(this);

      } catch (Exception e) {
         studio_.logs().showError(e, "Failed to start Explorer.");
         stopExplore();
      }
   }

   /**
    * Opens an existing NDTiff explorer dataset for viewing.
    */
   public void openExplore(String dir) {
      if (exploring_) {
         studio_.logs().showMessage("Explorer session already running.");
         return;
      }

      try {
         exploring_ = true;
         viewerClosing_ = false;
         loadedData_ = true;

         displayExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Explorer viewer communication"));
         acquisitionExecutor_ = Executors.newSingleThreadExecutor(r ->
                 new Thread(r, "Explorer acquisition"));
         stagePollingExecutor_ = Executors.newSingleThreadScheduledExecutor(r ->
                 new Thread(r, "Explorer stage polling"));

         dataSource_ = new ExplorerDataSource(this);
         dataSource_.setReadOnly(true);

         storage_ = new NDTiffStorage(dir, SAVING_QUEUE_SIZE, null);
         storageDir_ = new File(dir).getParent();
         acqName_ = new File(dir).getName();
         dataSource_.setStorage(storage_);

         JSONObject summaryMetadata = storage_.getSummaryMetadata();
         final int imageWidth = summaryMetadata.optInt("Width", 512);
         final int imageHeight = summaryMetadata.optInt("Height", 512);
         bitDepth_ = summaryMetadata.optInt("BitDepth", 16);
         isRGB_ = "RGB32".equals(summaryMetadata.optString("PixelType", ""));
         pixelSizeUm_ = summaryMetadata.optDouble("PixelSize_um", 1.0);
         if (pixelSizeUm_ <= 0) {
            pixelSizeUm_ = 1.0;
         }

         int overlapX = summaryMetadata.optInt("GridPixelOverlapX", 0);
         if (imageWidth > 0 && overlapX > 0) {
            overlapPercentage_ = (overlapX * 100.0) / imageWidth;
         } else {
            overlapPercentage_ = 0.0;
         }

         initialStageX_ = studio_.core().getXPosition();
         initialStageY_ = studio_.core().getYPosition();

         // Determine tile dimensions from first stored image
         int tw = imageWidth;
         int th = imageHeight;
         for (HashMap<String, Object> axes : storage_.getAxesSet()) {
            EssentialImageMetadata meta = storage_.getEssentialImageMetadata(axes);
            tw = meta.width;
            th = meta.height;
            break;
         }
         tileWidth_ = tw;
         tileHeight_ = th;
         dataSource_.setTileDimensions(tileWidth_, tileHeight_);

         cameraWidth_ = tileWidth_;
         cameraHeight_ = tileHeight_;
         initialPixelSizeUm_ = pixelSizeUm_;
         initialCameraWidth_ = cameraWidth_;
         initialCameraHeight_ = cameraHeight_;
         settingsMismatch_ = false;
         loadPixelSizeAffine();
         initialPixelSizeAffine_ = pixelSizeAffine_;
         initialPixelSizeAffineInverse_ = pixelSizeAffineInverse_;

         // Stage step estimate for a loaded dataset (not used for new acquisitions)
         stageTileWidthUm_ = tileWidth_ * pixelSizeUm_;
         stageTileHeightUm_ = tileHeight_ * pixelSizeUm_;

         for (HashMap<String, Object> axes : storage_.getAxesSet()) {
            Object rowObj = axes.get("row");
            Object colObj = axes.get("column");
            if (rowObj instanceof Number && colObj instanceof Number) {
               dataSource_.markTileAcquired(
                       ((Number) rowObj).intValue(),
                       ((Number) colObj).intValue());
            }
         }

         SummaryMetadata parsedSummaryMetadata = null;
         try {
            parsedSummaryMetadata = DefaultSummaryMetadata.fromPropertyMap(
                  NonPropertyMapJSONFormats.summaryMetadata().fromJSON(summaryMetadata.toString()));
         } catch (Exception e) {
            studio_.logs().logError(e, "Explorer: failed to parse stored summary metadata");
         }

         mm2DataProvider_ = TiledDataViewerFactory.createDataProvider(
                 studio_.data(), new NDTiffProviderAdapter(storage_), acqName_,
                 parsedSummaryMetadata);
         TiledDataViewerAcqInterface acqInterface = createAcqInterface();
         mm2Viewer_ = TiledDataViewerFactory.createDataViewer(
               studio_, dataSource_, acqInterface, mm2DataProvider_,
               summaryMetadata, pixelSizeUm_, isRGB_);
         mm2Viewer_.setAccumulateStats(true);

         // Load saved MM display settings or derive from heuristics
         File mmSettingsFile = new File(dir, MM_DISPLAY_SETTINGS_FILE);
         final DisplaySettings savedMMSettings = mmSettingsFile.canRead()
               ? DefaultDisplaySettings.getSavedDisplaySettings(mmSettingsFile) : null;
         try {
            if (savedMMSettings != null) {
               mm2Viewer_.setDisplaySettings(savedMMSettings);
            } else {
               applyDisplaySettingsHeuristics(summaryMetadata);
            }
         } catch (Exception e) {
            studio_.logs().logError(e, "Failed to initialize DisplaySettings");
         }

         viewer_ = mm2Viewer_.getTiledDataViewer();
         dataSource_.setViewer(viewer_);
         viewer_.setWindowTitle("Explorer - " + acqName_);

         mm2Viewer_.setOverlayerPlugin(dataSource_);
         viewer_.setCustomCanvasMouseListener(dataSource_);

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
         viewer_.setReadZMetadataFunction(tags -> tags.optDouble("ZPositionUm", 0.0));
         viewer_.setViewOffset(0, 0);

         File viewStateFile = new File(dir, VIEW_STATE_FILE);
         final JSONObject savedViewState = loadViewState(viewStateFile);

         if (displayExecutor_ != null && viewer_ != null) {
            displayExecutor_.submit(() -> {
               try {
                  List<Image> seedImages = new ArrayList<>();
                  List<HashMap<String, Object>> seedAxesList = new ArrayList<>();
                  Set<Object> seenChannels = new LinkedHashSet<>();
                  for (HashMap<String, Object> axes : storage_.getAxesSet()) {
                     Object ch = axes.get("channel");
                     if (!seenChannels.add(ch == null ? "" : ch)) {
                        continue;
                     }
                     HashMap<String, Object> channelAxes = new HashMap<>();
                     if (ch != null) {
                        channelAxes.put("channel", ch);
                     }
                     try {
                        Image img = mm2DataProvider_.getDownsampledImageByAxes(axes);
                        if (img != null) {
                           mm2DataProvider_.newImageArrived(img, channelAxes);
                           seedImages.add(img);
                           seedAxesList.add(channelAxes);
                        }
                     } catch (Exception e) {
                        studio_.logs().logMessage(
                                 "Explorer: exception fetching seed image: " + e);
                     }
                  }
                  if (mm2Viewer_ != null && !seedImages.isEmpty()) {
                     mm2Viewer_.newTileArrived(seedImages, seedAxesList);
                  }
                  viewer_.initializeViewerToLoaded(null);
                  viewer_.update();

                  if (savedViewState != null && viewer_ != null) {
                     double mag = savedViewState.optDouble("magnification", 0);
                     if (mag > 0) {
                        Point2D.Double displaySize = viewer_.getDisplayImageSize();
                        double newW = displaySize.x / mag;
                        double newH = displaySize.y / mag;
                        viewer_.setFullResSourceDataSize(newW, newH);
                     }
                     viewer_.setViewOffset(
                             savedViewState.optDouble("xView", 0),
                             savedViewState.optDouble("yView", 0));
                     viewer_.update();
                  }
               } catch (NullPointerException e) {
                  studio_.logs().logMessage("Explorer: NPE in histogram seed: " + e);
               }
            });
         }

         studio_.logs().logMessage("Explorer: opened dataset from " + dir);
         studio_.events().registerForEvents(this);

      } catch (Exception e) {
         studio_.logs().logMessage("Explorer: openExplore EXCEPTION: " + e);
         studio_.logs().showError(e, "Failed to open Explorer dataset.");
         stopExplore(false);
      }
   }

   private TiledDataViewerAcqInterface createAcqInterface() {
      return new TiledDataViewerAcqInterface() {
         @Override
         public boolean isFinished() {
            return true; // Prevents "Finish Acquisition?" dialog on close
         }

         /**
          * Called on the EDT when the user clicks the viewer's X button.
          * Shows the save/discard/cancel dialog when there is unsaved data.
          * Returns false (vetoing the close) if the user clicks Cancel.
          */
         @Override
         public boolean requestToClose() {
            return promptForUnsavedData();
         }

         @Override
         public void abort() {
         }

         @Override
         public void setPaused(boolean paused) {
         }

         @Override
         public boolean isPaused() {
            return false;
         }

         @Override
         public void waitForCompletion() {
         }
      };
   }

   public void stopExplore() {
      stopExplore(!loadedData_);
   }

   private void stopExplore(boolean deleteTempFiles) {
      if (!exploring_) {
         return;
      }
      try {
         studio_.events().unregisterForEvents(this);
      } catch (Exception e) {
         studio_.logs().logError(e);
      }
      exploring_ = false;
      loadedData_ = false;
      acquisitionInterrupted_ = true;
      dismissMismatchAlert();
      pendingBatches_.set(0);
      sessionCorrectionRotation_ = 0;
      sessionCorrectionMirror_   = false;
      flipperInPipeline_         = false;
      rawPixelSizeAffine_        = null;
      rawInitialPixelSizeAffine_ = null;
      if (dataSource_ != null) {
         dataSource_.clearPendingTiles();
      }

      if (displayExecutor_ != null) {
         displayExecutor_.shutdownNow();
         displayExecutor_ = null;
      }
      if (acquisitionExecutor_ != null) {
         acquisitionExecutor_.shutdownNow();
         acquisitionExecutor_ = null;
      }
      if (stagePollingExecutor_ != null) {
         stagePollingExecutor_.shutdownNow();
         stagePollingExecutor_ = null;
      }

      final DisplaySettings mmDisplaySettingsToSave;
      final JSONObject viewStateToSave;
      if (!deleteTempFiles) {
         if (mm2Viewer_ != null) {
            DisplaySettings captured = null;
            try {
               captured = mm2Viewer_.getDisplaySettings();
            } catch (Exception e) {
               studio_.logs().logError(e);
            }
            mmDisplaySettingsToSave = captured;
         } else {
            mmDisplaySettingsToSave = pendingMMDisplaySettings_;
         }
         if (viewer_ != null) {
            JSONObject captured = null;
            try {
               captured = captureViewState(viewer_);
            } catch (Exception e) {
               studio_.logs().logError(e);
            }
            viewStateToSave = captured;
         } else {
            viewStateToSave = pendingViewState_;
         }
      } else {
         mmDisplaySettingsToSave = null;
         viewStateToSave = null;
      }
      pendingMMDisplaySettings_ = null;
      pendingViewState_ = null;

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

      final NDTiffStorage storageToClose = storage_;
      final boolean doDelete = deleteTempFiles;
      storage_ = null;
      if (storageToClose != null || doDelete) {
         Runnable cleanupTask = () -> {
            try {
               Thread.sleep(1000);
            } catch (InterruptedException ignored) {
               Thread.currentThread().interrupt();
            }
            if (storageToClose != null) {
               try {
                  String diskLocation = storageToClose.getDiskLocation();
                  if (diskLocation != null) {
                     if (mmDisplaySettingsToSave != null) {
                        File mmSettingsFile = new File(diskLocation, MM_DISPLAY_SETTINGS_FILE);
                        ((DefaultDisplaySettings) mmDisplaySettingsToSave).save(mmSettingsFile);
                     }
                     if (viewStateToSave != null) {
                        File viewStateFile = new File(diskLocation, VIEW_STATE_FILE);
                        Files.write(viewStateFile.toPath(),
                                viewStateToSave.toString(2).getBytes(StandardCharsets.UTF_8));
                     }
                  }
                  if (!storageToClose.isFinished()) {
                     storageToClose.finishedWriting();
                  }
                  storageToClose.close();
               } catch (Exception e) {
                  studio_.logs().logError(e, "Error closing storage");
               }
            }
            if (doDelete) {
               try {
                  Thread.sleep(500);
               } catch (InterruptedException ignored) {
                  Thread.currentThread().interrupt();
               }
               deleteTempStorage();
            }
         };
         if (deleteTempFiles && shutdownInProgress_) {
            cleanupTask.run();
         } else {
            new Thread(cleanupTask, "Explorer cleanup").start();
         }
      }
   }

   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent event) {
      if (event.isCanceled() || !exploring_) {
         return;
      }
      if (!promptForUnsavedData()) {
         event.cancelShutdown();
         return;
      }
      shutdownInProgress_ = true;
      onViewerClosed();
   }

   @Subscribe
   public void onPixelSizeChanged(PixelSizeChangedEvent event) {
      if (!exploring_) {
         return;
      }
      double newSize = event.getNewPixelSizeUm();
      if (newSize <= 0) {
         newSize = 1.0;
      }
      pixelSizeUm_ = newSize;
      stageTileWidthUm_  = cameraWidth_  * pixelSizeUm_;
      stageTileHeightUm_ = cameraHeight_ * pixelSizeUm_;
      loadPixelSizeAffine();
      updateStagePositionPixel();
      updateSettingsMismatch();
      redrawOverlay();
   }

   @Subscribe
   public void onPixelSizeAffineChanged(PixelSizeAffineChangedEvent event) {
      if (!exploring_) {
         return;
      }
      loadPixelSizeAffine();
      redrawOverlay();
   }

   private void dismissMismatchAlert() {
      if (mismatchAlert_ != null) {
         if (mismatchAlert_.isUsable()) {
            mismatchAlert_.dismiss();
         }
         mismatchAlert_ = null;
      }
   }

   private void updateSettingsMismatch() {
      boolean mismatch = Math.abs(pixelSizeUm_ - initialPixelSizeUm_) > 0.001 * initialPixelSizeUm_
            || cameraWidth_  != initialCameraWidth_
            || cameraHeight_ != initialCameraHeight_;
      if (mismatch != settingsMismatch_) {
         settingsMismatch_ = mismatch;
         if (dataSource_ != null) {
            dataSource_.setSettingsMismatch(mismatch);
         }
         if (mismatch) {
            mismatchAlert_ = studio_.alerts().postAlert("Explorer",
                  ExplorerManager.class,
                  "Acquisition blocked: pixel size or camera ROI has changed from session "
                  + "start. Restore settings to re-enable tile acquisition.");
         } else {
            dismissMismatchAlert();
         }
         redrawOverlay();
      }
   }

   public void onViewerClosed() {
      if (!exploring_ || viewerClosing_) {
         return;
      }
      viewerClosing_ = true;

      if (mm2Viewer_ != null) {
         try {
            pendingMMDisplaySettings_ = mm2Viewer_.getDisplaySettings();
         } catch (Exception e) {
            studio_.logs().logError(e);
         }
      }
      if (viewer_ != null) {
         try {
            pendingViewState_ = captureViewState(viewer_);
         } catch (Exception e) {
            studio_.logs().logError(e);
         }
      }

      closeViewerReferences();
      stopExplore(!loadedData_);
   }

   /**
    * Shows the save/discard/cancel dialog when there is unsaved Explorer data.
    * Returns true if the caller should proceed (data was saved or discarded),
    * false if the user cancelled.  Returns true immediately when there is no
    * unsaved data (loaded-data sessions, empty storage, etc.).
    * Must be called on the EDT.
    */
   private boolean promptForUnsavedData() {
      if (loadedData_) {
         return true;
      }
      boolean hasData = false;
      try {
         hasData = storage_ != null && !storage_.isFinished()
               && !storage_.getAxesSet().isEmpty();
      } catch (Exception e) {
         studio_.logs().logMessage(
               "Explorer: could not check storage state: " + e.getMessage());
      }
      if (!hasData) {
         return true;
      }
      while (true) {
         int choice = JOptionPane.showOptionDialog(
                 null,
                 "Save the acquired Explorer data?",
                 "Save Explorer Data",
                 JOptionPane.YES_NO_CANCEL_OPTION,
                 JOptionPane.QUESTION_MESSAGE,
                 null,
                 new String[]{"Save", "Discard", "Cancel"},
                 "Save");
         if (choice == 2 || choice == JOptionPane.CLOSED_OPTION) {
            studio_.logs().logMessage(
                  "Explorer: close cancelled, data remains in: " + storageDir_);
            return false;
         } else if (choice == 0) {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Save Explorer Data");
            chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
            String suggestedPath = FileDialogs.getSuggestedFile(FileDialogs.MM_DATA_SET);
            if (suggestedPath != null) {
               File suggested = new File(suggestedPath);
               File dir = suggested.isDirectory() ? suggested : suggested.getParentFile();
               if (dir != null) {
                  chooser.setCurrentDirectory(dir);
               }
            }
            chooser.setSelectedFile(new File(acqName_));
            if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
               File destDir = chooser.getSelectedFile();
               FileDialogs.storePath(FileDialogs.MM_DATA_SET, destDir);
               writeSettingsToTempDir();
               if (saveDataTo(destDir)) {
                  return true;
               }
            }
         } else {
            return true; // Discard
         }
      }
   }

   private void closeViewerReferences() {
      if (mm2Viewer_ != null) {
         mm2Viewer_.closeWithoutTiledDataViewer();
      }
      mm2Viewer_ = null;
      mm2DataProvider_ = null;
      viewer_ = null;
   }

   private boolean saveDataTo(File destDir) {
      if (storageDir_ == null) {
         return false;
      }
      if (!destDir.exists() && !destDir.mkdirs()) {
         studio_.logs().showError("Cannot create destination directory: " + destDir);
         return false;
      }
      if (!destDir.canWrite()) {
         studio_.logs().showError("Cannot write to destination directory: " + destDir);
         return false;
      }

      try {
         if (storage_ != null) {
            NDTiffStorage storageRef = storage_;
            storage_ = null;
            try {
               if (!storageRef.isFinished()) {
                  storageRef.finishedWriting();
               }
               storageRef.close();
            } catch (Exception e) {
               studio_.logs().logError(e, "Error closing storage before save");
            }
         }

         File sourceDir = new File(storageDir_, acqName_);
         if (!sourceDir.exists()) {
            sourceDir = new File(storageDir_);
         }

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
            studio_.logs().showError("Save incomplete: " + copyErrors[0]
                  + " file(s) could not be copied.\n"
                  + "Your data is still in the temporary directory:\n  " + storageDir_
                  + "\nSee CoreLog for details.");
            return false;
         }

         studio_.logs().logMessage("Explorer: data saved to " + destDir.getAbsolutePath());
         studio_.logs().showMessage("Data saved to: " + destDir.getAbsolutePath());
         return true;

      } catch (Exception e) {
         studio_.logs().showError(e, "Failed to save Explorer data");
         return false;
      }
   }

   private void deleteTempStorage() {
      if (storageDir_ == null) {
         return;
      }
      try {
         File dir = new File(storageDir_);
         if (dir.exists()) {
            List<Path> pathsToDelete;
            try (Stream<Path> stream = Files.walk(dir.toPath())) {
               pathsToDelete = stream.sorted(Comparator.reverseOrder())
                       .collect(java.util.stream.Collectors.toList());
            }
            for (Path path : pathsToDelete) {
               deleteWithRetry(path);
            }
            studio_.logs().logMessage("Explorer: deleted temp storage at " + storageDir_);
         }
      } catch (IOException e) {
         studio_.logs().logError(e, "Failed to delete temp storage: " + storageDir_);
      }
   }

   private void deleteWithRetry(Path path) {
      int maxRetries = 5;
      long delayMs = 500;
      for (int attempt = 1; attempt <= maxRetries; attempt++) {
         try {
            Files.delete(path);
            return;
         } catch (IOException e) {
            if (attempt == maxRetries) {
               studio_.logs().logError(e, "Failed to delete after " + maxRetries
                     + " attempts: " + path);
               return;
            }
            try {
               Thread.sleep(delayMs);
               delayMs *= 2;
            } catch (InterruptedException ie) {
               Thread.currentThread().interrupt();
               return;
            }
         }
      }
   }

   /**
    * Signals all queued tile acquisitions to stop after the current tile finishes.
    */
   public void interruptAcquisition() {
      acquisitionInterrupted_ = true;
      if (dataSource_ != null) {
         dataSource_.clearPendingTiles();
         redrawOverlay();
      }
   }

   /**
    * Returns the inverse of the "mirror-X then rotate CW by rotation degrees" correction
    * transform.  Composing this into the canvas affine (A_corrected = A_raw * corrInv)
    * keeps canvas-pixel-delta → stage-micron-delta correct after tile images have been
    * corrected by the same mirror+rotation.
    */
   private AffineTransform buildCorrectionAffineInverse(int rotation, boolean mirror) {
      AffineTransform inv = new AffineTransform();
      if (rotation == 90) {
         inv.quadrantRotate(1); // CCW 90° = inverse of CW 90°
      } else if (rotation == 180) {
         inv.quadrantRotate(2);
      } else if (rotation == 270) {
         inv.quadrantRotate(3);
      }
      if (mirror) {
         inv.preConcatenate(AffineTransform.getScaleInstance(-1.0, 1.0));
      }
      return inv;
   }

   /**
    * Composes the orientation correction into both the current and session-start
    * canvas affines and recomputes their inverses.
    */
   private void applyOrientationCorrectionToAffines(int rotation, boolean mirror) {
      AffineTransform corrInv = buildCorrectionAffineInverse(rotation, mirror);
      if (pixelSizeAffine_ != null) {
         AffineTransform c = new AffineTransform(pixelSizeAffine_);
         c.concatenate(corrInv);
         pixelSizeAffine_ = c;
         try {
            pixelSizeAffineInverse_ = c.createInverse();
         } catch (NoninvertibleTransformException e) {
            pixelSizeAffine_ = null;
            pixelSizeAffineInverse_ = null;
         }
      }
      if (initialPixelSizeAffine_ != null) {
         AffineTransform c = new AffineTransform(initialPixelSizeAffine_);
         c.concatenate(corrInv);
         initialPixelSizeAffine_ = c;
         try {
            initialPixelSizeAffineInverse_ = c.createInverse();
         } catch (NoninvertibleTransformException e) {
            initialPixelSizeAffine_ = null;
            initialPixelSizeAffineInverse_ = null;
         }
      }
   }

   /**
    * Loads and validates the pixel-size affine transform from the core.
    * Sets pixelSizeAffine_ / pixelSizeAffineInverse_ on success, leaves both null
    * (scalar fallback) on any failure or if the affine does not match pixelSizeUm_.
    * The MM core affine has zero translation terms, so AffineTransform.transform()
    * on a pixel-delta Point2D gives the correct stage-micron delta.
    */
   private void loadPixelSizeAffine() {
      pixelSizeAffine_ = null;
      pixelSizeAffineInverse_ = null;
      if (pixelSizeUm_ <= 0) {
         return;
      }
      try {
         AffineTransform at = AffineUtils.doubleToAffine(
               studio_.core().getPixelSizeAffine(true));
         double affinePixelSize = AffineUtils.deducePixelSize(at);
         if (Math.abs(pixelSizeUm_ - affinePixelSize) > 0.1 * pixelSizeUm_) {
            studio_.logs().logMessage("Explorer: affine pixel size " + affinePixelSize
                  + " differs from declared " + pixelSizeUm_
                  + " by >10%; using scalar coordinate conversion");
            return;
         }
         pixelSizeAffine_ = at;
         try {
            pixelSizeAffineInverse_ = at.createInverse();
         } catch (NoninvertibleTransformException e) {
            studio_.logs().logMessage(
                  "Explorer: pixel-size affine is singular; using scalar coordinate conversion");
            pixelSizeAffine_ = null;
         }
      } catch (Exception e) {
         studio_.logs().logMessage("Explorer: could not load pixel-size affine ("
               + e.getMessage() + "); using scalar coordinate conversion");
      }
      // Re-apply session correction so that stage-movement affine stays consistent
      // after an objective / pixel-size change.
      if (pixelSizeAffine_ != null
            && (sessionCorrectionMirror_ || sessionCorrectionRotation_ != 0)) {
         AffineTransform corrInv = buildCorrectionAffineInverse(
               sessionCorrectionRotation_, sessionCorrectionMirror_);
         AffineTransform c = new AffineTransform(pixelSizeAffine_);
         c.concatenate(corrInv);
         pixelSizeAffine_ = c;
         try {
            pixelSizeAffineInverse_ = c.createInverse();
         } catch (NoninvertibleTransformException e) {
            pixelSizeAffine_ = null;
            pixelSizeAffineInverse_ = null;
         }
      }
   }

   /**
    * Acquires multiple tiles sequentially, moving the stage between positions.
    * Stage step is based on the camera FOV × pixel size (stageTileWidthUm_/stageTileHeightUm_).
    */
   public void acquireMultipleTiles(List<Point> tiles) {
      if (!exploring_ || acquisitionExecutor_ == null || tiles.isEmpty()) {
         return;
      }
      if (settingsMismatch_) {
         return;
      }

      acquisitionInterrupted_ = false;

      int batchCount = pendingBatches_.incrementAndGet();
      if (batchCount == 1) {
         dataSource_.setAcquisitionInProgress(true);
         frame_.setAcquisitionInProgress(true);
      }

      for (Point tile : tiles) {
         dataSource_.addPendingTile(tile.x, tile.y);
      }
      redrawOverlay();

      acquisitionExecutor_.submit(() -> {
         try {
            // Stage step in microns: derived from camera FOV (not pipeline-output tile size).
            // The pipeline may resize images, but the stage still moves by the camera FOV.
            double overlapFraction = overlapPercentage_ / 100.0;
            // Effective tile step in camera-pixel space (affine operates on camera pixels).
            double effectivePixelStepX = cameraWidth_  * (1.0 - overlapFraction);
            double effectivePixelStepY = cameraHeight_ * (1.0 - overlapFraction);

            for (Point tile : tiles) {
               if (acquisitionInterrupted_) {
                  break;
               }
               if (settingsMismatch_) {
                  studio_.logs().logMessage(
                        "Explorer: aborting in-flight acquisition — settings changed "
                              + "(pixel size or ROI mismatch)");
                  acquisitionInterrupted_ = true;
                  break;
               }
               int row = tile.x;
               int col = tile.y;

               double targetX;
               double targetY;
               if (pixelSizeAffine_ != null) {
                  Point2D.Double pixelOffset = new Point2D.Double(
                        col * effectivePixelStepX, row * effectivePixelStepY);
                  Point2D.Double stageOffset = new Point2D.Double();
                  pixelSizeAffine_.transform(pixelOffset, stageOffset);
                  targetX = initialStageX_ + stageOffset.x;
                  targetY = initialStageY_ + stageOffset.y;
               } else {
                  double effectiveStepWidthUm  = stageTileWidthUm_  * (1.0 - overlapFraction);
                  double effectiveStepHeightUm = stageTileHeightUm_ * (1.0 - overlapFraction);
                  targetX = initialStageX_ + col * effectiveStepWidthUm;
                  targetY = initialStageY_ + row * effectiveStepHeightUm;
               }

               studio_.core().setXYPosition(targetX, targetY);
               studio_.core().waitForDevice(studio_.core().getXYStageDevice());
               Thread.sleep(100);

               acquireSingleTileBlocking(row, col);
            }

         } catch (Exception e) {
            studio_.logs().logError(e, "Explorer: error during multi-tile acquisition");
         } finally {
            if (pendingBatches_.decrementAndGet() == 0) {
               dataSource_.setAcquisitionInProgress(false);
               frame_.setAcquisitionInProgress(false);
            }
         }
      });
   }

   /**
    * Acquires a single tile synchronously via the MM acquisition engine.
    * Images pass through the active application pipeline automatically.
    * This is called from within the acquisition executor.
    */
   private void acquireSingleTileBlocking(int row, int col) {
      try {
         SequenceSettings settings = studio_.acquisitions().getAcquisitionSettings();
         boolean useSlices = settings.useSlices() && settings.slices().size() > 0;
         SequenceSettings.Builder sb = settings.copyBuilder()
                 .useFrames(false)
                 .useSlices(useSlices)
                 .usePositionList(false)
                 .save(false)
                 .shouldDisplayImages(false)
                 .isTestAcquisition(true);

         // Preserve channel settings from MDA
         if (settings.useChannels()) {
            sb.useChannels(true);
         }
         if (!settings.useChannels() && !useSlices) {
            // AcquisitionEventIterator requires at least one acquisition function;
            // when no channels/slices/positions are used, enable a single frame.
            sb.useFrames(true).numFrames(1).intervalMs(0);
         }

         Datastore testStore = studio_.acquisitions().runAcquisitionWithSettings(
                 sb.build(), true);

         if (testStore == null) {
            studio_.logs().showError("Explorer: test acquisition failed at row="
                    + row + ", col=" + col);
            return;
         }

         if (testStore.getNumImages() == 0) {
            studio_.logs().showError("Explorer: test acquisition produced no images at row="
                    + row + ", col=" + col);
            freezeAndClose(testStore);
            return;
         }

         // If this is the first tile, update tile dimensions from actual pipeline output
         if (tileWidth_ < 0) {
            // Read dimensions from the first image (pipeline may have resized)
            Iterable<Coords> coords = testStore.getUnorderedImageCoords();
            Coords firstCoords = coords.iterator().next();
            Image firstImage = testStore.getImage(firstCoords);
            if (firstImage != null) {
               tileWidth_ = firstImage.getWidth();
               tileHeight_ = firstImage.getHeight();
               dataSource_.setTileDimensions(tileWidth_, tileHeight_);

               // Update storage overlap metadata with actual tile dimensions
               int overlapX = (int) Math.round(tileWidth_ * overlapPercentage_ / 100.0);
               int overlapY = (int) Math.round(tileHeight_ * overlapPercentage_ / 100.0);
               try {
                  JSONObject summaryMetadata = storage_.getSummaryMetadata();
                  summaryMetadata.put("GridPixelOverlapX", overlapX);
                  summaryMetadata.put("GridPixelOverlapY", overlapY);
                  summaryMetadata.put("Width", tileWidth_);
                  summaryMetadata.put("Height", tileHeight_);
               } catch (Exception e) {
                  studio_.logs().logError(e, "Explorer: failed to update overlap metadata");
               }

               // Issue 2: detect Image Flipper in the pipeline from first-image metadata.
               // If detected, override canvas affines with the Flipper's correction so that
               // stageToPixel() and moveStageToPixelPosition() map to the correct canvas
               // coordinates (the Flipper has already corrected the pixel data).
               if (!flipperInPipeline_) {
                  PropertyMap userData = firstImage.getMetadata().getUserData();
                  if (userData != null
                        && userData.containsInteger("ImageFlipper-Rotation")
                        && userData.containsString("ImageFlipper-Mirror")) {
                     final int flipRot = userData.getInteger("ImageFlipper-Rotation", 0);
                     final boolean flipMirror = "On".equals(
                           userData.getString("ImageFlipper-Mirror", "Off"));
                     flipperInPipeline_ = true;
                     // Restart from the raw (pre-camera-correction) affines so the
                     // Flipper's correction does not compound with the camera correction.
                     pixelSizeAffine_ = rawPixelSizeAffine_ != null
                           ? new AffineTransform(rawPixelSizeAffine_) : null;
                     pixelSizeAffineInverse_ = null;
                     initialPixelSizeAffine_ = rawInitialPixelSizeAffine_ != null
                           ? new AffineTransform(rawInitialPixelSizeAffine_) : null;
                     initialPixelSizeAffineInverse_ = null;
                     if (pixelSizeAffine_ != null) {
                        try {
                           pixelSizeAffineInverse_ = pixelSizeAffine_.createInverse();
                        } catch (NoninvertibleTransformException ignored) {
                           pixelSizeAffine_ = null;
                        }
                     }
                     if (initialPixelSizeAffine_ != null) {
                        try {
                           initialPixelSizeAffineInverse_ = initialPixelSizeAffine_.createInverse();
                        } catch (NoninvertibleTransformException ignored) {
                           initialPixelSizeAffine_ = null;
                        }
                     }
                     sessionCorrectionRotation_ = flipRot;
                     sessionCorrectionMirror_   = flipMirror;
                     if (flipRot != 0 || flipMirror) {
                        applyOrientationCorrectionToAffines(flipRot, flipMirror);
                     }
                     studio_.logs().logMessage("Explorer: Image Flipper detected (rot="
                           + flipRot + ", mirror=" + flipMirror
                           + "); canvas affine adjusted");
                  }
               }
            }
         }

         // Store each channel image
         SummaryMetadata summaryMeta = testStore.getSummaryMetadata();
         List<HashMap<String, Object>> storedAxes = new ArrayList<>();
         List<Image> storedImages = new ArrayList<>();

         List<Coords> allCoords = new ArrayList<>();
         testStore.getUnorderedImageCoords().forEach(allCoords::add);
         // Sort by channel, then z-slice, for consistent ordering
         allCoords.sort((a, b) -> {
            int cmp = Integer.compare(a.getChannel(), b.getChannel());
            return cmp != 0 ? cmp : Integer.compare(a.getZSlice(), b.getZSlice());
         });
         // Only attach a "z" axis when this is a genuine z-stack (more than one plane);
         // single-plane acquisitions keep their original axes and create no z slider.
         boolean isZStack = false;
         for (Coords c : allCoords) {
            if (c.getZSlice() > 0) {
               isZStack = true;
               break;
            }
         }

         for (Coords c : allCoords) {
            Image img = testStore.getImage(c);
            if (img == null) {
               continue;
            }
            // Issue 1: apply pixel correction when the Flipper is not in the pipeline.
            // The Flipper (Issue 2) has already corrected images when flipperInPipeline_ is true.
            if (!flipperInPipeline_
                  && (sessionCorrectionMirror_ || sessionCorrectionRotation_ != 0)) {
               try {
                  Object[] result = ImageTransformUtils.transformPixels(
                        img.getRawPixels(), img.getWidth(), img.getHeight(),
                        img.getBytesPerPixel(), sessionCorrectionMirror_,
                        sessionCorrectionRotation_);
                  img = studio_.data().createImage(
                        result[0], (Integer) result[1], (Integer) result[2],
                        img.getBytesPerPixel(), img.getNumComponents(),
                        img.getCoords(), img.getMetadata());
               } catch (Exception e) {
                  studio_.logs().logError(e, "Explorer: pixel orientation correction failed");
               }
            }
            int channelIndex = c.getChannel();
            String channelName = summaryMeta.getSafeChannelName(channelIndex);
            // Pass the z-slice index only for z-stacks (-1 means "no z axis").
            int zIndex = isZStack ? c.getZSlice() : -1;
            HashMap<String, Object> axes = storeImage(img, row, col, channelName, zIndex);
            if (axes != null) {
               storedAxes.add(axes);
               storedImages.add(img);
            }
         }

         dataSource_.markTileAcquired(row, col);

         if (displayExecutor_ != null && viewer_ != null && !storedAxes.isEmpty()) {
            final List<Image> tileImages = storedImages;
            final List<HashMap<String, Object>> fullAxesList = new ArrayList<>(storedAxes);
            final List<HashMap<String, Object>> displayAxesList = new ArrayList<>();
            for (HashMap<String, Object> axes : storedAxes) {
               HashMap<String, Object> displayAxes = new HashMap<>(axes);
               displayAxes.remove("row");
               displayAxes.remove("column");
               displayAxesList.add(displayAxes);
            }
            final int tileRow = row;
            final int tileCol = col;
            displayExecutor_.submit(() -> {
               for (int i = 0; i < tileImages.size() && i < displayAxesList.size(); i++) {
                  if (mm2DataProvider_ != null) {
                     // Use axes-only overload so the image is re-read from storage,
                     // ensuring per-image metadata tags are included.
                     mm2DataProvider_.newImageArrived(fullAxesList.get(i));
                  }
                  try {
                     viewer_.newImageArrived(displayAxesList.get(i));
                  } catch (NullPointerException e) {
                     // NDViewer histogram not yet initialized
                  }
               }
               if (mm2Viewer_ != null) {
                  mm2Viewer_.newTileArrived(tileImages, displayAxesList);
               }
               try {
                  viewer_.update();
               } catch (NullPointerException e) {
                  // NDViewer histogram not yet initialized
               }
               dataSource_.removePendingTile(tileRow, tileCol);
               redrawOverlay();
            });
         } else {
            dataSource_.removePendingTile(row, col);
            redrawOverlay();
         }

         freezeAndClose(testStore);

      } catch (Exception e) {
         studio_.logs().logError(e,
                  "Explorer: error acquiring tile at row=" + row + ", col=" + col);
      }
   }

   private void freezeAndClose(Datastore store) {
      try {
         store.freeze();
      } catch (IOException ignored) {
         studio_.logs().logError("Explorer: IOException ignored on store freeze");
      }
      try {
         store.close();
      } catch (IOException ignored) {
         studio_.logs().logError("Explorer: IOException ignored on store close");
      }
   }

   /**
    * Stores a single image at the specified tile position and channel.
    *
    * @param zIndex z-slice index for a z-stack, or -1 when there is no z axis
    *               (single-plane acquisition).
    */
   private HashMap<String, Object> storeImage(Image image, int row, int col,
                                               String channelName, int zIndex) {
      if (storage_ == null) {
         studio_.logs().logError("Explorer: storage is null, cannot store image");
         return null;
      }
      try {
         JSONObject tags = new JSONObject();
         tags.put("ElapsedTime-ms", System.currentTimeMillis());
         tags.put("Width", image.getWidth());
         tags.put("Height", image.getHeight());
         tags.put("BitDepth", bitDepth_);
         tags.put("PixelType", isRGB_ ? "RGB32" : (bitDepth_ <= 8 ? "GRAY8" : "GRAY16"));
         tags.put("BytesPerPixel", isRGB_ ? 4 : (bitDepth_ <= 8 ? 1 : 2));
         tags.put("NumComponents", isRGB_ ? 3 : 1);
         tags.put("PixelSizeUm", pixelSizeUm_);

         // Per-image metadata for MM Inspector "Plane Metadata" panel
         try {
            tags.put("Camera", studio_.core().getCameraDevice());
         } catch (Exception ignore) {
            if (loggedMetadataWarnings_.add("Camera")) {
               studio_.logs().logError("Explorer: camera device not found");
            }
         }
         try {
            tags.put("Exposure-ms", studio_.core().getExposure());
         } catch (Exception ignore) {
            if (loggedMetadataWarnings_.add("Exposure-ms")) {
               studio_.logs().logError("Explorer: exposure not found");
            }
         }
         try {
            tags.put("XPositionUm", studio_.core().getXPosition());
         } catch (Exception ignore) {
            if (loggedMetadataWarnings_.add("XPositionUm")) {
               studio_.logs().logError("Explorer: X position not found");
            }
         }
         try {
            tags.put("YPositionUm", studio_.core().getYPosition());
         } catch (Exception ignore) {
            if (loggedMetadataWarnings_.add("YPositionUm")) {
               studio_.logs().logError("Explorer: Y position not found");
            }
         }
         // For a z-stack, prefer the plane's true Z from the image metadata, since the
         // engine moves Z asynchronously and the stage may not be settled at store time.
         Double planeZum = (zIndex >= 0 && image.getMetadata() != null)
                 ? image.getMetadata().getZPositionUm() : null;
         try {
            tags.put("ZPositionUm",
                  planeZum != null ? planeZum : studio_.core().getPosition());
         } catch (Exception ignore) {
            if (loggedMetadataWarnings_.add("ZPositionUm")) {
               studio_.logs().logError("Explorer: Z position not found");
            }
         }
         tags.put("UUID", java.util.UUID.randomUUID().toString());
         tags.put("ReceivedTime", RECEIVED_TIME_FORMAT.format(java.time.Instant.now()));

         AcqEngMetadata.createAxes(tags);
         AcqEngMetadata.setAxisPosition(tags, "row", row);
         AcqEngMetadata.setAxisPosition(tags, "column", col);
         AcqEngMetadata.setAxisPosition(tags, "channel", channelName);
         if (zIndex >= 0) {
            AcqEngMetadata.setAxisPosition(tags, "z", zIndex);
         }

         HashMap<String, Object> axes = AcqEngMetadata.getAxes(tags);

         Future<?> future = storage_.putImageMultiRes(
                 image.getRawPixels(),
                 tags,
                 axes,
                 isRGB_,
                 bitDepth_,
                 image.getHeight(),
                 image.getWidth());

         future.get();
         storage_.increaseMaxResolutionLevel(4);
         if (dataSource_ != null) {
            dataSource_.invalidateImageKeysCache();
         }
         return axes;

      } catch (Exception e) {
         studio_.logs().logError(e, "Explorer: failed to store image");
         return null;
      }
   }

   // ===================== Stage position overlay =====================

   private void updateStagePositionPixel() {
      if (dataSource_ == null) {
         return;
      }
      try {
         double stageX = studio_.core().getXPosition();
         double stageY = studio_.core().getYPosition();
         Point2D.Double pixel = stageToPixel(stageX, stageY);
         dataSource_.setStagePositionPixel(pixel);
      } catch (Exception e) {
         // Stage not available
      }
   }

   private void startStagePositionPolling() {
      stagePollingExecutor_.scheduleWithFixedDelay(() -> {
         if (dataSource_ == null) {
            return;
         }
         try {
            // Detect camera ROI changes (no dedicated MM event for this)
            int newW = (int) studio_.core().getImageWidth();
            int newH = (int) studio_.core().getImageHeight();
            if (newW != cameraWidth_ || newH != cameraHeight_) {
               cameraWidth_ = newW;
               cameraHeight_ = newH;
               stageTileWidthUm_  = cameraWidth_  * pixelSizeUm_;
               stageTileHeightUm_ = cameraHeight_ * pixelSizeUm_;
               updateSettingsMismatch();
            }

            double stageX = studio_.core().getXPosition();
            double stageY = studio_.core().getYPosition();
            Point2D.Double pixel = stageToPixel(stageX, stageY);
            dataSource_.setStagePositionPixel(pixel);
            redrawOverlay();
         } catch (Exception e) {
            // Stage or camera not available
         }
      }, 0, 500, TimeUnit.MILLISECONDS);
   }

   private Point2D.Double stageToPixel(double stageX, double stageY) {
      int tw = dataSource_ != null ? dataSource_.getTileWidth() : -1;
      int th = dataSource_ != null ? dataSource_.getTileHeight() : -1;
      if (tw <= 0 || th <= 0 || initialPixelSizeUm_ <= 0) {
         return null;
      }
      int overlapPixelsX = (int) Math.round(tw * overlapPercentage_ / 100.0);
      int overlapPixelsY = (int) Math.round(th * overlapPercentage_ / 100.0);
      double effectiveTileWidth = tw - overlapPixelsX;
      double effectiveTileHeight = th - overlapPixelsY;
      double stageOffsetX = stageX - initialStageX_;
      double stageOffsetY = stageY - initialStageY_;

      double camPixelDeltaX;
      double camPixelDeltaY;
      if (initialPixelSizeAffineInverse_ != null) {
         // Use the session-start affine so position is always in the tile-grid coordinate system.
         Point2D.Double stageOffset = new Point2D.Double(stageOffsetX, stageOffsetY);
         Point2D.Double camDelta = new Point2D.Double();
         initialPixelSizeAffineInverse_.transform(stageOffset, camDelta);
         camPixelDeltaX = camDelta.x;
         camPixelDeltaY = camDelta.y;
      } else {
         // Use the session-start pixel size so position stays fixed in the tile-grid coordinate
         // system regardless of subsequent pixel-size changes.
         camPixelDeltaX = stageOffsetX / initialPixelSizeUm_;
         camPixelDeltaY = stageOffsetY / initialPixelSizeUm_;
      }
      // Scale from camera-pixel space to canvas (pipeline-output) pixel space.
      double pipelineScaleX = (tw > 0 && initialCameraWidth_  > 0)
            ? (double) tw / initialCameraWidth_  : 1.0;
      double pipelineScaleY = (th > 0 && initialCameraHeight_ > 0)
            ? (double) th / initialCameraHeight_ : 1.0;
      double pixelX = camPixelDeltaX * pipelineScaleX + effectiveTileWidth  / 2.0;
      double pixelY = camPixelDeltaY * pipelineScaleY + effectiveTileHeight / 2.0;
      return new Point2D.Double(pixelX, pixelY);
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

   public double getOverlapPercentage() {
      return overlapPercentage_;
   }

   /**
    * Ratio of current pixel size to the session-start pixel size.
    * Used to scale the red FOV indicator on the canvas when the objective changes.
    * Returns 1.0 when there is no change.
    */
   public double getPixelSizeRatio() {
      if (initialPixelSizeUm_ <= 0) {
         return 1.0;
      }
      return pixelSizeUm_ / initialPixelSizeUm_;
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
    * Moves the stage so that the given pixel position becomes the center of the FOV.
    */
   public void moveStageToPixelPosition(double pixelX, double pixelY) {
      if (!exploring_ || acquisitionExecutor_ == null) {
         return;
      }
      acquisitionExecutor_.submit(() -> {
         try {
            int tw = dataSource_ != null ? dataSource_.getTileWidth() : cameraWidth_;
            int th = dataSource_ != null ? dataSource_.getTileHeight() : cameraHeight_;
            int overlapPixelsX = (int) Math.round(tw * overlapPercentage_ / 100.0);
            int overlapPixelsY = (int) Math.round(th * overlapPercentage_ / 100.0);
            double effectiveTileWidth = tw - overlapPixelsX;
            double effectiveTileHeight = th - overlapPixelsY;

            double offsetPixelX = pixelX - effectiveTileWidth / 2.0;
            double offsetPixelY = pixelY - effectiveTileHeight / 2.0;

            double targetX;
            double targetY;
            // Always invert using the session-start pixel size / affine so that clicks on
            // the tile grid map to consistent physical stage positions regardless of the
            // current objective.
            if (initialPixelSizeAffine_ != null) {
               // Convert canvas-pixel delta to camera-pixel delta using initial camera dims,
               // then apply the session-start affine (forward: camera-pixel → stage-micron).
               double pipelineScaleX = (tw > 0 && initialCameraWidth_  > 0)
                     ? (double) initialCameraWidth_  / tw : 1.0;
               double pipelineScaleY = (th > 0 && initialCameraHeight_ > 0)
                     ? (double) initialCameraHeight_ / th : 1.0;
               Point2D.Double camPixelDelta = new Point2D.Double(
                     offsetPixelX * pipelineScaleX, offsetPixelY * pipelineScaleY);
               Point2D.Double stageOffset = new Point2D.Double();
               initialPixelSizeAffine_.transform(camPixelDelta, stageOffset);
               targetX = initialStageX_ + stageOffset.x;
               targetY = initialStageY_ + stageOffset.y;
            } else {
               double pipelineScaleX = (tw > 0 && initialCameraWidth_  > 0)
                     ? (double) initialCameraWidth_  / tw : 1.0;
               double pipelineScaleY = (th > 0 && initialCameraHeight_ > 0)
                     ? (double) initialCameraHeight_ / th : 1.0;
               targetX = initialStageX_ + offsetPixelX * pipelineScaleX * initialPixelSizeUm_;
               targetY = initialStageY_ + offsetPixelY * pipelineScaleY * initialPixelSizeUm_;
            }

            studio_.core().setXYPosition(targetX, targetY);
            studio_.core().waitForDevice(studio_.core().getXYStageDevice());

         } catch (Exception e) {
            studio_.logs().logError(e, "Explorer: error moving stage");
         }
      });
   }

   // ===================== Private helpers =====================

   private SummaryMetadata buildSummaryMetadata(int width, int height) {
      try {
         SequenceSettings settings = studio_.acquisitions().getAcquisitionSettings();

         List<String> chNames = new ArrayList<>();
         if (settings.useChannels() && settings.channels().size() > 0) {
            for (int i = 0; i < settings.channels().size(); i++) {
               if (settings.channels().get(i).useChannel()) {
                  chNames.add(settings.channels().get(i).config());
               }
            }
         }
         if (chNames.isEmpty()) {
            chNames.add("Default");
         }

         SummaryMetadata.Builder smb = studio_.data().summaryMetadataBuilder()
               .sequenceSettings(settings)
               .channelNames(chNames)
               .channelGroup(settings.channelGroup())
               .userName(System.getProperty("user.name"))
               .profileName(studio_.profile().getProfileName())
               .startDate(LocalDateTime.now().toString())
               .imageWidth(width)
               .imageHeight(height)
               .initialScopeData(studio_.acquisitions().scopeData()
                     .configurationToPropertyMap(studio_.core().getSystemStateCache()));
         if (settings.useSlices()) {
            smb.zStepUm(settings.sliceZStepUm());
         }
         DefaultSummaryMetadata sm = (DefaultSummaryMetadata) smb.build();
         // PixelType has no builder method — inject directly into the PropertyMap
         return DefaultSummaryMetadata.fromPropertyMap(
               sm.toPropertyMap().copyBuilder()
                     .putString("PixelType",
                              isRGB_ ? "RGB32" : (bitDepth_ <= 8 ? "GRAY8" : "GRAY16"))
                     .build());
      } catch (Exception e) {
         studio_.logs().logError(e, "Explorer: failed to build summary metadata");
         return studio_.data().summaryMetadataBuilder().build();
      }
   }

   private JSONObject summaryMetadataToJSON(SummaryMetadata sm) {
      try {
         String json = NonPropertyMapJSONFormats.summaryMetadata().toJSON(
               ((DefaultSummaryMetadata) sm).toPropertyMap());
         JSONObject md = new JSONObject(json);
         // NDTiff-specific fields not covered by SummaryMetadata API
         md.put("PixelSize_um", pixelSizeUm_);
         md.put("BitDepth", bitDepth_);
         // Alias "ChannelGroup" (NonPropertyMapJSONFormats key) as "ChGroup"
         // so applyDisplaySettingsHeuristics() and other readers can find it.
         if (md.has("ChannelGroup") && !md.has("ChGroup")) {
            md.put("ChGroup", md.get("ChannelGroup"));
         }
         return md;
      } catch (Exception e) {
         studio_.logs().logError(e, "Explorer: failed to serialize summary metadata");
         return new JSONObject();
      }
   }

   private void initDisplaySettings(JSONObject summaryMetadata) {
      try {
         SequenceSettings settings = studio_.acquisitions().getAcquisitionSettings();
         DisplaySettings.Builder dsBuilder = studio_.displays().displaySettingsBuilder();
         int displayChannelIndex = 0;
         if (settings.useChannels() && settings.channels().size() > 0) {
            for (int i = 0; i < settings.channels().size(); i++) {
               if (settings.channels().get(i).useChannel()) {
                  String channelGroup = settings.channelGroup();
                  String channelName = settings.channels().get(i).config();
                  Color channelColor = settings.channels().get(i).color();
                  org.micromanager.display.ChannelDisplaySettings remembered =
                        RememberedDisplaySettings.loadChannel(
                              studio_, channelGroup, channelName, null);
                  org.micromanager.display.ChannelDisplaySettings.Builder chBuilder =
                        studio_.displays().channelDisplaySettingsBuilder()
                              .groupName(channelGroup)
                              .name(channelName)
                              .color(channelColor);
                  if (remembered != null) {
                     for (int c = 0; c < remembered.getNumberOfComponents(); c++) {
                        chBuilder.component(c, remembered.getComponentSettings(c));
                     }
                  }
                  dsBuilder.channel(displayChannelIndex, chBuilder.build());
                  displayChannelIndex++;
               }
            }
            if (displayChannelIndex == 1) {
               dsBuilder.colorModeGrayscale();
            } else if (displayChannelIndex > 1) {
               dsBuilder.colorModeComposite();
            }
         } else {
            // Single-channel (including RGB) — load remembered settings for "Default"
            String channelGroup = settings.channelGroup();
            String channelName = "Default";
            org.micromanager.display.ChannelDisplaySettings remembered =
                  RememberedDisplaySettings.loadChannel(
                        studio_, channelGroup, channelName, null);
            if (remembered != null) {
               dsBuilder.channel(0, remembered);
               displayChannelIndex = 1;
            }
            dsBuilder.colorModeGrayscale();
         }
         if (mm2Viewer_ != null) {
            mm2Viewer_.setDisplaySettings(dsBuilder.build());
         }
      } catch (Exception e) {
         studio_.logs().logError(e, "Explorer: failed to init display settings");
      }
   }

   private void applyDisplaySettingsHeuristics(JSONObject summaryMetadata) {
      try {
         List<String> channelNames = new ArrayList<>();
         final String channelGroup = summaryMetadata.optString("ChGroup", "");
         if (summaryMetadata.has("ChNames")) {
            JSONArray chNames = summaryMetadata.getJSONArray("ChNames");
            for (int i = 0; i < chNames.length(); i++) {
               channelNames.add(chNames.getString(i));
            }
         } else {
            LinkedHashSet<String> seen = new LinkedHashSet<>();
            for (HashMap<String, Object> axes : storage_.getAxesSet()) {
               Object ch = axes.get("channel");
               if (ch != null) {
                  seen.add(ch.toString());
               }
            }
            channelNames.addAll(seen);
         }

         if (channelNames.isEmpty() || mm2Viewer_ == null) {
            return;
         }

         JSONObject storedNDVSettings = storage_.getDisplaySettings();
         DisplaySettings.Builder dsBuilder = studio_.displays().displaySettingsBuilder();
         dsBuilder = channelNames.size() > 1
               ? dsBuilder.colorModeComposite() : dsBuilder.colorModeGrayscale();
         for (int i = 0; i < channelNames.size(); i++) {
            String name = channelNames.get(i);
            Color color = null;
            if (storedNDVSettings != null) {
               try {
                  color = new Color(storedNDVSettings.getJSONObject(name).getInt("Color"));
               } catch (Exception e) {
                  // Ignore
               }
            }
            if (color == null || color.equals(Color.WHITE)) {
               org.micromanager.display.ChannelDisplaySettings remembered =
                     RememberedDisplaySettings.loadChannel(studio_, channelGroup, name, null);
               if (remembered != null && !remembered.getColor().equals(Color.WHITE)) {
                  color = remembered.getColor();
               }
            }
            if (color == null || color.equals(Color.WHITE)) {
               Color guessed = ColorPalettes.guessColor(name);
               if (!guessed.equals(Color.WHITE)) {
                  color = guessed;
               }
            }
            if (color == null || color.equals(Color.WHITE)) {
               color = ColorPalettes.getFromDefaultPalette(i);
            }
            dsBuilder.channel(i,
                  studio_.displays().channelDisplaySettingsBuilder()
                        .color(color).build());
         }
         mm2Viewer_.setDisplaySettings(dsBuilder.build());
      } catch (Exception e) {
         studio_.logs().logError(e, "Explorer: failed to apply display settings heuristics");
      }
   }

   private void writeSettingsToTempDir() {
      if (storage_ == null) {
         return;
      }
      String diskLocation = storage_.getDiskLocation();
      if (diskLocation == null) {
         return;
      }
      File dataDir = new File(diskLocation);
      if (!dataDir.exists()) {
         return;
      }
      try {
         if (pendingMMDisplaySettings_ != null) {
            File f = new File(dataDir, MM_DISPLAY_SETTINGS_FILE);
            ((DefaultDisplaySettings) pendingMMDisplaySettings_).save(f);
         }
         if (pendingViewState_ != null) {
            File f = new File(dataDir, VIEW_STATE_FILE);
            Files.write(f.toPath(),
                    pendingViewState_.toString(2).getBytes(StandardCharsets.UTF_8));
         }
      } catch (Exception e) {
         studio_.logs().logError(e, "Explorer: failed to write settings to data dir");
      }
   }

   private static JSONObject captureViewState(TiledDataViewerAPI viewer) {
      Point2D.Double offset = viewer.getViewOffset();
      Point2D.Double displaySize = viewer.getDisplayImageSize();
      Point2D.Double sourceSize = viewer.getFullResSourceDataSize();
      JSONObject json = new JSONObject();
      try {
         json.put("xView", offset.x);
         json.put("yView", offset.y);
         if (displaySize.x > 0 && sourceSize.x > 0) {
            json.put("magnification", displaySize.x / sourceSize.x);
         }
      } catch (mmcorej.org.json.JSONException e) {
         return null;
      }
      return json;
   }

   private static JSONObject loadViewState(File file) {
      if (!file.canRead()) {
         return null;
      }
      try {
         byte[] bytes = Files.readAllBytes(file.toPath());
         return new JSONObject(new String(bytes, StandardCharsets.UTF_8));
      } catch (Exception e) {
         return null;
      }
   }
}
