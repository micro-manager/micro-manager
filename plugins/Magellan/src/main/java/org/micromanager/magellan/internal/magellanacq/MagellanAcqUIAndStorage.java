///////////////////////////////////////////////////////////////////////////////
//FILE:          MMImageCache.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein
// COPYRIGHT:    University of California, San Francisco, 2010
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

package org.micromanager.magellan.internal.magellanacq;

import java.awt.Cursor;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngJDataSink;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.main.XYTiledAcquisition;
import org.micromanager.magellan.internal.explore.ChannelGroupSettings;
import org.micromanager.magellan.internal.explore.ExploreAcquisition;
import org.micromanager.magellan.internal.explore.ExploreImageExporter;
import org.micromanager.magellan.internal.explore.gui.ExploreControlsPanel;
import org.micromanager.magellan.internal.explore.gui.ExportControlsPanel;
import org.micromanager.magellan.internal.explore.gui.ExportDialog;
import org.micromanager.magellan.internal.explore.gui.ExportMouseListener;
import org.micromanager.magellan.internal.explore.gui.ExportSelectionOverlay;
import org.micromanager.magellan.internal.gui.MagellanMouseListener;
import org.micromanager.magellan.internal.gui.MagellanOverlayer;
import org.micromanager.magellan.internal.gui.SurfaceGridPanel;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridListener;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.ndviewer.api.NDViewerAPI;
import org.micromanager.ndviewer.api.NDViewerAcqInterface;
import org.micromanager.ndviewer.api.NDViewerDataSource;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.remote.PycroManagerCompatibleUI;

/**
 * This class links data storage, viewer, and acquisition, acting as
 * an intermediary when neccessary to implement functionality specific to
 * Explore acquisitions. For example, it removes the row and column axes
 * from the data stored on disk before passing to the viewer, so that
 * they display as one contiguous image.
 */
