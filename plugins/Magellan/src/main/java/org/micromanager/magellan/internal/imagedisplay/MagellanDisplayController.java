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
package org.micromanager.magellan.internal.imagedisplay;

import org.micromanager.magellan.internal.imagedisplay.events.MagellanNewImageEvent;
import org.micromanager.magellan.internal.imagedisplay.events.ExploreZLimitsChangedEvent;
import org.micromanager.magellan.internal.imagedisplay.events.ImageCacheClosingEvent;
import org.micromanager.magellan.internal.imagedisplay.events.SetImageEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.gui.Overlay;
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
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.internal.acq.Acquisition;
import org.micromanager.magellan.internal.acq.ExploreAcquisition;
import org.micromanager.magellan.internal.channels.MagellanChannelSpec;
import org.micromanager.magellan.internal.imagedisplay.events.AnimationToggleEvent;
import org.micromanager.magellan.internal.imagedisplay.events.CanvasResizeEvent;
import org.micromanager.magellan.internal.imagedisplay.events.ContrastUpdatedEvent;
import org.micromanager.magellan.internal.imagedisplay.events.DisplayClosingEvent;
import org.micromanager.magellan.internal.imagedisplay.events.ImageCacheFinishedEvent;
import org.micromanager.magellan.internal.imagedisplay.events.MagellanScrollbarPosition;
import org.micromanager.magellan.internal.misc.JavaUtils;
import org.micromanager.magellan.internal.misc.Log;
import org.micromanager.magellan.internal.misc.LongPoint;
import org.micromanager.magellan.internal.surfacesandregions.XYFootprint;

public final class MagellanDisplayController {

   private MagellanImageCache imageCache_;
   private DisplaySettings displaySettings_;

   private MagellanCoalescentEDTRunnablePool edtRunnablePool_ = MagellanCoalescentEDTRunnablePool.create();

