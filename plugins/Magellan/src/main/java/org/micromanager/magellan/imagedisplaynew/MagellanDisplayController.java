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
package org.micromanager.magellan.imagedisplaynew;

import org.micromanager.magellan.imagedisplaynew.events.MagellanNewImageEvent;
import org.micromanager.magellan.imagedisplaynew.events.ExploreZLimitsChangedEvent;
import org.micromanager.magellan.imagedisplaynew.events.ImageCacheClosingEvent;
import org.micromanager.magellan.imagedisplaynew.events.SetImageEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.gui.Overlay;
import java.awt.Image;
import java.awt.Point;
import java.awt.geom.AffineTransform;
import java.awt.geom.Point2D;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.magellan.acq.Acquisition;
import org.micromanager.magellan.acq.ExploreAcquisition;
import org.micromanager.magellan.channels.MagellanChannelSpec;
import org.micromanager.magellan.imagedisplay.DisplaySettings;
import org.micromanager.magellan.imagedisplaynew.events.CanvasResizeEvent;
import org.micromanager.magellan.imagedisplaynew.events.ContrastUpdatedEvent;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;
import org.micromanager.magellan.misc.LongPoint;
import org.micromanager.magellan.surfacesandregions.XYFootprint;

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
      overlayer_ = new MagellanOverlayer(this, edtRunnablePool_);
      imageMaker_ = new ImageMaker(this, cache);

      registerForEvents(this);

      // TODO Make sure frame controller forwards messages to us (e.g.
      // windowClosing() -> requestToClose())
      // Start receiving events
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
      //TODO reimplement once explore is back

      //compensate for the possibility of negative slice indices 
//      int slice = disp_.getVisibleSliceIndex() + ((ExploreAcquisition) acquisition_).getMinSliceIndex();
//
//      //check for valid tiles (at lowest res) at this slice        
//      Set<Point> tiles = multiResStorage_.getTileIndicesWithDataAt(slice);
//      if (tiles.size() == 0) {
//         return;
//      }
      //center of one tile must be within corners of current view 