public class MagellanAcqUIAndStorage
        implements SurfaceGridListener, AcqEngJDataSink, NDViewerDataSource,
      PycroManagerCompatibleUI {


   private static final int SAVING_QUEUE_SIZE = 30;

   protected MultiresNDTiffAPI storage_;
   private ExecutorService displayCommunicationExecutor_;
   public final boolean loadedData_;
   private String dir_;
   private String name_;
   private final boolean showDisplay_;
   protected NDViewer display_;
   private JSONObject summaryMetadata_;
   private LinkedList<Consumer<HashMap<String, Object>>> displayUpdateOnImageHooks_
           = new LinkedList<Consumer<HashMap<String, Object>>>();

   protected XYTiledAcquisition acq_;
   protected MagellanMouseListener mouseListener_;
   protected ExploreControlsPanel exploreControlsPanel_;
   protected ExportControlsPanel exportControlsPanel_;
   private Consumer<String> logger_;
   private ChannelGroupSettings exploreChannels_;

   private MagellanOverlayer overlayer_;
   private SurfaceGridPanel surfaceGridControls_;
   private final boolean useZ_;

   public MagellanAcqUIAndStorage(String dir, String name, boolean useZ,
                                  ChannelGroupSettings exploreChannels,
                                  boolean showDisplay) {
      displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r)
              -> new Thread(r, "Magellan viewer communication thread"));
      logger_ = new Consumer<String>() {
         @Override
         public void accept(String s) {
            Log.log(s);
         }
      };
      dir_ = dir;
      name_ = name;
      useZ_ = useZ;
      loadedData_ = false;
      showDisplay_ = showDisplay;
      exploreChannels_ = exploreChannels;

      SurfaceGridManager.getInstance().registerSurfaceGridListener(this);
   }

   //Constructor for opening loaded data
   public MagellanAcqUIAndStorage(String dir) throws IOException {
      logger_ = new Consumer<String>() {
         @Override
         public void accept(String s) {
            Log.log(s);
         }
      };
      displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r)
              -> new Thread(r, "Magellan viewer communication thread"));
      storage_ = new NDTiffStorage(dir);
      dir_ = dir;
      useZ_ = false;
      loadedData_ = true;
      showDisplay_ = true;
      summaryMetadata_ = storage_.getSummaryMetadata();
      createDisplay();
   }

   @Override
   public void initialize(Acquisition acq, JSONObject summaryMetadata) {
      acq_ = (XYTiledAcquisition) acq;

      summaryMetadata_ = summaryMetadata;

      AcqEngMetadata.setHeight(summaryMetadata, (int) Engine.getCore().getImageHeight());
      AcqEngMetadata.setWidth(summaryMetadata, (int) Engine.getCore().getImageWidth());

      storage_ = new NDTiffStorage(dir_, name_,
              summaryMetadata,
              AcqEngMetadata.getPixelOverlapX(summaryMetadata),
              AcqEngMetadata.getPixelOverlapY(summaryMetadata),
              true, null, SAVING_QUEUE_SIZE,
              Engine.getCore().debugLogEnabled() ? (Consumer<String>) s
                      -> Engine.getCore().logMessage(s) : null, true);

      if (showDisplay_) {
         createDisplay();
      }
      //storage class has determined unique acq name, so it can now be stored
      name_ = this.getUniqueAcqName();
   }

   @Override
   public void finish() {
      if (!storage_.isFinished()) {
         //Get most up to date display settings
         JSONObject displaySettings = display_.getDisplaySettingsJSON();
         storage_.setDisplaySettings(displaySettings);
         storage_.finishedWriting();
      }
      display_.setWindowTitle(getUniqueAcqName() + " (Finished)");
      displayCommunicationExecutor_.shutdown();
      displayCommunicationExecutor_ = null;
   }

   @Override
   public boolean isFinished() {
      return  storage_.isFinished();
   }

   public Object putImage(final TaggedImage taggedImg) {
      try {
         HashMap<String, Object> axes = AcqEngMetadata.getAxes(taggedImg.tags);
         final Future added = storage_.putImageMultiRes(taggedImg.pix, taggedImg.tags, axes,
                 AcqEngMetadata.isRGB(taggedImg.tags), AcqEngMetadata.getBitDepth(taggedImg.tags),
                 AcqEngMetadata.getHeight(taggedImg.tags), AcqEngMetadata.getWidth(taggedImg.tags));

         if (showDisplay_) {
            //put on different thread to not slow down acquisition
            displayCommunicationExecutor_.submit(() -> {
               try {
                  added.get();
                  HashMap<String, Object> axes1 = AcqEngMetadata.getAxes(taggedImg.tags);
                  //Display doesn't know about these in tiled layout
                  axes1.remove(AcqEngMetadata.AXES_GRID_ROW);
                  axes1.remove(AcqEngMetadata.AXES_GRID_COL);
                  //  String channelName = MagellanMD.getChannelName(taggedImg.tags);
                  display_.newImageArrived(axes1);
                  for (Consumer<HashMap<String, Object>> displayHook : displayUpdateOnImageHooks_) {
                     displayHook.accept(axes1);
                  }
               } catch (Exception e) {
                  e.printStackTrace();
                  ;
                  throw new RuntimeException(e);
               }
            });
         }
         return added.get();
      } catch (Exception e) {
         throw new RuntimeException(e);
      }
   }

   @Override
   public boolean anythingAcquired() {
      return  !storage_.getAxesSet().isEmpty();
   }

   private void createDisplay() {
      //create display
      try {
         display_ = new NDViewer(this, (NDViewerAcqInterface) acq_, summaryMetadata_,
               AcqEngMetadata.getPixelSizeUm(summaryMetadata_),
               AcqEngMetadata.isRGB(summaryMetadata_));

         display_.setWindowTitle(getUniqueAcqName() + (acq_ != null
                 ? (((NDViewerAcqInterface) acq_).isFinished()
               ? " (Finished)" : " (Running)") : " (Loaded)"));
         //add functions so display knows how to parse time and z infomration from image tags
         display_.setReadTimeMetadataFunction((JSONObject tags)
                 -> AcqEngMetadata.getElapsedTimeMs(tags));
         display_.setReadZMetadataFunction((JSONObject tags)
                 -> AcqEngMetadata.getStageZIntended(tags));

         //add overlayer
         surfaceGridControls_ = new SurfaceGridPanel(this, display_);

         //add in custom mouse listener for the canvas
         mouseListener_ = new MagellanMouseListener(display_, surfaceGridControls_, acq_);

         //add in additional overlayer
         overlayer_ = new MagellanOverlayer(display_, acq_, mouseListener_, surfaceGridControls_);

         // replace explore overlayer with magellan one
         display_.setOverlayerPlugin(overlayer_);

         if (!loadedData_) {
            display_.addControlPanel(surfaceGridControls_);
         }

         display_.setOverlayerPlugin(overlayer_);

         if (acq_ instanceof ExploreAcquisition) {
            exploreControlsPanel_ = new ExploreControlsPanel((ExploreAcquisition) acq_,
                    overlayer_, useZ_, exploreChannels_, acq_.getZAxes());
            display_.addControlPanel(exploreControlsPanel_);
            exportControlsPanel_ = new ExportControlsPanel(this::startExportMode);
            display_.addControlPanel(exportControlsPanel_);
         }

         display_.setCustomCanvasMouseListener(mouseListener_);

         SwingUtilities.invokeLater(() -> {
            Window w = SwingUtilities.getWindowAncestor(display_.getCanvasJPanel());
            if (w != null) {
               w.setIconImage(Toolkit.getDefaultToolkit().getImage(
                       getClass().getResource("/org/micromanager/icons/microscope.gif")));
            }
         });

         display_.addSetImageHook(new Consumer<HashMap<String, Object>>() {
            @Override
            public void accept(HashMap<String, Object> axes) {

               // iterate through z devices and update their current z position
               if (acq_ != null) {
                  for (String name : acq_.getZAxes().keySet()) {
                     if (axes.containsKey(name)) {
                        Integer i = (Integer) axes.get(name);
                        exploreControlsPanel_.updateGUIToReflectHardwareZPosition(name, i);
                     }
                  }
               }
            }
         });

      } catch (Exception e) {
         e.printStackTrace();
         logger_.accept("Couldn't create display successfully");
      }
   }

   public String getUniqueAcqName() {
      if (loadedData_) {
         return dir_;
      }
      File file = new File(storage_.getDiskLocation());
      String simpleFileName = file.getName();
      return simpleFileName;
   }

   @Override
   public int[] getBounds() {
      if (acq_ instanceof ExploreAcquisition && !loadedData_) {
         return null;
      }
      int[] bounds = storage_.getImageBounds();
      if (storage_.getAxesSet().isEmpty()) {
         return bounds;
      }
      HashMap<String, Object> axesPositions = new HashMap<>(
              storage_.getAxesSet().iterator().next());
      // Need to add back in row and column of a image thats in the data
      for (HashMap<String, Object> storedAxesPosition : storage_.getAxesSet()) {
         for (String axis : axesPositions.keySet()) {
            if (!storedAxesPosition.containsKey(axis) || !axesPositions.containsKey(axis)) {
               continue;
            }
         }
         axesPositions = storedAxesPosition;
         break;
      }
      int width = storage_.getEssentialImageMetadata(axesPositions).width;
      int height = storage_.getEssentialImageMetadata(axesPositions).height;
      bounds[0] = bounds[0] - width;
      bounds[1] = bounds[1] - height;
      bounds[2] = bounds[2] + width;
      bounds[3] = bounds[3] + 2 * height;
      return bounds;
   }

   @Override
   public TaggedImage getImageForDisplay(HashMap<String, Object> axes, int resolutionindex,
                                double xOffset, double yOffset, int imageWidth, int imageHeight) {
      return storage_.getDisplayImage(
              axes,
              resolutionindex,
              (int) xOffset, (int) yOffset,
              imageWidth, imageHeight);
   }

   @Override
   public Set<HashMap<String, Object>> getImageKeys() {
      return storage_.getAxesSet().stream().map(
              new Function<HashMap<String, Object>, HashMap<String, Object>>() {
                  @Override
                  public HashMap<String, Object> apply(HashMap<String, Object> axes) {
                     HashMap<String, Object> copy = new HashMap<String, Object>(axes);
                     //delete row and column so viewer doesn't use them
                     copy.remove(NDTiffStorage.ROW_AXIS);
                     copy.remove(NDTiffStorage.COL_AXIS);
                     return copy;
                  }
               }).collect(Collectors.toSet());
   }

   @Override
   public int getMaxResolutionIndex() {
      return storage_.getNumResLevels() - 1;
   }

   @Override
   public void increaseMaxResolutionLevel(int i) {
      storage_.increaseMaxResolutionLevel(i);
   }

   @Override
   public String getDiskLocation() {
      return storage_.getDiskLocation();
   }

   @Override
   public void close() {
      //on close
      if (!loadedData_) {
         SurfaceGridManager.getInstance().unregisterSurfaceGridListener(this);
      }
   }

   @Override
   public int getImageBitDepth(HashMap<String, Object> axesPositions) {
      // make a copy so we dont modify the original
      axesPositions = new HashMap<String, Object>(axesPositions);
      // Need to add back in row and column of a image thats in the data
      for (HashMap<String, Object> storedAxesPosition : storage_.getAxesSet()) {
         for (String axis : axesPositions.keySet()) {
            if (!storedAxesPosition.containsKey(axis) || !axesPositions.containsKey(axis)) {
               continue;
            }
         }
         axesPositions = storedAxesPosition;
         break;
      }
      return storage_.getEssentialImageMetadata(axesPositions).bitDepth;
   }


   public NDTiffAPI getStorage() {
      return storage_;
   }

   @Override
   public NDViewerAPI getViewer() {
      return display_;
   }

   public void setSurfaceDisplaySettings(boolean showInterp, boolean showStage) {
      overlayer_.setSurfaceDisplayParams(showInterp, showStage);
   }

   public Point2D.Double getStageCoordinateOfViewCenter() {
      return acq_.getPixelStageTranslator().getStageCoordsFromPixelCoords(
              (long) (display_.getViewOffset().x + display_.getFullResSourceDataSize().x / 2),
              (long) (display_.getViewOffset().y + display_.getFullResSourceDataSize().y / 2));
   }

   public void update() {
      display_.update();
   }

   public void initializeViewerToLoaded(
           HashMap<String, Object> axisMins, HashMap<String, Object> axisMaxs) {

      LinkedList<String> channelNames = new LinkedList<String>();
      for (HashMap<String, Object> axes : storage_.getAxesSet()) {
         if (axes.containsKey(MagellanMD.CHANNEL_AXIS)) {
            if (!channelNames.contains(axes.get(MagellanMD.CHANNEL_AXIS))) {
               channelNames.add((String) axes.get(MagellanMD.CHANNEL_AXIS));
            }
         }
      }
      display_.initializeViewerToLoaded(storage_.getDisplaySettings());
   }

   public Set<HashMap<String, Object>> getAxesSet() {
      return storage_.getAxesSet();
   }


   @Override
   public void surfaceOrGridChanged(XYFootprint f) {
      update();
   }

   @Override
   public void surfaceOrGridDeleted(XYFootprint f) {
      update();
   }

   @Override
   public void surfaceOrGridCreated(XYFootprint f) {
      update();
   }

   @Override
   public void surfaceOrGridRenamed(XYFootprint f) {
      update();
   }

   @Override
   public void surfaceInterpolationUpdated(SurfaceInterpolator s) {
      update();
   }

   public void startExportMode() {
      javax.swing.JPanel canvas = display_.getCanvasJPanel();
      canvas.setCursor(Cursor.getPredefinedCursor(Cursor.CROSSHAIR_CURSOR));
      if (exportControlsPanel_ != null) {
         exportControlsPanel_.setStatus("Draw a selection on the image");
      }

      ExportSelectionOverlay exportOverlay = new ExportSelectionOverlay(display_);
      display_.setOverlayerPlugin(exportOverlay);

      ExportMouseListener exportListener = new ExportMouseListener(display_,
              (dragStart, dragEnd) -> {
                 exportOverlay.setExportMouseListener(null);
                 display_.setOverlayerPlugin(overlayer_);
                 canvas.removeMouseListener(exportListener_);
                 canvas.removeMouseMotionListener(exportListener_);
                 canvas.removeMouseWheelListener(exportListener_);
                 display_.setCustomCanvasMouseListener(mouseListener_);
                 canvas.setCursor(Cursor.getDefaultCursor());
                 if (exportControlsPanel_ != null) {
                    exportControlsPanel_.setStatus(null);
                 }
                 onExportRoiSelected(dragStart, dragEnd);
              });
      exportListener_ = exportListener;
      exportOverlay.setExportMouseListener(exportListener);
      // Manually remove the current listener since NDViewer's setCustomCanvasMouseListener
      // does not track which listener is currently active after the first swap.
      canvas.removeMouseListener(mouseListener_);
      canvas.removeMouseMotionListener(mouseListener_);
      canvas.removeMouseWheelListener(mouseListener_);
      display_.setCustomCanvasMouseListener(exportListener);
   }

   private ExportMouseListener exportListener_;

   private void onExportRoiSelected(Point dragStart, Point dragEnd) {
      Point2D.Double viewOffset = display_.getViewOffset();
      double mag = display_.getMagnification();
      int x1 = (int) (viewOffset.x + Math.min(dragStart.x, dragEnd.x) / mag);
      int y1 = (int) (viewOffset.y + Math.min(dragStart.y, dragEnd.y) / mag);
      int x2 = (int) (viewOffset.x + Math.max(dragStart.x, dragEnd.x) / mag);
      int y2 = (int) (viewOffset.y + Math.max(dragStart.y, dragEnd.y) / mag);
      int roiW = Math.max(1, x2 - x1);
      int roiH = Math.max(1, y2 - y1);

      ExportDialog dialog = new ExportDialog(
              SwingUtilities.getWindowAncestor(display_.getCanvasJPanel()),
              storage_.getNumResLevels(), roiW, roiH);
      ExportDialog.ExportOptions opts = dialog.showAndGet();
      if (opts == null) {
         return;
      }

      HashMap<String, Object> baseAxes = new HashMap<>();
      if (acq_ instanceof ExploreAcquisition) {
         for (String zName : ((ExploreAcquisition) acq_).getZAxes().keySet()) {
            baseAxes.put(zName, display_.getAxisPosition(zName));
         }
      }

      List<String> channels = new ArrayList<>();
      for (HashMap<String, Object> axes : storage_.getAxesSet()) {
         if (axes.containsKey(MagellanMD.CHANNEL_AXIS)) {
            String ch = (String) axes.get(MagellanMD.CHANNEL_AXIS);
            if (!channels.contains(ch)) {
               channels.add(ch);
            }
         }
      }
      // If no channel axis exists, use null to indicate single-channel no-name data
      if (channels.isEmpty()) {
         channels.add(null);
      }

      JSONObject displaySettings = display_.getDisplaySettingsJSON();
      final int x1f = x1;
      final int y1f = y1;
      final int roiWf = roiW;
      final int roiHf = roiH;

      new Thread(() -> {
         try {
            new ExploreImageExporter(storage_, displaySettings)
                    .export(baseAxes, channels, x1f, y1f, roiWf, roiHf,
                            opts.resolutionLevel, opts.format, opts.filePath);
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                            "Export complete:\n" + opts.filePath));
         } catch (Exception ex) {
            ex.printStackTrace();
            SwingUtilities.invokeLater(() ->
                    JOptionPane.showMessageDialog(null,
                            "Export failed: " + ex.getMessage(),
                            "Export Error", JOptionPane.ERROR_MESSAGE));
         }
      }, "Magellan-Export").start();
   }

}
