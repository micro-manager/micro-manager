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

package org.micromanager.display.internal;

import com.google.common.eventbus.Subscribe;
import ij.ImagePlus;
import java.awt.Rectangle;
import java.awt.Window;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import javax.swing.SwingUtilities;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Image;
import org.micromanager.data.NewImageEvent;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplayManager;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.internal.animate.AnimationController;
import org.micromanager.display.internal.animate.DataCoordsAnimationState;
import org.micromanager.display.internal.imagestats.ImageStatsRequest;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.display.internal.imagestats.StatsComputeQueue;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.MMSwingUtilities;
import org.micromanager.internal.utils.MMSwingUtilities.CoalescentRunnable;
import org.micromanager.internal.utils.MustCallOnEDT;
import org.micromanager.internal.utils.performance.PerformanceMonitor;
import org.micromanager.internal.utils.performance.WallTimer;
import org.micromanager.internal.utils.performance.gui.PerformanceMonitorUI;

/**
 * Main controller for MMStudio's default image viewer.
 *
 * @author Mark A. Tsuchida, parts refactored from code by Chris Weisiger
 */
public final class DisplayController extends DisplayWindowAPIAdapter
      implements DisplayWindow,
      DataCoordsAnimationState.CoordsProvider,
      AnimationController.Listener<Coords>
{
   private final DefaultDisplayManager parent_;
   private final DataProvider dataProvider_;

   // Not final but set only upon creation
   private DataCoordsAnimationState animationState_;
   // Not final but set only upon creation
   private AnimationController<Coords> animationController_;

   private final StatsComputeQueue computeQueue_ = StatsComputeQueue.create();

   private static final long MIN_REPAINT_PERIOD_NS = Math.round(1e9 / 60.0);
   private Thread repaintSchedulerThread_;

   // The UI controller manages the actual JFrame and all the components in it,
   // including interaction with ImageJ. After being closed, set to null.
   // Must access on EDT
   private DisplayUIController uiController_;

   // A way to know from a non-EDT thread that the display has definitely
   // closed (may not be true for a short period after closing)
   // Guarded by monitor on this
   private volatile boolean closeCompleted_;

   // Guarded by monitor on this
   private String customTitle_; // TODO Use

   private final ControlsFactory controlsFactory_;

   public static final String DEFAULT_SETTINGS_PROFILE_KEY = "Default";
   private String settingsProfileKey_ = DEFAULT_SETTINGS_PROFILE_KEY;

   private final PerformanceMonitor perfMon_ =
         PerformanceMonitor.createWithTimeConstantMs(1000.0);
   private final PerformanceMonitorUI perfMonUI_ =
         PerformanceMonitorUI.create(perfMon_, "Display Performance");

   private static final class RepaintTaskTag {}

   public static class Builder {
      private final DefaultDisplayManager parent_;
      private DataProvider dataProvider_;
      private DisplaySettings displaySettings_;
      private String settingsProfileKey_;
      private boolean shouldShow_;
      private ControlsFactory controlsFactory_;

      public Builder(DefaultDisplayManager parent, DataProvider dataProvider)
      {
         if (parent == null || dataProvider == null) {
            throw new NullPointerException();
         }
         parent_ = parent;
         dataProvider_ = dataProvider;
      }

      public Builder dataProvider(DataProvider provider) {
         if (provider == null) {
            throw new NullPointerException();
         }
         dataProvider_ = provider;
         return this;
      }

      public Builder initialDisplaySettings(DisplaySettings settings) {
         displaySettings_ = settings;
         return this;
      }

      public Builder settingsProfileKey(String key) {
         settingsProfileKey_ = key;
         return this;
      }

      public Builder shouldShow(boolean flag) {
         shouldShow_ = flag;
         return this;
      }

      public Builder controlsFactory(ControlsFactory factory) {
         controlsFactory_ = factory;
         return this;
      }

      @MustCallOnEDT
      public DisplayController build() {
         return DisplayController.create(this);
      }
   }

   // TODO Provide parent display manager or Studio?
   @MustCallOnEDT
   private static DisplayController create(Builder builder)
   {
      DisplaySettings initialDisplaySettings = builder.displaySettings_;
      if (initialDisplaySettings == null) {
         initialDisplaySettings = RememberedChannelSettings.updateSettings(
               builder.dataProvider_.getSummaryMetadata(),
               DefaultDisplaySettings.getStandardSettings(
                     builder.settingsProfileKey_),
               builder.dataProvider_.getAxisLength(Coords.CHANNEL));
      }
      if (initialDisplaySettings == null) {
         initialDisplaySettings = new DefaultDisplaySettings.Builder().build();
      }

      DisplayController instance = new DisplayController(builder.parent_,
            builder.dataProvider_,
            initialDisplaySettings, builder.controlsFactory_,
            builder.settingsProfileKey_);
      instance.initialize();

      // Show inspector before our frame for correct window ordering
      instance.parent_.createFirstInspector();

      if (builder.shouldShow_) {
         instance.setFrameVisible(true);
         instance.toFront();
      }

      return instance;
   }

   private DisplayController(DefaultDisplayManager parent,
         DataProvider dataProvider,
         DisplaySettings initialDisplaySettings,
         ControlsFactory controlsFactory,
         String settingsProfileKey)
   {
      super(initialDisplaySettings);
      parent_ = parent;
      dataProvider_ = dataProvider;
      controlsFactory_ = controlsFactory;
      settingsProfileKey_ = settingsProfileKey != null ? settingsProfileKey :
            DEFAULT_SETTINGS_PROFILE_KEY;

      computeQueue_.setPerformanceMonitor(perfMon_);
   }

   @MustCallOnEDT
   private void initialize() {
      // Initialize some things that would leak 'this' if done in the
      // constructor
      animationState_ = DataCoordsAnimationState.create(this);
      animationController_ = AnimationController.create(animationState_);
      animationController_.addListener(this);
      // TODO Shut down animation controller

      computeQueue_.start();
      // TODO Shut down compute queue

      uiController_ = DisplayUIController.create(this, controlsFactory_);
      uiController_.setPerformanceMonitor(perfMon_);
      // TODO Make sure frame controller forwards messages to us (e.g.
      // windowClosing() -> requestToClose())

      // Start receiving events
      DefaultEventManager.getInstance().registerForEvents(this);
      dataProvider_.registerForEvents(this);

      // TODO Should we add this to DisplayGroupManager, or let DefaultDisplayManager do that?

      // TODO At this point DefaultDisplayWindow would issue a DefaultNewDisplayEvent. However, I think the DisplayManager factory method should be doing that.
      // TODO Likewise, DisplayManager should ensure the Inspector is created and in focus, if applicable.
   }

   @MustCallOnEDT
   public DisplayUIController getUIController() {
      return uiController_;
   }

   public Studio getStudio() {
      // TODO
      return null;
   }

   @MustCallOnEDT
   private void setFrameVisible(boolean visible) {
      uiController_.setVisible(visible);
   }

   // This is not in the API because it doesn't make sense under the current
   // implementation where the frame is not shown until the datastore has an
   // image. In order to allow API clients to show/hide the frame, we need to
   // first redesign so that the frame can be displayed even for empty data.
   public void setVisible(boolean visible) {
      if (uiController_ == null) {
         return;
      }
      uiController_.setVisible(visible);
   }


   //
   // Scheduling images for display
   //

   @SuppressWarnings("SleepWhileInLoop") // We sleep for throttling
   private void displaySchedulerLoop() throws InterruptedException {
      long lastIterationStartNs_ = 0;
      for (;;) {
         // Wait, so that we don't exceed max display frequency
         long elapsedNs = System.nanoTime() - lastIterationStartNs_;
         long sleepDurationNs = MIN_REPAINT_PERIOD_NS - elapsedNs;
         if (sleepDurationNs > 0) {
            Thread.sleep(sleepDurationNs / 1000000,
                  (int) (sleepDurationNs % 1000000));
         }
         lastIterationStartNs_ = System.nanoTime();

         perfMon_.sampleTimeInterval("Repaint scheduler");
         WallTimer retrievalTimer = WallTimer.createStarted();

         ImagesAndStats iamges = computeQueue_.waitAndRetrieveResult();

         perfMon_.sample("Repaint scheduler wait time (ms)",
               retrievalTimer.getMs());

         scheduleDisplayInUI(iamges);
      }
   }

   private void scheduleDisplayInUI(final ImagesAndStats images) {
      // A note about congestion of event queue by excessive paint events:
      //
      // We know from experience (since Micro-Manager 1.4) that scheduling
      // repaints too frequently will congest the EDT.
      //
      // One method to avoid congestion (which was previously used) is to
      // explicitly detect and wait for the completion of painting on the EDT.
      // If using that method, here would be the place to wait for previously
      // requested paints to finish.
      //
      // Here, we schedule painting using coalescent runnables. No repaint is
      // scheduled until the EDT catches up to the last of (the InvocationEvent
      // resulting from) our coalescent runnables. Because the EDT will not
      // catch up if congested, we don't exacerbate the situation by scheduling
      // further repaints.
      // It is also guaranteed that a repaint _will_ be scheduled when no
      // longer congested, because before becoming idle the EDT will have to
      // process the last of our coalescent runnable, thereby scheduling a
      // repaint with the latest requested display position.
      // One of the nice things about doing it this way is that we can
      // coalesce the displaying tasks for multiple display windows.

      MMSwingUtilities.invokeAsLateAsPossibleWithCoalescence(
            new MMSwingUtilities.CoalescentRunnable() {
         @Override
         public Class<?> getCoalescenceClass() {
            return RepaintTaskTag.class;
         }

         @Override
         public CoalescentRunnable coalesceWith(CoalescentRunnable another) {
            // Only the most recent repaint task need be run
            // TODO XXX We need to keep the most recent for _each_display_!
            return another;
         }

         @Override
         public void run() {
            if (uiController_ == null) { // Closed
               return;
            }

            uiController_.displayImages(images);
            // TODO Rather than hiding pixel info, update it
            uiController_.updatePixelInfoUI(null);
            // TODO XXX Update inspector for imagesToPaint (if composite,
            // need to select channel for metadata from ui state)

            perfMon_.sampleTimeInterval("Scheduled repaint on EDT");
         }
      });
   }

   public double getDisplayIntervalQuantile(double q) {
      if (uiController_ == null) {
         return 0.0;
      }
      return uiController_.getDisplayIntervalQuantile(q);
   }

   public void resetDisplayIntervalEstimate() {
      if (uiController_ != null) {
         uiController_.resetDisplayIntervalEstimate();
      }
   }


   //
   // Implementation of DataCoordsAnimationState.CoordsProvider
   //

   @Override
   public List<String> getOrderedAxes() {
      // TODO XXX Check if this is ordered; if not, fix.
      return dataProvider_.getAxes();
   }

   @Override
   public int getMaximumExtentOfAxis(String axis) {
      return dataProvider_.getAxisLength(axis);
   }

   @Override
   public boolean coordsExist(Coords c) {
      return dataProvider_.hasImage(c);
   }

   @Override
   public Collection<String> getAnimatedAxes() {
      // TODO return true state
      return Collections.emptySet();
   }

   // A new conceptual display position has been set; arrange for the UI to
   // eventually display it.
   private void handleNewDisplayPosition(Coords position) {
      MMSwingUtilities.invokeLaterWithCoalescence(
            new ExpandDisplayRangeCoalescentRunnable(position));
      // TODO Update (later with coalescence) the position indicators in the UI

      Coords channellessPos = position.hasAxis(Coords.CHANNEL) ?
            position.copy().removeAxis(Coords.CHANNEL).build() :
            position;
      List<Image> images = dataProvider_.getImagesMatching(channellessPos);
      // TODO XXX We need to handle missing images if so requested. User should
      // be able to enable "filling in Z slices" and "filling in time points".
      // If 'images' is empty, search first in Z, then in time, until at least
      // one channel has an image (Q: or just leave empty when no channel has
      // an image?). Then, for missing channels, first search for nearest Z in
      // either direction. For channels still missing, search back in time,
      // looking for the nearest available slice in each time point.
      // Record the substitutions made (requested and provided coords) in to
      // the ImageStatsRequest!
      // Consider implementing this substitution mechanism as a utility class
      // (not a Datastore method, because then every datastore would have to
      // reinvent the wheel).
      // XXX On the other hand, we do NOT want to fill in images if they are
      // about to be acquired! How do we know if this is the case?
      Rectangle roiRect = null; // TODO XXX
      byte[] roiMask = null; // TODO XXX
      computeQueue_.submitRequest(
            ImageStatsRequest.create(images, roiRect, roiMask));
   }


   //
   // Implementation of AnimationController.Listener<Coords>
   //

   @Override
   public void animationShouldDisplayDataPosition(Coords position) {
      perfMon_.sampleTimeInterval("Coords from animation controller");

      // We do not skip handling this position even if it equals the current
      // position, because the image data may have changed (e.g. if we have
      // been displaying a position that didn't yet have an image, or if this
      // is a special datastore such as the one used for snap/live preview).

      // Also, we do not throttle the processing rate here because that is done
      // automatically by the compute queue based on result retrieval.

      // Set the "official" position of this data viewer
      setDisplayPosition(position, true);
   }

   @Override
   public void animationAcknowledgeDataPosition(final Coords position) {
      // Tell the UI controller to expand the display range to include the
      // newly seen position. But use coalescent invocation to avoid doing
      // too much on the EDT.
      MMSwingUtilities.invokeLaterWithCoalescence(
            new ExpandDisplayRangeCoalescentRunnable(position));
   }

   @Override
   public void animationWillJumpToNewDataPosition(Coords position) {
      // Nothing to do
   }

   @Override
   public void animationDidJumpToNewDataPosition(Coords position) {
      // TODO Show a visual indicator that the image is new (with a timer to
      // self-dismiss)
   }

   @Override
   public void animationWillSnapBackFromNewDataPosition() {
      // TODO Hide any visual indicator displayed in didJumpToNewDataPosition()
   }

   @Override
   public void animationDidSnapBackFromNewDataPosition() {
      // Nothing to do
   }


   //
   // Event handlers
   //

   // From the datastore
   @Subscribe
   public void onNewImage(final NewImageEvent event) {
      perfMon_.sampleTimeInterval("NewImageEvent");

      synchronized (this) {
         if (closeCompleted_) {
            return;
         }

         // Kick off the thread to schedule display of the processed images.
         // TODO Use ScheduledExecutorService for this.
         if (repaintSchedulerThread_ == null) {
            repaintSchedulerThread_ =
                  new Thread("Display Scheduler Thread") {
               @Override
               public void run() {
                  try {
                     displaySchedulerLoop();
                  }
                  catch (InterruptedException exitRequested) {
                  }
               }
            };
            repaintSchedulerThread_.start(); // TODO XXX Shutdown
         }
      }

      // Generally we want to display new images (if not instructed otherwise
      // by the user), but we let the animation controller coordinate that with
      // any ongoing playback animation. Actual display of new images happens
      // upon receiving callbacks via the AnimationController.Listener
      // interface.
      animationController_.newDataPosition(event.getImage().getCoords());
   }

   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      // TODO Find out diff and invokeLater to apply
   }


   //
   //
   //

   // A coalescent runnable to avoid excessively frequent update of the data
   // coords range in the UI
   private class ExpandDisplayRangeCoalescentRunnable
         implements MMSwingUtilities.CoalescentRunnable
   {
      private final List<Coords> coords_ = new ArrayList<Coords>();

      ExpandDisplayRangeCoalescentRunnable(Coords coords) {
         coords_.add(coords);
      }

      @Override
      public Class<?> getCoalescenceClass() {
         return ExpandDisplayRangeCoalescentRunnable.class;
      }

      @Override
      public CoalescentRunnable coalesceWith(CoalescentRunnable another) {
         // TODO XXX We must not coalesce with tasks for other displays!
         coords_.addAll(
               ((ExpandDisplayRangeCoalescentRunnable) another).coords_);
         return this;
      }

      @Override
      public void run() {
         if (uiController_ == null) {
            return;
         }
         uiController_.expandDisplayedRangeToInclude(coords_);
      }
   }


   //
   // Implementation of DisplayWindow interface
   //

   @Override
   public DataProvider getDataProvider() {
      // No threading concerns because final
      return dataProvider_;
   }

   @Override
   public void setDisplayPosition(Coords position) {
      // TODO Make the version with forceRedisplay also official
      setDisplayPosition(position, true);
   }

   public void setDisplayPosition(Coords position, boolean forceRedisplay) {
      // TODO Convert to event bus event in AbstractDataViewer, like settings
      synchronized (this) {
         if (!forceRedisplay && getDisplayPosition().equals(position)) {
            return;
         }
         super.setDisplayPosition(position);
         handleNewDisplayPosition(position);
      }
   }

   @Override
   public List<Image> getDisplayedImages() {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean isClosed() {
      if (!SwingUtilities.isEventDispatchThread()) {
         RunnableFuture<Boolean> edtFuture = new FutureTask(
               new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
               return isClosed();
            }
         });
         SwingUtilities.invokeLater(edtFuture);
         try {
            return edtFuture.get();
         }
         catch (InterruptedException notUsedByUs) {
            Thread.currentThread().interrupt();
            return isClosed();
         }
         catch (ExecutionException unexpected) {
            throw new RuntimeException(unexpected);
         }
      }

      return (uiController_ == null);
   }

   @Override
   public String getName() {
      // TODO
      return "NAME-TODO";
   }

   @Override
   public void displayStatusString(String status) {
      throw new UnsupportedOperationException();
   }

   @Override
   public double getZoom() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void setZoom(double ratio) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void adjustZoom(double factor) {
      throw new UnsupportedOperationException();
   }

   @Override
   public void autostretch() {
      throw new UnsupportedOperationException();
   }

   @Override
   public ImagePlus getImagePlus() {
      if (!SwingUtilities.isEventDispatchThread()) {
         RunnableFuture<ImagePlus> edtFuture = new FutureTask(
               new Callable<ImagePlus>() {
            @Override
            public ImagePlus call() throws Exception {
               return getImagePlus();
            }
         });
         SwingUtilities.invokeLater(edtFuture);
         try {
            return edtFuture.get();
         }
         catch (InterruptedException notUsedByUs) {
            Thread.currentThread().interrupt();
            return getImagePlus();
         }
         catch (ExecutionException unexpected) {
            throw new RuntimeException(unexpected);
         }
      }

      return uiController_.getIJImagePlus();
   }

   @Override
   public boolean requestToClose() {
      if (!SwingUtilities.isEventDispatchThread()) {
         RunnableFuture<Boolean> edtFuture = new FutureTask(
               new Callable<Boolean>() {
            @Override
            public Boolean call() throws Exception {
               return requestToClose();
            }
         });
         SwingUtilities.invokeLater(edtFuture);
         try {
            return edtFuture.get();
         }
         catch (InterruptedException notUsedByUs) {
            Thread.currentThread().interrupt();
            return requestToClose();
         }
         catch (ExecutionException unexpected) {
            throw new RuntimeException(unexpected);
         }
      }

      /* TODO
      DataViewerDelegate delegate = firstDelegate_;
      do {
         boolean okToClose = delegate.canCloseViewer(this);
         if (!okToClose) {
            return false;
         }
         delegate = delegate.getNextViewerDelegate(this);
      } while (delegate != null);
      forceClose();
      return true;
      */
      forceClose(); // Temporary
      return true;
   }

   @Override
   public void forceClose() {
      uiController_.close();
      uiController_ = null;
      closeCompleted_ = true;
   }

   @Override
   public void setFullScreen(boolean enable) {
      throw new UnsupportedOperationException();
   }

   @Override
   public boolean isFullScreen() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void toggleFullScreen() {
      throw new UnsupportedOperationException();
   }

   @Override
   public DisplayWindow duplicate() {
      throw new UnsupportedOperationException();
   }

   @Override
   public void toFront() {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               toFront();
            }
         });
      }

      if (uiController_ == null) {
         return;
      }
      uiController_.toFront();
   }

   @Override
   public Window getWindow() throws IllegalStateException {
      if (!SwingUtilities.isEventDispatchThread()) {
         RunnableFuture<Window> edtFuture = new FutureTask(
               new Callable<Window>() {
            @Override
            public Window call() throws Exception {
               return getWindow();
            }
         });
         SwingUtilities.invokeLater(edtFuture);
         try {
            return edtFuture.get();
         }
         catch (InterruptedException notUsedByUs) {
            Thread.currentThread().interrupt();
            return getWindow();
         }
         catch (ExecutionException e) {
            if (e.getCause() instanceof IllegalStateException) {
               throw (IllegalStateException) e.getCause();
            }
            throw new RuntimeException(e);
         }
      }

      if (uiController_ == null) {
         throw new IllegalStateException(
               "Display has closed; no window available");
      }
      return uiController_.getFrame();
   }

   @Override
   public void setCustomTitle(String title) {
      // TODO
   }
}