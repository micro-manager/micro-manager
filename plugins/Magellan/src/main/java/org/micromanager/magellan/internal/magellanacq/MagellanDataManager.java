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

import org.micromanager.magellan.internal.gui.MagellanViewer;
import java.awt.Color;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.api.XYStagePosition;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import org.micromanager.magellan.internal.channels.MagellanChannelGroupSettings;
import org.micromanager.magellan.internal.gui.ExploreControlsPanel;
import org.micromanager.magellan.internal.gui.MagellanMouseListener;
import org.micromanager.magellan.internal.gui.MagellanOverlayer;
import org.micromanager.magellan.internal.gui.SurfaceGridPanel;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;
import org.micromanager.multiresstorage.MultiResMultipageTiffStorage;
import org.micromanager.multiresstorage.StorageAPI;
import org.micromanager.ndviewer.api.AcquisitionInterface;
import org.micromanager.ndviewer.api.DataSourceInterface;
import org.micromanager.ndviewer.api.OverlayerPlugin;
import org.micromanager.ndviewer.overlay.Overlay;

/**
 * Created by magellan acquisition to manage viewer, data storage, and
 * conversion between pixel coordinate space (which the viewer and storage work
 * in) and the stage coordiante space (which the acquisition works in)
 */
public class MagellanDataManager implements DataSink, DataSourceInterface {

