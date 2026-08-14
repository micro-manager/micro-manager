package org.micromanager.tileddataviewer;

import java.awt.Color;
import java.awt.geom.Point2D;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONObject;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.TiledDataOpenerPlugin;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.RememberedDisplaySettings;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.ColorPalettes;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.tileddataprovider.DatasetPathUtils;
import org.micromanager.tileddataprovider.NDTiffProviderAdapter;
import org.micromanager.tileddataprovider.TiledDataProviderFactory;
import org.scijava.plugin.Plugin;

/**
 * Opens tiled (pyramidal) datasets from Micro-Manager's File menu and by drag-and-drop.
 *
 * <p>Before this existed, OME-Zarr, OME-BigTIFF and tiled NDTiff datasets could only be opened
 * through the Explorer plugin. Dropping one on the main window either failed with a message
 * suggesting the data was not a Micro-Manager dataset, or - for tiled NDTiff - appeared to
 * succeed while showing a single tile instead of the mosaic.
 *
 * <p>This opener is read-only and deliberately independent of Explorer: it has no acquisition,
 * stage or region-selection behaviour, and exists only to display data that is already on disk.
 * The viewer setup sequence follows Explorer's, whose ordering constraints are noted inline.
 */
@Plugin(type = TiledDataOpenerPlugin.class)
public final class TiledDataOpenerImpl implements TiledDataOpenerPlugin {