   private EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());

   private CoalescentExecutor displayCalculationExecutor_ = new CoalescentExecutor("Display calculation executor");
   private CoalescentExecutor overlayCalculationExecutor_ = new CoalescentExecutor("Overlay calculation executor");

   private DisplayWindowNew displayWindow_;
   private ImageMaker imageMaker_;
   private MagellanOverlayer overlayer_;
   private Timer animationTimer_;
   private double animationFPS_ = 7;

   private MagellanDataViewCoords viewCoords_;
   private Acquisition acq_;

   public MagellanDisplayController(MagellanImageCache cache, DisplaySettings initialDisplaySettings,
           Acquisition acq) {
      imageCache_ = cache;
      acq_ = acq;
      displaySettings_ = initialDisplaySettings;
      viewCoords_ = new MagellanDataViewCoords(cache, 0, 0, 0, 0, 0,
              isExploreAcquisiton() ? 700 : cache.getFullResolutionSize().x,
              isExploreAcquisiton() ? 700 : cache.getFullResolutionSize().y, imageCache_.getImageBounds());
      displayWindow_ = new DisplayWindowNew(this);
      displayWindow_.setTitle(cache.getUniqueAcqName() + (acq != null ? (acq.isFinished() ? " (Finished)" : " (Running)") : " (Loaded)"));
      overlayer_ = new MagellanOverlayer(this, edtRunnablePool_);
      imageMaker_ = new ImageMaker(this, cache);

      registerForEvents(this);

      cache.registerForEvents(this);
   }

   public double getZStep() {
      return imageCache_.getZStep();
   }

   public boolean isExploreAcquisiton() {
      return imageCache_.isExploreAcquisition();
   }

   public void pan(int dx, int dy) {
      Point2D.Double offset = viewCoords_.getViewOffset();
      double newX = offset.x + (dx / viewCoords_.getDisplayScaleFactor()) * viewCoords_.getDownsampleFactor();
      double newY = offset.y + (dy / viewCoords_.getDisplayScaleFactor()) * viewCoords_.getDownsampleFactor();

      if (imageCache_.isXYBounded()) {
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
      if (imageCache_.isXYBounded()) {
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
      if (imageCache_.isXYBounded()) {
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
    * used to keep explored area visible in explire acquisitons
    */
   private void moveViewToVisibleArea() {

      //check for valid tiles (at lowest res) at this slice        
      Set<Point> tiles = imageCache_.getTileIndicesWithDataAt(viewCoords_.getAxisPosition("z"));
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
         long tileX1 = (long) ((0.1 + p.x) * imageCache_.getTileWidth());
         long tileX2 = (long) ((0.9 + p.x) * imageCache_.getTileWidth());
         long tileY1 = (long) ((0.1 + p.y) * imageCache_.getTileHeight());
         long tileY2 = (long) ((0.9 + p.y) * imageCache_.getTileHeight());
//         long visibleWidth = (long) (0.8 * imageCache_.getTileWidth());
//         long visibleHeight = (long) (0.8 * imageCache_.getTileHeight());
         //get bounds of viewing area
         long fovX1 = (long) viewCoords_.getViewOffset().x;
         long fovY1 = (long) viewCoords_.getViewOffset().y;
         long fovX2 = (long) (fovX1 + viewCoords_.getSourceDataSize().x);
         long fovY2 = (long) (fovY1 + viewCoords_.getSourceDataSize().y);

         System.out.println(viewCoords_.getSourceDataSize().y);
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
      if (!isActiveExploreAcquisiton()) {
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
      edtRunnablePool_.invokeLaterWithCoalescence(
              new MagellanDisplayController.ExpandDisplayRangeCoalescentRunnable(new MagellanScrollbarPosition() {
                 @Override
                 public int getPositionForAxis(String axis) {
                    if (axis.equals("z")) {
                       return maxSliceIndex;
                    } else if (axis.equals("t")) {
                       return nFrames - 1;
                    } else if (axis.equals("c")) {
                       return channelNames.size() - 1;
                    } else if (axis.equals("r")) {
                       return 0;
                    } else {
                       throw new RuntimeException("unknown axis");
                    }
                 }
              }));
      //minimum scrollbar extensts
      edtRunnablePool_.invokeLaterWithCoalescence(
              new MagellanDisplayController.ExpandDisplayRangeCoalescentRunnable(new MagellanScrollbarPosition() {
                 @Override
                 public int getPositionForAxis(String axis) {
                    if (axis.equals("z")) {
                       return minSliceIndex;
                    }
                    return 0;
                 }
              }));
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

   @Subscribe
   public void onCacheFinished(final ImageCacheFinishedEvent e) {
      displayWindow_.setTitle(imageCache_.getUniqueAcqName() + " (Finished)");
   }

   @Subscribe
   public void onNewImage(final MagellanNewImageEvent event) {
      displayWindow_.onNewImage(); //needed because events dont propagte for some reason
      displayWindow_.updateExploreZControls(event.getPositionForAxis("z"));

      int channelIndex = event.getPositionForAxis("c");
      boolean newChannel = viewCoords_.addChannelIfNew(channelIndex, event.channelName_);
      if (newChannel) {
         displayWindow_.addContrastControls(channelIndex, event.channelName_);
      }
      //expand the scrollbars with new images
      edtRunnablePool_.invokeLaterWithCoalescence(
              new MagellanDisplayController.ExpandDisplayRangeCoalescentRunnable(event));
      //move scrollbars to new position
      postEvent(new SetImageEvent(event.axisToPosition_, false));
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
      displayWindow_.updateExploreZControls(event.getPositionForAxis("z"));
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

   public void setSurfaceDisplaySettings(boolean showInterp, boolean showStage) {
      overlayer_.setSurfaceDisplayParams(showInterp, showStage);
      redrawOverlay();
   }

   public void superlockAllScrollers() {
      displayWindow_.superlockAllScrollers();
   }

   public void unlockAllScroller() {
      displayWindow_.unlockAllScrollers();
   }

   public Point2D.Double getStageCoordinateOfViewCenter() {
      return imageCache_.stageCoordinateFromPixelCoordinate(
              (long) (viewCoords_.getViewOffset().x + viewCoords_.getSourceDataSize().x / 2),
              (long) (viewCoords_.getViewOffset().y + viewCoords_.getSourceDataSize().y / 2));
   }

   public void showFolder() {
      try {
         File location = new File(imageCache_.getDiskLocation());
         if (JavaUtils.isWindows()) {
            Runtime.getRuntime().exec("Explorer /n,/select," + location.getAbsolutePath());
         } else if (JavaUtils.isMac()) {
            if (!location.isDirectory()) {
               location = location.getParentFile();
            }
            Runtime.getRuntime().exec(new String[]{"open", location.getAbsolutePath()});
         }
      } catch (IOException ex) {
         Log.log(ex);
      }
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

   public void acquireTileAtCurrentPosition() {
      if (isActiveExploreAcquisiton()) {
         ((ExploreAcquisition) acq_).acquireTileAtCurrentLocation();
      }
   }

   void abortAcquisition() {
      if (acq_ != null && !acq_.isFinished()) {
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

   public Point getExploreStartTile() {
      return displayWindow_.getExploreStartTile();
   }

   public Point getExploreEndTile() {
      return displayWindow_.getExploreEndTile();
   }

   public Point getMouseDragStartPointLeft() {
      return displayWindow_.getMouseDragStartPointLeft();
   }

   public Point getCurrentMouseLocation() {
      return displayWindow_.getCurrentMouseLocation();
   }

   public boolean isRGB() {
      return imageCache_.isRGB();
   }

   Point2D.Double stageCoordFromImageCoords(int x, int y) {
      long newX = (long) (x / viewCoords_.getDisplayToFullScaleFactor() + viewCoords_.getViewOffset().x);
      long newY = (long) (y / viewCoords_.getDisplayToFullScaleFactor() + viewCoords_.getViewOffset().y);
      return imageCache_.stageCoordinateFromPixelCoordinate(newX, newY);
   }

   boolean isCurrentlyEditableSurfaceGridVisible() {
      return displayWindow_.isCurrentlyEditableSurfaceGridVisible();
   }

   XYFootprint getCurrentEditableSurfaceOrGrid() {
      return displayWindow_.getCurrenEditableSurfaceOrGrid();
   }

   ArrayList<XYFootprint> getSurfacesAndGridsForDisplay() {
      return displayWindow_.getSurfacesAndGridsForDisplay();
   }

   double getZCoordinateOfDisplayedSlice() {
      return acq_.getZCoordinateOfDisplaySlice(viewCoords_.getAxisPosition("z"));
   }

   void acquireTiles(int y, int x, int y0, int x0) {
      ((ExploreAcquisition) acq_).acquireTiles(y, x, y0, x0);
   }

   Point getTileIndicesFromDisplayedPixel(int x, int y) {
      double scale = viewCoords_.getDisplayToFullScaleFactor();
      int fullResX = (int) ((x / scale) + viewCoords_.getViewOffset().x);
      int fullResY = (int) ((y / scale) + viewCoords_.getViewOffset().y);
      int xTileIndex = fullResX / imageCache_.getTileWidth() - (fullResX >= 0 ? 0 : 1);
      int yTileIndex = fullResY / imageCache_.getTileHeight() - (fullResY >= 0 ? 0 : 1);
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
   public LongPoint getDisplayedPixel(long row, long col) {
      double scale = viewCoords_.getDisplayToFullScaleFactor();
      long x = (long) ((col * imageCache_.getTileWidth() - viewCoords_.getViewOffset().x) * scale);
      long y = (long) ((row * imageCache_.getTileWidth() - viewCoords_.getViewOffset().y) * scale);
      return new LongPoint(x, y);
   }

   DisplaySettings getDisplaySettings() {
      return displaySettings_;
   }

   int getTileHeight() {
      return imageCache_.getTileHeight();
   }

   int getTileWidth() {
      return imageCache_.getTileWidth();
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

   void redrawOverlay() {
      //this will automatically trigger overlay redrawing in a coalescent fashion
      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(new DisplayImageComputationRunnable());
   }

   boolean anythingAcquired() {
      return imageCache_.anythingAcquired();
   }

   LinkedBlockingQueue<ExploreAcquisition.ExploreTileWaitingToAcquire> getTilesWaitingToAcquireAtVisibleSlice() {
      return ((ExploreAcquisition) acq_).getTilesWaitingToAcquireAtSlice(viewCoords_.getAxisPosition("z"));
   }

   double getScale() {
      return viewCoords_.getDisplayToFullScaleFactor();
   }

   MagellanChannelSpec getChannels() {
      return acq_.getChannels();
   }

   LongPoint imageCoordsFromStageCoords(double x, double y, MagellanDataViewCoords viewCoords) {
      LongPoint pixelCoord = imageCache_.pixelCoordsFromStageCoords(x, y);
      return new LongPoint(
              (long) ((pixelCoord.x_ - viewCoords.getViewOffset().x) * viewCoords.getDisplayToFullScaleFactor()),
              (long) ((pixelCoord.y_ - viewCoords.getViewOffset().y) * viewCoords.getDisplayToFullScaleFactor()));
   }

   int getSliceIndexFromZCoordinate(double z) {
      return acq_.getDisplaySliceIndexFromZCoordinate(z);
   }

   double getPixelSize() {
      return imageCache_.getPixelSize_um();
   }

   void showScaleBar(boolean selected) {
      overlayer_.setShowScaleBar(selected);
   }

   boolean isActiveExploreAcquisiton() {
      return isExploreAcquisiton() && acq_ != null;
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

   /**
    * A coalescent runnable to avoid excessively frequent update of the data
    * coords range in the UI
    */
   private class ExpandDisplayRangeCoalescentRunnable
           implements CoalescentRunnable {

      private final List<MagellanScrollbarPosition> newIamgeEvents = new ArrayList<MagellanScrollbarPosition>();

      ExpandDisplayRangeCoalescentRunnable(MagellanScrollbarPosition event) {
         newIamgeEvents.add(event);
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
         if (acq_ != null && !acq_.isFinished()) {
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
      imageCache_.unregisterForEvents(this);

      imageMaker_.close();
      imageMaker_ = null;

      overlayer_.shutdown();
      overlayer_ = null;

      if (animationTimer_ != null) {
         animationTimer_.stop();
      }
      animationTimer_ = null;

      imageCache_.close();
      imageCache_ = null;
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

   @Subscribe
   public void onExploreZLimitsChangedEvent(ExploreZLimitsChangedEvent event) {
      ((ExploreAcquisition) acq_).setZLimits(event.top_, event.bottom_);
   }

   @Subscribe
   public void onDatastoreClosing(ImageCacheClosingEvent event) {
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
         return new JSONObject(imageCache_.getSummaryMD().toString());
      } catch (JSONException ex) {
         return null; //this shouldnt happen
      }
   }

   private class DisplayImageComputationRunnable implements CoalescentRunnable {

      MagellanDataViewCoords view_;

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
         Overlay cheapOverlay = overlayer_.createEasyPartsOfOverlay(view_);

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
      MagellanDataViewCoords view_;
      HashMap<Integer, int[]> hists_;
      HashMap<Integer, Integer> mins_;
      HashMap<Integer, Integer> maxs_;
      Overlay overlay_;
      JSONObject imageMD_;

      public CanvasRepaintRunnable(Image img, HashMap<Integer, int[]> hists, HashMap<Integer, Integer> pixelMins,
              HashMap<Integer, Integer> pixelMaxs, MagellanDataViewCoords view, Overlay cheapOverlay, JSONObject imageMD) {
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
         displayWindow_.displayOverlay(overlay_);

      }

   }

}