   private StorageAPI storage_;
   private ExecutorService displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r) -> new Thread(r, "Image cache thread"));
   private final boolean loadedData_;
   private String dir_;
   private String name_;
   private MagellanAcquisition acq_;
   private final boolean showDisplay_;
   private PixelStageTranslator stageCoordinateTranslator_;
   private MagellanViewer display_;
   private JSONObject summaryMetadata_;
   private CopyOnWriteArrayList<String> channelNames_ = new CopyOnWriteArrayList<String>();
   private MagellanMouseListener mouseListener_;
   private OverlayerPlugin overlayer_;
   private double pixelSizeXY_, pixelSizeZ_;
   private ExploreControlsPanel zExploreControls_;
   private SurfaceGridPanel surfaceGridControls_;

   public MagellanDataManager(String dir, boolean showDisplay) {
      dir_ = dir;
      loadedData_ = false;
      showDisplay_ = showDisplay;
   }

   //Constructor for opening loaded data
   public MagellanDataManager(String dir) throws IOException {
      storage_ = new MultiResMultipageTiffStorage(dir);
      dir_ = dir;
      loadedData_ = true;
      showDisplay_ = true;
      summaryMetadata_ = storage_.getSummaryMetadata();
      createDisplay();
   }

   public void initialize(AcquisitionBase acq, JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      acq_ = (MagellanAcquisition) acq;
      pixelSizeXY_ = MagellanMD.getPixelSizeUm(summaryMetadata);
      pixelSizeZ_ = acq_.getZStep();
      stageCoordinateTranslator_ = new PixelStageTranslator(stringToTransform(MagellanMD.getAffineTransformString(summaryMetadata)),
              MagellanMD.getWidth(summaryMetadata), MagellanMD.getHeight(summaryMetadata),
              MagellanMD.getPixelOverlapX(summaryMetadata), MagellanMD.getPixelOverlapY(summaryMetadata),
              MagellanMD.getInitialPositionList(summaryMetadata));

      storage_ = new MultiResMultipageTiffStorage(dir_, MagellanMD.getSavingPrefix(summaryMetadata),
              summaryMetadata, ((MagellanAcquisition) acq).getOverlapX(),
              ((MagellanAcquisition) acq).getOverlapY(),
              //TODO: in the futre may want to make multiple datasets if one
              //of these parameters changes, or better yet implement
              //in the thin the storage class to output different imaged
              //parameters to different files within the dataset
              MagellanMD.getWidth(summaryMetadata),
              MagellanMD.getHeight(summaryMetadata),
              MagellanMD.getBytesPerPixel(summaryMetadata));

      if (showDisplay_) {
         createDisplay();
      }
      //storage class has determined unique acq name, so it can now be stored
      name_ = this.getUniqueAcqName();
   }

   private void createDisplay() {
      //create display
      try {
         display_ = new MagellanViewer(this, (AcquisitionInterface) acq_, summaryMetadata_);
         display_.setWindowTitle(getUniqueAcqName() + (acq_ != null
                 ? (acq_.isComplete() ? " (Finished)" : " (Running)") : " (Loaded)"));
         //add functions so display knows how to parse time and z infomration from image tags
         display_.setReadTimeMetadataFunction((JSONObject tags) -> AcqEngMetadata.getElapsedTimeMs(tags));
         display_.setReadZMetadataFunction((JSONObject tags) -> AcqEngMetadata.getZPositionUm(tags));

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
                       (MagellanChannelGroupSettings) acq_.getAcquisitionSettings().channels_);
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

      storage_.putImage(taggedImg, axes, MagellanMD.getGridRow(taggedImg.tags),
              MagellanMD.getGridCol(taggedImg.tags));

      if (showDisplay_) {
         //put on different thread to not slow down acquisition
         displayCommunicationExecutor_.submit(new Runnable() {
            @Override
            public void run() {
               if (newChannel) {
                  //Insert a preferred color. Make a copy just in case concurrency issues
                  String chName = MagellanMD.getChannelName(taggedImg.tags);
                  MagellanChannelGroupSettings ch = ((MagellanChannelGroupSettings) acq_.getChannels());
                  Color c = ch == null ? Color.white : ch.getPreferredChannelColor(chName);
                  int bitDepth = MagellanMD.getBitDepth(taggedImg.tags);
                  display_.setChannelDisplaySettings(chName, c, bitDepth);
               }
               HashMap<String, Integer> axes = MagellanMD.getAxes(taggedImg.tags);
               axes.remove("p"); //Magellan doesn't have positions axis
               String channelName = MagellanMD.getChannelName(taggedImg.tags);
               display_.newImageArrived(axes, channelName);
               zExploreControls_.updateExploreZControls(axes.get("z"));
               surfaceGridControls_.enable(); //technically this only needs to happen once, but eh
            }
         });
      }
   }

   private static AffineTransform stringToTransform(String s) {
      if (s.equals("Undefined")) {
         return null;
      }
      double[] mat = new double[4];
      String[] vals = s.split("_");
      for (int i = 0; i < 4; i++) {
         mat[i] = parseDouble(vals[i]);
      }
      return new AffineTransform(mat);
   }

   private static double parseDouble(String s) {
      try {
         return DecimalFormat.getNumberInstance().parse(s).doubleValue();
      } catch (ParseException ex) {
         throw new RuntimeException(ex);
      }
   }

   /**
    * Called when images done arriving
    */
   public void finished() {
      if (!storage_.isFinished()) {
         //Get most up to date display settings
         JSONObject displaySettings = display_.getDisplaySettingsJSON();
         storage_.setDisplaySettings(displaySettings);
         storage_.finishedWriting();
      }
      display_.setWindowTitle(getUniqueAcqName() + " (Finished)");
   }

   public boolean isFinished() {
      return storage_.isFinished();
   }

   public String getDiskLocation() {
      return storage_.getDiskLocation();
   }

   /**
    * Used for data loaded from disk
    *
    * @return
    */
   public JSONObject getDisplayJSON() {
      try {
         return storage_.getDisplaySettings() == null ? null : new JSONObject(storage_.getDisplaySettings().toString());
      } catch (JSONException ex) {
         throw new RuntimeException("THis shouldnt happen");
      }
   }

   /**
    * Call when display and acquisition both done
    */
   public void viewerClosing() {
      if (storage_.isFinished()) {
         storage_.close();
         storage_ = null;
         displayCommunicationExecutor_.shutdownNow();
         displayCommunicationExecutor_ = null;
         mouseListener_ = null;
         overlayer_ = null;
         display_ = null;
         zExploreControls_ = null;
      } else {
         //keep resubmitting so that finish, which comes from a different thread, happens first
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               MagellanDataManager.this.viewerClosing();
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

   public int getTileHeight() {
      return stageCoordinateTranslator_ == null ? 0 : stageCoordinateTranslator_.getTileHeight();
   }

   public int getTileWidth() {
      return stageCoordinateTranslator_ == null ? 0 : stageCoordinateTranslator_.getTileWidth();
   }

   public boolean isExploreAcquisition() {
      return MagellanMD.isExploreAcq(summaryMetadata_) ;
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

      return storage_.getStitchedImage(
              axes,
              resolutionindex,
              (int) xOffset, (int) yOffset,
              imageWidth, imageHeight);
   }

   public boolean anythingAcquired() {
      return storage_ == null || !storage_.getAxesSet().isEmpty();
   }

   public String getUniqueAcqName() {
      if (loadedData_) {
         return dir_;
      }
      String[] parts = storage_.getDiskLocation().split(File.separator);

      return parts[parts.length - 1];
   }

   public Point2D.Double stageCoordsFromPixelCoords(int x, int y) {
      return stageCoordsFromPixelCoords(x, y, display_.getMagnification(), display_.getViewOffset());
   }

   /**
    *
    * @param xAbsolute x coordinate in the full Res stitched image
    * @param yAbsolute y coordinate in the full res stitched image
    * @return stage coordinates of the given pixel position
    */
   public Point2D.Double stageCoordsFromPixelCoords(int absoluteX, int absoluteY,
           double mag, Point2D.Double offset) {
      long newX = (long) (absoluteX / mag + offset.x);
      long newY = (long) (absoluteY / mag + offset.y);
      return stageCoordinateTranslator_.getStageCoordsFromPixelCoords(newX, newY);
   }

   public int getFullResPositionIndexFromStageCoords(double xPos, double yPos) {
      return stageCoordinateTranslator_.getFullResPositionIndexFromStageCoords(xPos, yPos);
   }

   /* 
    * @param stageCoords x and y coordinates of image in stage space
    * @return absolute, full resolution pixel coordinate of given stage posiiton
    */
   public Point pixelCoordsFromStageCoords(double x, double y, double magnification,
           Point2D.Double offset) {
      Point fullResCoords = stageCoordinateTranslator_.getPixelCoordsFromStageCoords(x, y);
      return new Point(
              (int) ((fullResCoords.x - offset.x) * magnification),
              (int) ((fullResCoords.y - offset.y) * magnification));
   }

   public XYStagePosition getXYPosition(int posIndex) {
      return stageCoordinateTranslator_.getXYPosition(posIndex);
   }

   public int[] getPositionIndices(int[] newPositionRows, int[] newPositionCols) {
      return stageCoordinateTranslator_.getPositionIndices(newPositionRows, newPositionCols);
   }

   public List<XYStagePosition> getPositionList() {
      return stageCoordinateTranslator_.getPositionList();
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

   public double getZCoordinateOfDisplayedSlice() {
      return acq_.getZCoordOfNonnegativeZIndex(display_.getAxisPosition("z"));
   }

   public int getDisplaySliceIndexFromZCoordinate(double z) {
      return acq_.getDisplaySliceIndexFromZCoordinate(z);
   }

   public LinkedBlockingQueue<ExploreAcquisition.ExploreTileWaitingToAcquire> getTilesWaitingToAcquireAtVisibleSlice() {
      return ((ExploreAcquisition) acq_).getTilesWaitingToAcquireAtSlice(display_.getAxisPosition("z"));
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

   public double getPixelSize() {
      return pixelSizeXY_;
   }

   public double getZCoordOfNonnegativeZIndex(int axisPosition) {
      return acq_.getZCoordOfNonnegativeZIndex(axisPosition);
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
      return stageCoordinateTranslator_.getStageCoordsFromPixelCoords(
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
      
      display_.initializeViewerToLoaded(channelNamesList, storage_.getDisplaySettings(), axisMins, axisMaxs);
   }

   public Set<HashMap<String, Integer>> getAxesSet() {
      return storage_.getAxesSet();
   }

}
