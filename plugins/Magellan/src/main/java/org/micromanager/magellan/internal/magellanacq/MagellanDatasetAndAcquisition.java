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

import java.awt.Color;
import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
//import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.util.xytiling.XYStagePosition;
import org.micromanager.magellan.internal.channels.ChannelGroupSettings;
import org.micromanager.magellan.internal.gui.ExploreControlsPanel;
import org.micromanager.magellan.internal.gui.MagellanMouseListener;
import org.micromanager.magellan.internal.gui.MagellanOverlayer;
import org.micromanager.magellan.internal.gui.MagellanViewer;
import org.micromanager.magellan.internal.gui.SurfaceGridPanel;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridListener;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceGridManager;
import org.micromanager.magellan.internal.surfacesandregions.SurfaceInterpolator;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.ndviewer.api.DataSourceInterface;
import org.micromanager.ndviewer.api.OverlayerPlugin;
import org.micromanager.ndviewer.api.ViewerAcquisitionInterface;
import org.micromanager.ndviewer.overlay.Overlay;

/**
 * Created by magellan acquisition to manage viewer, data storage, and
 * conversion between pixel coordinate space (which the viewer and storage work
 * in) and the stage coordiante space (which the acquisition works in).
 */
public class MagellanDatasetAndAcquisition implements DataSink, DataSourceInterface,
        SurfaceGridListener {

   private static final int SAVING_QUEUE_SIZE = 30;

   private MultiresNDTiffAPI storage_;
   private ExecutorService displayCommunicationExecutor_;
   private final boolean loadedData_;
   private String dir_;
   private String name_;
   private MagellanAcquisition acq_;
   private final boolean showDisplay_;
   private MagellanViewer display_;
   private JSONObject summaryMetadata_;
   private CopyOnWriteArrayList<String> channelNames_ = new CopyOnWriteArrayList<String>();
   private MagellanMouseListener mouseListener_;
   private OverlayerPlugin overlayer_;
   private double pixelSizeZ_;
   private ExploreControlsPanel zExploreControls_;
   private SurfaceGridPanel surfaceGridControls_;

   public MagellanDatasetAndAcquisition(MagellanAcquisition acq, String dir, String name,
                                        boolean showDisplay) {
      acq_ = acq;
      displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r)
              -> new Thread(r, "Magellan viewer communication thread"));

      dir_ = dir;
      name_ = name;
      loadedData_ = false;
      showDisplay_ = showDisplay;
      SurfaceGridManager.getInstance().registerSurfaceGridListener(this);
   }

   //Constructor for opening loaded data
   public MagellanDatasetAndAcquisition(String dir) throws IOException {
      displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r)
              -> new Thread(r, "Magellan viewer communication thread"));
      storage_ = new NDTiffStorage(dir);
      dir_ = dir;
      loadedData_ = true;
      showDisplay_ = true;
      summaryMetadata_ = storage_.getSummaryMetadata();
      createDisplay();
   }

   public void initialize(Acquisition acq, JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      pixelSizeZ_ = acq_.getZStep();

      AcqEngMetadata.setHeight(summaryMetadata, (int) Magellan.getCore().getImageHeight());
      AcqEngMetadata.setWidth(summaryMetadata, (int) Magellan.getCore().getImageWidth());

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

   public MultiresNDTiffAPI getStorage() {
      return storage_;
   }

   private void createDisplay() {
      //create display
      try {
         display_ = new MagellanViewer(this, (ViewerAcquisitionInterface) acq_, summaryMetadata_);
         display_.setWindowTitle(getUniqueAcqName() + (acq_ != null
                 ? (acq_.isFinished() ? " (Finished)" : " (Running)") : " (Loaded)"));
         //add functions so display knows how to parse time and z infomration from image tags
         display_.setReadTimeMetadataFunction((JSONObject tags)
               -> AcqEngMetadata.getElapsedTimeMs(tags));
         display_.setReadZMetadataFunction((JSONObject tags)
               -> AcqEngMetadata.getStageZIntended(tags));

         //add in custom mouse listener for the canvas
         mouseListener_ = new MagellanMouseListener(this, display_);
         display_.setCustomCanvasMouseListener(mouseListener_);

         //add in additional overlayer
         overlayer_ = new MagellanOverlayer(this);
         display_.setOverlayerPlugin(overlayer_);

         //Add in magellan specific panels
         surfaceGridControls_ = new SurfaceGridPanel(this, display_);
         if (!loadedData_) {
            display_.addControlPanel(surfaceGridControls_);
            if (isExploreAcquisition()) {
               zExploreControls_ = new ExploreControlsPanel(this,
                       (ChannelGroupSettings) acq_.getAcquisitionSettings().channels_);
               display_.addControlPanel(zExploreControls_);
               this.setSurfaceGridMode(false); //start in explore mode
            }
         }

      } catch (Exception e) {
         e.printStackTrace();
         Log.log("Couldn't create display succesfully");
      }
   }

   public void putImage(final TaggedImage taggedImg) {

      String channelName = MagellanMD.getChannelName(taggedImg.tags);
      boolean newChannel = !channelNames_.contains(channelName);
      if (newChannel) {
         channelNames_.add(channelName);
      }

      HashMap<String, Integer> axes = MagellanMD.getAxes(taggedImg.tags);
      axes.put(MagellanMD.CHANNEL_AXIS, channelNames_.indexOf(channelName));

      Future added = storage_.putImageMultiRes(taggedImg.pix, taggedImg.tags, axes,
              AcqEngMetadata.isRGB(taggedImg.tags), AcqEngMetadata.getHeight(taggedImg.tags),
              AcqEngMetadata.getWidth(taggedImg.tags));

      if (showDisplay_) {
         //put on different thread to not slow down acquisition

         displayCommunicationExecutor_.submit(new Runnable() {
            @Override
            public void run() {
               try {
                  added.get();

                  if (newChannel) {
                     //Insert a preferred color. Make a copy just in case concurrency issues
                     String chName = MagellanMD.getChannelName(taggedImg.tags);
                     ChannelGroupSettings ch = ((ChannelGroupSettings) acq_.getChannels());
                     Color c = ch == null ? Color.white : ch.getPreferredChannelColor(chName);
                     int bitDepth = MagellanMD.getBitDepth(taggedImg.tags);
                     display_.setChannelDisplaySettings(chName, c, bitDepth);
                  }

                  HashMap<String, Integer> axes = MagellanMD.getAxes(taggedImg.tags);
                  //Display doesn't know about these in tiled layout
                  axes.remove(AcqEngMetadata.AXES_GRID_ROW);
                  axes.remove(AcqEngMetadata.AXES_GRID_COL);
                  String channelName = MagellanMD.getChannelName(taggedImg.tags);
                  display_.newImageArrived(axes, channelName);

                  if (axes.containsKey(AcqEngMetadata.Z_AXIS) && axes.get(AcqEngMetadata.Z_AXIS)
                        != null && zExploreControls_ != null) {
                     Integer i = axes.get(AcqEngMetadata.Z_AXIS);
                     zExploreControls_.updateExploreZControls(i);
                  }

                  //technically this only needs to happen once, but eh
                  surfaceGridControls_.enable();
               } catch (Exception e) {
                  e.printStackTrace();;
                  throw new RuntimeException(e);
               }
            }
         });
      }
   }

   public void updateExploreZControls(int i) {
      zExploreControls_.updateExploreZControls(i);
   }

   /**
    * Called when images done arriving.
    */
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

   public boolean isFinished() {
      return storage_ == null ? true : storage_.isFinished();
   }

   public String getDiskLocation() {
      return storage_.getDiskLocation();
   }

   /**
    * Used for data loaded from disk.
    *
    * @return
    */
   public JSONObject getDisplayJSON() {
      try {
         return storage_.getDisplaySettings() == null ? null
               : new JSONObject(storage_.getDisplaySettings().toString());
      } catch (JSONException ex) {
         throw new RuntimeException("THis shouldnt happen");
      }
   }

   /**
    * The display calls this when its closing.
    */
   @Override
   public void close() {
      if (storage_.isFinished()) {
         if (!loadedData_) {
            SurfaceGridManager.getInstance().unregisterSurfaceGridListener(this);
         }
         storage_.close();
         storage_ = null;

         mouseListener_ = null;
         overlayer_ = null;
         display_ = null;
         zExploreControls_ = null;

      } else {
         //keep resubmitting so that finish, which comes from a different thread, happens first
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               MagellanDatasetAndAcquisition.this.close();
            }
         });
      }
   }

   public JSONObject getSummaryMD() {
      if (storage_ == null) {
         Log.log("imageStorage_ is null in getSummaryMetadata", true);
         return null;
      }
      try {
         return new JSONObject(storage_.getSummaryMetadata().toString());
      } catch (JSONException ex) {
         throw new RuntimeException("This shouldnt happen");
      }
   }

   public int getDisplayTileHeight() {
      return acq_.getPixelStageTranslator() == null ? 0
            : acq_.getPixelStageTranslator().getDisplayTileHeight();
   }

   public int getDisplayTileWidth() {
      return acq_.getPixelStageTranslator() == null ? 0
            : acq_.getPixelStageTranslator().getDisplayTileWidth();
   }

   public boolean isExploreAcquisition() {
      return MagellanMD.isExploreAcq(summaryMetadata_);
   }

   public int[] getBounds() {
      if (isExploreAcquisition() && !loadedData_) {
         return null;
      }
      return storage_.getImageBounds();
   }

   @Override
   public TaggedImage getImageForDisplay(HashMap<String, Integer> axes, int resolutionindex,
           double xOffset, double yOffset, int imageWidth, int imageHeight) {

      return storage_.getDisplayImage(
              axes,
              resolutionindex,
              (int) xOffset, (int) yOffset,
              imageWidth, imageHeight);
   }

   @Override
   public Set<HashMap<String, Integer>> getStoredAxes() {

      return storage_.getAxesSet().stream().map(
            new Function<HashMap<String, Integer>, HashMap<String, Integer>>() {
            @Override
            public HashMap<String, Integer> apply(HashMap<String, Integer> axes) {
               HashMap<String, Integer> copy = new HashMap<String, Integer>(axes);
               //delete row and column so viewer doesn't use them
               copy.remove(NDTiffStorage.ROW_AXIS);
               copy.remove(NDTiffStorage.COL_AXIS);
               return copy;
            }
         }).collect(Collectors.toSet());
   }

   public boolean anythingAcquired() {
      return storage_ == null || !storage_.getAxesSet().isEmpty();
   }

   public String getName() {
      return name_;
   }

   public String getDir() {
      return dir_;
   }

   public String getUniqueAcqName() {
      if (loadedData_) {
         return dir_;
      }
      File file = new File(storage_.getDiskLocation());
      String simpleFileName = file.getName();
      return simpleFileName;
   }

   public Point2D.Double stageCoordsFromPixelCoords(int x, int y) {
      return stageCoordsFromPixelCoords(x, y, display_.getMagnification(),
            display_.getViewOffset());
   }

   /**
    *
    * @param absoluteX x coordinate in the full Res stitched image
    * @param absoluteY y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public Point2D.Double stageCoordsFromPixelCoords(int absoluteX, int absoluteY,
           double mag, Point2D.Double offset) {
      long newX = (long) (absoluteX / mag + offset.x);
      long newY = (long) (absoluteY / mag + offset.y);
      return acq_.getPixelStageTranslator().getStageCoordsFromPixelCoords(newX, newY);
   }

   public int getFullResPositionIndexFromStageCoords(double xPos, double yPos) {
      return acq_.getPixelStageTranslator().getFullResPositionIndexFromStageCoords(xPos, yPos);
   }

   /* 
    * @param stageCoords x and y coordinates of image in stage space
    * @return absolute, full resolution pixel coordinate of given stage posiiton
    */
   public Point pixelCoordsFromStageCoords(double x, double y, double magnification,
           Point2D.Double offset) {
      Point fullResCoords = acq_.getPixelStageTranslator().getPixelCoordsFromStageCoords(x, y);
      return new Point(
              (int) ((fullResCoords.x - offset.x) * magnification),
              (int) ((fullResCoords.y - offset.y) * magnification));
   }

   public XYStagePosition getXYPosition(int posIndex) {
      return acq_.getPixelStageTranslator().getXYPosition(posIndex);
   }

   public int[] getPositionIndices(int[] newPositionRows, int[] newPositionCols) {
      return acq_.getPixelStageTranslator().getPositionIndices(newPositionRows, newPositionCols);
   }

   public List<XYStagePosition> getPositionList() {
      return acq_.getPixelStageTranslator().getPositionList();
   }

   public int getMaxResolutionIndex() {
      return storage_.getNumResLevels() - 1;
   }

   public Set<Point> getTileIndicesWithDataAt(int zIndex) {
      return storage_.getTileIndicesWithDataAt(zIndex);
   }

   public double getZStep() {
      return pixelSizeZ_;
   }

   public LinkedBlockingQueue<ExploreAcquisition.ExploreTileWaitingToAcquire>
         getTilesWaitingToAcquireAtVisibleSlice() {
      return ((ExploreAcquisition) acq_).getTilesWaitingToAcquireAtSlice(
            display_.getAxisPosition(AcqEngMetadata.Z_AXIS));
   }

   public MagellanMouseListener getMouseListener() {
      return mouseListener_;
   }

   public XYFootprint getCurrentEditableSurfaceOrGrid() {
      return surfaceGridControls_.getCurrentSurfaceOrGrid();
   }

   public boolean isCurrentlyEditableSurfaceGridVisible() {
      return surfaceGridControls_.isCurrentlyEditableSurfaceGridVisible();
   }

   public void acquireTiles(int y, int x, int y0, int x0) {
      ((ExploreAcquisition) acq_).acquireTiles(y, x, y0, x0);
   }

   public ArrayList<XYFootprint> getSurfacesAndGridsForDisplay() {
      return surfaceGridControls_.getSurfacesAndGridsForDisplay();
   }

   public Point getTileIndicesFromDisplayedPixel(int x, int y) {
      return display_.getTileIndicesFromDisplayedPixel(x, y);
   }

   public void setOverlay(Overlay surfOverlay) {
      display_.setOverlay(surfOverlay);
   }

   public Point getDisplayedPixel(long row1, long col1) {
      return display_.getDisplayedPixel(row1, col1);
   }

   public void acquireTileAtCurrentPosition() {
      ((ExploreAcquisition) acq_).acquireTileAtCurrentLocation();
   }

   public void setExploreZLimits(double zTop, double zBottom) {
      ((ExploreAcquisition) acq_).setZLimits(zTop, zBottom);
   }

   public void setSurfaceDisplaySettings(boolean showInterp, boolean showStage) {
      ((MagellanOverlayer) overlayer_).setSurfaceDisplayParams(showInterp, showStage);
   }

   public Point2D.Double getStageCoordinateOfViewCenter() {
      return acq_.getPixelStageTranslator().getStageCoordsFromPixelCoords(
              (long) (display_.getViewOffset().x + display_.getFullResSourceDataSize().x / 2),
              (long) (display_.getViewOffset().y + display_.getFullResSourceDataSize().y / 2));

   }

   public void setSurfaceGridMode(boolean b) {
      ((MagellanOverlayer) overlayer_).setSurfaceGridMode(b);
      ((MagellanMouseListener) mouseListener_).setSurfaceGridMode(b);
   }

   public void update() {
      display_.update();
   }

   public void initializeViewerToLoaded(
           HashMap<String, Integer> axisMins, HashMap<String, Integer> axisMaxs) {

      HashMap<Integer, String> channelNames = new HashMap<Integer, String>();
      for (HashMap<String, Integer> axes : storage_.getAxesSet()) {
         if (axes.containsKey(MagellanMD.CHANNEL_AXIS)) {
            if (!channelNames.containsKey(axes.get(MagellanMD.CHANNEL_AXIS))) {
               //read this channel indexs name from metadata
               String channelName = MagellanMD.getChannelName(storage_.getImage(axes).tags);
               channelNames.put(axes.get(MagellanMD.CHANNEL_AXIS), channelName);
            }
         }
      }
      List<String> channelNamesList = new ArrayList<String>(channelNames.values());

      display_.initializeViewerToLoaded(channelNamesList, storage_.getDisplaySettings(),
            axisMins, axisMaxs);
   }

   public Set<HashMap<String, Integer>> getAxesSet() {
      return storage_.getAxesSet();
   }

   public Point2D.Double[] getDisplayTileCorners(XYStagePosition pos) {
      return acq_.getPixelStageTranslator().getDisplayTileCornerStageCoords(pos);
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

   public double getZCoordinateOfDisplayedSlice() {
      int index = display_.getAxisPosition(AcqEngMetadata.Z_AXIS);
      return index * acq_.getZStep() + acq_.getZOrigin();
   }

   public int zCoordinateToZIndex(double z) {
      return (int) ((z - acq_.getZOrigin()) / acq_.getZStep());
   }
}
