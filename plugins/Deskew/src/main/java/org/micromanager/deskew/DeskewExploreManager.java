package org.micromanager.deskew;

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import com.google.common.eventbus.Subscribe;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.RememberedDisplaySettings;
import org.micromanager.internal.utils.ColorPalettes;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.lightsheet.StackResampler;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.ndviewer2.NDViewer2API;
import org.micromanager.ndviewer2.NDViewer2AcqInterface;
import org.micromanager.ndviewer2.NDViewer2DataProviderAPI;
import org.micromanager.ndviewer2.NDViewer2DataViewerAPI;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.ndviewer2.NDViewer2Factory;

/**
 * Manages the Deskew Explore session.
 * Coordinates between the GUI, NDViewer, storage, and acquisition.
 */
public class DeskewExploreManager {

   private static final int SAVING_QUEUE_SIZE = 30;
   private static final String MM_DISPLAY_SETTINGS_FILE = "mm_display_settings.json";
   private static final String VIEW_STATE_FILE = "view_state.json";

   private final Studio studio_;
   private final DeskewFrame frame_;
   private final DeskewFactory deskewFactory_;

   private NDViewer2API viewer_;
   private NDViewer2DataViewerAPI mm2Viewer_;
   private NDViewer2DataProviderAPI mm2DataProvider_;
   private NDTiffStorage storage_;
   private DeskewExploreDataSource dataSource_;
   private ExecutorService displayExecutor_;
   private ExecutorService acquisitionExecutor_;
   private ScheduledExecutorService stagePollingExecutor_;

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
   // MM display settings captured before viewer is nulled
   private DisplaySettings pendingMMDisplaySettings_ = null;
   // View offset/zoom captured before viewer is nulled
   private JSONObject pendingViewState_ = null;
   private String storageDir_;
   private String acqName_;

   // Stage position tracking for multi-tile acquisition
   private double initialStageX_ = 0;
   private double initialStageY_ = 0;
   private double pixelSizeUm_ = 1.0;
   private double overlapPercentage_ = 10.0;  // Percentage overlap between tiles (0-50)
   private volatile boolean acquisitionInterrupted_ = false;
   // Counts tile-batch tasks currently queued or running in acquisitionExecutor_
   private final AtomicInteger pendingBatches_ = new AtomicInteger(0);

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
         stagePollingExecutor_ = Executors.newSingleThreadScheduledExecutor(r ->
                 new Thread(r, "Deskew Explore stage polling"));

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
         // Read overlap percentage from settings
         overlapPercentage_ = studio_.profile().getSettings(DeskewFrame.class)
                 .getInteger(DeskewFrame.EXPLORE_OVERLAP_PERCENT, 10);

         // Estimate tile dimensions based on camera size
         // (actual size will be determined after first deskew)
         estimatedTileWidth_ = imageWidth;
         estimatedTileHeight_ = imageHeight;
         // Set initial estimated dimensions on data source for first-click support
         updateTileDimensionsForRotation();

         // Create summary metadata for storage and viewer
         // Use estimated tile dimensions (will be updated after first acquisition)
         JSONObject summaryMetadata = new JSONObject();
         summaryMetadata.put("Width", estimatedTileWidth_);
         summaryMetadata.put("Height", estimatedTileHeight_);
         summaryMetadata.put("PixelSize_um", pixelSizeUm_);
         summaryMetadata.put("BitDepth", bitDepth_);
         summaryMetadata.put("PixelType", bitDepth_ <= 8 ? "GRAY8" : "GRAY16");
         // Calculate pixel overlap based on camera dimensions
         // For projected images: width = camera width, so overlapX is correct
         // For height: use same overlap pixels as X for now (will be updated after first tile)
         int overlapX = (int) Math.round(estimatedTileWidth_ * overlapPercentage_ / 100.0);
         int overlapY = overlapX; // Use same pixel overlap as X for now

         // Account for rotation: swap overlap if rotated 90° or 270°
         int rotateDegrees = studio_.profile().getSettings(DeskewFrame.class)
                 .getInteger(DeskewFrame.EXPLORE_ROTATE, 0);
         if (rotateDegrees == 90 || rotateDegrees == 270) {
            int temp = overlapX;
            overlapX = overlapY;
            overlapY = temp;
         }

         // Store metadata normally: X for X, Y for Y
         summaryMetadata.put("GridPixelOverlapX", overlapX);
         summaryMetadata.put("GridPixelOverlapY", overlapY);

         // Add channel metadata from MDA settings for Inspector display
         SequenceSettings settings = studio_.acquisitions().getAcquisitionSettings();
         if (settings.useChannels() && settings.channels().size() > 0) {
            JSONArray channelNames = new JSONArray();
            // Only include enabled channels (useChannel() == true)
            for (int i = 0; i < settings.channels().size(); i++) {
               if (settings.channels().get(i).useChannel()) {
                  channelNames.put(settings.channels().get(i).config());
               }
            }
            if (channelNames.length() > 0) {
               summaryMetadata.put("ChNames", channelNames);
               summaryMetadata.put("Channels", channelNames.length());
               summaryMetadata.put("ChGroup", settings.channelGroup());
            }
         }

