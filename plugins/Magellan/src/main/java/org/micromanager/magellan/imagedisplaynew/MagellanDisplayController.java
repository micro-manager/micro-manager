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

import org.micromanager.magellan.imagedisplaynew.events.ChannelAddedToDisplayEvent;
import org.micromanager.magellan.imagedisplaynew.events.MagellanNewImageEvent;
import org.micromanager.magellan.imagedisplaynew.events.ExploreZLimitsChangedEvent;
import org.micromanager.magellan.imagedisplaynew.events.ImageCacheClosingEvent;
import org.micromanager.magellan.imagedisplaynew.events.SetImageEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import javax.swing.SwingUtilities;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.data.Coords;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.internal.utils.EventBusExceptionLogger;
import org.micromanager.magellan.imagedisplaynew.events.DisplayClosingEvent;

public final class MagellanDisplayController {

   public static final int NONE = 0, EXPLORE = 1, SURFACE_AND_GRID = 2;
   private int magellanMode_;

   private MagellanImageCache imageCache_;


   private  MagellanCoalescentEDTRunnablePool edtRunnablePool_ = MagellanCoalescentEDTRunnablePool.create();

   private EventBus eventBus_ = new EventBus(EventBusExceptionLogger.getInstance());

   // Guarded by monitor on this
   private DisplaySettings displaySettings_;
   // Guarded by monitor on this

   private CoalescentExecutor displayCalculationExecutor_ = new CoalescentExecutor("Display calculation executor");
   private MagellanDataView dataView_;
   private ImageMaker imageMaker_;
   private DisplayWindowNew displayWindow_;
   private HashSet<Integer> channelsSeen_ = new HashSet<Integer>();

   //Parameters that track what part of the dataset is being viewed
   private volatile int resolutionIndex_ = 0;
   private volatile int displayImageWidth_, displayImageHeight_;
   private volatile long xView_ = 0, yView_ = 0;  //top left pixel of view in current res
   //TODO: do these belong here?
   private long xMax_, yMax_, xMin_, yMin_;

   public MagellanDisplayController(MagellanImageCache cache, DisplaySettings initialDisplaySettings) {

      imageCache_ = cache;
      displayWindow_ = new DisplayWindowNew(this);
      //TODO: maybe get these from display windows

      //TODO: these will change depending on zoom and pan
      displayImageWidth_ = 512;
      displayImageHeight_ = 512;

      registerForEvents(this);

//      dataView_ = new MagellanDataView(cache, width, height);
      imageMaker_ = new ImageMaker(this, cache, 500, 500);

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

   //TODO: switch to events from the datastore for updating z scroll bars
   //
   // Event handlers
   //
   // From the datastore
   @Subscribe
   public void onNewImage(final MagellanNewImageEvent event) {

      displayWindow_.updateExploreZControls(event.getPositionForAxis("z"));

      //Make imageMaker aware of new channels as they come in
      displayCalculationExecutor_.submitNonCoalescent(new Runnable() {
         @Override
         public void run() {
            int channelIndex = event.getPositionForAxis("c");
            if (!channelsSeen_.contains(channelIndex)) {
               //Alert imageMaker to new channels to process
               postEvent(new ChannelAddedToDisplayEvent(channelIndex));
               channelsSeen_.add(channelIndex);
            }
         }
      });

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

      //TODO: replace this with something from the histogram controls
      int[] visibleChannelIndices = new int[]{0};

      MagellanDataViewCoords viewCoords = new MagellanDataViewCoords(resolutionIndex_,
              displayImageHeight_, displayImageWidth_, xView_, yView_, event.getPositionForAxis("c"),
              event.getPositionForAxis("z"), event.getPositionForAxis("t"), visibleChannelIndices);

      displayCalculationExecutor_.invokeAsLateAsPossibleWithCoalescence(new DisplayImageComputationRunnable(viewCoords));
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

   /***
    * Acquisition should be closed vbefore calling this
    */
   public void close() {
      //acquisiiton should be aborted or finished already when this is called
      
      //make everything else close
      postEvent(new DisplayClosingEvent());
      
      imageMaker_.close();
      imageMaker_ = null;
      displayCalculationExecutor_.shutdownNow();
      imageCache_.unregisterForEvents(this);
      
      imageCache_.close();
      imageCache_ = null;
      unregisterForEvents(this);

      displayWindow_ = null;
      imageMaker_ = null;
      dataView_ = null;
      eventBus_ = null;
      
       edtRunnablePool_ = null;
     displaySettings_ = null;
     displayCalculationExecutor_ = null;
     dataView_ = null;
     imageMaker_ = null;
     displayWindow_ =null;
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

      public DisplayImageComputationRunnable(MagellanDataViewCoords viewCoords) {
         view_ = viewCoords;
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