//      double minDistance = Integer.MAX_VALUE;
//      //do all calculations at full resolution
//      long newXView = xView_ * getDownsampleFactor();
//      long newYView = yView_ * getDownsampleFactor();
//      for (Point p : tiles) {
//         //calclcate limits on margin of tile that must remain in view
//         long tileX1 = (long) ((0.1 + p.x) * tileWidth_);
//         long tileX2 = (long) ((0.9 + p.x) * tileWidth_);
//         long tileY1 = (long) ((0.1 + p.y) * tileHeight_);
//         long tileY2 = (long) ((0.9 + p.y) * tileHeight_);
//         long visibleWidth = (long) (0.8 * tileWidth_);
//         long visibleHeight = (long) (0.8 * tileHeight_);
//         //get bounds of viewing area
//         long fovX1 = getAbsoluteFullResPixelCoordinate(0, 0).x_;
//         long fovY1 = getAbsoluteFullResPixelCoordinate(0, 0).y_;
//         long fovX2 = fovX1 + displayImageWidth_ * getDownsampleFactor();
//         long fovY2 = fovY1 + displayImageHeight_ * getDownsampleFactor();
//
//         //check if tile and fov intersect
//         boolean xInView = fovX1 < tileX2 && fovX2 > tileX1;
//         boolean yInView = fovY1 < tileY2 && fovY2 > tileY1;
//         boolean intersection = xInView && yInView;
//
//         if (intersection) {
//            return; //at least one tile is in view, don't need to do anything
//         }
//         //tile to fov corner to corner distances
//         double tl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY1 - fovY2) * (tileY1 - fovY2)); //top left tile, botom right fov
//         double tr = ((tileX2 - fovX1) * (tileX2 - fovX1) + (tileY1 - fovY2) * (tileY1 - fovY2)); // top right tile, bottom left fov
//         double bl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY2 - fovY1) * (tileY2 - fovY1)); // bottom left tile, top right fov
//         double br = ((tileX1 - fovX1) * (tileX1 - fovX1) + (tileY2 - fovY1) * (tileY2 - fovY1)); //bottom right tile, top left fov
//
//         double closestCornerDistance = Math.min(Math.min(tl, tr), Math.min(bl, br));
//         if (closestCornerDistance < minDistance) {
//            minDistance = closestCornerDistance;
//            if (tl <= tr && tl <= bl && tl <= br) { //top left tile, botom right fov
//               newXView = xInView ? newXView : tileX1 - displayImageWidth_ * getDownsampleFactor();
//               newYView = yInView ? newYView : tileY1 - displayImageHeight_ * getDownsampleFactor();
//            } else if (tr <= tl && tr <= bl && tr <= br) { // top right tile, bottom left fov
//               newXView = xInView ? newXView : tileX2;
//               newYView = yInView ? newYView : tileY1 - displayImageHeight_ * getDownsampleFactor();
//            } else if (bl <= tl && bl <= tr && bl <= br) { // bottom left tile, top right fov
//               newXView = xInView ? newXView : tileX1 - displayImageWidth_ * getDownsampleFactor();
//               newYView = yInView ? newYView : tileY2;
//            } else { //bottom right tile, top left fov
//               newXView = xInView ? newXView : tileX2;
//               newYView = yInView ? newYView : tileY2;
//            }
//         }
//      }
//      //readjust to current res level
//      xView_ = newXView / getDownsampleFactor();
//      yView_ = newYView / getDownsampleFactor();
   }

   @Subscribe
   public void onCanvasResize(final CanvasResizeEvent e) {
      Point2D.Double displaySizeOld = viewCoords_.getDisplayImageSize();
      viewCoords_.setDisplayImageSize(e.w, e.h);
      //reshape the source image to match canvas aspect ratio
      //expand it, unless it would put it out of range
      double canvasAspect = e.w / (double) e.h;
      Point2D.Double source = viewCoords_.getSourceDataSize();
      double sourceAspect = source.x / source.y;
      double newSourceX;
      double newSourceY;
      if (!isExploreAcquisiton()) {
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
         newSourceX = source.x;
         newSourceY = source.y;
      }
      //move into visible area
      viewCoords_.setViewOffset(
              Math.max(viewCoords_.xMin_, Math.min(viewCoords_.xMax_
                      - newSourceX, viewCoords_.getViewOffset().x)),
              Math.max(viewCoords_.yMin_, Math.min(viewCoords_.yMax_
                      - newSourceY, viewCoords_.getViewOffset().y)));

      viewCoords_.setSourceDataSize(newSourceX, newSourceY);
      recomputeDisplayedImage();
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

   public void recomputeDisplayedImage() {
      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(new DisplayImageComputationRunnable());
   }

   public MagellanCanvas getCanvas() {
      return displayWindow_.getCanvas();
   }

   public boolean[] getActiveChannels() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public boolean isCompositeMode() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void setAnimateFPS(double doubleValue) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void acquireTileAtCurrentPosition() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   void abortAcquisition() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   void togglePauseAcquisition() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   boolean isAcquisitionPaused() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   void synchronizeChannelExposures() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   void synchronizeUseOnChannels() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
      return viewCoords_.getAxisPosition("z");
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

   long getNumCols() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   int getNumRows() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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

   AffineTransform getAffineTransform() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   double getPixelSize() {
      return imageCache_.getPixelSize_um();
   }

   void showScaleBar(boolean selected) {
      overlayer_.setShowScaleBar(selected);
   }

   /**
    * A coalescent runnable to avoid excessively frequent update of the data
    * coords range in the UI
    */
   private class ExpandDisplayRangeCoalescentRunnable
           implements CoalescentRunnable {

      private final List<MagellanNewImageEvent> newIamgeEvents = new ArrayList<MagellanNewImageEvent>();

      ExpandDisplayRangeCoalescentRunnable(MagellanNewImageEvent event) {
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
         if (acq_ != null && !acq_.isFinished()) {
            int result = JOptionPane.showConfirmDialog(null, "Finish acquisition?",
                    "Finish Current Acquisition", JOptionPane.OK_CANCEL_OPTION);
            if (result == JOptionPane.OK_OPTION) {
               acq_.abort();
            } else {
               return;
            }
         }
         //TODO: check to stop acquisiton?, return here if the attempt to close window unsuccesslful

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

         Overlay cheapOverlay = overlayer_.createEasyPartsOfOverlay(view_);

         HashMap<Integer, int[]> channelHistograms = imageMaker_.getHistograms();
         edtRunnablePool_.invokeAsLateAsPossibleWithCoalescence(new CanvasRepaintRunnable(img, channelHistograms, view_, cheapOverlay));
         //now send expensive overlay computation to overlay creation thread
         overlayer_.redrawOverlay(view_);
      }
   }

   private class CanvasRepaintRunnable implements CoalescentRunnable {

      final Image img_;
      MagellanDataViewCoords view_;
      HashMap<Integer, int[]> hists_;
      Overlay overlay_;

      public CanvasRepaintRunnable(Image img, HashMap<Integer, int[]> hists, MagellanDataViewCoords view, Overlay cheapOverlay) {
         img_ = img;
         view_ = view;
         hists_ = hists;
         overlay_ = cheapOverlay;
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
         displayWindow_.displayImage(img_, hists_, view_);
         displayWindow_.displayOverlay(overlay_);

      }

   }

}
