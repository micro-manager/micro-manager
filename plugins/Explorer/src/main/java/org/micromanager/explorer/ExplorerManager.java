package org.micromanager.explorer;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.text.DecimalFormat;
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
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import mmcorej.DeviceType;
import mmcorej.StrVector;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.AutofocusPlugin;
import org.micromanager.MultiStagePosition;
import org.micromanager.PositionList;
import org.micromanager.PropertyMap;
import org.micromanager.StagePosition;
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
import org.micromanager.internal.positionlist.utils.ZGenerator;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.ColorPalettes;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.ndtiffstorage.EssentialImageMetadata;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.propertymap.MutablePropertyMapView;
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

   // Z-axis decision locked at session start. The MDA slice settings can be toggled
   // mid-session, but the dataset's axes shape must stay consistent: TiledDataViewer
   // treats "z axis absent" as different from "z axis present at index 0", so mixing
   // tiles with and without a "z" axis makes earlier tiles disappear. We therefore
   // decide once, at session start, whether every tile in this session carries a
   // z axis, and reuse that decision for all tiles regardless of later MDA edits.
   private boolean sessionUseSlices_ = false;
   private boolean sessionIsZStack_ = false;
   // Slice positions captured at session start, applied to every tile acquisition so the
   // z-axis shape is independent of later MDA slice edits. Empty when not using slices.
   private java.util.ArrayList<Double> sessionSlices_ = new java.util.ArrayList<>();
   // Set to true after the first time we notify the user that MDA slice settings were
   // overridden; prevents showing the same dialog on every subsequent tile.
   private volatile boolean mdaSliceOverrideWarningShown_ = false;

   // Vessel outline: type selected in ExplorerFrame, anchor set at runtime.
   // Vessel axes are assumed parallel to stage X/Y (standard setup).
   private VesselType vesselType_ = VesselType.NONE;
   private VesselType.AnchorType vesselAnchorType_ = null;
   // For simple vessels: stage position of the named anchor point.
   // For multi-well plates: stage position of the plate top-left corner.
   private double vesselAnchorStageX_ = 0;
   private double vesselAnchorStageY_ = 0;
   private int vesselAnchorWellRow_ = 0;
   private int vesselAnchorWellCol_ = 0;
   private boolean vesselUsedHcsCal_ = false;
   // True when anchor is set but stageToPixel() was not ready; retry after first tile.
   private boolean vesselOutlinePending_ = false;

   // Create-Positions: number of regions already committed this session (for "Reg<n>" labels).
   private int regionCounter_ = 0;

   // Refine-Z state. Reference points (stage XY + measured Z per checked Z stage), the chosen
   // interpolation method, and the pending manual tile. Read on the EDT; the automatic collection
   // runs on a SwingWorker. Guarded where shared.
   private final java.util.List<RefineZPoint> refineZPoints_ = new java.util.ArrayList<>();
   private ZGenerator.Type refineZMethod_ = ZGenerator.Type.SHEPINTERPOLATE;
   private RefineZWorker refineZWorker_ = null;
   // The Refine Z window while it is open (status/running route here), else null.
   private RefineZFrame refineZFrame_ = null;

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

         // Lock the z-axis decision for the whole session (see field comment). Editing
         // the MDA slice settings after this point will not change the dataset's axes
         // shape, so previously acquired tiles never disappear.
         SequenceSettings startSettings = studio_.acquisitions().getAcquisitionSettings();
         sessionUseSlices_ = startSettings.useSlices() && startSettings.slices().size() > 0;
         sessionIsZStack_ = sessionUseSlices_ && startSettings.slices().size() > 1;
         sessionSlices_ = sessionUseSlices_
                 ? new java.util.ArrayList<>(startSettings.slices())
                 : new java.util.ArrayList<>();
         mdaSliceOverrideWarningShown_ = false;

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

         // Restore vessel type from profile; anchor is always re-set per session.
         String savedVessel = studio_.profile().getSettings(ExplorerFrame.class)
               .getString(ExplorerFrame.VESSEL_TYPE, VesselType.NONE.getName());
         vesselType_ = VesselType.builtIn().stream()
               .filter(v -> v.getName().equals(savedVessel))
               .findFirst().orElse(VesselType.NONE);
         vesselAnchorType_ = null;
         vesselUsedHcsCal_ = false;
         vesselOutlinePending_ = false;
         regionCounter_ = 0;
         synchronized (refineZPoints_) {
            refineZPoints_.clear();
         }
         frame_.setExploringActive(true, true);
         if (vesselType_.isMultiWell()) {
            Point2D.Double hcsOffset = tryReadHcsCalibration();
            frame_.setHcsCalibrationStatus(hcsOffset != null);
            if (hcsOffset != null) {
               applyHcsCalibration(hcsOffset);
            }
         } else {
            frame_.setHcsCalibrationStatus(false);
         }

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

         // Load saved MM display settings or derive from heuristics. Applying these is
         // deferred until after initializeViewerToLoaded() has registered the channels
         // (below), so the settings attach to channels that already exist in the display
         // model. The channel NAMES shown in the Inspector and overlay dropdowns come from
         // each channel's DisplaySettings .getName() (see applyDisplaySettingsHeuristics,
         // which now sets .name(...)), not from the channel scrollbar.
         File mmSettingsFile = new File(dir, MM_DISPLAY_SETTINGS_FILE);
         final DisplaySettings savedMMSettings = mmSettingsFile.canRead()
               ? DefaultDisplaySettings.getSavedDisplaySettings(mmSettingsFile) : null;

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
                  // Register the (named) channels in the display model FIRST.
                  viewer_.initializeViewerToLoaded(null);
                  // Now that channels exist, apply display settings so they attach to the
                  // named channels (colors, contrast). Done here -- not synchronously during
                  // construction -- so the channel names are not lost.
                  try {
                     if (savedMMSettings != null) {
                        mm2Viewer_.setDisplaySettings(savedMMSettings);
                     } else {
                        applyDisplaySettingsHeuristics(summaryMetadata);
                     }
                  } catch (Exception e) {
                     studio_.logs().logError(e, "Failed to initialize DisplaySettings");
                  }
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
      frame_.setExploringActive(false, false);
      vesselAnchorType_ = null;
      vesselUsedHcsCal_ = false;
      vesselOutlinePending_ = false;
      if (dataSource_ != null) {
         dataSource_.clearPendingTiles();
         dataSource_.clearVesselOutline();
         dataSource_.setPositionTool(ExplorerDataSource.PositionTool.NONE);
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

         // Detect if the user changed MDA slice settings mid-session and warn once.
         if (!mdaSliceOverrideWarningShown_) {
            boolean currentUseSlices = settings.useSlices() && !settings.slices().isEmpty();
            boolean slicesDiffer = (currentUseSlices != sessionUseSlices_)
                    || (sessionUseSlices_ && !settings.slices().equals(sessionSlices_));
            if (slicesDiffer) {
               mdaSliceOverrideWarningShown_ = true;
               SwingUtilities.invokeLater(() ->
                     JOptionPane.showMessageDialog(
                           frame_,
                           "<html><body style='width:350px'>"
                           + "The MDA slice (z-stack) settings were changed after this "
                           + "Explorer session started.<br><br>"
                           + "To keep the z-axis consistent across all tiles already "
                           + "acquired, the Explorer will continue using the "
                           + "<b>original slice settings</b> for the rest of this session."
                           + "<br><br>"
                           + "Start a new Explorer session if you want the updated "
                           + "slice settings to take effect."
                           + "</body></html>",
                           "Explorer: MDA Settings Override",
                           JOptionPane.INFORMATION_MESSAGE));
            }
         }

         // Use the z-axis decision locked at session start, not the current MDA
         // settings, so every tile in this session has the same axes shape.
         boolean useSlices = sessionUseSlices_;
         SequenceSettings.Builder sb = settings.copyBuilder()
                 .useFrames(false)
                 .useSlices(useSlices)
                 // Use the slice positions captured at session start, not the current MDA
                 // settings, so the z planes stay consistent even if the user edits the
                 // slice list mid-session (an empty list here would yield no z planes).
                 .slices(new java.util.ArrayList<>(sessionSlices_))
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
               if (vesselOutlinePending_) {
                  updateVesselOutline();
               }

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
         // Locked at session start so the dataset's axes shape stays consistent even if
         // the MDA slice settings are edited mid-session (see sessionIsZStack_).
         boolean isZStack = sessionIsZStack_;

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
            Point2D.Double target = pixelToStageSessionFrame(pixelX, pixelY);
            if (target == null) {
               return;
            }
            studio_.core().setXYPosition(target.x, target.y);
            studio_.core().waitForDevice(studio_.core().getXYStageDevice());
         } catch (Exception e) {
            studio_.logs().logError(e, "Explorer: error moving stage");
         }
      });
   }

   /**
    * Converts a full-resolution canvas pixel coordinate (the center of a tile/FOV) to the
    * stage coordinate it represents, using the SESSION-START pixel size / affine. This keeps
    * canvas positions mapped to consistent physical stage positions regardless of the current
    * objective. Returns null if the conversion is not possible (no tile dimensions yet).
    */
   private Point2D.Double pixelToStageSessionFrame(double pixelX, double pixelY) {
      int tw = dataSource_ != null ? dataSource_.getTileWidth() : cameraWidth_;
      int th = dataSource_ != null ? dataSource_.getTileHeight() : cameraHeight_;
      if (tw <= 0 || th <= 0) {
         return null;
      }
      int overlapPixelsX = (int) Math.round(tw * overlapPercentage_ / 100.0);
      int overlapPixelsY = (int) Math.round(th * overlapPercentage_ / 100.0);
      double effectiveTileWidth = tw - overlapPixelsX;
      double effectiveTileHeight = th - overlapPixelsY;

      double offsetPixelX = pixelX - effectiveTileWidth / 2.0;
      double offsetPixelY = pixelY - effectiveTileHeight / 2.0;

      double pipelineScaleX = (tw > 0 && initialCameraWidth_  > 0)
            ? (double) initialCameraWidth_  / tw : 1.0;
      double pipelineScaleY = (th > 0 && initialCameraHeight_ > 0)
            ? (double) initialCameraHeight_ / th : 1.0;

      double targetX;
      double targetY;
      if (initialPixelSizeAffine_ != null) {
         // Convert canvas-pixel delta to camera-pixel delta using initial camera dims,
         // then apply the session-start affine (forward: camera-pixel -> stage-micron).
         Point2D.Double camPixelDelta = new Point2D.Double(
               offsetPixelX * pipelineScaleX, offsetPixelY * pipelineScaleY);
         Point2D.Double stageOffset = new Point2D.Double();
         initialPixelSizeAffine_.transform(camPixelDelta, stageOffset);
         targetX = initialStageX_ + stageOffset.x;
         targetY = initialStageY_ + stageOffset.y;
      } else {
         targetX = initialStageX_ + offsetPixelX * pipelineScaleX * initialPixelSizeUm_;
         targetY = initialStageY_ + offsetPixelY * pipelineScaleY * initialPixelSizeUm_;
      }
      return new Point2D.Double(targetX, targetY);
   }

   // ===================== Create Positions =====================

   private static final DecimalFormat FMT_POS = new DecimalFormat("000");

   /**
    * Enters or leaves position-draw mode. Only allowed for a live (Started) session, not for
    * an opened (read-only) dataset. Passing {@link ExplorerDataSource.PositionTool#NONE} leaves
    * draw mode. Sets the canvas cursor accordingly.
    */
   public void setPositionDrawTool(ExplorerDataSource.PositionTool tool) {
      if (dataSource_ == null) {
         return;
      }
      if (!exploring_ || loadedData_) {
         dataSource_.setPositionTool(ExplorerDataSource.PositionTool.NONE);
         return;
      }
      boolean drawing = tool != null && tool != ExplorerDataSource.PositionTool.NONE;
      if (!drawing) {
         // Leaving draw mode ends the whole Create-Positions flow: close Refine Z and drop points.
         cancelRefineZ();
         if (refineZFrame_ != null) {
            refineZFrame_.dispose(); // disposes -> onRefineZFrameClosed disarms the canvas
         }
         synchronized (refineZPoints_) {
            refineZPoints_.clear();
         }
         dataSource_.setRefineZMarkers(null);
      }
      dataSource_.setPositionTool(tool);
      if (viewer_ != null) {
         viewer_.getCanvasJPanel().setCursor(java.awt.Cursor.getPredefinedCursor(
               drawing ? java.awt.Cursor.CROSSHAIR_CURSOR : java.awt.Cursor.DEFAULT_CURSOR));
      }
      redrawOverlay();
   }

   /** Discards the current Create-Positions ROI. */
   public void clearPositionRoi() {
      if (dataSource_ != null) {
         dataSource_.clearPositionRoi();
         redrawOverlay();
      }
   }

   /** True when a completed Create-Positions ROI exists. */
   public boolean hasPositionRoi() {
      return dataSource_ != null && dataSource_.hasPositionRoi();
   }

   /**
    * Notifies that the ROI state changed: refreshes the live position preview (grey FOVs) and
    * the Generate button. Called from ExplorerDataSource when an ROI is completed or cleared.
    */
   public void onPositionRoiChanged() {
      SwingUtilities.invokeLater(() -> updatePositionPreview(frame_.isWithinVesselSelected()));
   }

   /**
    * Recomputes the position grid for the current ROI and shows it as grey FOV rectangles on
    * the overlay WITHOUT committing it to the MM position list. Safe to call repeatedly (on ROI
    * completion and when the "within vessel" checkbox toggles). Clears the preview when there is
    * no usable ROI.
    */
   public void updatePositionPreview(boolean withinVesselOnly) {
      if (dataSource_ == null || !exploring_ || loadedData_ || !hasPositionRoi()) {
         if (dataSource_ != null) {
            dataSource_.setGeneratedPositionFovs(null);
            redrawOverlay();
         }
         frame_.setGenerateEnabled(false);
         return;
      }
      try {
         PositionGridResult preview = computePositionGrid(withinVesselOnly);
         dataSource_.setGeneratedPositionFovs(preview.fovsPx);
         redrawOverlay();
         String status = preview.posList.getNumberOfPositions() + " positions ("
               + preview.regionLabel + ")";
         if (preview.warning != null) {
            status = preview.warning + " (" + status + ")";
         }
         frame_.setPositionStatus(status);
         frame_.setGenerateEnabled(true);
      } catch (Exception ex) {
         dataSource_.setGeneratedPositionFovs(null);
         redrawOverlay();
         frame_.setPositionStatus(ex.getMessage());
         frame_.setGenerateEnabled(false);
      }
   }

   /**
    * Commits the previewed positions to the MM position list. Positions are sized using the
    * CURRENT pixel size and camera FOV. When {@code withinVesselOnly} is true the ROI is first
    * intersected with the vessel boundary. The grid is recomputed so the committed list reflects
    * the latest hardware/checkbox state, then a region counter is advanced so the next region
    * gets a fresh identifier.
    */
   public void createPositionsFromRoi(boolean withinVesselOnly) {
      if (dataSource_ == null || !exploring_ || loadedData_ || !hasPositionRoi()) {
         frame_.setPositionStatus("Draw an ROI first.");
         return;
      }
      PositionGridResult result;
      try {
         result = computePositionGrid(withinVesselOnly);
      } catch (Exception ex) {
         frame_.setPositionStatus(ex.getMessage());
         return;
      }
      PositionList existing = studio_.positions().getPositionList();
      for (int i = 0; i < result.posList.getNumberOfPositions(); i++) {
         existing.addPosition(result.posList.getPosition(i));
      }
      studio_.positions().setPositionList(existing);
      studio_.app().showPositionList();
      dataSource_.setGeneratedPositionFovs(result.fovsPx);
      redrawOverlay();
      // Advance the region counter so a subsequent ROI gets the next identifier.
      regionCounter_++;
      String status = "Added " + result.posList.getNumberOfPositions() + " positions ("
            + result.regionLabel + ")";
      if (result.warning != null) {
         status = result.warning + " (" + status + ")";
      }
      frame_.setPositionStatus(status);
      // Refresh the preview (now showing the NEXT region's identifier) and keep the ROI.
      updatePositionPreview(withinVesselOnly);
   }

   /** Holds the result of a position-grid computation. */
   private static final class PositionGridResult {
      private final PositionList posList;
      private final java.util.List<Rectangle2D.Double> fovsPx;
      private final java.util.List<Tile> tiles;
      private final String warning;
      private final String regionLabel;

      private PositionGridResult(PositionList posList,
            java.util.List<Rectangle2D.Double> fovsPx, java.util.List<Tile> tiles,
            String warning, String regionLabel) {
         this.posList = posList;
         this.fovsPx = fovsPx;
         this.tiles = tiles;
         this.warning = warning;
         this.regionLabel = regionLabel;
      }
   }

   /** Returns the accepted, ordered tiles for the current ROI (used by automatic Refine Z). */
   private java.util.List<Tile> collectAcceptedTiles(boolean withinVesselOnly) throws Exception {
      return computePositionGrid(withinVesselOnly).tiles;
   }

   /**
    * Chooses up to {@code n} tiles spread across the given set using farthest-point sampling in
    * stage space. On multi-well plates the budget is split across wells so each gets coverage.
    */
   private java.util.List<Tile> chooseSpreadTiles(java.util.List<Tile> tiles, int n) {
      java.util.List<Tile> result = new java.util.ArrayList<>();
      if (tiles.isEmpty() || n <= 0) {
         return result;
      }
      // Group by well so each well is sampled; non-plate -> single group.
      java.util.Map<Long, java.util.List<Tile>> byWell = new java.util.LinkedHashMap<>();
      boolean multiWell = false;
      for (Tile t : tiles) {
         long key = 0;
         if (t.well != null) {
            multiWell = true;
            key = ((long) t.well[0] << 32) | (t.well[1] & 0xffffffffL);
         }
         byWell.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(t);
      }
      int nWells = byWell.size();
      for (java.util.List<Tile> group : byWell.values()) {
         int budget = multiWell ? Math.max(1, n) : n; // n points per well on plates, else n total
         result.addAll(farthestPointSample(group, Math.min(budget, group.size())));
      }
      // On non-plate the loop ran once; on plates each well got up to n points.
      if (!multiWell && result.size() > n) {
         return result.subList(0, n);
      }
      return result;
   }

   /** Farthest-point sampling of {@code count} tiles from {@code group} (Euclidean stage XY). */
   private java.util.List<Tile> farthestPointSample(java.util.List<Tile> group, int count) {
      java.util.List<Tile> chosen = new java.util.ArrayList<>();
      if (group.isEmpty() || count <= 0) {
         return chosen;
      }
      // Seed with the tile nearest the group's centroid for a stable first pick.
      double cx = 0;
      double cy = 0;
      for (Tile t : group) {
         cx += t.stageX;
         cy += t.stageY;
      }
      cx /= group.size();
      cy /= group.size();
      Tile seed = group.get(0);
      double best = Double.MAX_VALUE;
      for (Tile t : group) {
         double d = (t.stageX - cx) * (t.stageX - cx) + (t.stageY - cy) * (t.stageY - cy);
         if (d < best) {
            best = d;
            seed = t;
         }
      }
      chosen.add(seed);
      while (chosen.size() < count) {
         Tile bestTile = null;
         double bestDist = -1.0;
         for (Tile t : group) {
            if (chosen.contains(t)) {
               continue;
            }
            double nearest = Double.MAX_VALUE;
            for (Tile c : chosen) {
               double dx = t.stageX - c.stageX;
               double dy = t.stageY - c.stageY;
               double d = dx * dx + dy * dy;
               if (d < nearest) {
                  nearest = d;
               }
            }
            if (nearest > bestDist) {
               bestDist = nearest;
               bestTile = t;
            }
         }
         if (bestTile == null) {
            break;
         }
         chosen.add(bestTile);
      }
      return chosen;
   }

   /**
    * Computes the tile-grid position list and FOV rectangles for the current ROI. Pure
    * computation (plus current-hardware reads); does not mutate the MM position list. A tile is
    * included if its FOV rectangle overlaps the (optionally vessel-clipped) ROI shape. Positions
    * are ordered serpentine, prefixed with a region identifier, and include every checked stage.
    */
   private PositionGridResult computePositionGrid(boolean withinVesselOnly) throws Exception {
      final Area roiAreaPx = dataSource_.getPositionRoiAreaPx();
      if (roiAreaPx == null) {
         throw new Exception("Draw an ROI first.");
      }
      if (withinVesselOnly) {
         Area vesselArea = dataSource_.getVesselAreaPx();
         if (vesselArea != null) {
            roiAreaPx.intersect(vesselArea);
         }
      }
      if (roiAreaPx.isEmpty()) {
         throw new Exception("ROI does not overlap the vessel.");
      }

      String warning = null;

      // ---- Live hardware: affine + FOV for the new position grid ----
      AffineTransform pixToStage = null;
      double livePixelSizeUm = 0.0;
      try {
         AffineTransform at = AffineUtils.doubleToAffine(
               studio_.core().getPixelSizeAffine(true));
         livePixelSizeUm = AffineUtils.deducePixelSize(at);
         if (livePixelSizeUm > 0.0) {
            pixToStage = at;
         }
      } catch (Exception ignore) {
         // No affine configured -- scalar fallback below.
      }
      if (livePixelSizeUm <= 0.0) {
         livePixelSizeUm = pixelSizeUm_ > 0 ? pixelSizeUm_ : 1.0;
      }

      int tileW = (int) studio_.core().getImageWidth();
      int tileH = (int) studio_.core().getImageHeight();
      if (tileW <= 0 || tileH <= 0) {
         throw new Exception("Cannot create positions: camera image size unavailable.");
      }

      int overlapX = (int) Math.round(tileW * overlapPercentage_ / 100.0);
      int overlapY = (int) Math.round(tileH * overlapPercentage_ / 100.0);
      int stepPxX = tileW - overlapX;
      int stepPxY = tileH - overlapY;
      if (stepPxX <= 0 || stepPxY <= 0) {
         throw new Exception("Cannot create positions: overlap is >= tile size.");
      }

      if (Math.abs(livePixelSizeUm - initialPixelSizeUm_) > 0.01 * initialPixelSizeUm_) {
         warning = String.format(
               "Note: current pixel size (%.4f um) differs from session start (%.4f um) "
               + "- positions sized for the current objective.",
               livePixelSizeUm, initialPixelSizeUm_);
      }

      // ---- Step vectors and FOV magnitudes in stage space (current hardware) ----
      final double stageStepXdx;
      final double stageStepXdy;
      final double stageStepYdx;
      final double stageStepYdy;
      double fovW;
      double fovH;
      if (pixToStage != null) {
         Point2D.Double sx = new Point2D.Double();
         Point2D.Double sy = new Point2D.Double();
         Point2D.Double fx = new Point2D.Double();
         Point2D.Double fy = new Point2D.Double();
         pixToStage.deltaTransform(new Point2D.Double(stepPxX, 0), sx);
         pixToStage.deltaTransform(new Point2D.Double(0, stepPxY), sy);
         pixToStage.deltaTransform(new Point2D.Double(tileW, 0), fx);
         pixToStage.deltaTransform(new Point2D.Double(0, tileH), fy);
         stageStepXdx = sx.x;
         stageStepXdy = sx.y;
         stageStepYdx = sy.x;
         stageStepYdy = sy.y;
         fovW = Math.hypot(fx.x, fx.y);
         fovH = Math.hypot(fy.x, fy.y);
      } else {
         stageStepXdx = stepPxX * livePixelSizeUm;
         stageStepXdy = 0;
         stageStepYdx = 0;
         stageStepYdy = stepPxY * livePixelSizeUm;
         fovW = tileW * livePixelSizeUm;
         fovH = tileH * livePixelSizeUm;
      }
      double stepMagX = Math.hypot(stageStepXdx, stageStepXdy);
      double stepMagY = Math.hypot(stageStepYdx, stageStepYdy);
      if (stepMagX <= 0 || stepMagY <= 0) {
         throw new Exception("Cannot create positions: degenerate step size.");
      }

      // ---- ROI corners -> stage space (session-start frame) ----
      Rectangle2D bbox = roiAreaPx.getBounds2D();
      Point2D.Double[] cornersPx = new Point2D.Double[] {
         new Point2D.Double(bbox.getMinX(), bbox.getMinY()),
         new Point2D.Double(bbox.getMaxX(), bbox.getMinY()),
         new Point2D.Double(bbox.getMinX(), bbox.getMaxY()),
         new Point2D.Double(bbox.getMaxX(), bbox.getMaxY())
      };
      Point2D.Double[] cornersStage = new Point2D.Double[4];
      for (int i = 0; i < 4; i++) {
         cornersStage[i] = pixelToStageSessionFrame(cornersPx[i].x, cornersPx[i].y);
         if (cornersStage[i] == null) {
            throw new Exception("Cannot create positions: tile dimensions not ready.");
         }
      }

      // ---- Grid dimensions: project corners onto unit step axes ----
      double uxX = stageStepXdx / stepMagX;
      double uxY = stageStepXdy / stepMagX;
      double uyX = stageStepYdx / stepMagY;
      double uyY = stageStepYdy / stepMagY;
      double projXMin = Double.MAX_VALUE;
      double projXMax = -Double.MAX_VALUE;
      double projYMin = Double.MAX_VALUE;
      double projYMax = -Double.MAX_VALUE;
      double sumX = 0;
      double sumY = 0;
      for (Point2D.Double c : cornersStage) {
         double px = c.x * uxX + c.y * uxY;
         double py = c.x * uyX + c.y * uyY;
         projXMin = Math.min(projXMin, px);
         projXMax = Math.max(projXMax, px);
         projYMin = Math.min(projYMin, py);
         projYMax = Math.max(projYMax, py);
         sumX += c.x;
         sumY += c.y;
      }
      double roiExtentX = projXMax - projXMin;
      double roiExtentY = projYMax - projYMin;
      int nCols = Math.max(1, (int) Math.ceil((roiExtentX + fovW - stepMagX) / stepMagX));
      int nRows = Math.max(1, (int) Math.ceil((roiExtentY + fovH - stepMagY) / stepMagY));

      // ---- Grid origin: center over the ROI bounding box ----
      double roiStageCX = sumX / 4.0;
      double roiStageCY = sumY / 4.0;
      double originX = roiStageCX
            - (nCols - 1) / 2.0 * stageStepXdx
            - (nRows - 1) / 2.0 * stageStepYdx;
      double originY = roiStageCY
            - (nCols - 1) / 2.0 * stageStepXdy
            - (nRows - 1) / 2.0 * stageStepYdy;

      String xyStage;
      try {
         xyStage = studio_.core().getXYStageDevice();
      } catch (Exception e) {
         xyStage = null;
      }
      if (xyStage == null || xyStage.isEmpty()) {
         throw new Exception("Cannot create positions: no XY stage device is configured.");
      }

      // FOV size in full-resolution pixels (session frame), for the overlap test.
      double pxPerUm = computePxPerUm();
      double fovPxW = pxPerUm > 0 ? fovW * pxPerUm : tileW;
      double fovPxH = pxPerUm > 0 ? fovH * pxPerUm : tileH;

      final double overlapXUm = overlapX * livePixelSizeUm;
      final double overlapYUm = overlapY * livePixelSizeUm;

      // Region number (shared by all positions in this ROI). The per-position well prefix is
      // computed individually below so an ROI spanning several wells labels each correctly.
      // Use the NEXT region number; createPositionsFromRoi advances it on commit.
      int regionNumber = regionCounter_ + 1;
      String regionLabel = "Reg" + regionNumber;
      boolean multiWell = vesselType_ != null && vesselType_.isMultiWell();

      // Snapshot the checked auxiliary stages (e.g. Z) once, applied to every position.
      // When Refine Z has reference points, the 1D (Z) stages it measured are interpolated
      // per tile instead of using this snapshot; other aux stages keep the snapshot.
      java.util.List<StagePosition> auxStages = readCheckedAuxStages(xyStage);
      boolean haveRefineZ = hasRefineZPoints();
      java.util.Set<String> refinedZStages = haveRefineZ
            ? new java.util.HashSet<>(getCheckedZStages()) : java.util.Collections.emptySet();
      // ZGenerators keyed by well key (or one global generator under key 0 when not per-well).
      java.util.Map<Long, java.util.Map<String, ZGenerator>> zGenByWell =
            new java.util.HashMap<>();
      java.util.Map<String, ZGenerator> zGenGlobal = haveRefineZ
            ? buildZGenerators(null) : java.util.Collections.emptyMap();

      // Group positions by well only when clipping to a multi-well vessel; otherwise the whole
      // ROI is one contiguous region and the global serpentine already minimizes travel.
      boolean groupByWell = multiWell && withinVesselOnly;

      // ---- Pass 1: collect accepted tiles (already in global serpentine row/col order) ----
      java.util.List<Tile> tiles = new java.util.ArrayList<>();
      java.util.List<Rectangle2D.Double> fovsPx = new java.util.ArrayList<>();
      for (int row = 0; row < nRows; row++) {
         for (int col = 0; col < nCols; col++) {
            int c = ((row & 1) == 0) ? col : (nCols - 1 - col); // serpentine
            double dx = c * stageStepXdx + row * stageStepYdx;
            double dy = c * stageStepXdy + row * stageStepYdy;
            double stageX = originX + dx;
            double stageY = originY + dy;

            // Include the tile if its FOV rectangle overlaps the ROI shape.
            Point2D.Double centerPx = stageToPixel(stageX, stageY);
            Rectangle2D.Double fovRect = null;
            if (centerPx != null) {
               fovRect = new Rectangle2D.Double(
                     centerPx.x - fovPxW / 2.0, centerPx.y - fovPxH / 2.0, fovPxW, fovPxH);
               if (!roiAreaPx.intersects(fovRect)) {
                  continue;
               }
            }
            if (fovRect != null) {
               fovsPx.add(fovRect);
            }
            // Compute the well whenever multi-well so both per-well ordering and per-well Z
            // interpolation work, even without within-vessel clipping.
            int[] well = multiWell ? wellIndexForStage(stageX, stageY) : null;
            tiles.add(new Tile(row, c, stageX, stageY, well));
         }
      }
      if (tiles.isEmpty()) {
         throw new Exception("No positions fall within the ROI.");
      }

      // ---- Pass 2: order tiles. When grouping by well, visit wells in a boustrophedon
      // (snake) pattern across the plate and snake the tiles within each well, so the stage
      // never jumps back and forth between wells. ----
      if (groupByWell) {
         orderTilesByWell(tiles);
      }

      // ---- Pass 3: build the position list in the chosen order ----
      PositionList posList = new PositionList();
      for (Tile t : tiles) {
         String prefix = regionLabel;
         if (multiWell) {
            String well = t.well != null
                  ? VesselType.getWellLabel(t.well[0], t.well[1])
                  : wellLabelForStage(t.stageX, t.stageY);
            if (well != null) {
               prefix = well + "-" + regionLabel;
            }
         }

         MultiStagePosition msp = new MultiStagePosition();
         msp.setDefaultXYStage(xyStage);
         msp.add(StagePosition.create2D(xyStage, t.stageX, t.stageY));
         // Non-refined aux stages keep their snapshot; refined Z stages are interpolated below.
         for (StagePosition aux : auxStages) {
            if (refinedZStages.contains(aux.getStageDeviceLabel())) {
               continue;
            }
            msp.add(StagePosition.newInstance(aux));
         }
         if (haveRefineZ) {
            // Per-well generator when this tile has a well and that well was refined, else global.
            java.util.Map<String, ZGenerator> gens = zGenGlobal;
            if (t.well != null) {
               long wkey = ((long) t.well[0] << 32) | (t.well[1] & 0xffffffffL);
               java.util.Map<String, ZGenerator> perWell = zGenByWell.get(wkey);
               if (perWell == null) {
                  perWell = buildZGenerators(t.well);
                  zGenByWell.put(wkey, perWell);
               }
               if (!perWell.isEmpty()) {
                  gens = perWell;
               }
            }
            String defaultZ = null;
            for (String zStage : refinedZStages) {
               ZGenerator gen = gens.get(zStage);
               if (gen != null) {
                  msp.add(StagePosition.create1D(zStage, gen.getZ(t.stageX, t.stageY, zStage)));
                  if (defaultZ == null) {
                     defaultZ = zStage;
                  }
               }
            }
            if (defaultZ != null) {
               msp.setDefaultZStage(defaultZ);
            }
         }
         msp.setLabel(prefix + "-Pos-" + FMT_POS.format(t.row) + "_" + FMT_POS.format(t.col));
         msp.setGridCoordinates(t.row, t.col);
         msp.setProperty("OverlapUmX", String.valueOf(overlapXUm));
         msp.setProperty("OverlapUmY", String.valueOf(overlapYUm));
         msp.setProperty("OverlapPixelsX", String.valueOf(overlapX));
         msp.setProperty("OverlapPixelsY", String.valueOf(overlapY));
         msp.setProperty("Source", "ExplorerCreatePositions");
         msp.setProperty("Region", regionLabel);
         posList.addPosition(msp);
      }
      return new PositionGridResult(posList, fovsPx, tiles, warning, regionLabel);
   }

   /** One accepted grid tile, before being turned into a MultiStagePosition. */
   private static final class Tile {
      private final int row;
      private final int col;
      private final double stageX;
      private final double stageY;
      private final int[] well; // {wellRow, wellCol} when grouping by well, else null

      private Tile(int row, int col, double stageX, double stageY, int[] well) {
         this.row = row;
         this.col = col;
         this.stageX = stageX;
         this.stageY = stageY;
         this.well = well;
      }
   }

   /**
    * Re-orders the tiles in place so that all tiles of one well are visited consecutively, the
    * wells themselves are traversed in a boustrophedon (snake) pattern (left-to-right on even
    * well-rows, right-to-left on odd ones), and within each well the tiles snake by grid row/col.
    * Minimizes stage travel by never jumping back and forth between wells.
    */
   private void orderTilesByWell(java.util.List<Tile> tiles) {
      // Bucket tiles by well. LinkedHashMap keeps first-seen order as a stable fallback.
      java.util.Map<Long, java.util.List<Tile>> byWell = new java.util.LinkedHashMap<>();
      for (Tile t : tiles) {
         int wr = t.well != null ? t.well[0] : 0;
         int wc = t.well != null ? t.well[1] : 0;
         long key = ((long) wr << 32) | (wc & 0xffffffffL);
         byWell.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(t);
      }
      // Sort wells: row ascending; column ascending on even rows, descending on odd rows.
      java.util.List<Long> wellKeys = new java.util.ArrayList<>(byWell.keySet());
      wellKeys.sort((a, b) -> {
         int ra = (int) (a >> 32);
         int rb = (int) (b >> 32);
         if (ra != rb) {
            return Integer.compare(ra, rb);
         }
         int ca = (int) (a & 0xffffffffL);
         int cb = (int) (b & 0xffffffffL);
         return (ra & 1) == 0 ? Integer.compare(ca, cb) : Integer.compare(cb, ca);
      });
      tiles.clear();
      for (Long key : wellKeys) {
         java.util.List<Tile> wellTiles = byWell.get(key);
         // Snake within the well: grid row ascending; col ascending on even rows, desc on odd.
         wellTiles.sort((a, b) -> {
            if (a.row != b.row) {
               return Integer.compare(a.row, b.row);
            }
            return (a.row & 1) == 0 ? Integer.compare(a.col, b.col) : Integer.compare(b.col, a.col);
         });
         tiles.addAll(wellTiles);
      }
   }

   /**
    * Returns the well label (e.g. "B7") whose center is nearest the given stage coordinate for
    * the current multi-well vessel, or null if no vessel/anchor is available. The plate top-left
    * in stage coordinates is {@code vesselAnchorStageX_/Y_}; wells are laid out by the vessel
    * geometry (axes assumed parallel to stage X/Y).
    */
   private String wellLabelForStage(double stageX, double stageY) {
      int[] well = wellIndexForStage(stageX, stageY);
      return well != null ? VesselType.getWellLabel(well[0], well[1]) : null;
   }

   /**
    * Returns the 0-based {@code {row, col}} of the well whose center is nearest the given stage
    * coordinate for the current multi-well vessel, or null if no vessel/anchor is available.
    */
   private int[] wellIndexForStage(double stageX, double stageY) {
      if (vesselType_ == null || !vesselType_.isMultiWell()) {
         return null;
      }
      double relX = stageX - vesselAnchorStageX_ - vesselType_.getFirstWellXUm();
      double relY = stageY - vesselAnchorStageY_ - vesselType_.getFirstWellYUm();
      double spacX = vesselType_.getWellSpacingXUm();
      double spacY = vesselType_.getWellSpacingYUm();
      if (spacX <= 0 || spacY <= 0) {
         return null;
      }
      int col = (int) Math.round(relX / spacX);
      int row = (int) Math.round(relY / spacY);
      col = Math.max(0, Math.min(vesselType_.getWellCols() - 1, col));
      row = Math.max(0, Math.min(vesselType_.getWellRows() - 1, row));
      return new int[]{row, col};
   }

   /**
    * Reads the current positions of every stage that is checked ("use") in the MM Stage Position
    * List, EXCLUDING the main XY stage (which carries the grid position). The checked state is the
    * same profile setting the Position List editor uses. Failures are logged and skipped.
    */
   private java.util.List<StagePosition> readCheckedAuxStages(String mainXyStage) {
      java.util.List<StagePosition> result = new java.util.ArrayList<>();
      MutablePropertyMapView axisSettings = axisCheckedSettings();
      try {
         StrVector oneDStages = studio_.core().getLoadedDevicesOfType(DeviceType.StageDevice);
         for (int i = 0; i < oneDStages.size(); i++) {
            String name = oneDStages.get(i);
            if (!axisChecked(axisSettings, name)) {
               continue;
            }
            try {
               double pos = studio_.core().getPosition(name);
               result.add(StagePosition.create1D(name, pos));
            } catch (Exception e) {
               studio_.logs().logError(e, "Explorer: could not read position of " + name);
            }
         }
         StrVector xyStages = studio_.core().getLoadedDevicesOfType(DeviceType.XYStageDevice);
         for (int i = 0; i < xyStages.size(); i++) {
            String name = xyStages.get(i);
            if (name.equals(mainXyStage)) {
               continue;
            }
            if (!axisChecked(axisSettings, name)) {
               continue;
            }
            try {
               double x = studio_.core().getXPosition(name);
               double y = studio_.core().getYPosition(name);
               result.add(StagePosition.create2D(name, x, y));
            } catch (Exception e) {
               studio_.logs().logError(e, "Explorer: could not read position of " + name);
            }
         }
      } catch (Exception e) {
         studio_.logs().logError(e, "Explorer: could not enumerate stage devices");
      }
      return result;
   }

   /**
    * Returns the profile settings node the MM Position List editor uses to persist which stage
    * axes are checked ("use"), or null if it cannot be read (then all axes are treated as checked).
    */
   private MutablePropertyMapView axisCheckedSettings() {
      try {
         Class<?> axisModel = Class.forName(
               "org.micromanager.internal.positionlist.AxisTableModel");
         return studio_.profile().getSettings(axisModel);
      } catch (Exception e) {
         return null;
      }
   }

   /** True if the named stage axis is checked in the Position List editor (default true). */
   private boolean axisChecked(MutablePropertyMapView axisSettings, String name) {
      return axisSettings == null || axisSettings.getBoolean(name, true);
   }

   /** Returns the checked 1D stage (Z) device names, in core order. */
   private java.util.List<String> getCheckedZStages() {
      java.util.List<String> zStages = new java.util.ArrayList<>();
      MutablePropertyMapView axisSettings = axisCheckedSettings();
      try {
         StrVector oneDStages = studio_.core().getLoadedDevicesOfType(DeviceType.StageDevice);
         for (int i = 0; i < oneDStages.size(); i++) {
            String name = oneDStages.get(i);
            if (axisChecked(axisSettings, name)) {
               zStages.add(name);
            }
         }
      } catch (Exception e) {
         studio_.logs().logError(e, "Explorer: could not enumerate Z stages");
      }
      return zStages;
   }

   // ===================== Refine Z =====================

   /** Opens (or brings to front) the Refine Z window for the current session. */
   public void showRefineZFrame() {
      if (!exploring_ || loadedData_) {
         return;
      }
      if (refineZFrame_ == null || !refineZFrame_.isDisplayable()) {
         refineZFrame_ = new RefineZFrame(studio_, this, frame_);
      }
      // While the window is open, suppress ROI drawing so canvas clicks drive refinement.
      if (dataSource_ != null) {
         dataSource_.setRefineZActive(true);
      }
      refineZFrame_.setVisible(true);
      refineZFrame_.toFront();
   }

   /** Called by the Refine Z window when it is disposed. */
   public void onRefineZFrameClosed(RefineZFrame frame) {
      if (refineZFrame_ == frame) {
         refineZFrame_ = null;
      }
      if (dataSource_ != null) {
         dataSource_.setRefineZActive(false);
      }
   }

   /** True while the Refine Z window is open. */
   public boolean isRefineZFrameOpen() {
      return refineZFrame_ != null && refineZFrame_.isDisplayable();
   }

   /** Routes a Refine-Z status message to the Refine Z window if open. */
   private void setRefineZStatus(String text) {
      if (refineZFrame_ != null) {
         refineZFrame_.setRefineZStatus(text);
      }
   }

   /** Routes the running state to the Refine Z window if open. */
   private void setRefineZRunningUi(boolean running) {
      if (refineZFrame_ != null) {
         refineZFrame_.setRefineZRunning(running);
      }
   }

   /** One Refine-Z reference point: stage XY, measured Z per checked stage, and well index. */
   private static final class RefineZPoint {
      private final double stageX;
      private final double stageY;
      private final java.util.Map<String, Double> z;
      private final int[] well; // {wellRow, wellCol} on multi-well plates, else null

      private RefineZPoint(double stageX, double stageY,
            java.util.Map<String, Double> z, int[] well) {
         this.stageX = stageX;
         this.stageY = stageY;
         this.z = z;
         this.well = well;
      }
   }

   /** Sets the Z interpolation method (Weighted / Average). */
   public void setRefineZMethod(ZGenerator.Type method) {
      if (method != null) {
         refineZMethod_ = method;
      }
   }

   /** True when at least one Refine-Z reference point has been collected. */
   public boolean hasRefineZPoints() {
      synchronized (refineZPoints_) {
         return !refineZPoints_.isEmpty();
      }
   }

   /** Discards all collected Refine-Z reference points and clears their markers. */
   public void clearRefineZ() {
      synchronized (refineZPoints_) {
         refineZPoints_.clear();
      }
      pushRefineZMarkers();
      setRefineZStatus("Refine Z cleared.");
   }

   /** Pushes the current reference-point markers (stage->pixel) to the data source overlay. */
   private void pushRefineZMarkers() {
      if (dataSource_ == null) {
         return;
      }
      java.util.List<Point2D.Double> markers = new java.util.ArrayList<>();
      synchronized (refineZPoints_) {
         for (RefineZPoint p : refineZPoints_) {
            Point2D.Double px = stageToPixel(p.stageX, p.stageY);
            if (px != null) {
               markers.add(px);
            }
         }
      }
      dataSource_.setRefineZMarkers(markers);
      redrawOverlay();
   }

   /** True while the automatic Refine-Z worker is running. */
   public boolean isRefineZRunning() {
      return refineZWorker_ != null && !refineZWorker_.isDone();
   }

   /** Cancels a running automatic Refine-Z run. */
   public void cancelRefineZ() {
      if (refineZWorker_ != null) {
         refineZWorker_.cancel(true);
      }
   }

   /**
    * Starts automatic Refine Z: chooses up to {@code nPoints} reference tiles spread across the
    * current previewed grid (per well on multi-well plates), then for each one moves the stage,
    * runs the selected autofocus method, and records the resulting Z of every checked Z stage.
    * Runs off the EDT.
    */
   public void startRefineZAutomatic(int nPoints, String afMethodName, boolean withinVesselOnly) {
      if (dataSource_ == null || !exploring_ || loadedData_ || !hasPositionRoi()) {
         setRefineZStatus("Draw an ROI first.");
         return;
      }
      if (isRefineZRunning()) {
         return;
      }
      final java.util.List<String> zStages = getCheckedZStages();
      if (zStages.isEmpty()) {
         setRefineZStatus("No Z stage is checked in the Position List.");
         return;
      }
      java.util.List<Tile> tiles;
      try {
         tiles = collectAcceptedTiles(withinVesselOnly);
      } catch (Exception ex) {
         setRefineZStatus(ex.getMessage());
         return;
      }
      java.util.List<Tile> chosen = chooseSpreadTiles(tiles, nPoints);
      if (chosen.isEmpty()) {
         setRefineZStatus("No tiles to refine.");
         return;
      }
      setRefineZRunningUi(true);
      setRefineZStatus("Refining Z...");
      refineZWorker_ = new RefineZWorker(chosen, zStages, afMethodName);
      refineZWorker_.execute();
   }

   /** SwingWorker that visits chosen tiles, autofocuses, and records Z. */
   private final class RefineZWorker extends SwingWorker<Void, RefineZPoint> {
      private final java.util.List<Tile> cells_;
      private final java.util.List<String> zStages_;
      private final String afMethodName_;
      private final java.util.List<String> failures_ = new java.util.ArrayList<>();

      private RefineZWorker(java.util.List<Tile> cells, java.util.List<String> zStages,
            String afMethodName) {
         cells_ = cells;
         zStages_ = zStages;
         afMethodName_ = afMethodName;
      }

      @Override
      protected Void doInBackground() {
         AutofocusPlugin af = null;
         try {
            if (afMethodName_ != null && !afMethodName_.isEmpty()) {
               studio_.getAutofocusManager().setAutofocusMethodByName(afMethodName_);
            }
            af = studio_.getAutofocusManager().getAutofocusMethod();
         } catch (Exception e) {
            studio_.logs().logError(e, "Refine Z: could not select autofocus method");
         }
         int done = 0;
         for (Tile t : cells_) {
            if (isCancelled()) {
               break;
            }
            String failure = refineOneTile(af, t);
            if (failure != null) {
               failures_.add(failure);
            }
            done++;
            setProgress(100 * done / cells_.size());
         }
         return null;
      }

      private String refineOneTile(AutofocusPlugin af, Tile t) {
         // Snap to the Explorer grid cell center under this tile so the reference point, the
         // autofocus measurement, and the acquired image all coincide on the canvas grid.
         int[] cell = explorerGridCellForStage(t.stageX, t.stageY);
         Point2D.Double target = cell != null
               ? stageForExplorerCell(cell[0], cell[1]) : new Point2D.Double(t.stageX, t.stageY);
         try {
            studio_.core().setXYPosition(studio_.core().getXYStageDevice(), target.x, target.y);
            studio_.core().waitForDevice(studio_.core().getXYStageDevice());
         } catch (Exception e) {
            studio_.logs().logError(e, "Refine Z: move failed");
            return "tile (move failed: " + e.getMessage() + ")";
         }
         if (af == null) {
            return "tile (no autofocus method)";
         }
         try {
            af.fullFocus();
         } catch (Exception e) {
            studio_.logs().logError(e, "Refine Z: autofocus failed");
            return "tile (autofocus failed: " + e.getMessage() + ")";
         }
         java.util.Map<String, Double> zVals = new java.util.LinkedHashMap<>();
         try {
            for (String z : zStages_) {
               zVals.put(z, studio_.core().getPosition(z));
            }
         } catch (Exception e) {
            studio_.logs().logError(e, "Refine Z: read Z failed");
            return "tile (Z read failed)";
         }
         // Acquire the in-focus image so it shows on the canvas like a normal tile.
         acquireRefineZTileAtCurrentStage();
         publish(new RefineZPoint(target.x, target.y, zVals, t.well));
         return null;
      }

      @Override
      protected void process(java.util.List<RefineZPoint> chunks) {
         synchronized (refineZPoints_) {
            refineZPoints_.addAll(chunks);
         }
         pushRefineZMarkers();
         setRefineZStatus("Refined " + refineZPointCount() + " point(s)...");
      }

      @Override
      protected void done() {
         setRefineZRunningUi(false);
         if (isCancelled()) {
            setRefineZStatus("Refine Z cancelled (" + refineZPointCount() + " points).");
            return;
         }
         if (!failures_.isEmpty()) {
            StringBuilder sb = new StringBuilder("Autofocus skipped some tiles:\n");
            for (String f : failures_) {
               sb.append("  ").append(f).append('\n');
            }
            JOptionPane.showMessageDialog(refineZFrame_ != null ? refineZFrame_ : frame_,
                  sb.toString(), "Refine Z", JOptionPane.WARNING_MESSAGE);
         }
         setRefineZStatus("Refine Z done (" + refineZPointCount() + " points).");
         pushRefineZMarkers();
      }
   }

   private int refineZPointCount() {
      synchronized (refineZPoints_) {
         return refineZPoints_.size();
      }
   }

   /**
    * Returns the Explorer session tile-grid cell {row, col} that contains the given stage
    * position, or null if the tile grid is not ready. Uses the same mapping the canvas uses to
    * place tiles, so acquiring this cell shows the image at the expected canvas location.
    */
   private int[] explorerGridCellForStage(double stageX, double stageY) {
      Point2D.Double px = stageToPixel(stageX, stageY);
      int tw = dataSource_ != null ? dataSource_.getTileWidth() : -1;
      int th = dataSource_ != null ? dataSource_.getTileHeight() : -1;
      if (px == null || tw <= 0 || th <= 0) {
         return null;
      }
      int overlapPixelsX = (int) Math.round(tw * overlapPercentage_ / 100.0);
      int overlapPixelsY = (int) Math.round(th * overlapPercentage_ / 100.0);
      double effW = tw - overlapPixelsX;
      double effH = th - overlapPixelsY;
      int col = (int) Math.floor(px.x / effW);
      int row = (int) Math.floor(px.y / effH);
      return new int[]{row, col};
   }

   /**
    * Returns the stage XY of the center of the Explorer session tile-grid cell {row, col}, using
    * the same grid->stage mapping as acquireMultipleTiles so the cell aligns with the canvas.
    */
   private Point2D.Double stageForExplorerCell(int row, int col) {
      double overlapFraction = overlapPercentage_ / 100.0;
      double effectivePixelStepX = cameraWidth_  * (1.0 - overlapFraction);
      double effectivePixelStepY = cameraHeight_ * (1.0 - overlapFraction);
      if (pixelSizeAffine_ != null) {
         Point2D.Double pixelOffset = new Point2D.Double(
               col * effectivePixelStepX, row * effectivePixelStepY);
         Point2D.Double stageOffset = new Point2D.Double();
         pixelSizeAffine_.transform(pixelOffset, stageOffset);
         return new Point2D.Double(initialStageX_ + stageOffset.x, initialStageY_ + stageOffset.y);
      }
      double effectiveStepWidthUm  = stageTileWidthUm_  * (1.0 - overlapFraction);
      double effectiveStepHeightUm = stageTileHeightUm_ * (1.0 - overlapFraction);
      return new Point2D.Double(initialStageX_ + col * effectiveStepWidthUm,
            initialStageY_ + row * effectiveStepHeightUm);
   }

   /**
    * Moves the stage to the center of the Explorer grid cell under the current position and
    * acquires that tile so the in-focus image appears on the canvas (exactly like a left-click
    * acquisition) at the correct cell. Runs synchronously; must be called off the EDT. Returns
    * the cell-center stage XY actually acquired, or null on failure.
    */
   private Point2D.Double acquireRefineZTileAtCurrentStage() {
      try {
         double stageX = studio_.core().getXPosition();
         double stageY = studio_.core().getYPosition();
         int[] cell = explorerGridCellForStage(stageX, stageY);
         if (cell == null) {
            return null;
         }
         int row = cell[0];
         int col = cell[1];
         // Snap to the exact cell center so the stored tile aligns with the canvas grid.
         Point2D.Double cellCenter = stageForExplorerCell(row, col);
         studio_.core().setXYPosition(cellCenter.x, cellCenter.y);
         studio_.core().waitForDevice(studio_.core().getXYStageDevice());
         if (!dataSource_.isTileAcquired(row, col)) {
            acquireSingleTileBlocking(row, col);
            dataSource_.markTileAcquired(row, col);
            dataSource_.invalidateImageKeysCache();
            redrawOverlay();
         }
         return cellCenter;
      } catch (Exception e) {
         studio_.logs().logError(e, "Refine Z: could not acquire in-focus tile");
         return null;
      }
   }

   /**
    * Manual Refine Z: moves the stage so the clicked full-res pixel becomes the FOV center, so the
    * operator can focus there and then capture with {@link #addManualRefineZ()}.
    */
   public void refineZManualMove(double fullResX, double fullResY) {
      if (!exploring_ || loadedData_) {
         return;
      }
      moveStageToPixelPosition(fullResX, fullResY);
   }

   /**
    * Captures the current Z of every checked Z stage at the current stage XY as a Refine-Z
    * reference point.
    */
   public void addManualRefineZ() {
      if (!exploring_ || loadedData_) {
         return;
      }
      java.util.List<String> zStages = getCheckedZStages();
      if (zStages.isEmpty()) {
         setRefineZStatus("No Z stage is checked in the Position List.");
         return;
      }
      try {
         double stageX = studio_.core().getXPosition();
         double stageY = studio_.core().getYPosition();
         java.util.Map<String, Double> zVals = new java.util.LinkedHashMap<>();
         for (String z : zStages) {
            zVals.put(z, studio_.core().getPosition(z));
         }
         // Record the point at the Explorer grid cell center so the marker, the reference point,
         // and the acquired tile all coincide on the canvas grid.
         int[] cell = explorerGridCellForStage(stageX, stageY);
         Point2D.Double refXy = cell != null
               ? stageForExplorerCell(cell[0], cell[1]) : new Point2D.Double(stageX, stageY);
         int[] well = (vesselType_ != null && vesselType_.isMultiWell())
               ? wellIndexForStage(refXy.x, refXy.y) : null;
         synchronized (refineZPoints_) {
            refineZPoints_.add(new RefineZPoint(refXy.x, refXy.y, zVals, well));
         }
         pushRefineZMarkers();
         setRefineZStatus("Set Z (" + refineZPointCount() + " points).");
         // Acquire the in-focus image (off the EDT) so it shows on the canvas.
         if (acquisitionExecutor_ != null) {
            acquisitionExecutor_.submit(this::acquireRefineZTileAtCurrentStage);
         }
      } catch (Exception e) {
         studio_.logs().showError(e, "Refine Z: could not read stage position.");
      }
   }

   /**
    * Builds, per Z stage, a ZGenerator from the collected reference points (optionally restricted
    * to a single well). Returns an empty map when there are no usable reference points.
    */
   private java.util.Map<String, ZGenerator> buildZGenerators(int[] well) {
      java.util.List<RefineZPoint> pts = new java.util.ArrayList<>();
      synchronized (refineZPoints_) {
         for (RefineZPoint p : refineZPoints_) {
            if (well == null || p.well == null
                  || (p.well[0] == well[0] && p.well[1] == well[1])) {
               pts.add(p);
            }
         }
      }
      java.util.Map<String, ZGenerator> generators = new java.util.HashMap<>();
      if (pts.isEmpty()) {
         return generators;
      }
      // ZGenerator builds one interpolator per 1D stage present on the first position, so each
      // reference MultiStagePosition must carry every checked Z stage.
      PositionList refList = new PositionList();
      String xyStage;
      try {
         xyStage = studio_.core().getXYStageDevice();
      } catch (Exception e) {
         xyStage = "XY";
      }
      for (RefineZPoint p : pts) {
         MultiStagePosition msp = new MultiStagePosition();
         // setDefaultXYStage is REQUIRED: ZGeneratorShepard reads each reference point's XY via
         // MultiStagePosition.getX()/getY(), which return 0 unless the default XY stage is set.
         // Without it every reference point reads (0,0) and the interpolator returns a constant.
         msp.setDefaultXYStage(xyStage);
         msp.add(StagePosition.create2D(xyStage, p.stageX, p.stageY));
         for (java.util.Map.Entry<String, Double> e : p.z.entrySet()) {
            msp.add(StagePosition.create1D(e.getKey(), e.getValue()));
         }
         refList.addPosition(msp);
      }
      ZGenerator gen = ZGenerator.create(refineZMethod_, refList);
      synchronized (refineZPoints_) {
         for (String z : pts.get(0).z.keySet()) {
            generators.put(z, gen);
         }
      }
      return generators;
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
                        .name(name)
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

   // ===================== Vessel outline =====================

   /**
    * Called from ExplorerFrame when the operator changes the vessel type dropdown.
    * Clears any existing anchor and outline so the new vessel starts fresh.
    */
   public void setVesselType(VesselType v) {
      vesselType_ = (v != null) ? v : VesselType.NONE;
      vesselAnchorType_ = null;
      vesselUsedHcsCal_ = false;
      vesselOutlinePending_ = false;
      if (dataSource_ != null) {
         dataSource_.clearVesselOutline();
         redrawOverlay();
      }
      if (exploring_ && vesselType_.isMultiWell()) {
         Point2D.Double hcsOffset = tryReadHcsCalibration();
         frame_.setHcsCalibrationStatus(hcsOffset != null);
         if (hcsOffset != null) {
            applyHcsCalibration(hcsOffset);
         }
      } else {
         frame_.setHcsCalibrationStatus(false);
      }
      frame_.setExploringActive(exploring_, exploring_ && !loadedData_);
   }

   /**
    * Called from ExplorerFrame when an anchor button is clicked.
    * Reads the current stage position, computes the vessel outline in pixel space,
    * pushes it to ExplorerDataSource, triggers a redraw, and zooms to show the vessel.
    */
   public void setVesselAnchor(VesselType.AnchorType anchorType) {
      if (!exploring_ || vesselType_.isNone()) {
         return;
      }
      try {
         vesselAnchorStageX_ = studio_.core().getXPosition();
         vesselAnchorStageY_ = studio_.core().getYPosition();
         vesselAnchorType_   = anchorType;
         updateVesselOutline();
      } catch (Exception e) {
         studio_.logs().showError(e,
               "Explorer: could not read stage position for vessel anchor.");
      }
   }

   private void updateVesselOutline() {
      if (dataSource_ == null || vesselType_.isNone()) {
         return;
      }
      if (!vesselType_.isMultiWell() && vesselAnchorType_ == null) {
         return;
      }

      double pxPerUm = computePxPerUm();
      if (pxPerUm <= 0) {
         vesselOutlinePending_ = true;
         return;
      }
      vesselOutlinePending_ = false;

      double widthPx  = vesselType_.getWidthUm()  * pxPerUm;
      double heightPx = vesselType_.getHeightUm() * pxPerUm;

      double tlX;
      double tlY;
      if (vesselType_.isMultiWell()) {
         // vesselAnchorStageX_/Y_ holds the plate top-left in stage coordinates
         // (set by applyHcsCalibration or setVesselWellAnchor).
         Point2D.Double plateTlPx = stageToPixel(vesselAnchorStageX_, vesselAnchorStageY_);
         if (plateTlPx == null) {
            vesselOutlinePending_ = true;
            return;
         }
         tlX = plateTlPx.x;
         tlY = plateTlPx.y;
      } else {
         Point2D.Double anchorPx = stageToPixel(vesselAnchorStageX_, vesselAnchorStageY_);
         if (anchorPx == null) {
            vesselOutlinePending_ = true;
            return;
         }
         switch (vesselAnchorType_) {
            case TOP_LEFT:
               tlX = anchorPx.x;
               tlY = anchorPx.y;
               break;
            case TOP_RIGHT:
               tlX = anchorPx.x - widthPx;
               tlY = anchorPx.y;
               break;
            case BOTTOM_LEFT:
               tlX = anchorPx.x;
               tlY = anchorPx.y - heightPx;
               break;
            case BOTTOM_RIGHT:
               tlX = anchorPx.x - widthPx;
               tlY = anchorPx.y - heightPx;
               break;
            case CENTER:
            default:
               tlX = anchorPx.x - widthPx / 2.0;
               tlY = anchorPx.y - heightPx / 2.0;
               break;
         }
      }
      dataSource_.setVesselOutline(vesselType_, tlX, tlY, widthPx, heightPx);
      redrawOverlay();
      zoomToVessel(tlX, tlY, widthPx, heightPx);
   }

   private double computePxPerUm() {
      int tw = dataSource_ != null ? dataSource_.getTileWidth() : -1;
      if (tw <= 0 || initialCameraWidth_ <= 0 || initialPixelSizeUm_ <= 0) {
         return -1;
      }
      return ((double) tw / initialCameraWidth_) / initialPixelSizeUm_;
   }

   private void zoomToVessel(double tlX, double tlY, double widthPx, double heightPx) {
      if (viewer_ == null) {
         return;
      }
      final double margin = 0.15;
      final double displayW = widthPx  * (1.0 + 2 * margin);
      final double displayH = heightPx * (1.0 + 2 * margin);
      final double offsetX  = tlX - widthPx  * margin;
      final double offsetY  = tlY - heightPx * margin;
      SwingUtilities.invokeLater(() -> {
         viewer_.setViewOffset(offsetX, offsetY);
         viewer_.setFullResSourceDataSizeAspectCorrected(displayW, displayH);
      });
   }

   /**
    * Re-reads the HCS calibration from the profile and applies it if found.
    * Called from ExplorerFrame when the operator clicks "Refresh".
    */
   public void refreshHcsCalibration() {
      if (!exploring_ || !vesselType_.isMultiWell()) {
         return;
      }
      Point2D.Double hcsOffset = tryReadHcsCalibration();
      frame_.setHcsCalibrationStatus(hcsOffset != null);
      if (hcsOffset != null) {
         applyHcsCalibration(hcsOffset);
      }
   }

   private void applyHcsCalibration(Point2D.Double offset) {
      // HCS formula: stage_XY = plate_definition_XY + offset.
      // Plate top-left is at plate definition (0, 0), so plateTL in stage coords = offset.
      vesselAnchorStageX_ = offset.x;
      vesselAnchorStageY_ = offset.y;
      vesselUsedHcsCal_   = true;
      vesselAnchorWellRow_ = 0;
      vesselAnchorWellCol_ = 0;
      updateVesselOutline();
   }

   /**
    * Sets the vessel anchor from the current stage position, interpreted as the center of
    * the given well (0-based row/col).  Computes the plate top-left from the well geometry.
    * Called from ExplorerFrame when the operator clicks "Set Anchor".
    */
   public void setVesselWellAnchor(int row, int col) {
      if (!exploring_ || !vesselType_.isMultiWell()) {
         return;
      }
      try {
         double stageX = studio_.core().getXPosition();
         double stageY = studio_.core().getYPosition();
         // Stage position of plate top-left = stage position of well center minus
         // how far that well's center is from the plate's top-left corner.
         vesselAnchorStageX_ = stageX
               - vesselType_.getFirstWellXUm()
               - col * vesselType_.getWellSpacingXUm();
         vesselAnchorStageY_ = stageY
               - vesselType_.getFirstWellYUm()
               - row * vesselType_.getWellSpacingYUm();
         vesselAnchorWellRow_ = row;
         vesselAnchorWellCol_ = col;
         vesselUsedHcsCal_ = false;
         vesselAnchorType_ = null;
         updateVesselOutline();
      } catch (Exception e) {
         studio_.logs().showError(e,
               "Explorer: could not read stage position for vessel well anchor.");
      }
   }

   /**
    * Reads the HCS SiteGenerator offset from the profile via reflection.
    * Returns null if HCS is not installed, not calibrated, or calibration values are NaN.
    * The returned offset satisfies: stage_XY = plate_definition_XY + offset.
    */
   private Point2D.Double tryReadHcsCalibration() {
      try {
         Class<?> sgClass = Class.forName("org.micromanager.hcs.SiteGenerator");
         java.util.List<Double> offset = studio_.profile()
               .getSettings(sgClass)
               .getDoubleList("site_offset",
                     java.util.Arrays.asList(Double.NaN, Double.NaN));
         if (offset.size() >= 2
               && Double.isFinite(offset.get(0))
               && Double.isFinite(offset.get(1))) {
            return new Point2D.Double(offset.get(0), offset.get(1));
         }
      } catch (ClassNotFoundException ignored) {
         // HCS plugin not installed
      } catch (Exception e) {
         studio_.logs().logError(e, "Explorer: error reading HCS calibration");
      }
      return null;
   }
}
