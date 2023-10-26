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

package org.micromanager.magellan.internal.explore;

import java.awt.Point;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.acqj.api.AcqEngJDataSink;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.magellan.internal.explore.gui.ExploreControlsPanel;
import org.micromanager.magellan.internal.explore.gui.ExploreMouseListener;
import org.micromanager.magellan.internal.explore.gui.ExploreOverlayer;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.ndviewer.api.NDViewerAPI;
import org.micromanager.ndviewer.api.NDViewerAcqInterface;
import org.micromanager.ndviewer.api.NDViewerDataSource;
import org.micromanager.ndviewer.api.OverlayerPlugin;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.remote.PycroManagerCompatibleUI;

/**
 * This class links data storage, viewer, and acquisition, acting as
 * an intermediary when neccessary to implement functionality specific to
 * Explore acquisitions. For example, it removes the row and column axes
 * from the data stored on disk before passing to the viewer, so that
 * they display as one contiguous image.
 */
public class ExploreAcqUIAndStorage implements AcqEngJDataSink, NDViewerDataSource,
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
   private CopyOnWriteArrayList<String> channelNames_ = new CopyOnWriteArrayList<String>();
   private LinkedList<Consumer<HashMap<String, Object>>> displayUpdateOnImageHooks_
           = new LinkedList<Consumer<HashMap<String, Object>>>();

   private OverlayerPlugin overlayer_;

   protected ExploreAcquisition acq_;
   protected ExploreMouseListener mouseListener_;
   protected ExploreControlsPanel exploreControlsPanel_;
   private Consumer<String> logger_;
   private ChannelGroupSettings channels_;
   private final boolean useZ_;


   public static ExploreAcqUIAndStorage create(Studio studio, String dir, String name,
                                               int overlapX, int overlapY, boolean useZ,
                                               double zStep, String channelGroup)
           throws Exception {
      ChannelGroupSettings channels = new ChannelGroupSettings(channelGroup, studio.core(),
              studio.profile());
      ExploreAcqUIAndStorage exploreAcqUIAndStorage = new ExploreAcqUIAndStorage(
              dir, name, true, useZ, channels, (String s) -> {});
      ExploreAcquisition acquisition = new ExploreAcquisition(overlapX, overlapY, useZ, zStep,
              channels, exploreAcqUIAndStorage);
      return exploreAcqUIAndStorage;
   }

   public ExploreAcqUIAndStorage(String dir, String name, boolean showDisplay,
                                 boolean useZ, ChannelGroupSettings exploreChannels,
                                 Consumer<String> logger) {
      displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r)
              -> new Thread(r, "Magellan viewer communication thread"));
      logger_ = logger;
      dir_ = dir;
      name_ = name;
      useZ_ = useZ;
      loadedData_ = false;
      showDisplay_ = showDisplay;
      channels_ = exploreChannels;
   }

   public ExploreAcqUIAndStorage(String dir, String name, boolean showDisplay,
                                boolean useZ, ChannelGroupSettings exploreChannels) {
      this(dir, name, showDisplay, useZ, exploreChannels, (String s) -> {});
   }

   //Constructor for opening loaded data
   public ExploreAcqUIAndStorage(String dir, Consumer<String> logger) throws IOException {
      logger_ = logger;
      useZ_ = false;
      displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r)
              -> new Thread(r, "Magellan viewer communication thread"));
      storage_ = new NDTiffStorage(dir);
      dir_ = dir;
      loadedData_ = true;
      showDisplay_ = true;
      summaryMetadata_ = storage_.getSummaryMetadata();
      createDisplay();
   }

   public NDViewerAPI getViewer() {
      return display_;
   }

   public ExploreAcquisition getAcquisition() {
      return acq_;
   }

   @Override
   public void initialize(Acquisition acq, JSONObject summaryMetadata) {
      acq_ = (ExploreAcquisition) acq;

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

      boolean addExploreControls = true;
      if (showDisplay_) {
         createDisplay();
      }
      //storage class has determined unique acq name, so it can now be stored
      name_ = this.getUniqueAcqName();
   }

   public MultiresNDTiffAPI getStorage() {
      return storage_;
   }

   private void moveViewToVisibleArea() {
      //check for valid tiles (at lowest res) at this slice

      Set<Point> tiles = new HashSet<Point>();
      for (String zName : acq_.getZAxes().keySet()) {
         tiles.addAll(getTileIndicesWithDataAt(zName,
                 (Integer) display_.getAxisPosition(zName)));
      }
      if (tiles.size() == 0) {
         return;
      }
      // center of one tile must be within corners of current view
      double minDistance = Integer.MAX_VALUE;
      //do all calculations at full resolution
      long currentX = (long) display_.getViewOffset().x;
      long currentY = (long) display_.getViewOffset().y;

      //Check if any point is visible, if so return
      for (Point p : tiles) {
         //calclcate limits on margin of tile that must remain in view
         long tileX1 = (long) ((0.1 + p.x) * acq_.getPixelStageTranslator().getDisplayTileWidth());
         long tileX2 = (long) ((0.9 + p.x) * acq_.getPixelStageTranslator().getDisplayTileWidth());
         long tileY1 = (long) ((0.1 + p.y) * acq_.getPixelStageTranslator().getDisplayTileHeight());
         long tileY2 = (long) ((0.9 + p.y) * acq_.getPixelStageTranslator().getDisplayTileHeight());
         //get bounds of viewing area
         long fovX1 = (long) display_.getViewOffset().x;
         long fovY1 = (long) display_.getViewOffset().y;
         long fovX2 = (long) (fovX1 + display_.getFullResSourceDataSize().x);
         long fovY2 = (long) (fovY1 + display_.getFullResSourceDataSize().y);

         //check if tile and fov intersect
         boolean xInView = fovX1 < tileX2 && fovX2 > tileX1;
         boolean yInView = fovY1 < tileY2 && fovY2 > tileY1;
         boolean intersection = xInView && yInView;

         if (intersection) {
            return; //at least one tile is in view, don't need to do anything
         }
      }

      //Go through all tiles and find minium move to reset visible criteria
      ArrayList<Point2D.Double> newPos = new ArrayList<Point2D.Double>();
      for (Point p : tiles) {
         //do all calculations at full resolution
         currentX = (long) display_.getViewOffset().x;
         currentY = (long) display_.getViewOffset().y;

         //calclcate limits on margin of tile that must remain in view
         long tileX1 = (long) ((0.1 + p.x) * acq_.getPixelStageTranslator().getDisplayTileWidth());
         long tileX2 = (long) ((0.9 + p.x) * acq_.getPixelStageTranslator().getDisplayTileWidth());
         long tileY1 = (long) ((0.1 + p.y) * acq_.getPixelStageTranslator().getDisplayTileHeight());
         long tileY2 = (long) ((0.9 + p.y) * acq_.getPixelStageTranslator().getDisplayTileHeight());
         //get bounds of viewing area
         long fovX1 = (long) display_.getViewOffset().x;
         long fovY1 = (long) display_.getViewOffset().y;
         long fovX2 = (long) (fovX1 + display_.getFullResSourceDataSize().x);
         long fovY2 = (long) (fovY1 + display_.getFullResSourceDataSize().y);

         //check if tile and fov intersect
         boolean xInView = fovX1 < tileX2 && fovX2 > tileX1;
         boolean yInView = fovY1 < tileY2 && fovY2 > tileY1;

         //tile to fov corner to corner distances
         double tl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY1 - fovY2)
                 * (tileY1 - fovY2)); //top left tile, botom right fov
         double tr = ((tileX2 - fovX1) * (tileX2 - fovX1) + (tileY1 - fovY2)
                 * (tileY1 - fovY2)); // top right tile, bottom left fov
         double bl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY2 - fovY1)
                 * (tileY2 - fovY1)); // bottom left tile, top right fov
         double br = ((tileX1 - fovX1) * (tileX1 - fovX1) + (tileY2 - fovY1)
                 * (tileY2 - fovY1)); //bottom right tile, top left fov

         double closestCornerDistance = Math.min(Math.min(tl, tr), Math.min(bl, br));
         if (closestCornerDistance < minDistance) {
            minDistance = closestCornerDistance;
            long newX;
            long newY;
            if (tl <= tr && tl <= bl && tl <= br) { //top left tile, botom right fov
               newX = (long) (xInView ? currentX : tileX1 - display_.getFullResSourceDataSize().x);
               newY = (long) (yInView ? currentY : tileY1 - display_.getFullResSourceDataSize().y);
            } else if (tr <= tl && tr <= bl && tr <= br) { // top right tile, bottom left fov
               newX = xInView ? currentX : tileX2;
               newY = (long) (yInView ? currentY : tileY1 - display_.getFullResSourceDataSize().y);
            } else if (bl <= tl && bl <= tr && bl <= br) { // bottom left tile, top right fov
               newX = (long) (xInView ? currentX : tileX1 - display_.getFullResSourceDataSize().x);
               newY = yInView ? currentY : tileY2;
            } else { //bottom right tile, top left fov
               newX = xInView ? currentX : tileX2;
               newY = yInView ? currentY : tileY2;
            }
            newPos.add(new Point2D.Double(newX, newY));
         }
      }

      long finalCurrentX = currentX;
      long finalCurrentY = currentY;
      DoubleStream dists = newPos.stream().mapToDouble(value -> Math.pow(value.x - finalCurrentX, 2)
              + Math.pow(value.y - finalCurrentY, 2));

      double minDist = dists.min().getAsDouble();
      Point2D.Double newPoint =  newPos.stream().filter(
              value -> (Math.pow(value.x - finalCurrentX, 2)
                      + Math.pow(value.y - finalCurrentY, 2))
                      == minDist).collect(Collectors.toList()).get(0);

      display_.setViewOffset(newPoint.x, newPoint.y);
   }

   //Override zoom and pan to restrain viewer to explored region in explore acqs
   public void pan(int dx, int dy) {
      display_.pan(dx, dy);
      if (getBounds() == null) {
         moveViewToVisibleArea();
         display_.update();
      }
   }

   public void zoom(double factor, Point mouseLocation) {
      display_.zoom(factor, mouseLocation);
      if (getBounds() == null) {
         moveViewToVisibleArea();
         display_.update();
      }
   }

   private void createDisplay() {
      //create display
      try {

         display_ = new NDViewer(this, acq_, summaryMetadata_,
                 AcqEngMetadata.getPixelSizeUm(summaryMetadata_),
                 AcqEngMetadata.isRGB(summaryMetadata_));

         display_.setWindowTitle(getUniqueAcqName() + (acq_ != null
                 ? (((NDViewerAcqInterface) acq_).isFinished() ? " (Finished)" : " (Running)")
                 : " (Loaded)"));
         //add functions so display knows how to parse time and z infomration from image tags
         display_.setReadTimeMetadataFunction((JSONObject tags)
               -> AcqEngMetadata.getElapsedTimeMs(tags));
         display_.setReadZMetadataFunction((JSONObject tags)
               -> AcqEngMetadata.getStageZIntended(tags));

         //add mouse listener for the canvas
         //add overlayer
         mouseListener_ = createMouseListener();
         overlayer_ = createOverlayer();

         display_.setOverlayerPlugin(overlayer_);

         exploreControlsPanel_ = new ExploreControlsPanel(acq_,
                  overlayer_,  useZ_, channels_, acq_.getZAxes());
         display_.addControlPanel(exploreControlsPanel_);

         display_.setCustomCanvasMouseListener(mouseListener_);

         display_.addSetImageHook(new Consumer<HashMap<String, Object>>() {
            @Override
            public void accept(HashMap<String, Object> axes) {

               // iterate through z devices and update their current z position
               for (String name : acq_.getZAxes().keySet()) {
                  if (axes.containsKey(name)) {
                     Integer i = (Integer) axes.get(name);
                     exploreControlsPanel_.updateGUIToReflectHardwareZPosition(name, i);
                  }
               }
            }
         });

      } catch (Exception e) {
         e.printStackTrace();
         logger_.accept("Couldn't create display succesfully");
      }
   }

   protected OverlayerPlugin createOverlayer() {
      return new ExploreOverlayer(display_, mouseListener_, acq_);
   }

   protected ExploreMouseListener createMouseListener() {
      return new ExploreMouseListener(acq_, display_, logger_);
   }

   public Object putImage(final TaggedImage taggedImg) {

      String channelName = (String) AcqEngMetadata.getAxes(taggedImg.tags).get("channel");
      boolean newChannel = !channelNames_.contains(channelName);
      if (newChannel) {
         channelNames_.add(channelName);
      }
      HashMap<String, Object> axes = AcqEngMetadata.getAxes(taggedImg.tags);
      Future added = storage_.putImageMultiRes(taggedImg.pix, taggedImg.tags, axes,
              AcqEngMetadata.isRGB(taggedImg.tags), AcqEngMetadata.getBitDepth(taggedImg.tags),
              AcqEngMetadata.getHeight(taggedImg.tags), AcqEngMetadata.getWidth(taggedImg.tags));

      if (showDisplay_) {
         //put on different thread to not slow down acquisition

         displayCommunicationExecutor_.submit(new Runnable() {
            @Override
            public void run() {
               try {
                  added.get();


                  HashMap<String, Object> axes = AcqEngMetadata.getAxes(taggedImg.tags);
                  //Display doesn't know about these in tiled layout
                  axes.remove(AcqEngMetadata.AXES_GRID_ROW);
                  axes.remove(AcqEngMetadata.AXES_GRID_COL);
                  //  String channelName = MagellanMD.getChannelName(taggedImg.tags);
                  display_.newImageArrived(axes);

                  for (Consumer<HashMap<String, Object>> displayHook : displayUpdateOnImageHooks_) {
                     displayHook.accept(axes);
                  }


               } catch (Exception e) {
                  e.printStackTrace();;
                  throw new RuntimeException(e);
               }
            }
         });
      }
      return null;
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

      try {
         storage_.checkForWritingException();
         display_.setWindowTitle(name_ + " (Finished)");
      } catch (Exception e) {
         display_.setWindowTitle(name_ + " (Finished with saving error)");
      } finally {
         displayCommunicationExecutor_.shutdown();
      }
      displayCommunicationExecutor_ = null;
   }

   public boolean isFinished() {
      return storage_.isFinished();
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

         storage_.close();
         storage_ = null;
         displayUpdateOnImageHooks_ = null;

         mouseListener_ = null;
         overlayer_ = null;
         display_ = null;

      } else {
         //keep resubmitting so that finish, which comes from a different thread, happens first
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               ExploreAcqUIAndStorage.this.close();
            }
         });
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

   public JSONObject getSummaryMD() {
      if (storage_ == null) {
         logger_.accept("imageStorage_ is null in getSummaryMetadata");
         return null;
      }
      try {
         return new JSONObject(storage_.getSummaryMetadata().toString());
      } catch (JSONException ex) {
         throw new RuntimeException("This shouldnt happen");
      }
   }

   public int[] getBounds() {
      // Explore acquisition has no bounds while active
      // Could uncomment below if loaded data mode added
      //      if (isExploreAcquisition() && !loadedData_) {
      return null;
      //      }
      //      return storage_.getImageBounds();
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


   public int getMaxResolutionIndex() {
      return storage_.getNumResLevels() - 1;
   }

   @Override
   public void increaseMaxResolutionLevel(int newMaxResolutionLevel) {
      storage_.increaseMaxResolutionLevel(newMaxResolutionLevel);
   }

   public Set<Point> getTileIndicesWithDataAt(String zName, int zIndex) {
      return storage_.getTileIndicesWithDataAt(zName, zIndex);
   }

   public Point2D.Double getStageCoordinateOfViewCenter() {
      return acq_.getPixelStageTranslator().getStageCoordsFromPixelCoords(
              (long) (display_.getViewOffset().x + display_.getFullResSourceDataSize().x / 2),
              (long) (display_.getViewOffset().y + display_.getFullResSourceDataSize().y / 2));

   }

}