         // Initialize storage immediately so NDViewer has something to work with
         // Pass overlap values in Magellan order: (overlapX, overlapY)
         // Both values are based on camera width for now (height-based value unknown
         // until first tile)
         storage_ = new NDTiffStorage(storageDir_, acqName_, summaryMetadata,
                 overlapX, overlapY, true, null,
                 SAVING_QUEUE_SIZE, null, true);
         dataSource_.setStorage(storage_);

         // Create NDViewer2 (NDViewer + MM Inspector)
         mm2DataProvider_ = NDViewer2Factory.createDataProvider(studio_.data(), storage_, acqName_);
         NDViewer2AcqInterface acqInterface = createAcqInterface();
         mm2Viewer_ = NDViewer2Factory.createDataViewer(
               studio_, dataSource_, acqInterface, mm2DataProvider_,
               summaryMetadata, pixelSizeUm_, false);
         mm2Viewer_.setAccumulateStats(true);

         // Initialize DisplaySettings with channels for Inspector and histogram support
         // Filter to only include enabled channels (useChannel() == true)
         if (settings.useChannels() && settings.channels().size() > 0) {
            org.micromanager.display.DisplaySettings.Builder dsBuilder =
                  studio_.displays().displaySettingsBuilder();
            // Count enabled channels and collect their settings
            int displayChannelIndex = 0;
            for (int i = 0; i < settings.channels().size(); i++) {
               if (settings.channels().get(i).useChannel()) {
                  String channelGroup = settings.channelGroup();
                  String channelName = settings.channels().get(i).config();
                  Color channelColor = settings.channels().get(i).color();
                  // Build ChannelDisplaySettings directly using MDA color,
                  // bypassing RememberedDisplaySettings which prefers persisted colors
                  dsBuilder.channel(displayChannelIndex,
                        studio_.displays().channelDisplaySettingsBuilder()
                              .groupName(channelGroup)
                              .name(channelName)
                              .color(channelColor)
                              .build());
                  displayChannelIndex++;
               }
            }
            if (displayChannelIndex == 1) {
               dsBuilder.colorModeGrayscale();
            } else if (displayChannelIndex > 1) {
               dsBuilder.colorModeComposite();
            }
            if (displayChannelIndex > 0) {
               mm2Viewer_.setDisplaySettings(dsBuilder.build());
            }
         }

         viewer_ = mm2Viewer_.getNDViewer();
         dataSource_.setViewer(viewer_);
         viewer_.setWindowTitle("Deskew Explore - Right-click to select, "
               + "Left-drag to extend, Left-click to acquire");

         // Set up overlayer and mouse listener
         // Route through mm2Viewer_ so the bridge plugin chains this as externalOverlayerPlugin_
         mm2Viewer_.setOverlayerPlugin(dataSource_);
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

         startStagePositionPolling();
         studio_.events().registerForEvents(this);

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
         stagePollingExecutor_ = Executors.newSingleThreadScheduledExecutor(r ->
                 new Thread(r, "Deskew Explore stage polling"));

         // Create data source
         dataSource_ = new DeskewExploreDataSource(this);
         dataSource_.setReadOnly(true);

         // Open existing storage in write-append mode so pyramid levels can be built on demand
         storage_ = new NDTiffStorage(dir, SAVING_QUEUE_SIZE, null);
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

