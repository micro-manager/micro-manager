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
import java.awt.Point;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.swing.SwingUtilities;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.magellan.imagedisplaynew.events.CanvasResizeEvent;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;

public final class MagellanDisplayController {

   public static final int NONE = 0, EXPLORE = 1, SURFACE_AND_GRID = 2;
   private int magellanMode_;

   private MagellanImageCache imageCache_;

   private MagellanCoalescentEDTRunnablePool edtRunnablePool_ = MagellanCoalescentEDTRunnablePool.create();

   private EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());

   // Guarded by monitor on this
   private DisplaySettings displaySettings_;
   // Guarded by monitor on this

   private CoalescentExecutor displayCalculationExecutor_ = new CoalescentExecutor("Display calculation executor");
   private DisplayWindowNew displayWindow_;
   private HashSet<Integer> channelsSeen_ = new HashSet<Integer>();
   private ImageMaker imageMaker_;

   private MagellanDataViewCoords viewCoords_;
   private final boolean xyBounded_;

   public MagellanDisplayController(MagellanImageCache cache, DisplaySettings initialDisplaySettings) {

      viewCoords_ = new MagellanDataViewCoords(0, 0, 0, 0, 0);
      imageCache_ =  cache;
      displayWindow_ = new DisplayWindowNew(this);
      //TODO: maybe get these from display windows

      imageMaker_ = new ImageMaker(this, cache, 500, 500);
      xyBounded_ = imageCache_.isXYBounded();

      
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
      viewCoords_.xView_ += dx;
      viewCoords_.yView_ += dy;
      recomputeDisplayedImage();
   }
   
   @Subscribe
   public void onCanvasResize(final CanvasResizeEvent e) {
      viewCoords_.displayImageWidth_ = e.w;
      viewCoords_.displayImageHeight_ = e.h;      
      recomputeDisplayedImage();
   }
   
   @Subscribe
   public void onNewImage(final MagellanNewImageEvent event) {

      displayWindow_.updateExploreZControls(event.getPositionForAxis("z"));


      int channelIndex = event.getPositionForAxis("c");
      if (!viewCoords_.channelsActive_.keySet().contains(channelIndex)) {
         //TODO: might not actually want to set it to true depending on display mode
         viewCoords_.channelsActive_.put(channelIndex, true);
      }

      //expand the scrollbars with new images
      edtRunnablePool_.invokeLaterWithCoalescence(
              new MagellanDisplayController.ExpandDisplayRangeCoalescentRunnable(event));
      //move scrollbars to new position
      postEvent(new SetImageEvent(event.axisToPosition_));
   }

   /**
    * Called when scrollbars move
    */
   @Subscribe
   public void onSetImageEvent(final SetImageEvent event) {
      recomputeDisplayedImage();
   }
   
   public void recomputeDisplayedImage() {
      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(new DisplayImageComputationRunnable());
   }

   public boolean[] getActiveChannels() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public boolean isCompositeMode() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void setMagellanMode(int mode) {
      magellanMode_ = mode;
   }

   void fitExploreCanvasToWindow() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void setSurfaceDisplaySettings(boolean selected, boolean selected0) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void superlockAllScrollers() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void unlockAllScroller() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public Point2D.Double getCurrentDisplayedCoordinate() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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

   public boolean isRGB() {
      return imageCache_.isRGB();
   }

   void redrawOverlay() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   Point2D.Double stageCoordFromImageCoords(int i, int i0) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   boolean isCurrentlyEditableSurfaceGridVisible() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   Object getCurrentEditableSurfaceOrGrid() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   double getZCoordinateOfDisplayedSlice() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   void acquireTiles(int y, int x, int y0, int x0) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   Point getTileIndicesFromDisplayedPixel(int x, int y) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   void zoom(int i) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
      imageCache_.unregisterForEvents(this);
         
      imageMaker_.close();
      imageMaker_ = null;
      
      imageCache_.close();
      imageCache_ = null;
      unregisterForEvents(this);

      displayWindow_ = null;
      viewCoords_ = null;
      eventBus_ = null;

      edtRunnablePool_ = null;
      displaySettings_ = null;
      displayCalculationExecutor_ = null;
      channelsSeen_ = null;
   }

   @Subscribe
   public void onExploreZLimitsChangedEvent(ExploreZLimitsChangedEvent event) {
      //TODO
//      ((ExploreAcquisition) acq_).setZLimits(zTop, zBottom);

   }

   @Subscribe
   public void onDatastoreClosing(ImageCacheClosingEvent event) {
//      if (event.getDatastore().equals(dataProvider_)) {
      requestToClose();
//      }
   }

   public void displayStatusString(String status) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public double getZoom() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void setZoom(double ratio) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void adjustZoom(double factor) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void autostretch() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void addOverlay(Overlay overlay) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void removeOverlay(Overlay overlay) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public List<Overlay> getOverlays() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void toFront() {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   public void setCustomTitle(String title) {

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
         BufferedImage img = imageMaker_.makeBufferedImage(view_);
         //TODO: maybe also calcualte stats here
         edtRunnablePool_.invokeAsLateAsPossibleWithCoalescence(new CanvasRepaintRunnable(img, view_));
      }

   }

   private class CanvasRepaintRunnable implements CoalescentRunnable {

      final BufferedImage img_;
      MagellanDataViewCoords view_;

      public CanvasRepaintRunnable(BufferedImage img, MagellanDataViewCoords view) {
         img_ = img;
         view_ = view;
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
         displayWindow_.displayImage(img_, view_);
      }

   }

}
