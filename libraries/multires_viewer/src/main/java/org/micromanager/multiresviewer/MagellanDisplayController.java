// Copyright (C) 2015-2017 Open Imaging, Inc.
//           (C) 2015 Regents of the University of California
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
package org.micromanager.multiresviewer;

import org.micromanager.multiresviewer.api.DataSource;
import org.micromanager.multiresviewer.api.AcquisitionPlugin;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Image;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.Point2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.prefs.Preferences;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.multiresviewer.events.AnimationToggleEvent;
import org.micromanager.multiresviewer.events.CanvasResizeEvent;
import org.micromanager.multiresviewer.events.ContrastUpdatedEvent;
import org.micromanager.multiresviewer.events.DisplayClosingEvent;
import org.micromanager.multiresviewer.events.ImageCacheClosingEvent;
import org.micromanager.multiresviewer.events.SetImageEvent;
import org.micromanager.multiresviewer.overlay.Overlay;

public final class MagellanDisplayController {

   private DataSource dataSource_;
   private DisplaySettings displaySettings_;

   private MagellanCoalescentEDTRunnablePool edtRunnablePool_ = MagellanCoalescentEDTRunnablePool.create();

   private EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());

   private CoalescentExecutor displayCalculationExecutor_ = new CoalescentExecutor("Display calculation executor");
   private CoalescentExecutor overlayCalculationExecutor_ = new CoalescentExecutor("Overlay calculation executor");

   private DisplayWindow displayWindow_;
   private ImageMaker imageMaker_;
   private BaseOverlayer overlayer_;
   private Timer animationTimer_;
   private double animationFPS_ = 7;

   private DataViewCoords viewCoords_;
   private AcquisitionPlugin acq_;

   public MagellanDisplayController(DataSource cache, AcquisitionPlugin acq) {
      dataSource_ = cache;
      acq_ = acq;
      displaySettings_ = new DisplaySettings();
      viewCoords_ = new DataViewCoords(cache, 0, 0, 0, 0, 0,
              cache.isXYBounded() ? 700 : cache.getFullResolutionSize().x,
              cache.isXYBounded() ? 700 : cache.getFullResolutionSize().y, dataSource_.getImageBounds());
      displayWindow_ = new DisplayWindow(this);
      overlayer_ = new BaseOverlayer(this);
      imageMaker_ = new ImageMaker(this, cache);

      registerForEvents(this);
   }
   
   public boolean isImageXYBounded() {
      return dataSource_.isXYBounded();
   }

   public JSONObject getDisplaySettingsJSON() {
      return displaySettings_.toJSON();
   }

   static Preferences getPreferences() {
      //TODO: add option to pass in micromanager preferences to get user features
      return Preferences.systemNodeForPackage(MagellanDisplayController.class);
   }

   public void pan(int dx, int dy) {
      Point2D.Double offset = viewCoords_.getViewOffset();
      double newX = offset.x + (dx / viewCoords_.getDisplayScaleFactor()) * viewCoords_.getDownsampleFactor();
      double newY = offset.y + (dy / viewCoords_.getDisplayScaleFactor()) * viewCoords_.getDownsampleFactor();

      if (dataSource_.isXYBounded()) {
         viewCoords_.setViewOffset(
                 Math.max(viewCoords_.xMin_, Math.min(newX, viewCoords_.xMax_ - viewCoords_.getSourceDataSize().x)),
                 Math.max(viewCoords_.yMin_, Math.min(newY, viewCoords_.yMax_ - viewCoords_.getSourceDataSize().y)));
      } else {
         viewCoords_.setViewOffset(newX, newY);
         moveViewToVisibleArea();
      }
      recomputeDisplayedImage();
   }

   public void zoom(double factor, Point mouseLocation) {
      //get zoom center in full res pixel coords
      Point2D.Double viewOffset = viewCoords_.getViewOffset();
      Point2D.Double sourceDataSize = viewCoords_.getSourceDataSize();
      Point2D.Double zoomCenter;
      //compute centroid of the zoom in full res coordinates
      if (mouseLocation == null) {
         //if mouse not over image zoom to center
         zoomCenter = new Point2D.Double(viewOffset.x + sourceDataSize.y / 2, viewOffset.y + sourceDataSize.y / 2);
      } else {
         zoomCenter = new Point2D.Double(
                 (long) viewOffset.x + mouseLocation.x / viewCoords_.getDisplayScaleFactor() * viewCoords_.getDownsampleFactor(),
                 (long) viewOffset.y + mouseLocation.y / viewCoords_.getDisplayScaleFactor() * viewCoords_.getDownsampleFactor());
      }

      //Do zooming--update size of source data
      double newSourceDataWidth = sourceDataSize.x * factor;
      double newSourceDataHeight = sourceDataSize.y * factor;
      if (newSourceDataWidth < 5 || newSourceDataHeight < 5) {
         return; //constrain maximum zoom
      }
      if (dataSource_.isXYBounded()) {
         //don't let either of these go bigger than the actual data
         double overzoomXFactor = newSourceDataWidth / (viewCoords_.xMax_ - viewCoords_.xMin_);
         double overzoomYFactor = newSourceDataHeight / (viewCoords_.yMax_ - viewCoords_.yMin_);
         if (overzoomXFactor > 1 || overzoomYFactor > 1) {
            newSourceDataWidth = newSourceDataWidth / Math.max(overzoomXFactor, overzoomYFactor);
            newSourceDataHeight = newSourceDataHeight / Math.max(overzoomXFactor, overzoomYFactor);
         }
      }
      viewCoords_.setSourceDataSize(newSourceDataWidth, newSourceDataHeight);

      double xOffset = (zoomCenter.x - (zoomCenter.x - viewOffset.x) * newSourceDataWidth / sourceDataSize.x);
      double yOffset = (zoomCenter.y - (zoomCenter.y - viewOffset.y) * newSourceDataHeight / sourceDataSize.y);
      //make sure view doesn't go outside image bounds
      if (dataSource_.isXYBounded()) {
         viewCoords_.setViewOffset(
                 Math.max(viewCoords_.xMin_, Math.min(xOffset, viewCoords_.xMax_ - viewCoords_.getSourceDataSize().x)),
                 Math.max(viewCoords_.yMin_, Math.min(yOffset, viewCoords_.yMax_ - viewCoords_.getSourceDataSize().y)));
      } else {
         viewCoords_.setViewOffset(xOffset, yOffset);
         //TODO: move to an area youve explored in explore acq
         //explore acquisition must have some area you've already explored in view
         moveViewToVisibleArea();
      }

      recomputeDisplayedImage();
   }

   /**
    * TODO: might want to move this out to simplyfy API
    * used to keep explored area visible in explire acquisitons
    */
   private void moveViewToVisibleArea() {

      //check for valid tiles (at lowest res) at this slice        
      Set<Point> tiles = dataSource_.getTileIndicesWithDataAt(viewCoords_.getAxisPosition("z"));
      if (tiles.size() == 0) {
         return;
      }
//      center of one tile must be within corners of current view 
      double minDistance = Integer.MAX_VALUE;
      //do all calculations at full resolution
      long currentX = (long) viewCoords_.getViewOffset().x;
      long currentY = (long) viewCoords_.getViewOffset().y;

      for (Point p : tiles) {
         //calclcate limits on margin of tile that must remain in view
         long tileX1 = (long) ((0.1 + p.x) * dataSource_.getTileWidth());
         long tileX2 = (long) ((0.9 + p.x) * dataSource_.getTileWidth());
         long tileY1 = (long) ((0.1 + p.y) * dataSource_.getTileHeight());
         long tileY2 = (long) ((0.9 + p.y) * dataSource_.getTileHeight());
//         long visibleWidth = (long) (0.8 * imageCache_.getTileWidth());
//         long visibleHeight = (long) (0.8 * imageCache_.getTileHeight());
         //get bounds of viewing area
         long fovX1 = (long) viewCoords_.getViewOffset().x;
         long fovY1 = (long) viewCoords_.getViewOffset().y;
         long fovX2 = (long) (fovX1 + viewCoords_.getSourceDataSize().x);
         long fovY2 = (long) (fovY1 + viewCoords_.getSourceDataSize().y);

         //check if tile and fov intersect
         boolean xInView = fovX1 < tileX2 && fovX2 > tileX1;
         boolean yInView = fovY1 < tileY2 && fovY2 > tileY1;
         boolean intersection = xInView && yInView;

         if (intersection) {
            return; //at least one tile is in view, don't need to do anything
         }
         //tile to fov corner to corner distances
         double tl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY1 - fovY2) * (tileY1 - fovY2)); //top left tile, botom right fov
         double tr = ((tileX2 - fovX1) * (tileX2 - fovX1) + (tileY1 - fovY2) * (tileY1 - fovY2)); // top right tile, bottom left fov
         double bl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY2 - fovY1) * (tileY2 - fovY1)); // bottom left tile, top right fov
         double br = ((tileX1 - fovX1) * (tileX1 - fovX1) + (tileY2 - fovY1) * (tileY2 - fovY1)); //bottom right tile, top left fov

         double closestCornerDistance = Math.min(Math.min(tl, tr), Math.min(bl, br));
         if (closestCornerDistance < minDistance) {
            minDistance = closestCornerDistance;
            if (tl <= tr && tl <= bl && tl <= br) { //top left tile, botom right fov
               currentX = (long) (xInView ? currentX : tileX1 - viewCoords_.getSourceDataSize().x);
               currentY = (long) (yInView ? currentY : tileY1 - viewCoords_.getSourceDataSize().y);
            } else if (tr <= tl && tr <= bl && tr <= br) { // top right tile, bottom left fov
               currentX = xInView ? currentX : tileX2;
               currentY = (long) (yInView ? currentY : tileY1 - viewCoords_.getSourceDataSize().y);
            } else if (bl <= tl && bl <= tr && bl <= br) { // bottom left tile, top right fov
               currentX = (long) (xInView ? currentX : tileX1 - viewCoords_.getSourceDataSize().x);
               currentY = yInView ? currentY : tileY2;
            } else { //bottom right tile, top left fov
               currentX = xInView ? currentX : tileX2;
               currentY = yInView ? currentY : tileY2;
            }
         }
      }
      viewCoords_.setViewOffset(currentX, currentY);
   }

   @Subscribe
   public void onCanvasResize(final CanvasResizeEvent e) {
      Point2D.Double displaySizeOld = viewCoords_.getDisplayImageSize();
      //reshape the source image to match canvas aspect ratio
      //expand it, unless it would put it out of range
      double canvasAspect = e.w / (double) e.h;
      Point2D.Double source = viewCoords_.getSourceDataSize();
      double sourceAspect = source.x / source.y;
      double newSourceX;
      double newSourceY;
      if (dataSource_.isXYBounded()) {
         if (canvasAspect > sourceAspect) {
            newSourceX = canvasAspect / sourceAspect * source.x;
            newSourceY = source.y;
            //check that still within image bounds
         } else {
            newSourceX = source.x;
            newSourceY = source.y / (canvasAspect / sourceAspect);
         }

         double overzoomXFactor = newSourceX / (viewCoords_.xMax_ - viewCoords_.xMin_);
         double overzoomYFactor = newSourceY / (viewCoords_.yMax_ - viewCoords_.yMin_);
         if (overzoomXFactor > 1 || overzoomYFactor > 1) {
            newSourceX = newSourceX / Math.max(overzoomXFactor, overzoomYFactor);
            newSourceY = newSourceY / Math.max(overzoomXFactor, overzoomYFactor);
         }
      } else if (displaySizeOld.x != 0 && displaySizeOld.y != 0) {
         newSourceX = source.x * (e.w / (double) displaySizeOld.x);
         newSourceY = source.y * (e.h / (double) displaySizeOld.y);
      } else {
         newSourceX = source.x / sourceAspect * canvasAspect;
         newSourceY = source.y;
      }
      //move into visible area
      viewCoords_.setViewOffset(
              Math.max(viewCoords_.xMin_, Math.min(viewCoords_.xMax_
                      - newSourceX, viewCoords_.getViewOffset().x)),
              Math.max(viewCoords_.yMin_, Math.min(viewCoords_.yMax_
                      - newSourceY, viewCoords_.getViewOffset().y)));

      //set the size of the display iamge
      viewCoords_.setDisplayImageSize(e.w, e.h);
      //and the size of the source pixels from which it derives
      viewCoords_.setSourceDataSize(newSourceX, newSourceY);
      recomputeDisplayedImage();
   }

   public void setLoadedDataScrollbarBounds(List<String> channelNames, int nFrames, int minSliceIndex, int maxSliceIndex) {

      for (int c = 0; c < channelNames.size(); c++) {
         boolean newChannel = viewCoords_.addChannelIfNew(c, channelNames.get(c));
         if (newChannel) {
            displayWindow_.addContrastControls(c, channelNames.get(c));
         }
      }
      //maximum scrollbar extensts
      HashMap<String, Integer> axisPositions = new HashMap<String, Integer>();
      axisPositions.put("z", maxSliceIndex);
      axisPositions.put("t", nFrames - 1);
      axisPositions.put("c", channelNames.size() - 1);
      //TODO: generalize
      edtRunnablePool_.invokeLaterWithCoalescence(new MagellanDisplayController.ExpandDisplayRangeCoalescentRunnable(axisPositions));

      HashMap<String, Integer> axisPositionsMin = new HashMap<String, Integer>();
      axisPositions.put("z", minSliceIndex);
      //minimum scrollbar extensts
      edtRunnablePool_.invokeLaterWithCoalescence(new MagellanDisplayController.ExpandDisplayRangeCoalescentRunnable(axisPositionsMin));
   }

   void channelSetActive(int channelIndex, boolean selected) {
      if (!displaySettings_.isCompositeMode()) {
         if (selected) {
            viewCoords_.setAxisPosition("c", channelIndex);
            //only one channel can be active so inacivate others
            for (Integer cIndex : viewCoords_.getChannelIndices()) {
               displaySettings_.setActive(viewCoords_.getChannelName(cIndex),
                       cIndex == viewCoords_.getAxisPosition("c"));
            }
         } else {
            //if channel turns off, nothing will show, so dont let this happen
         }
         //make sure other checkboxes update if they autochanged
         displayWindow_.displaySettingsChanged();

      } else {
         //composite mode
         displaySettings_.setActive(viewCoords_.getChannelName(channelIndex), selected);
      }
      recomputeDisplayedImage();
   }

   public void setWindowTitle(String s) {
      displayWindow_.setTitle(s);
   }

   public void newImageArrived(JSONObject tags) {
      displayWindow_.onNewImage(); //needed because events dont propagte for some reason

      //TODO: generalize
//      displayWindow_.updateExploreZControls(event.getPositionForAxis("z"));
      int channelIndex = DisplayMetadata.getChannelIndex(tags);
      String channelName = DisplayMetadata.getChannelName(tags);
      int bitDepth = DisplayMetadata.getBitDepth(tags);
      Color color = DisplayMetadata.getChannelDisplayColor(tags);

      boolean newChannel = viewCoords_.addChannelIfNew(channelIndex, channelName);
      if (newChannel) {
         displaySettings_.addChannel(channelName, bitDepth, color);
         displayWindow_.addContrastControls(channelIndex, channelName);
      }

      //TODO: replac this with arbitrary logic
      HashMap<String, Integer> axisPos = new HashMap<String, Integer>();
      axisPos.put("c", DisplayMetadata.getChannelIndex(tags));
      axisPos.put("z", DisplayMetadata.getSliceIndex(tags));
      axisPos.put("t", DisplayMetadata.getFrameIndex(tags));

      //expand the scrollbars with new images
      edtRunnablePool_.invokeLaterWithCoalescence(
              new MagellanDisplayController.ExpandDisplayRangeCoalescentRunnable(axisPos));

      //move scrollbars to new position
      postEvent(new SetImageEvent(axisPos, false));
   }

   /**
    * Called when scrollbars move
    */
   @Subscribe
   public void onSetImageEvent(final SetImageEvent event) {
      for (String axis : new String[]{"c", "z", "t", "r"}) {
         if (!displayWindow_.isScrollerAxisLocked(axis) || event.fromHuman_) {
            viewCoords_.setAxisPosition(axis, event.getPositionForAxis(axis));
            if (!displaySettings_.isCompositeMode()) {
               //set all channels inactive except current one
               for (Integer cIndex : viewCoords_.getChannelIndices()) {
                  displaySettings_.setActive(viewCoords_.getChannelName(cIndex),
                          cIndex == viewCoords_.getAxisPosition("c"));
                  displayWindow_.displaySettingsChanged();
               }
            }
         }
      }

      //TODO: generalize
//      displayWindow_.updateExploreZControls(event.getPositionForAxis("z"));
      recomputeDisplayedImage();
   }

   /**
    * Called when scrollbars move
    */
   @Subscribe
   public void onContrastUpdated(final ContrastUpdatedEvent event) {
      recomputeDisplayedImage();
   }

   @Subscribe
   public void onAnimationToggle(AnimationToggleEvent event) {
      if (animationTimer_ != null) {
         animationTimer_.stop();
      }
      if (event.getIsAnimated()) {
         animationTimer_ = new Timer((int) (1000 / animationFPS_), new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
               event.scroller_.setPosition((event.scroller_.getPosition() + 1)
                       % (event.scroller_.getMaximum() - event.scroller_.getMinimum()));
            }
         });
         animationTimer_.start();
      }
   }

   public void recomputeDisplayedImage() {
      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(new DisplayImageComputationRunnable());
   }

   public MagellanCanvas getCanvas() {
      return displayWindow_.getCanvas();
   }

   public void superlockAllScrollers() {
      displayWindow_.superlockAllScrollers();
   }

   public void unlockAllScroller() {
      displayWindow_.unlockAllScrollers();
   }

   public void showFolder() {
      try {
         File location = new File(dataSource_.getDiskLocation());
         if (isWindows()) {
            Runtime.getRuntime().exec("Explorer /n,/select," + location.getAbsolutePath());
         } else if (isMac()) {
            if (!location.isDirectory()) {
               location = location.getParentFile();
            }
            Runtime.getRuntime().exec(new String[]{"open", location.getAbsolutePath()});
         }
      } catch (IOException ex) {
         throw new RuntimeException(ex);
      }
   }

   public static boolean isWindows() {
      String os = System.getProperty("os.name").toLowerCase();
      return (os.contains("win"));
   }

   public static boolean isMac() {
      String os = System.getProperty("os.name").toLowerCase();
      return (os.contains("mac"));
   }

   public void setAnimateFPS(double doubleValue) {
      animationFPS_ = doubleValue;
      if (animationTimer_ != null) {
         ActionListener action = animationTimer_.getActionListeners()[0];
         animationTimer_.stop();
         animationTimer_ = new Timer((int) (1000 / animationFPS_), action);
         animationTimer_.start();
      }
   }

   void abortAcquisition() {
      if (acq_ != null && !acq_.isComplete()) {
         int result = JOptionPane.showConfirmDialog(null, "Finish acquisition?",
                 "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
         if (result == JOptionPane.OK_OPTION) {
            acq_.abort();
         } else {
            return;
         }
      }
   }

   void togglePauseAcquisition() {
      acq_.togglePaused();
   }

   boolean isAcquisitionPaused() {
      return acq_.isPaused();
   }

//   public boolean isRGB() {
//      return imageCache_.isRGB();
//   }

   Point getTileIndicesFromDisplayedPixel(int x, int y) {
      double scale = viewCoords_.getDisplayToFullScaleFactor();
      int fullResX = (int) ((x / scale) + viewCoords_.getViewOffset().x);
      int fullResY = (int) ((y / scale) + viewCoords_.getViewOffset().y);
      int xTileIndex = fullResX / dataSource_.getTileWidth() - (fullResX >= 0 ? 0 : 1);
      int yTileIndex = fullResY / dataSource_.getTileHeight() - (fullResY >= 0 ? 0 : 1);
      return new Point(xTileIndex, yTileIndex);
   }

   /**
    * return the pixel location in coordinates at appropriate res level of the
    * top left pixel for the given row/column
    *
    * @param row
    * @param col
    * @return
    */
   public Point getDisplayedPixel(long row, long col) {
      double scale = viewCoords_.getDisplayToFullScaleFactor();
      int x = (int) ((col * dataSource_.getTileWidth() - viewCoords_.getViewOffset().x) * scale);
      int y = (int) ((row * dataSource_.getTileWidth() - viewCoords_.getViewOffset().y) * scale);
      return new Point(x, y);
   }

   int getTileHeight() {
      return dataSource_.getTileHeight();
   }

   int getTileWidth() {
      return dataSource_.getTileWidth();
   }

   void setOverlay(Overlay overlay) {
      displayWindow_.displayOverlay(overlay);
   }

   void setOverlayMode(int mode) {
      viewCoords_.setOverlayMode(mode);
   }

   int getOverlayMode() {
      return viewCoords_.getOverlayMode();
   }

   public void redrawOverlay() {
      //this will automatically trigger overlay redrawing in a coalescent fashion
      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(new DisplayImageComputationRunnable());
   }

   boolean anythingAcquired() {
      return dataSource_.anythingAcquired();
   }

   double getScale() {
      return viewCoords_.getDisplayToFullScaleFactor();
   }

   double getPixelSize() {
      return dataSource_.getPixelSize_um();
   }

   void showScaleBar(boolean selected) {
      overlayer_.setShowScaleBar(selected);
   }

   void setCompositeMode(boolean selected) {
      displaySettings_.setCompositeMode(selected);
      //select all channels if composite mode is being turned on
      if (selected) {
         for (Integer cIndex : viewCoords_.getChannelIndices()) {
            displaySettings_.setActive(viewCoords_.getChannelName(cIndex), true);
            displayWindow_.displaySettingsChanged();
         }
      } else {
         for (Integer cIndex : viewCoords_.getChannelIndices()) {
            displaySettings_.setActive(viewCoords_.getChannelName(cIndex),
                    cIndex == viewCoords_.getAxisPosition("c"));
            displayWindow_.displaySettingsChanged();
         }
      }
      recomputeDisplayedImage();
   }

   boolean isCompositMode() {
      return displaySettings_.isCompositeMode();
   }

   DisplaySettings getDisplaySettingsObject() {
      return displaySettings_;
   }

   /**
    * A coalescent runnable to avoid excessively frequent update of the data
    * coords range in the UI
    */
   private class ExpandDisplayRangeCoalescentRunnable
           implements CoalescentRunnable {

      private final List<HashMap<String, Integer>> newIamgeEvents = new ArrayList<HashMap<String, Integer>>();

      ExpandDisplayRangeCoalescentRunnable(HashMap<String, Integer> axisPosisitons) {
         newIamgeEvents.add(axisPosisitons);
      }

      @Override
      public Class<?> getCoalescenceClass() {
         return getClass();
      }

      @Override
      public CoalescentRunnable coalesceWith(CoalescentRunnable another) {
         newIamgeEvents.addAll(
                 ((ExpandDisplayRangeCoalescentRunnable) another).newIamgeEvents);
         return this;
      }

      @Override
      public void run() {
         if (displayWindow_ != null) {
            displayWindow_.expandDisplayedRangeToInclude(newIamgeEvents);
         }
         newIamgeEvents.clear();
      }
   }

   public void requestToClose() {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               requestToClose();
            }
         });
      } else {
         //check to stop acquisiton?, return here if the attempt to close window unsuccesslful
         if (acq_ != null && !acq_.isComplete()) {
            int result = JOptionPane.showConfirmDialog(null, "Finish acquisition?",
                    "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
               acq_.abort();
            } else {
               return;
            }
         }

         close();
      }
   }

   /**
    * *
    * Acquisition should be closed vbefore calling this
    */
   public void close() {
      //acquisiiton should be aborted or finished already when this is called

      //make everything else close
      postEvent(new DisplayClosingEvent());

      displayCalculationExecutor_.shutdownNow();
      overlayCalculationExecutor_.shutdownNow();

      imageMaker_.close();
      imageMaker_ = null;

      overlayer_.shutdown();
      overlayer_ = null;

      if (animationTimer_ != null) {
         animationTimer_.stop();
      }
      animationTimer_ = null;

      dataSource_.close();
      dataSource_ = null;
      unregisterForEvents(this);

      displayWindow_ = null;
      viewCoords_ = null;
      eventBus_ = null;

      edtRunnablePool_ = null;
      displaySettings_ = null;
      displayCalculationExecutor_ = null;
      overlayCalculationExecutor_ = null;
      displaySettings_ = null;
      acq_ = null;
   }

   //TODO: deal with