         // Read overlap from metadata normally
         int overlapX = summaryMetadata.optInt("GridPixelOverlapX", 0);
         int overlapY = summaryMetadata.optInt("GridPixelOverlapY", 0);
         // Calculate overlap percentage from pixel values (use X overlap)
         // Note: Will get actual tile dimensions later; this is a first estimate
         if (imageWidth > 0 && overlapX > 0) {
            overlapPercentage_ = (overlapX * 100.0) / imageWidth;
         } else {
            overlapPercentage_ = 0.0;  // No overlap in this dataset
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
         mm2DataProvider_ = NDViewer2Factory.createDataProvider(studio_.data(), storage_, acqName_);
         NDViewer2AcqInterface acqInterface = createAcqInterface();
         mm2Viewer_ = NDViewer2Factory.createDataViewer(
               studio_, dataSource_, acqInterface, mm2DataProvider_,
               summaryMetadata, pixelSizeUm_, false);
         mm2Viewer_.setAccumulateStats(true);

         // Initialize MM DisplaySettings.
         // Priority: saved mm_display_settings.json (full MM settings incl. autostretch/bit-depth)
         // > per-channel color heuristics (NDViewer JSON → RememberedDisplaySettings
         //   → ColorPalettes.guessColor → ColorPalettes.getFromDefaultPalette).
         // savedMMSettings is declared here so the display executor lambda can capture it.
         File mmSettingsFile = new File(dir, MM_DISPLAY_SETTINGS_FILE);
         final DisplaySettings savedMMSettings = mmSettingsFile.canRead()
               ? DefaultDisplaySettings.getSavedDisplaySettings(mmSettingsFile) : null;
         try {
            if (savedMMSettings != null) {
               mm2Viewer_.setDisplaySettings(savedMMSettings);
            } else {
               // No saved MM settings — build from color heuristics.
               // Collect channel names in order from ChNames or from axes set.
               List<String> channelNames = new ArrayList<>();
               String channelGroup = summaryMetadata.optString("ChGroup", "");
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

               if (!channelNames.isEmpty()) {
                  JSONObject storedNDVSettings = storage_.getDisplaySettings();
                  DisplaySettings.Builder dsBuilder = studio_.displays().displaySettingsBuilder();
                  dsBuilder = channelNames.size() > 1
                        ? dsBuilder.colorModeComposite() : dsBuilder.colorModeGrayscale();
                  for (int i = 0; i < channelNames.size(); i++) {
                     String name = channelNames.get(i);
                     Color color = null;
                     // 1. Try stored NDViewer display_settings.txt
                     if (storedNDVSettings != null) {
                        try {
                           color = new Color(
                                 storedNDVSettings.getJSONObject(name).getInt("Color"));
                        } catch (Exception e) {
                           studio_.logs().logError(e);
                        }
                     }
                     // 2. Try MM RememberedDisplaySettings
                     if (color == null || color.equals(Color.WHITE)) {
                        org.micromanager.display.ChannelDisplaySettings remembered =
                              RememberedDisplaySettings.loadChannel(
                                       studio_, channelGroup, name, null);
                        if (remembered != null && !remembered.getColor().equals(Color.WHITE)) {
                           color = remembered.getColor();
                        }
                     }
                     // 3. Guess from channel name (wavelength-based)
                     if (color == null || color.equals(Color.WHITE)) {
                        Color guessed = ColorPalettes.guessColor(name);
                        if (!guessed.equals(Color.WHITE)) {
                           color = guessed;
                        }
                     }
                     // 4. Fall back to MM default palette by index
                     if (color == null || color.equals(Color.WHITE)) {
                        color = ColorPalettes.getFromDefaultPalette(i);
                     }
                     dsBuilder.channel(i,
                           studio_.displays().channelDisplaySettingsBuilder()
                                 .color(color).build());
                  }
                  mm2Viewer_.setDisplaySettings(dsBuilder.build());
               }
            }
         } catch (Exception e) {
            studio_.logs().logError(e, "Failed to initialize DisplaySettings");
         }

         viewer_ = mm2Viewer_.getNDViewer();
         dataSource_.setViewer(viewer_);
         viewer_.setWindowTitle("Deskew Explore - " + acqName_);

         // Set up overlayer and mouse listener
         // Route through mm2Viewer_ so the bridge plugin chains this as externalOverlayerPlugin_
         mm2Viewer_.setOverlayerPlugin(dataSource_);
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

         // Load saved view state (pan offset + zoom) — will be applied after
         // initializeViewerToLoaded.
         File viewStateFile = new File(dir, VIEW_STATE_FILE);
         final JSONObject savedViewState = loadViewState(viewStateFile);

         // Trigger initial display and seed the histogram for all stored images.
         if (displayExecutor_ != null && viewer_ != null) {
            displayExecutor_.submit(() -> {
               try {
                  // Pick one representative image per channel from the first tile found in
                  // storage, then feed it through the full histogram pipeline so the
                  // Inspector panel is created and stats are computed on open.
                  List<Image> seedImages = new ArrayList<>();
                  List<HashMap<String, Object>> seedAxesList = new ArrayList<>();
                  Set<Object> seenChannels = new LinkedHashSet<>();
                  for (HashMap<String, Object> axes : storage_.getAxesSet()) {
                     Object ch = axes.get("channel");
                     if (!seenChannels.add(ch == null ? "" : ch)) {
                        continue; // already have a representative for this channel
                     }
                     // Build channel-only axes for histogram registration (row/column not needed)
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
                                 "Deskew Explore: exception fetching seed image: " + e);
                     }
                  }
                  if (mm2Viewer_ != null && !seedImages.isEmpty()) {
                     mm2Viewer_.newTileArrived(seedImages, seedAxesList);
                  }
                  // Initialize NDViewer from the data source's image keys — this registers
                  // all channels, sets up scrollbars, and triggers the first canvas render.
                  viewer_.initializeViewerToLoaded(null);
                  viewer_.update();

                  // Restore view state after the canvas is initialized and sized.
                  // Restore saved view state after the canvas is initialized.
                  if (savedViewState != null && viewer_ != null) {
                     double mag = savedViewState.optDouble("magnification", 0);
                     if (mag > 0) {
                        // Derive source size from canvas size and saved magnification.
                        // This is already aspect-correct since newW/newH are both derived
                        // from the canvas dimensions.
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
                  studio_.logs().logMessage("Deskew Explore: NPE in histogram seed: " + e);
               }
            });
         }

         studio_.logs().logMessage("Deskew Explore: opened dataset from " + dir);
         studio_.events().registerForEvents(this);

      } catch (Exception e) {
         studio_.logs().logMessage("Deskew Explore: openExplore EXCEPTION: " + e);
         studio_.logs().showError(e, "Failed to open Deskew Explore dataset.");
         stopExplore(false);  // Never delete existing data on open failure
      }
   }

   /**
    * Create an NDViewer2AcqInterface for the explore session.
    */
   private NDViewer2AcqInterface createAcqInterface() {
      return new NDViewer2AcqInterface() {
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
    * Deletes temporary storage only for new (non-loaded) sessions.
    */
   public void stopExplore() {
      stopExplore(!loadedData_);
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
      try {
         studio_.events().unregisterForEvents(this);
      } catch (Exception ignored) {
         // Not registered — safe to ignore
      }
      exploring_ = false;
      loadedData_ = false;
      acquisitionInterrupted_ = true;
      pendingBatches_.set(0);
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

      // Capture MM DisplaySettings and view state so we can persist them alongside the dataset.
      // mm2Viewer_/viewer_ may already be null if onViewerClosed() ran first (loaded-data
      // close path), in which case pending* fields were captured there before the viewer was
      // nulled.
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
            // join/callback, so we use a best-effort delay. 1000ms is sufficient
            // in practice, but if NDViewer adds a close-completion callback
            // this sleep should be replaced.
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
                        // Save full MM display settings (autostretch, bit-depth, colors, etc.)
                        // to mm_display_settings.json alongside the dataset.
                        File mmSettingsFile = new File(diskLocation, MM_DISPLAY_SETTINGS_FILE);
                        ((DefaultDisplaySettings) mmDisplaySettingsToSave).save(mmSettingsFile);
                     }
                     if (viewStateToSave != null) {
                        // Save view offset and zoom to view_state.json alongside the dataset.
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
               // Additional delay after closing storage to allow memory-mapped
               // file buffers to release (especially important on Windows)
               try {
                  Thread.sleep(500);
               } catch (InterruptedException ignored) {
                  Thread.currentThread().interrupt();
               }
               deleteTempStorage();
            }
         }, "Deskew Explore cleanup").start();
      }
   }

   /**
    * Called when the application is shutting down.
    * Triggers the same save/discard prompt as closing the viewer window.
    * Cancels shutdown if the user picks "Cancel".
    */
   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent event) {
      if (event.isCanceled() || !exploring_) {
         return;
      }
      onViewerClosed();
      // If the session is still open (user cancelled the prompt), cancel shutdown.
      if (exploring_) {
         event.cancelShutdown();
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

      // Capture MM display settings and view state before nulling viewer references so
      // stopExplore() can save them.
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
         // Loop until the user makes a definitive choice (save completed, discard, or cancel).
         // If the user picks "Save" but then cancels the file chooser, re-show this dialog.
         while (true) {
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
               // Cancel — keep data in temp storage and stop without deleting
               studio_.logs().logMessage("Deskew Explore: close cancelled, data remains in: "
                        + storageDir_);
               stopExplore(false);
               return;
            } else if (choice == 0) {
               // Save — let user choose location; loop back if they cancel the file chooser
               JFileChooser chooser = new JFileChooser();
               chooser.setDialogTitle("Save Deskew Explore Data");
               chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
               chooser.setSelectedFile(new File(acqName_));

               if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) {
                  File destDir = chooser.getSelectedFile();
                  // Write view state and display settings into the temp directory
                  // before copying, so they are included in the saved dataset.
                  writeSettingsToTempDir();
                  saveDataTo(destDir);
                  break;  // Save completed — fall through to delete temp files
               }
               // File chooser cancelled — re-show the save/discard/cancel dialog
            } else {
               // Discard
               break;  // Fall through to delete temp files
            }
         }
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
    * Uses retry with exponential backoff for files that may still be held
    * by memory-mapped buffers (common on Windows).
    */
   private void deleteTempStorage() {
      if (storageDir_ == null) {
         return;
      }

      try {
         File dir = new File(storageDir_);
         if (dir.exists()) {
            // Collect paths first, then delete in reverse order (files before directories)
            List<Path> pathsToDelete;
            try (Stream<Path> stream = Files.walk(dir.toPath())) {
               pathsToDelete = stream.sorted(Comparator.reverseOrder())
                       .collect(java.util.stream.Collectors.toList());
            }

            // Delete each path with retry logic for locked files
            for (Path path : pathsToDelete) {
               deleteWithRetry(path);
            }
            studio_.logs().logMessage("Deskew Explore: deleted temp storage at " + storageDir_);
         }
      } catch (IOException e) {
         studio_.logs().logError(e, "Failed to delete temp storage: " + storageDir_);
      }
   }

   /**
    * Attempts to delete a file/directory with retry and exponential backoff.
    * On Windows, memory-mapped files may hold locks briefly after close().
    */
   private void deleteWithRetry(Path path) {
      int maxRetries = 5;
      long delayMs = 500;

      for (int attempt = 1; attempt <= maxRetries; attempt++) {
         try {
            Files.delete(path);
            return; // Success
         } catch (IOException e) {
            if (attempt == maxRetries) {
               studio_.logs().logError(e, "Failed to delete after " + maxRetries
                     + " attempts: " + path);
               return;
            }
            // Wait before retry, with exponential backoff
            try {
               Thread.sleep(delayMs);
               delayMs *= 2; // Double the delay for next attempt
            } catch (InterruptedException ie) {
               Thread.currentThread().interrupt();
               return;
            }
         }
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

            // Preserve channel settings from MDA
            if (settings.useChannels()) {
               sb.useChannels(true);
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

            // Process through our own deskew to get XY projection(s)
            List<Image> projectedImages = processStackThroughDeskew(testStore);

            if (projectedImages == null || projectedImages.isEmpty()) {
               studio_.logs().showError("Deskew processing failed.");
               try {
                  testStore.freeze();
               } catch (IOException ignored) {
                  studio_.logs().logError("Ignoring IO Exception in DeskewExploreManager");
               }
               testStore.close();
               return;
            }

            // If this is the first acquisition, update tile dimensions (use first channel)
            if (projectedWidth_ < 0) {
               Image firstChannel = projectedImages.get(0);
               // Store the pre-rotation dimensions so updateTileDimensionsForRotation
               // can correctly swap them when rotation is 90° or 270°.
               int rotateDegrees = frame_.getSettings().getInteger(
                       DeskewFrame.EXPLORE_ROTATE, 0);
               if (rotateDegrees == 90 || rotateDegrees == 270) {
                  projectedWidth_ = firstChannel.getHeight();
                  projectedHeight_ = firstChannel.getWidth();
               } else {
                  projectedWidth_ = firstChannel.getWidth();
                  projectedHeight_ = firstChannel.getHeight();
               }
               updateTileDimensionsForRotation();
            }

            // Store each channel separately with channel axis
            // Get channel names from source datastore's SummaryMetadata
            SummaryMetadata summaryMeta = testStore.getSummaryMetadata();

            // Collect axes and images from stored images for viewer notification
            List<HashMap<String, Object>> storedAxes = new ArrayList<>();
            List<Image> storedImages = new ArrayList<>();
            for (Image projectedImage : projectedImages) {
               int channelIndex = projectedImage.getCoords().getChannel();
               // Get channel name from SummaryMetadata (with safe fallback)
               String channelName = summaryMeta.getSafeChannelName(channelIndex);
               HashMap<String, Object> axes = storeProjectedImage(projectedImage, row, col,
                     channelName);
               if (axes != null) {
                  storedAxes.add(axes);
                  storedImages.add(projectedImage);
               }
            }

            // Mark tile as acquired
            dataSource_.markTileAcquired(row, col);

            // Notify viewer of new images (one per channel)
            if (displayExecutor_ != null && viewer_ != null && !storedAxes.isEmpty()) {
               final List<Image> tileImages = storedImages;
               // Strip row/col from axes — channel name is what NDViewer/AxesBridge needs
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
                  // Notify data provider per-channel (needed for DataProviderHasNewImageEvent)
                  for (int i = 0; i < tileImages.size() && i < displayAxesList.size(); i++) {
                     if (mm2DataProvider_ != null) {
                        mm2DataProvider_.newImageArrived(tileImages.get(i), displayAxesList.get(i));
                     }
                     try {
                        viewer_.newImageArrived(displayAxesList.get(i));
                     } catch (NullPointerException e) {
                        // NDViewer histogram not yet initialized - ignore
                     }
                  }
                  // Notify viewer with all channels at once so they are submitted
                  // as a single stats request (avoids sequence-number conflicts)
                  if (mm2Viewer_ != null) {
                     mm2Viewer_.newTileArrived(tileImages, displayAxesList);
                  }
                  try {
                     viewer_.update();
                  } catch (NullPointerException e) {
                     // NDViewer histogram not yet initialized - ignore
                  }
                  // Remove from pending overlay now that the image is being displayed
                  dataSource_.removePendingTile(tileRow, tileCol);
                  redrawOverlay();
               });
            } else {
               // No display submission — remove from pending immediately
               dataSource_.removePendingTile(row, col);
               redrawOverlay();
            }

            // Clean up test store - freeze first to prevent "save" dialogs
            try {
               testStore.freeze();
            } catch (IOException ignored) {
               studio_.logs().logError("Ignoring IO Exception in DeskewExploreManager");
            }
            testStore.close();

         } catch (Exception e) {
            studio_.logs().logError(e, "Deskew Explore: error acquiring tile");
         }
      });
   }

   /**
    * Signals all queued and running tile-batch acquisitions to stop after the current
    * tile finishes, and clears the pending-tile overlay.
    */
   public void interruptAcquisition() {
      acquisitionInterrupted_ = true;
      if (dataSource_ != null) {
         dataSource_.clearPendingTiles();
         redrawOverlay();
      }
   }

   /**
    * Acquires multiple tiles sequentially, moving the stage between positions.
    * May be called while a previous batch is still running; the new batch is queued
    * and will start as soon as the previous one finishes (or is interrupted).
    * Each tile position is calculated relative to the initial stage position when explore started.
    *
    * @param tiles List of tile positions as (row, col) Points
    */
   public void acquireMultipleTiles(List<Point> tiles) {
      if (!exploring_ || acquisitionExecutor_ == null || tiles.isEmpty()) {
         return;
      }

      // Clear any prior interrupt so this new batch (and any already-queued batches
      // that haven't started yet) will run.  Do this before incrementing the counter
      // so there is no window where a batch could see a stale interrupted flag.
      acquisitionInterrupted_ = false;

      int batchCount = pendingBatches_.incrementAndGet();
      if (batchCount == 1) {
         // First batch entering the queue — flip UI to in-progress
         dataSource_.setAcquisitionInProgress(true);
         frame_.setAcquisitionInProgress(true);
      }

      // Mark all tiles in this batch as pending immediately so the blue overlay
      // appears before the executor picks up the task.
      for (Point tile : tiles) {
         dataSource_.addPendingTile(tile.x, tile.y);
      }
      redrawOverlay();

      acquisitionExecutor_.submit(() -> {
         try {
            // Get the projected tile dimensions (use estimated if not yet determined)
            int tileWidth = projectedWidth_ > 0 ? projectedWidth_ : estimatedTileWidth_;
            int tileHeight = projectedHeight_ > 0 ? projectedHeight_ : estimatedTileHeight_;

            // Account for overlap: overlap pixel count is derived from X tile width only,
            // so both axes subtract the same number of pixels (not the same percentage).
            int overlapPixels = (int) Math.round(tileWidth * overlapPercentage_ / 100.0);
            double effectiveTileWidthUm = (tileWidth - overlapPixels) * pixelSizeUm_;
            double effectiveTileHeightUm = (tileHeight - overlapPixels) * pixelSizeUm_;

            for (Point tile : tiles) {
               if (acquisitionInterrupted_) {
                  break;
               }
               int row = tile.x;
               int col = tile.y;

               // Calculate target stage position relative to initial position.
               // Tile (0,0) is at the initial position.
               // Positive col -> move stage in +X direction
               // Positive row -> move stage in +Y direction
               double targetX = initialStageX_ + col * effectiveTileWidthUm;
               double targetY = initialStageY_ + row * effectiveTileHeightUm;

               studio_.core().setXYPosition(targetX, targetY);
               studio_.core().waitForDevice(studio_.core().getXYStageDevice());

               // Brief settle time after stage move. Most stages report
               // "ready" before vibrations fully dampen; 100ms is a
               // conservative default for piezo and motorized stages.
               Thread.sleep(100);

               // Acquire this tile (inline the acquisition logic to avoid executor issues)
               acquireSingleTileBlocking(row, col);
            }

         } catch (Exception e) {
            studio_.logs().logError(e, "Deskew Explore: error during multi-tile acquisition");
         } finally {
            if (pendingBatches_.decrementAndGet() == 0) {
               // Last batch finished — clear in-progress state
               dataSource_.setAcquisitionInProgress(false);
               frame_.setAcquisitionInProgress(false);
            }
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

         // Preserve channel settings from MDA
         if (settings.useChannels()) {
            sb.useChannels(true);
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

         if (testStore.getNumImages() == 0) {
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

         // Process through deskew to get XY projection(s)
         List<Image> projectedImages = processStackThroughDeskew(testStore);

         if (projectedImages == null || projectedImages.isEmpty()) {
            studio_.logs().showError("Deskew processing failed at row=" + row + ", col=" + col);
            try {
               testStore.freeze();
            } catch (IOException ignored) {
               studio_.logs().logError("IOException ignored in DeskewExploreManager");
            }
            testStore.close();
            return;
         }

         // Update tile dimensions if this is first acquisition (use first channel)
         if (projectedWidth_ < 0) {
            Image firstChannel = projectedImages.get(0);
            projectedWidth_ = firstChannel.getWidth();
            projectedHeight_ = firstChannel.getHeight();
            dataSource_.setTileDimensions(projectedWidth_, projectedHeight_);

            // Update overlap metadata with actual tile dimensions
            // Note: projectedWidth/Height are pre-rotation dimensions
            int overlapX = (int) Math.round(projectedWidth_ * overlapPercentage_ / 100.0);
            int overlapY = (int) Math.round(projectedHeight_ * overlapPercentage_ / 100.0);

            // Account for rotation: swap overlap if rotated 90° or 270°
            int rotateDegrees = frame_.getSettings().getInteger(DeskewFrame.EXPLORE_ROTATE, 0);
            int finalWidth = projectedWidth_;
            int finalHeight = projectedHeight_;
            if (rotateDegrees == 90 || rotateDegrees == 270) {
               int temp = overlapX;
               overlapX = overlapY;
               overlapY = temp;
               // Also swap width/height for viewer
               finalWidth = projectedHeight_;
               finalHeight = projectedWidth_;
            }

            // Update metadata with overlap and channel info
            try {
               JSONObject summaryMetadata = storage_.getSummaryMetadata();
               summaryMetadata.put("GridPixelOverlapX", overlapX);
               summaryMetadata.put("GridPixelOverlapY", overlapY);
               summaryMetadata.put("Width", finalWidth);
               summaryMetadata.put("Height", finalHeight);

               // Add channel metadata (for both single and multi-channel)
               // Only include enabled channels (useChannel() == true)
               if (settings.useChannels() && settings.channels().size() > 0) {
                  JSONArray channelNames = new JSONArray();
                  for (int i = 0; i < settings.channels().size(); i++) {
                     if (settings.channels().get(i).useChannel()) {
                        channelNames.put(settings.channels().get(i).config());
                     }
                  }
                  if (channelNames.length() > 0) {
                     summaryMetadata.put("ChNames", channelNames);
                     summaryMetadata.put("Channels", channelNames.length());
                  }
               }

            } catch (Exception e) {
               studio_.logs().logError(e, "Failed to update overlap metadata");
            }
         }

         // Store each channel separately with channel axis
         // Get channel names from source datastore's SummaryMetadata
         SummaryMetadata summaryMeta = testStore.getSummaryMetadata();

         // Collect axes and images from stored images for viewer notification
         List<HashMap<String, Object>> storedAxes = new ArrayList<>();
         List<Image> storedImages = new ArrayList<>();
         for (Image projectedImage : projectedImages) {
            int channelIndex = projectedImage.getCoords().getChannel();
            // Get channel name from SummaryMetadata (with safe fallback)
            String channelName = summaryMeta.getSafeChannelName(channelIndex);
            HashMap<String, Object> axes = storeProjectedImage(projectedImage, row, col,
                  channelName);
            if (axes != null) {
               storedAxes.add(axes);
               storedImages.add(projectedImage);
            }
         }

         // Mark tile as acquired
         dataSource_.markTileAcquired(row, col);

         // Notify viewer of new images (one per channel)
         if (displayExecutor_ != null && viewer_ != null && !storedAxes.isEmpty()) {
            final List<Image> tileImages = storedImages;
            // Strip row/col from axes — channel name is what NDViewer/AxesBridge needs
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
               // Notify data provider per-channel (needed for DataProviderHasNewImageEvent)
               for (int i = 0; i < tileImages.size() && i < displayAxesList.size(); i++) {
                  if (mm2DataProvider_ != null) {
                     mm2DataProvider_.newImageArrived(tileImages.get(i), displayAxesList.get(i));
                  }
                  try {
                     viewer_.newImageArrived(displayAxesList.get(i));
                  } catch (NullPointerException e) {
                     // NDViewer histogram not yet initialized - ignore
                  }
               }
               // Notify viewer with all channels at once so they are submitted
               // as a single stats request (avoids sequence-number conflicts)
               if (mm2Viewer_ != null) {
                  mm2Viewer_.newTileArrived(tileImages, displayAxesList);
               }
               try {
                  viewer_.update();
               } catch (NullPointerException e) {
                  // NDViewer histogram not yet initialized - ignore
               }
               // Remove from pending overlay now that the image is being displayed
               dataSource_.removePendingTile(tileRow, tileCol);
               redrawOverlay();
            });
         } else {
            // No display submission — remove from pending immediately
            dataSource_.removePendingTile(row, col);
            redrawOverlay();
         }

         // Clean up
         try {
            testStore.freeze();
         } catch (IOException ignored) {
            studio_.logs().logError("IOException ignored in DeskewExploreManager");
         }
         testStore.close();

      } catch (Exception e) {
         studio_.logs().logError(e, "Deskew Explore: error acquiring tile at row=" + row + ", col="
                  + col);
      }
   }

   /**
    * Processes a Z-stack through the deskew pipeline to produce an XY projection.
    * Handles multiple channels by processing each channel's Z-stack independently.
    *
    * @return List of projected images, one per channel
    */
   private List<Image> processStackThroughDeskew(Datastore source) {
      try {
         // Get deskew settings from frame
         double theta = Math.toRadians(NumberUtils.displayStringToDouble(
                 frame_.getSettings().getString(DeskewFrame.DEGREE, "20.0")));

         SummaryMetadata summaryMetadata = source.getSummaryMetadata();
         int nSlices = summaryMetadata.getIntendedDimensions().getZ();

         // Detect channels present in the datastore and group coordinates by channel
         List<Coords> allCoordsList = new ArrayList<>();
         source.getUnorderedImageCoords().forEach(allCoordsList::add);

         Map<Integer, List<Coords>> coordsByChannel = new HashMap<>();
         for (Coords c : allCoordsList) {
            int channel = c.getChannel();
            coordsByChannel.computeIfAbsent(channel, k -> new ArrayList<>()).add(c);
         }

         List<Image> projectedImages = new ArrayList<>();

         // Process each channel independently
         for (Map.Entry<Integer, List<Coords>> entry : coordsByChannel.entrySet()) {
            int channelIndex = entry.getKey();
            List<Coords> channelCoords = entry.getValue();

            // Get first image of this channel for dimensions
            Coords firstCoords = channelCoords.get(0);
            Image firstImage = source.getImage(firstCoords);

            double pixelSizeUm = firstImage.getMetadata().getPixelSizeUm();
            double zStepUm = summaryMetadata.getZStepUm();

            // Create resampler for this channel's XY projection
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
            } catch (Exception e) {
               studio_.logs().logError(e,
                     "Deskew Explore: failed to create StackResampler for channel " + channelIndex);
               throw e;
            }

            resampler.initializeProjections();

            // Start processing in background thread
            Runnable processing = resampler.startStackProcessing();
            Thread processingThread = new Thread(processing, "Deskew Explore Processing Ch"
                  + channelIndex);
            processingThread.start();

            // Build Z-stack for this channel only
            List<Image> zStack = new ArrayList<>();
            for (Coords c : channelCoords) {
               zStack.add(source.getImage(c));
            }

            // Sort by Z
            zStack.sort((a, b) -> Integer.compare(a.getCoords().getZ(), b.getCoords().getZ()));

            // Feed this channel's Z slices to the resampler
            for (int z = 0; z < zStack.size(); z++) {
               Image img = zStack.get(z);
               resampler.addToProcessImageQueue((short[]) img.getRawPixels(), z);
            }

            // Wait for processing to complete
            processingThread.join();

            resampler.finalizeProjections();

            // Get the YX projection for this channel
            short[] projectionPixels = resampler.getYXProjection();
            int width = resampler.getResampledShapeX();
            int height = resampler.getResampledShapeY();

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
               final int newWidth = height;
               final int newHeight = width;
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

            // Create output image WITH channel coordinate preserved
            Coords projectedCoords = firstImage.getCoords().copyBuilder()
                    .z(0)
                    .channel(channelIndex)
                    .build();

            Image result = studio_.data().createImage(
                    projectionPixels,
                    width,
                    height,
                    2, // bytes per pixel for 16-bit
                    1, // number of components
                    projectedCoords,
                    firstImage.getMetadata());
            projectedImages.add(result);
         }

         return projectedImages;

      } catch (ParseException e) {
         studio_.logs().logError(e, "Failed to parse deskew angle");
         return new ArrayList<>();
      } catch (IOException e) {
         studio_.logs().logError(e, "Failed to read image from datastore");
         return new ArrayList<>();
      } catch (Exception e) {
         studio_.logs().logError(e, "Unexpected error in deskew processing");
         return new ArrayList<>();
      }
   }

   /**
    * Stores a projected image at the specified tile position and channel.
    */
   private HashMap<String, Object> storeProjectedImage(Image image, int row, int col,
                                                       String channelName) {
      if (storage_ == null) {
         studio_.logs().logError("Deskew Explore: storage is null, cannot store image");
         return null;
      }

      try {
         // Create image metadata with row/col/channel axes
         JSONObject tags = new JSONObject();
         tags.put("ElapsedTime-ms", System.currentTimeMillis());
         tags.put("Width", image.getWidth());
         tags.put("Height", image.getHeight());
         tags.put("BitDepth", bitDepth_);
         tags.put("PixelType", bitDepth_ <= 8 ? "GRAY8" : "GRAY16");
         tags.put("PixelSizeUm", pixelSizeUm_);

         // Set up axes using AcqEngMetadata
         // Store channel as STRING (channel name) for NDViewer
         // AxesBridge will translate between string names and integer indices
         AcqEngMetadata.createAxes(tags);
         AcqEngMetadata.setAxisPosition(tags, "row", row);
         AcqEngMetadata.setAxisPosition(tags, "column", col);
         AcqEngMetadata.setAxisPosition(tags, "channel", channelName);

         HashMap<String, Object> axes = AcqEngMetadata.getAxes(tags);

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

         return axes;

      } catch (Exception e) {
         studio_.logs().logError(e, "Failed to store projected image");
         return null;
      }
   }

   // ===================== Stage position overlay =====================

   /**
    * Starts a background task that polls the stage position every 500 ms and
    * updates the red FOV rectangle in the overlay.
    */
   private void startStagePositionPolling() {
      stagePollingExecutor_.scheduleWithFixedDelay(() -> {
         if (dataSource_ == null) {
            return;
         }
         try {
            double stageX = studio_.core().getXPosition();
            double stageY = studio_.core().getYPosition();
            Point2D.Double pixel = stageToPixel(stageX, stageY);
            dataSource_.setStagePositionPixel(pixel);
            redrawOverlay();
         } catch (Exception e) {
            // Stage may not be available; silently skip this update
         }
      }, 0, 500, TimeUnit.MILLISECONDS);
   }

   /**
    * Converts a stage position (in microns) to full-resolution pixel coordinates
    * in NDTiffStorage display space, where tile (0,0) occupies [0, effectiveTileWidth).
    * Tile (0,0) center is at (initialStageX_, initialStageY_) in stage space and
    * at pixel (effectiveTileWidth/2, effectiveTileHeight/2).
    */
   private Point2D.Double stageToPixel(double stageX, double stageY) {
      int tileWidth = dataSource_.getTileWidth();
      int tileHeight = dataSource_.getTileHeight();
      if (tileWidth <= 0 || tileHeight <= 0 || pixelSizeUm_ <= 0) {
         return null;
      }
      int overlapPixels = (int) Math.round(tileWidth * overlapPercentage_ / 100.0);
      double effectiveTileWidth = tileWidth - overlapPixels;
      double effectiveTileHeight = tileHeight - overlapPixels;
      double pixelX = (stageX - initialStageX_) / pixelSizeUm_ + effectiveTileWidth / 2.0;
      double pixelY = (stageY - initialStageY_) / pixelSizeUm_ + effectiveTileHeight / 2.0;
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

            // The center of tile (0,0) is at pixel (effectiveTileWidth/2, effectiveTileHeight/2)
            // and corresponds to stage (initialStageX_, initialStageY_)
            int overlapPixels = (int) Math.round(tileWidth * overlapPercentage_ / 100.0);
            double effectiveTileWidth = tileWidth - overlapPixels;
            double effectiveTileHeight = tileHeight - overlapPixels;
            double offsetPixelX = pixelX - effectiveTileWidth / 2.0;
            double offsetPixelY = pixelY - effectiveTileHeight / 2.0;

            double targetX = initialStageX_ + offsetPixelX * pixelSizeUm_;
            double targetY = initialStageY_ + offsetPixelY * pixelSizeUm_;

            studio_.core().setXYPosition(targetX, targetY);
            studio_.core().waitForDevice(studio_.core().getXYStageDevice());

         } catch (Exception e) {
            studio_.logs().logError(e, "Deskew Explore: error moving stage");
         }
      });
   }

   /**
    * Writes pending display settings and view state into the storage directory
    * so they are included when the data is subsequently copied to its final location.
    * Must be called while storage_ is still open (before saveDataTo closes it).
    */
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
         studio_.logs().logError(e, "Failed to write settings to data dir");
      }
   }

   private static JSONObject captureViewState(NDViewer2API viewer) {
      Point2D.Double offset = viewer.getViewOffset();
      Point2D.Double displaySize = viewer.getDisplayImageSize();
      Point2D.Double sourceSize = viewer.getFullResSourceDataSize();
      JSONObject json = new JSONObject();
      try {
         json.put("xView", offset.x);
         json.put("yView", offset.y);
         // Save magnification (display pixels / source pixels) rather than raw source dims,
         // so that view-state restore works correctly regardless of canvas size at restore time.
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