   private static final String MM_DISPLAY_SETTINGS_FILE = "mm_display_settings.json";
   private static final String VIEW_STATE_FILE = "view_state.json";

   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Tiled Data Opener";
   }

   @Override
   public String getHelpText() {
      return "Opens tiled (pyramidal) datasets in the Tiled Data Viewer.";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Regents of the University of California, 2026";
   }

   @Override
   public boolean canOpen(String path) {
      String dir = DatasetPathUtils.normalizeDatasetPath(path);
      return dir != null && DatasetPathUtils.isTiledDataset(dir);
   }

   @Override
   public String open(String path) {
      final String dir = DatasetPathUtils.normalizeDatasetPath(path);
      if (dir == null) {
         studio_.logs().showMessage("Not a recognized Micro-Manager dataset: " + path);
         return null;
      }

      MultiresNDTiffAPI storage = null;
      try {
         storage = TiledDataProviderFactory.openExisting(dir);
         final MultiresNDTiffAPI theStorage = storage;

         final JSONObject summaryMetadata = storage.getSummaryMetadata();
         final boolean isRgb = "RGB32".equals(summaryMetadata.optString("PixelType", ""));
         double pixelSizeUm = summaryMetadata.optDouble("PixelSize_um", 1.0);
         if (pixelSizeUm <= 0) {
            pixelSizeUm = 1.0;
         }
         final String name = new File(dir).getName();

         SummaryMetadata parsedSummaryMetadata = null;
         try {
            PropertyMap pm = NonPropertyMapJSONFormats.summaryMetadata()
                  .fromJSON(summaryMetadata.toString());
            parsedSummaryMetadata = DefaultSummaryMetadata.fromPropertyMap(pm);
         } catch (Exception e) {
            studio_.logs().logError(e, "Failed to parse stored summary metadata");
         }

         final TiledDataViewerDataProviderAPI dataProvider =
               TiledDataViewerFactory.createDataProvider(studio_.data(),
                     new NDTiffProviderAdapter(storage), name, parsedSummaryMetadata);

         // Holder so the close callback can reach the viewer that is about to be built.
         final TiledDataViewerDataViewerAPI[] viewerHolder = new TiledDataViewerDataViewerAPI[1];
         final LoadedDataSource dataSource = new LoadedDataSource(storage,
               () -> saveSettingsAndClose(theStorage, viewerHolder[0], dir));

         final TiledDataViewerDataViewerAPI mm2Viewer = TiledDataViewerFactory.createDataViewer(
               studio_, dataSource, createAcqInterface(), dataProvider,
               summaryMetadata, pixelSizeUm, isRgb);
         viewerHolder[0] = mm2Viewer;
         mm2Viewer.setAccumulateStats(true);

         // Read before the viewer is populated, so the background task below can apply them.
         File mmSettingsFile = new File(dir, MM_DISPLAY_SETTINGS_FILE);
         final DisplaySettings savedMMSettings = mmSettingsFile.canRead()
               ? DefaultDisplaySettings.getSavedDisplaySettings(mmSettingsFile) : null;
         final JSONObject savedViewState = loadViewState(new File(dir, VIEW_STATE_FILE));

         final TiledDataViewerAPI viewer = mm2Viewer.getTiledDataViewer();
         viewer.setWindowTitle(name);
         viewer.setReadTimeMetadataFunction(tags -> {
            try {
               if (tags.has("ElapsedTime-ms")) {
                  return tags.getLong("ElapsedTime-ms");
               }
            } catch (Exception e) {
               // No elapsed time recorded for this image.
            }
            return 0L;
         });
         viewer.setReadZMetadataFunction(tags -> tags.optDouble("ZPositionUm", 0.0));
         viewer.setViewOffset(0, 0);

         // Nothing arrives on its own for a dataset that is already written, so the viewer has
         // to be seeded. Done off the EDT because it reads images from disk.
         ExecutorService initExecutor = Executors.newSingleThreadExecutor(
               r -> new Thread(r, "Tiled dataset loading"));
         initExecutor.submit(() -> {
            try {
               seedAndInitialize(theStorage, dataProvider, mm2Viewer, viewer,
                     summaryMetadata, savedMMSettings, savedViewState);
            } catch (RuntimeException e) {
               studio_.logs().logError(e, "Failed to initialize viewer for " + dir);
            }
         });
         initExecutor.shutdown();

         studio_.logs().logMessage("Opened tiled dataset from " + dir);
         return dir;
      } catch (Exception e) {
         studio_.logs().showError(e, "Failed to open dataset at " + dir);
         if (storage != null) {
            try {
               storage.close();
            } catch (Exception ce) {
               studio_.logs().logError(ce);
            }
         }
         return null;
      }
   }

   /**
    * Populates the viewer with the dataset that was just opened.
    *
    * <p>The order here matters and is the same as Explorer's: the channels have to be
    * registered by {@code initializeViewerToLoaded()} before any display settings are applied,
    * or the channel names are lost and the Inspector shows unnamed channels.
    */
   private void seedAndInitialize(MultiresNDTiffAPI storage,
                                  TiledDataViewerDataProviderAPI dataProvider,
                                  TiledDataViewerDataViewerAPI mm2Viewer,
                                  TiledDataViewerAPI viewer,
                                  JSONObject summaryMetadata,
                                  DisplaySettings savedMMSettings,
                                  JSONObject savedViewState) {
      List<Image> seedImages = new ArrayList<>();
      List<HashMap<String, Object>> seedAxesList = new ArrayList<>();
      Set<Object> seenChannels = new LinkedHashSet<>();
      for (HashMap<String, Object> axes : storage.getAxesSet()) {
         Object ch = axes.get("channel");
         if (!seenChannels.add(ch == null ? "" : ch)) {
            continue;
         }
         HashMap<String, Object> channelAxes = new HashMap<>();
         if (ch != null) {
            channelAxes.put("channel", ch);
         }
         try {
            Image img = dataProvider.getDownsampledImageByAxes(axes);
            if (img != null) {
               dataProvider.newImageArrived(img, channelAxes);
               seedImages.add(img);
               seedAxesList.add(channelAxes);
            }
         } catch (Exception e) {
            studio_.logs().logMessage("Exception fetching seed image: " + e);
         }
      }
      if (!seedImages.isEmpty()) {
         mm2Viewer.newTileArrived(seedImages, seedAxesList);
      }

      // Register the channels first; only then attach settings to them. The cast picks the
      // JSONObject overload, matching what Explorer does.
      viewer.initializeViewerToLoaded((JSONObject) null);
      try {
         if (savedMMSettings != null) {
            mm2Viewer.setDisplaySettings(savedMMSettings);
         } else {
            applyDisplaySettingsHeuristics(storage, mm2Viewer, summaryMetadata);
         }
      } catch (Exception e) {
         studio_.logs().logError(e, "Failed to initialize DisplaySettings");
      }
      viewer.update();

      if (savedViewState != null) {
         double mag = savedViewState.optDouble("magnification", 0);
         if (mag > 0) {
            Point2D.Double displaySize = viewer.getDisplayImageSize();
            viewer.setFullResSourceDataSize(displaySize.x / mag, displaySize.y / mag);
         }
         viewer.setViewOffset(savedViewState.optDouble("xView", 0),
               savedViewState.optDouble("yView", 0));
         viewer.update();
      }
   }

   /**
    * Gives each channel a name and a sensible colour when the dataset has no saved MM display
    * settings. Colours are taken from the dataset's own stored settings where present, then
    * from what the user last chose for that channel, then from the name, and finally from the
    * default palette.
    */
   private void applyDisplaySettingsHeuristics(MultiresNDTiffAPI storage,
                                               TiledDataViewerDataViewerAPI mm2Viewer,
                                               JSONObject summaryMetadata) {
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
            for (HashMap<String, Object> axes : storage.getAxesSet()) {
               Object ch = axes.get("channel");
               if (ch != null) {
                  seen.add(ch.toString());
               }
            }
            channelNames.addAll(seen);
         }
         if (channelNames.isEmpty()) {
            return;
         }

         JSONObject storedSettings = storage.getDisplaySettings();
         DisplaySettings.Builder dsBuilder = studio_.displays().displaySettingsBuilder();
         dsBuilder = channelNames.size() > 1
               ? dsBuilder.colorModeComposite() : dsBuilder.colorModeGrayscale();
         for (int i = 0; i < channelNames.size(); i++) {
            String name = channelNames.get(i);
            Color color = null;
            if (storedSettings != null) {
               try {
                  color = new Color(storedSettings.getJSONObject(name).getInt("Color"));
               } catch (Exception e) {
                  // No stored colour for this channel.
               }
            }
            if (color == null || color.equals(Color.WHITE)) {
               ChannelDisplaySettings remembered =
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
            dsBuilder.channel(i, studio_.displays().channelDisplaySettingsBuilder()
                  .name(name)
                  .color(color)
                  .build());
         }
         mm2Viewer.setDisplaySettings(dsBuilder.build());
      } catch (Exception e) {
         studio_.logs().logError(e, "Failed to apply display settings heuristics");
      }
   }

   /**
    * Writes the display settings and view state back to the dataset, then closes the storage.
    *
    * <p>Both have to be read from the viewer before it is closed, so they are captured here
    * rather than on the background thread that does the writing.
    */
   private void saveSettingsAndClose(MultiresNDTiffAPI storage,
                                     TiledDataViewerDataViewerAPI mm2Viewer,
                                     String dir) {
      DisplaySettings capturedSettings = null;
      JSONObject capturedViewState = null;
      if (mm2Viewer != null) {
         try {
            capturedSettings = mm2Viewer.getDisplaySettings();
         } catch (Exception e) {
            studio_.logs().logError(e);
         }
         try {
            capturedViewState = captureViewState(mm2Viewer.getTiledDataViewer());
         } catch (Exception e) {
            studio_.logs().logError(e);
         }
      }

      final DisplaySettings settingsToSave = capturedSettings;
      final JSONObject viewStateToSave = capturedViewState;
      new Thread(() -> {
         try {
            if (settingsToSave != null) {
               ((DefaultDisplaySettings) settingsToSave)
                     .save(new File(dir, MM_DISPLAY_SETTINGS_FILE));
            }
            if (viewStateToSave != null) {
               Files.write(new File(dir, VIEW_STATE_FILE).toPath(),
                     viewStateToSave.toString(2).getBytes(StandardCharsets.UTF_8));
            }
         } catch (Exception e) {
            studio_.logs().logError(e, "Error saving display settings for " + dir);
         }
         try {
            storage.close();
         } catch (Exception e) {
            studio_.logs().logError(e, "Error closing storage for " + dir);
         }
      }, "Tiled dataset cleanup").start();
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

   /**
    * A loaded dataset is never acquiring, so reporting it as finished keeps the viewer from
    * asking whether to finish the acquisition when the window is closed.
    */
   private static TiledDataViewerAcqInterface createAcqInterface() {
      return new TiledDataViewerAcqInterface() {
         @Override
         public boolean isFinished() {
            return true;
         }

         @Override
         public boolean requestToClose() {
            return true;
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
}