//   @Subscribe
//   public void onExploreZLimitsChangedEvent(ExploreZLimitsChangedEvent event) {
//      ((ExploreAcquisition) acq_).setZLimits(event.top_, event.bottom_);
//   }
   public void onDataSourceClosing() {
      requestToClose();
   }

   public final void registerForEvents(Object recipient) {
      eventBus_.register(recipient);
   }

   public final void unregisterForEvents(Object recipient) {
      eventBus_.unregister(recipient);
   }

   /**
    * Post an event on the viewer event bus.
    *
    * Implementations should call this method to post required notification
    * events.
    * <p>
    * Some standard viewer events require that they be posted on the Swing/AWT
    * event dispatch thread. Make sure you are on the right thread when posting
    * such events.
    * <p>
    * Viewers are required to post the following events:
    * <ul>
    * <li>{@link DisplaySettingsChangedEvent} (posted by this abstract class)
    * <li>{@link DisplayPositionChangedEvent} (posted by this abstract class)
    * </ul>
    *
    * @param event the event to post
    */
   public final void postEvent(Object event) {
      eventBus_.post(event);
   }

   public JSONObject getSummaryMD() {
      try {
         return new JSONObject(dataSource_.getSummaryMD().toString());
      } catch (JSONException ex) {
         return null; //this shouldnt happen
      }
   }

   private class DisplayImageComputationRunnable implements CoalescentRunnable {

      DataViewCoords view_;

      public DisplayImageComputationRunnable() {
         view_ = viewCoords_.copy();
      }

      @Override
      public Class<?> getCoalescenceClass() {
         return this.getClass();
      }

      @Override
      public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
         return later; //Always update with newest image 
      }

      @Override
      public void run() {
         //This is where most of the calculation of creating a display image happens
         Image img = imageMaker_.makeOrGetImage(view_);
         JSONObject tags = imageMaker_.getLatestTags();
         Overlay cheapOverlay = null;
         //TODO: add to overlay rather than all external
         if (overlayer_ != null) {
            cheapOverlay = overlayer_.createDefaultOverlay(view_);
         }

         HashMap<Integer, int[]> channelHistograms = imageMaker_.getHistograms();
         HashMap<Integer, Integer> pixelMins = imageMaker_.getPixelMins();
         HashMap<Integer, Integer> pixelMaxs = imageMaker_.getPixelMaxs();
         edtRunnablePool_.invokeAsLateAsPossibleWithCoalescence(new CanvasRepaintRunnable(img,
                 channelHistograms, pixelMins, pixelMaxs, view_, cheapOverlay, tags));
         //now send expensive overlay computation to overlay creation thread
         overlayer_.redrawOverlay(view_);
      }
   }

   private class CanvasRepaintRunnable implements CoalescentRunnable {

      final Image img_;
      DataViewCoords view_;
      HashMap<Integer, int[]> hists_;
      HashMap<Integer, Integer> mins_;
      HashMap<Integer, Integer> maxs_;
      Overlay overlay_;
      JSONObject imageMD_;

      public CanvasRepaintRunnable(Image img, HashMap<Integer, int[]> hists, HashMap<Integer, Integer> pixelMins,
              HashMap<Integer, Integer> pixelMaxs, DataViewCoords view, Overlay cheapOverlay, JSONObject imageMD) {
         img_ = img;
         view_ = view;
         hists_ = hists;
         mins_ = pixelMins;
         maxs_ = pixelMaxs;
         overlay_ = cheapOverlay;
         imageMD_ = imageMD;
      }

      @Override
      public Class<?> getCoalescenceClass() {
         return this.getClass();
      }

      @Override
      public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
         return later;
      }

      @Override
      public void run() {
         displayWindow_.displayImage(img_, hists_, mins_, maxs_, view_);
         displayWindow_.setImageMetadata(imageMD_);
         if (overlay_ != null) {
            displayWindow_.displayOverlay(overlay_);
         }
         displayWindow_.repaintCanvas();
      }

   }

}
