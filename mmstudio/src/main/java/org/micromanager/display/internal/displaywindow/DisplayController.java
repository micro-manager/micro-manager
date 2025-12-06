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

package org.micromanager.display.internal.displaywindow;


import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import ij.ImagePlus;
import java.awt.Window;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import javax.swing.SwingUtilities;
import org.micromanager.Studio;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.DataProviderHasNewNameEvent;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreClosingEvent;
import org.micromanager.data.DatastoreFrozenEvent;
import org.micromanager.data.Image;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.DisplayWindowControlsFactory;
import org.micromanager.display.inspector.internal.panels.intensity.ImageStatsPublisher;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.RememberedDisplaySettings;
import org.micromanager.display.internal.animate.AnimationController;
import org.micromanager.display.internal.animate.DataCoordsAnimationState;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeInvisibleEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeVisibleEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.display.internal.event.DefaultDisplayDidShowImageEvent;
import org.micromanager.display.internal.event.DisplayWindowDidAddOverlayEvent;
import org.micromanager.display.internal.event.DisplayWindowDidRemoveOverlayEvent;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImageStatsRequest;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.display.internal.imagestats.StatsComputeQueue;
import org.micromanager.display.internal.link.LinkManager;
import org.micromanager.display.internal.link.internal.DefaultLinkManager;
import org.micromanager.display.overlay.Overlay;
import org.micromanager.display.overlay.OverlayListener;
import org.micromanager.internal.utils.CoalescentEDTRunnablePool;
import org.micromanager.internal.utils.CoalescentEDTRunnablePool.CoalescentRunnable;
import org.micromanager.internal.utils.MustCallOnEDT;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.performance.PerformanceMonitor;

/**
 * Main controller for the standard image viewer.
 * This is also the implementation for the DisplayWindow API, for now (it might
 * make sense to refactor and separate API support from controller
 * implementation).
 *
 * @author Mark A. Tsuchida, parts refactored from code by Chris Weisiger
 */
public final class DisplayController extends DisplayWindowAPIAdapter
      implements DisplayWindow,
      ImageStatsPublisher,
      DataCoordsAnimationState.CoordsProvider,
      AnimationController.Listener<Coords>,
      StatsComputeQueue.Listener,
      OverlayListener {
   private final Studio studio_;
   private final DataProvider dataProvider_;

   // The actually painted images. Accessed only on EDT.
   private ImagesAndStats displayedImages_;

   // Accessed only on EDT
   private long latestStatsSeqNr_ = -1;

   // Not final but set only upon creation
   private AnimationController<Coords> animationController_;

   private final Set<String> playbackAxes_ = new HashSet<>();

   private final StatsComputeQueue computeQueue_ = StatsComputeQueue.create();

   // Adaptive display throttling - reduce display rate during high-speed acquisition
   // to minimize resource consumption and prevent circular buffer overflows
   private static final long BASE_REPAINT_PERIOD_NS = Math.round(1e9 / 60.0); // 60 FPS normal
   private static final long HIGH_SPEED_REPAINT_PERIOD_NS =
            Math.round(1e9 / 30.0); // 30 FPS high-speed
   private static final long VERY_HIGH_SPEED_REPAINT_PERIOD_NS =
            Math.round(1e9 / 15.0); // 15 FPS very high-speed

   // Thresholds for switching display rates based on estimated camera FPS
   private static final double HIGH_SPEED_THRESHOLD_FPS = 30.0;
   private static final double VERY_HIGH_SPEED_THRESHOLD_FPS = 60.0;

   private final LinkManager linkManager_;

   // The UI controller manages the actual JFrame and all the components in it,
   // including interaction with ImageJ. After being closed, set to null.
   // Must access on EDT
   private DisplayUIController uiController_;

   // The key for the display profile. This is used to save
   // display settings for this display window.
   private String profileKey_ = "DefaultDisplayProfile";
   // The key for the display window. This is used to save
   // the display window's position and size.
   private String windowKey_ = "DefaultDisplayWindow";

   private final Object selectionLock_ = new Object();
   private BoundsRectAndMask selection_ = BoundsRectAndMask.unselected();

   private final List<Overlay> overlays_ = new ArrayList<>();

   private final TreeMap<Integer, DataViewerListener> listeners_ =
         new TreeMap<>();

   // A way to know from a non-EDT thread that the display has definitely
   // closed (may not be true for a short period after closing)
   // Guarded by monitor on itself
   private volatile boolean closeCompleted_;
   private final Object closeGuard_ = new Object();

   private DisplayWindowControlsFactory controlsFactory_;

   private CoalescentEDTRunnablePool runnablePool_ =
         CoalescentEDTRunnablePool.create();

   // Track pending display runnables to prevent memory accumulation
   private final AtomicInteger pendingDisplayRunnables_ = new AtomicInteger(0);
   // Maximum number of pending display runnables to prevent memory accumulation.
   // Set to 2 to enable aggressive frame dropping at high frame rates - ensures
   // display shows newest frames instead of accumulating old ones.
   // Works with AnimationController coalescence to keep display current.
   private static final int MAX_PENDING_DISPLAYS = 2;

   // Track when counter has been stuck at maximum to enable recovery.
   // If counter stays at MAX for more than COUNTER_RESET_TIMEOUT_NS,
   // we force a reset to allow at least one display update through.
   // Using AtomicLong for thread-safe compareAndSet operations.
   private final AtomicLong counterMaxSinceNs_ = new AtomicLong(0);
   // 100ms timeout (reduced from 500ms)
   private static final long COUNTER_RESET_TIMEOUT_NS = 100_000_000L;

   // Track image arrival times to estimate camera FPS for adaptive throttling
   private static final int IMAGE_TIMING_WINDOW_SIZE = 10;
   private final long[] imageArrivalTimes_ = new long[IMAGE_TIMING_WINDOW_SIZE];
   private int imageTimingIndex_ = 0;
   private volatile double estimatedCameraFps_ = 0.0;

   private PerformanceMonitor perfMon_
         = PerformanceMonitor.createWithTimeConstantMs(1000.0);

   // Single-threaded executor for display position updates to prevent blocking
   // AnimationController's scheduler thread with disk I/O operations
   private final java.util.concurrent.ExecutorService displayPositionExecutor_ =
         java.util.concurrent.Executors.newSingleThreadExecutor(
               org.micromanager.internal.utils.ThreadFactoryFactory
                     .createThreadFactory("Display Position Processor"));

   // Track pending display position for coalescence - only process latest position
   private final java.util.concurrent.atomic.AtomicReference<Coords> pendingDisplayPosition_ =
         new java.util.concurrent.atomic.AtomicReference<>(null);

   // Track if display position processor is currently running
   private final java.util.concurrent.atomic.AtomicBoolean displayPositionProcessing_ =
         new java.util.concurrent.atomic.AtomicBoolean(false);


   //This static counter makes sure that each object has it's own unique id during runtime.
   private static final AtomicInteger counter = new AtomicInteger();
   private final Integer uid = counter.getAndIncrement();

   @Override
   public void addListener(DataViewerListener listener, int priority) {
      int tmpPriority = priority;
      synchronized (listeners_) {
         while (listeners_.containsKey(tmpPriority)) {
            tmpPriority += 1;
         }
         listeners_.put(tmpPriority, listener);
      }
   }

   @Override
   public void removeListener(DataViewerListener listener) {
      // Collect keys to remove (can't remove during iteration)
      List<Integer> keysToRemove = new ArrayList<>();

      // Copy entrySet to avoid ConcurrentModificationException
      synchronized (listeners_) {
         Set<Map.Entry<Integer, DataViewerListener>> entriesCopy =
                  new HashSet<>(listeners_.entrySet());
         for (Map.Entry<Integer, DataViewerListener> entry : entriesCopy) {
            if (listener.equals(entry.getValue())) {
               keysToRemove.add(entry.getKey());
            }
         }

         // Remove collected keys
         for (Integer key : keysToRemove) {
            listeners_.remove(key);
         }
      }
   }

   /**
    * Builder for DisplayController.
    */
   public static class Builder {
      private DataProvider dataProvider_;
      private DisplaySettings displaySettings_;
      private LinkManager linkManager_ = DefaultLinkManager.create();
      private DisplayWindowControlsFactory controlsFactory_;

      public Builder(DataProvider dataProvider) {
         Preconditions.checkNotNull(dataProvider);
         dataProvider_ = dataProvider;
      }

      /**
       * Sets DataProvider for this DisplayController Builder.
       *
       * @param provider DataProvider for this display
       * @return ourselves, (to enable chaining).
       */
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

      public Builder linkManager(LinkManager manager) {
         linkManager_ = manager;
         return this;
      }

      public Builder controlsFactory(DisplayWindowControlsFactory factory) {
         controlsFactory_ = factory;
         return this;
      }

      public Builder displaySettings(DisplaySettings displaySettings) {
         displaySettings_ = displaySettings;
         return this;
      }

      @MustCallOnEDT
      public DisplayController build(Studio studio) {
         return DisplayController.create(studio, this);
      }
   }

   @MustCallOnEDT
   private static DisplayController create(Studio studio, Builder builder) {
      DisplaySettings initialDisplaySettings = builder.displaySettings_;
      if (initialDisplaySettings == null) {
         initialDisplaySettings = RememberedDisplaySettings.loadDefaultDisplaySettings(
               studio,
               builder.dataProvider_.getSummaryMetadata());
      }
      if (initialDisplaySettings == null) {
         initialDisplaySettings = DefaultDisplaySettings.builder().build();
      }

      final DisplayController instance =
            new DisplayController(studio, builder.dataProvider_,
                  initialDisplaySettings, builder.controlsFactory_,
                  builder.linkManager_);
      instance.initialize();

      instance.computeQueue_.addListener(instance);

      if (instance.dataProvider_.getNumImages() > 0) {
         Coords.Builder startPosition = Coordinates.builder();
         Coords.Builder endPosition = Coordinates.builder();
         for (String axis : instance.dataProvider_.getAxes()) {
            endPosition.index(axis, instance.dataProvider_.getNextIndex(axis) - 1);
         }
         instance.setDisplayPosition(startPosition.build());

         // this is needed for the display to know the initial number of axis.
         // important when reading data from disk
         instance.animationAcknowledgeDataPosition(endPosition.build());
      }

      return instance;
   }

   private DisplayController(Studio studio,
                             DataProvider dataProvider,
                             DisplaySettings initialDisplaySettings,
                             DisplayWindowControlsFactory controlsFactory,
                             LinkManager linkManager) {
      super(initialDisplaySettings);
      studio_ = studio;
      dataProvider_ = dataProvider;
      controlsFactory_ = controlsFactory;
      linkManager_ = linkManager;

      computeQueue_.setPerformanceMonitor(perfMon_);

      ReportingUtils.logMessage("Class: " + this.getClass());
      ReportingUtils.logMessage("Classloader: " + this.getClass().getClassLoader());
   }

   @MustCallOnEDT
   private void initialize() {
      // Initialize some things that would leak 'this' if done in the
      // constructor
      DataCoordsAnimationState animationState = DataCoordsAnimationState.create(this);
      animationController_ = AnimationController.create(animationState);
      animationController_.setPerformanceMonitor(perfMon_);
      animationController_.addListener(this);

      uiController_ = DisplayUIController.create(studio_, this, controlsFactory_,
            animationController_);
      uiController_.setPerformanceMonitor(perfMon_);
      // TODO Make sure frame controller forwards messages to us (e.g.
      // windowClosing() -> requestToClose())

      // Start receiving events
      studio_.events().registerForEvents(this);
      dataProvider_.registerForEvents(this);
   }

   // Allow internal objects (in particular, UI controller) to post events
   void postDisplayEvent(Object event) {
      postEvent(event);
   }

   @MustCallOnEDT
   public DisplayUIController getUIController() {
      return uiController_;
   }

   LinkManager getLinkManager() {
      return linkManager_;
   }

   @MustCallOnEDT
   private void setFrameVisible(boolean visible) {
      uiController_.setVisible(visible);
      // TODO Post these events based on ComponentListener on the JFrame
      postEvent(visible
            ? DataViewerDidBecomeVisibleEvent.create(this)
            : DataViewerDidBecomeInvisibleEvent.create(this));
   }

   void frameDidBecomeActive() {
      postEvent(DataViewerDidBecomeActiveEvent.create(this));
   }

   @Override
   public void show() {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(this::show);
      }
      setFrameVisible(true);
      toFront();
   }


   //
   // Scheduling images for display
   //

   @Override
   public long imageStatsReady(ImagesAndStats stats) {
      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("Image stats ready");
      }

      scheduleDisplayInUI(stats);

      // Return adaptive throttle period based on camera speed
      long throttleNs = getAdaptiveRepaintPeriodNs();
      return throttleNs;
   }

   /**
    * Calculate estimated camera frame rate from recent image arrivals.
    * Used for adaptive display throttling during high-speed acquisition.
    *
    * @return Estimated camera FPS, or 0 if insufficient data
    */
   private double calculateCameraFps() {
      // Need at least 3 samples to calculate FPS
      if (imageTimingIndex_ < 3) {
         return 0.0;
      }

      // Calculate time span across the window
      int count = Math.min(imageTimingIndex_, IMAGE_TIMING_WINDOW_SIZE);
      long oldest = imageArrivalTimes_[0];
      long newest = imageArrivalTimes_[(imageTimingIndex_ - 1 + IMAGE_TIMING_WINDOW_SIZE)
                                        % IMAGE_TIMING_WINDOW_SIZE];

      if (newest <= oldest) {
         return 0.0; // Invalid timing data
      }

      long spanNs = newest - oldest;
      if (spanNs == 0) {
         return 0.0;
      }

      // FPS = (count - 1) / time_span_in_seconds
      double spanSeconds = spanNs / 1e9;
      return (count - 1) / spanSeconds;
   }

   /**
    * Get display throttle period based on current camera acquisition speed.
    * Automatically reduces display rate during high-speed acquisition to
    * minimize resource consumption and prevent circular buffer overflows.
    *
    * @return Minimum period in nanoseconds between display updates
    */
   private long getAdaptiveRepaintPeriodNs() {
      double cameraFps = estimatedCameraFps_;

      if (cameraFps > VERY_HIGH_SPEED_THRESHOLD_FPS) {
         // Very high speed (>60 FPS): Display at 15 FPS to minimize overhead
         return VERY_HIGH_SPEED_REPAINT_PERIOD_NS;
      } else if (cameraFps > HIGH_SPEED_THRESHOLD_FPS) {
         // High speed (30-60 FPS): Display at 30 FPS for balance
         return HIGH_SPEED_REPAINT_PERIOD_NS;
      } else {
         // Normal speed (<30 FPS): Display at full 60 FPS
         return BASE_REPAINT_PERIOD_NS;
      }
   }

   private void scheduleDisplayInUI(final ImagesAndStats images) {
      Preconditions.checkArgument(images.getRequest().getNumberOfImages() > 0);

      // Check if too many display runnables are pending to prevent memory buildup
      int currentPending = pendingDisplayRunnables_.get();

      if (currentPending >= MAX_PENDING_DISPLAYS) {
         long now = System.nanoTime();

         // Thread-safe: Try to set timeout start time if not already set
         // compareAndSet ensures only one thread sets the initial timeout value
         long expectedZero = 0;
         counterMaxSinceNs_.compareAndSet(expectedZero, now);

         // Read the actual start time (either our value or another thread's)
         long stuckSince = counterMaxSinceNs_.get();

         // If counter has been at max for too long, reset it to allow recovery
         if (now - stuckSince > COUNTER_RESET_TIMEOUT_NS) {
            // Diagnostic logging for stuck counter
            ReportingUtils.logMessage("WARNING: Display counter stuck at " + currentPending
                  + " for " + TimeUnit.NANOSECONDS.toMillis(now - stuckSince)
                  + "ms - forcing reset. This may indicate EDT issues.");

            if (perfMon_ != null) {
               perfMon_.sampleTimeInterval("Display counter forced reset after timeout");
               perfMon_.sample("Pending count at timeout", currentPending);
               perfMon_.sample("Display counter forced reset after timeout", 1);
            }
            // Force reset to allow at least one display update through
            pendingDisplayRunnables_.set(MAX_PENDING_DISPLAYS - 1);
            counterMaxSinceNs_.set(0);  // Reset for next potential stuck state
         } else {
            if (perfMon_ != null) {
               perfMon_.sampleTimeInterval("Display scheduling skipped - queue full");
            }
            return;  // Skip this display update to prevent memory accumulation
         }
      } else {
         // Reset the timeout tracking when counter is below max
         counterMaxSinceNs_.set(0);
      }

      int newPending = pendingDisplayRunnables_.incrementAndGet();

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

      // Use invokeLaterWithCoalescence instead of invokeAsLateAsPossibleWithCoalescence
      // to avoid EDT congestion from skip-count callbacks during high-speed acquisition.
      // Regular coalescing is sufficient since MIN_REPAINT_PERIOD_NS throttles display rate.
      runnablePool_.invokeLaterWithCoalescence(new CoalescentRunnable() {
         @Override
         public Class<?> getCoalescenceClass() {
            return getClass();
         }

         @Override
         public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
            // Only the most recent repaint task need be run
            if (perfMon_ != null) {
               perfMon_.sampleTimeInterval("Scheduling of repaint coalesced");
            }
            return later;
         }

         @Override
         public void run() {
            try {
               if (uiController_ == null) { // Closed
                  return;
               }

               // Diagnostic logging for EDT congestion monitoring
               if (perfMon_ != null) {
                  perfMon_.sampleTimeInterval("Display runnable started on EDT");
                  perfMon_.sample("Pending display count at start", pendingDisplayRunnables_.get());
               }

               Image primaryImage = images.getRequest().getImage(0);
               Coords nominalCoords = images.getRequest().getNominalCoords();
               if (nominalCoords.hasAxis(Coords.CHANNEL)) {
                  int channel = nominalCoords.getChannel();
                  for (Image image : images.getRequest().getImages()) {
                     if (image.getCoords().hasAxis(Coords.CHANNEL)
                           && image.getCoords().getChannel() == channel) {
                        primaryImage = image;
                        break;
                     }
                  }
               }

               boolean imagesDiffer = true;
               if (displayedImages_ != null
                     && images.getRequest().getNumberOfImages()
                     == displayedImages_.getRequest().getNumberOfImages()) {
                  imagesDiffer = false;
                  for (int i = 0; i < images.getRequest().getNumberOfImages(); ++i) {
                     if (images.getRequest().getImage(i)
                           != displayedImages_.getRequest().getImage(i)) {
                        imagesDiffer = true;
                        break;
                     }
                  }
               }

               if (perfMon_ != null) {
                  perfMon_.sample("Scheduling identical images (%)", imagesDiffer ? 0.0 : 100.0);
               }
               if (imagesDiffer || getDisplaySettings().isAutostretchEnabled()
                     || getDisplaySettings().getColorMode()
                     != DisplaySettings.ColorMode.COMPOSITE) {
                  uiController_.displayImages(images);
               } else if (getDisplaySettings().getColorMode()
                     == DisplaySettings.ColorMode.COMPOSITE) {
                  // in composite mode, keep the channel name in sync with the
                  // channel set by the slider.  It would be even better to
                  // disable the channel slider and display the names of all
                  // channels, but that becomes very hacky
                  uiController_.updateSliders(images);
                  uiController_.setImageInfoLabel(images);
               }

               postEvent(DefaultDisplayDidShowImageEvent.create(
                     DisplayController.this,
                     images.getRequest().getImages(),
                     primaryImage));

               if (images.getStatsSequenceNumber() > latestStatsSeqNr_) {
                  postEvent(ImageStatsChangedEvent.create(images));
                  latestStatsSeqNr_ = images.getStatsSequenceNumber();
               }
               displayedImages_ = images;

               if (perfMon_ != null) {
                  perfMon_.sampleTimeInterval("Scheduled repaint on EDT");
               }
            } catch (Exception e) {
               // Log exception but don't rethrow - let EDT continue
               ReportingUtils.logError(e, "Exception in display runnable - EDT protected");

               if (perfMon_ != null) {
                  perfMon_.sampleTimeInterval("Display runnable exception caught");
               }
            } finally {
               // Decrement counter to allow new display updates
               int newCount = pendingDisplayRunnables_.decrementAndGet();

               // Safety check: if counter was stuck at max but we're completing,
               // ensure timeout marker is reset to prevent permanent stuck state
               if (newCount == MAX_PENDING_DISPLAYS - 1) {
                  counterMaxSinceNs_.set(0);
               }
            }
         }
      });
   }

   @Override
   @MustCallOnEDT
   public ImagesAndStats getCurrentImagesAndStats() {
      return displayedImages_;
   }

   /**
    * Return the DisplayIntervalQuantile.  If anyone knows what that actually is,
    * please update this documentation.
    *
    * @param q I don't know what this is.
    * @return The mysterious DisplayIntervalQuantile.
    */
   public double getDisplayIntervalQuantile(double q) {
      if (uiController_ == null) {
         return 0.0;
      }
      return uiController_.getDisplayIntervalQuantile(q);
   }

   /**
    * Resets the estimate when another display event can/should happen.
    */
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
      return dataProvider_.getNextIndex(axis);
   }

   @Override
   public boolean coordsExist(Coords c) {
      return dataProvider_.hasImage(c);
   }

   @Override
   public Collection<String> getAnimatedAxes() {
      synchronized (playbackAxes_) {
         return new ArrayList<>(playbackAxes_);
      }
   }

   @Override
   protected DisplaySettings handleDisplaySettings(
         DisplaySettings requestedSettings) {
      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("Handle display settings");
      }

      final DisplaySettings oldSettings = getDisplaySettings();
      final DisplaySettings adjustedSettings = requestedSettings;
      // We don't currently make any adjustments (normalizations) to the
      // settings, but we probably should check that they are consistent.

      if (adjustedSettings.isROIAutoscaleEnabled() != oldSettings.isROIAutoscaleEnabled()) {
         // We can't let this coalesce. No need to run on EDT but it's just as
         // good a thread as any.
         SwingUtilities.invokeLater(() -> {
            Coords pos;
            do {
               pos = getDisplayPosition();
            } while (!compareAndSetDisplayPosition(pos, pos, true));
         });
      }

      runnablePool_.invokeLaterWithCoalescence(new CoalescentRunnable() {
         @Override
         public Class<?> getCoalescenceClass() {
            return getClass();
         }

         @Override
         public CoalescentRunnable coalesceWith(CoalescentRunnable later) {
            return later;
         }

         @Override
         public void run() {
            if (uiController_ == null) {
               return;
            }

            uiController_.applyDisplaySettings(adjustedSettings);
            ((DefaultDisplaySettings) adjustedSettings).saveToProfile(profileKey_);
         }
      });
      return adjustedSettings;
   }

   @Override
   protected Coords handleDisplayPosition(Coords position) {
      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("Handle display position");
      }

      // Always compute stats for all channels
      List<Image> images;
      try {
         images = dataProvider_.getImagesIgnoringAxes(
               position.copyRemovingAxes(Coords.CHANNEL),
               Coords.CHANNEL);
      } catch (IOException e) {
         // TODO Should display error
         images = Collections.emptyList();
      }


      // Handle missing images.
      // TODO: provide user interface so that user can request to enable/disable
      // "filling in Z slices" and "filling in time points".
      //
      // If 'images' is empty, search first in Z, then in time, until at least
      // one channel has an image (Q: or just leave empty when no channel has
      // an image?). Then, for missing channels, first search for nearest Z in
      // either direction. For channels still missing, search back in time,
      // looking for the nearest available slice in each time point.
      // Record the substitutions made (requested and provided coords) in to
      // the ImageStatsRequest! Then display coords of actual displayed image(s)
      // The substitution strategy should be pluggable. ("MissingImageStrategy"),
      // and we should always go through it (default = no substitution)
      // XXX On the other hand, we do NOT want to fill in images if they are
      // about to be acquired! How do we know if this is the case?
      // During acquisition, do not search back in time if newest timepoint.
      // During acquisition, do not search in Z if newest timepoint.


      // CRITICAL FIX: Skip image filling during HIGH-SPEED acquisition to avoid lock contention
      // The condition check calls dataProvider_.getNextIndex() which can block on locks
      // held by the acquisition thread writing images at very high speed (>30 FPS).
      // During high-speed acquisition, it's better to display whatever images we have rather
      // than block waiting for locks to check for missing images.
      // At slower speeds (<30 FPS), the image filling loop is safe and useful.
      boolean isLiveAcquisition = false;
      try {
         isLiveAcquisition = studio_.acquisitions().isAcquisitionRunning();
      } catch (Exception e) {
         studio_.logs().logError(e, "Failed to query acquisition status");
      }

      boolean isHighSpeedAcquisition =
               isLiveAcquisition && estimatedCameraFps_ > HIGH_SPEED_THRESHOLD_FPS;

      // Skip image filling loop during high-speed acquisition to avoid lock contention
      if (!isHighSpeedAcquisition || images.size() == 0) {
         try {
            if (images.size() != dataProvider_.getNextIndex(Coords.CHANNEL)
                    && (!isLiveAcquisition
                    || position.getT() < dataProvider_.getNextIndex(Coords.T) - 1)) {

               for (int c = 0; c < dataProvider_.getNextIndex(Coords.CHANNEL); c++) {
                  Coords.CoordsBuilder cb = position.copyBuilder();
                  Coords targetCoord = cb.channel(c).build();
                  CHANNEL_SEARCH:
                  if (!dataProvider_.hasImage(targetCoord)) {
                     // c is missing, first look in z
                     int zOffset = 1;
                     while (position.getZ() - zOffset > -1
                             || position.getZ() + zOffset <= dataProvider_.getNextIndex(Coords.Z)) {
                        Coords testPosition = cb.z(position.getZ() - zOffset).build();
                        if (dataProvider_.hasImage(testPosition)) {
                           images.add(dataProvider_.getImage(testPosition)
                                    .copyAtCoords(targetCoord));
                           break CHANNEL_SEARCH;
                        }
                        testPosition = cb.z(position.getZ() + zOffset).build();
                        if (dataProvider_.hasImage(testPosition)) {
                           images.add(dataProvider_.getImage(testPosition)
                                    .copyAtCoords(targetCoord));
                           break CHANNEL_SEARCH;
                        }
                        zOffset++;
                     }
                     // not found in z, now look backwards in time
                     cb = targetCoord.copyBuilder();
                     for (int t = position.getT(); t > -1; t--) {
                        Coords testPosition = cb.time(t).build();
                        if (dataProvider_.hasImage(testPosition)) {
                           images.add(dataProvider_.getImage(testPosition)
                                    .copyAtCoords(targetCoord));
                           break;
                        }
                     }
                  }
               }
            }
         } catch (IOException e) {
            // TODO Should display error
            images = Collections.emptyList();
            studio_.logs().showError(e, "Error reading image from data provider");
         }
      }


      // Images are sorted by channel here, since we don't (yet) have any other
      // way to correctly recombine stats with newer images (when update rate
      // is finite).
      if (images.size() > 1) {
         images.sort((Image o1, Image o2) ->
                  Integer.compare(o1.getCoords().getChannel(), o2.getCoords().getChannel()));
      }

      BoundsRectAndMask selection = BoundsRectAndMask.unselected();
      if (getDisplaySettings().isROIAutoscaleEnabled()) {
         synchronized (selectionLock_) {
            selection = selection_;
         }
      }

      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("Submitting compute request");
      }
      computeQueue_.submitRequest(ImageStatsRequest.create(position,
            images,
            selection));

      return position;
   }


   //
   // Implementation of AnimationController.Listener<Coords>
   //
   @Override
   public void animationShouldDisplayDataPosition(Coords position) {
      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("Coords from animation controller");
      }

      // CRITICAL: This callback runs on AnimationController's scheduler thread.
      // We must NOT do blocking I/O here (like reading images from disk in handleDisplayPosition)
      // because that would block the scheduler thread and freeze the entire animation pipeline.
      //
      // We use coalescence to ensure only the LATEST position is processed, skipping
      // intermediate positions when disk I/O is slower than the update rate.

      // Store the latest position (coalescence - overwrites any pending position)
      pendingDisplayPosition_.set(position);

      // If not already processing, start the processing loop
      if (displayPositionProcessing_.compareAndSet(false, true)) {
         displayPositionExecutor_.submit(() -> {
            try {
               // Process positions in a loop until no more pending
               while (true) {
                  // Get and clear the pending position atomically
                  Coords positionToDisplay = pendingDisplayPosition_.getAndSet(null);
                  if (positionToDisplay == null) {
                     break; // No more pending positions
                  }


                  // We do not skip handling this position even if it equals the current
                  // position, because the image data may have changed (e.g. if we have
                  // been displaying a position that didn't yet have an image, or if this
                  // is a special datastore such as the one used for snap/live preview).

                  // Set the "official" position of this data viewer
                  setDisplayPosition(positionToDisplay, true);
               }
            } finally {
               // Mark as not processing
               displayPositionProcessing_.set(false);

               // Check if a new position arrived while we were marking as not processing
               if (pendingDisplayPosition_.get() != null) {
                  // Restart processing if needed
                  if (displayPositionProcessing_.compareAndSet(false, true)) {
                     displayPositionExecutor_.submit(this::processDisplayPositions);
                  }
               }
            }
         });
      }
   }

   private void processDisplayPositions() {
      // This is a workaround to allow recursive submission - see finally block above
      try {
         while (true) {
            Coords positionToDisplay = pendingDisplayPosition_.getAndSet(null);
            if (positionToDisplay == null) {
               break;
            }
            setDisplayPosition(positionToDisplay, true);
         }
      } finally {
         displayPositionProcessing_.set(false);
         if (pendingDisplayPosition_.get() != null) {
            if (displayPositionProcessing_.compareAndSet(false, true)) {
               displayPositionExecutor_.submit(this::processDisplayPositions);
            }
         }
      }
   }

   @Override
   public void animationAcknowledgeDataPosition(final Coords position) {
      // Tell the UI controller to expand the display range to include the
      // newly seen position. But use coalescent invocation to avoid doing
      // too much on the EDT.
      runnablePool_.invokeLaterWithCoalescence(
            new ExpandDisplayRangeCoalescentRunnable(position));
   }

   @Override
   public void animationWillJumpToNewDataPosition(Coords position) {
   }

   @Override
   public void animationDidJumpToNewDataPosition(Coords position) {
      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("Animation Did Jump To New Data Position");
      }
      uiController_.setNewImageIndicator(true);
   }

   @Override
   public void animationNewDataPositionExpired() {
      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("Animation New Data Position Expired");
      }
      uiController_.setNewImageIndicator(false);
   }


   //
   // Overlay support
   //

   @Override
   public void addOverlay(final Overlay overlay) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> addOverlay(overlay));
         return;
      }

      overlays_.add(overlay);
      overlay.addOverlayListener(this);
      if (overlay.isVisible() && uiController_ != null) {
         uiController_.overlaysChanged();
      }
      postEvent(DisplayWindowDidAddOverlayEvent.create(this, overlay));
   }

   @Override
   public void removeOverlay(final Overlay overlay) {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> removeOverlay(overlay));
         return;
      }

      overlay.removeOverlayListener(this);
      overlays_.remove(overlay);
      if (overlay.isVisible() && uiController_ != null) {
         uiController_.overlaysChanged();
      }
      postEvent(DisplayWindowDidRemoveOverlayEvent.create(this, overlay));
   }

   @Override
   public List<Overlay> getOverlays() {
      if (!SwingUtilities.isEventDispatchThread()) {
         RunnableFuture<List<Overlay>> edtFuture = new FutureTask<>(this::getOverlays);
         SwingUtilities.invokeLater(edtFuture);
         try {
            return edtFuture.get();
         } catch (InterruptedException notUsedByUs) {
            Thread.currentThread().interrupt();
            return getOverlays(); // Bad
         } catch (ExecutionException unexpected) {
            throw new RuntimeException(unexpected);
         }
      }

      return new ArrayList<>(overlays_);
   }

   @Override
   public void overlayTitleChanged(Overlay overlay) {
      // Nothing to do
   }

   @Override
   public void overlayConfigurationChanged(Overlay overlay) {
      if (overlay.isVisible() && uiController_ != null) {
         uiController_.overlaysChanged();
      }
   }

   @Override
   public void overlayVisibleChanged(Overlay overlay) {
      if (uiController_ != null) {
         uiController_.overlaysChanged();
      }
   }

   //
   // Misc
   //

   /**
    * Notification from UI controller that the selection changed.
    *
    * @param selectionIn provides information about the new selection.
    */
   public void selectionDidChange(final BoundsRectAndMask selectionIn) {
      BoundsRectAndMask selection = selectionIn;
      if (selection == null) {
         selection = BoundsRectAndMask.unselected();
      }
      synchronized (selectionLock_) {
         selection_ = selection;
      }
      if (getDisplaySettings().isROIAutoscaleEnabled()) {
         // This is the thread-safe way to trigger a redisplay
         Coords pos;
         do {
            pos = getDisplayPosition();
         } while (!compareAndSetDisplayPosition(pos, pos, true));
      }
   }

   /**
    * Sets the rate at which Statistics for images will be calculated.
    *
    * @param hzIn new frequency for calculation of image statistics.
    */
   public void setStatsComputeRateHz(final double hzIn) {
      double hz = Math.max(0.0, hzIn);
      long intervalNs;
      if (hz == 0.0) {
         intervalNs = Long.MAX_VALUE;
      } else if (Double.isInfinite(hz)) {
         intervalNs = 0;
      } else {
         intervalNs = Math.round(1e9 / hz);
      }
      computeQueue_.setProcessIntervalNs(intervalNs);
   }

   /**
    * Returns the rate at which image statistics are computed.
    *
    * @return rate at which image statistics are computed.
    */
   public double getStatsComputeRateHz() {
      long intervalNs = computeQueue_.getProcessIntervalNs();
      if (intervalNs == Long.MAX_VALUE) {
         return 0.0;
      }
      if (intervalNs == 0) {
         return Double.POSITIVE_INFINITY;
      }
      return 1e9 / intervalNs;
   }

   /**
    * Sets the axes that will be animated.
    *
    * @param axes Axes to be animated.
    */
   public void setPlaybackAnimationAxes(String... axes) {
      synchronized (playbackAxes_) {
         playbackAxes_.clear();
         playbackAxes_.addAll(Arrays.asList(axes));
      }
      if (axes.length > 0) {
         resetDisplayIntervalEstimate();
         int initialTickIntervalMs = Math.max(17, Math.min(500,
               (int) Math.round(1000.0 / getPlaybackSpeedFps())));
         animationController_.setTickIntervalMs(initialTickIntervalMs);
         animationController_.startAnimation();
      } else {
         animationController_.stopAnimation();
      }
   }

   public boolean isAnimating() {
      return animationController_.isAnimating();
   }

   public double getPlaybackSpeedFps() {
      return animationController_.getAnimationRateFPS();
   }

   /**
    * Stes the animation playback speed.
    *
    * @param fps Animation playback speed in frames per second.
    */
   public void setPlaybackSpeedFps(double fps) {
      animationController_.setAnimationRateFPS(fps);
      int initialTickIntervalMs = Math.max(17, Math.min(500,
            (int) Math.round(1000.0 / fps)));
      animationController_.setTickIntervalMs(initialTickIntervalMs);
      uiController_.setPlaybackFpsIndicator(fps);
      resetDisplayIntervalEstimate();
   }

   /**
    * Sets the state of the animation lock icon.
    *
    * @param axis  Axes for which the animation lock icon should change.
    * @param state New state, either "U" (jump and stay), or "F" (Flash and Snap back),
    *              other options will result in Ignore state.
    */
   public void setAxisAnimationLock(String axis, String state) {
      // TODO This is a string-based prototype
      AnimationController.NewPositionHandlingMode newMode;
      switch (state) {
         case "U":
            newMode = AnimationController.NewPositionHandlingMode.JUMP_TO_AND_STAY;
            break;
         case "F":
            newMode = AnimationController.NewPositionHandlingMode.FLASH_AND_SNAP_BACK;
            break;
         default:
            newMode = AnimationController.NewPositionHandlingMode.IGNORE;
            break;
      }
      animationController_.setNewPositionHandlingMode(axis, newMode);
   }


   //
   // Event handlers
   //

   /**
    * A new image arrived in the Datastore.
    *
    * @param event Contains information about the newly arrived image.
    */
   @Subscribe
   public void onNewImage(final DataProviderHasNewImageEvent event) {

      // Track image arrival time for adaptive display throttling
      long now = System.nanoTime();
      imageArrivalTimes_[imageTimingIndex_] = now;
      imageTimingIndex_ = (imageTimingIndex_ + 1) % IMAGE_TIMING_WINDOW_SIZE;
      estimatedCameraFps_ = calculateCameraFps();

      if (perfMon_ != null) {
         perfMon_.sampleTimeInterval("NewImageEvent");
         perfMon_.sample("Estimated camera FPS", estimatedCameraFps_);
         perfMon_.sample("Display throttle period (ms)", getAdaptiveRepaintPeriodNs() / 1000000.0);
      }

      synchronized (closeGuard_) {
         if (closeCompleted_) {
            return;
         }
      }

      // Generally we want to display new images (if not instructed otherwise
      // by the user), but we let the animation controller coordinate that with
      // any ongoing playback animation. Actual display of new images happens
      // upon receiving callbacks via the AnimationController.Listener
      // interface.
      animationController_.newDataPosition(event.getImage().getCoords());
   }


   /**
    * A coalescent runnable to avoid excessively frequent update of the data
    * coords range in the UI.
    */
   private class ExpandDisplayRangeCoalescentRunnable
         implements CoalescentRunnable {
      private final List<Coords> coords_ = new ArrayList<>();
      // Limit accumulation to prevent EDT blocking
      private static final int MAX_COALESCED_COORDS = 100;

      ExpandDisplayRangeCoalescentRunnable(Coords coords) {
         coords_.add(coords);
      }

      @Override
      public Class<?> getCoalescenceClass() {
         return getClass();
      }

      @Override
      public CoalescentRunnable coalesceWith(CoalescentRunnable another) {
         List<Coords> otherCoords =
               ((ExpandDisplayRangeCoalescentRunnable) another).coords_;

         // Only add coords if we haven't exceeded the limit
         // This prevents a single runnable from accumulating thousands of coords
         // which would block EDT for 500ms+ when executed
         if (coords_.size() + otherCoords.size() <= MAX_COALESCED_COORDS) {
            coords_.addAll(otherCoords);
            return this;
         } else {
            // Too many coords - don't coalesce, let the other runnable execute separately
            ReportingUtils.logMessage("ExpandDisplayRangeCoalescentRunnable exceeded max coords ("
                  + coords_.size() + " + " + otherCoords.size() + "), not coalescing");
            return another;  // Return the OTHER runnable to execute it separately
         }
      }

      @Override
      public void run() {
         if (uiController_ != null) {
            uiController_.expandDisplayedRangeToInclude(coords_);
         }
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
   public List<Image> getDisplayedImages() throws IOException {
      // TODO Make sure this is accurate for composite and single-channel
      if (getDisplaySettings().getColorMode().equals(DisplaySettings.ColorMode.COMPOSITE)) {
         Coords displayPositionNoChannel = getDisplayPosition().copyRemovingAxes(Coords.CHANNEL);
         return dataProvider_.getImagesIgnoringAxes(displayPositionNoChannel, Coords.CHANNEL);
      }
      List<Image> result = new ArrayList<>();
      result.add(dataProvider_.getImage(getDisplayPosition()));
      return result;
   }

   @Override
   public boolean isVisible() {
      return !isClosed() && uiController_.getFrame().isVisible();
   }

   @Override
   public boolean isClosed() {
      return (uiController_ == null);
   }

   /**
    * Returns name assocaited with given channel index.
    *
    * @param channelIndex Index for channel (zero-based).
    * @return Channel Name, or null when not found.
    */
   public String getChannelName(int channelIndex) {
      if (dataProvider_ != null) {
         return dataProvider_.getSummaryMetadata().getSafeChannelName(channelIndex);
      }
      return null;
   }

   @Override
   public String getName() {
      //The UID ensures that each object has a unique name during runtime.
      return dataProvider_.getName() + "-" + uid;
   }


   @Override
   public void displayStatusString(String status) {
      throw new UnsupportedOperationException();
   }

   @Override
   public double getZoom() {
      return getDisplaySettings().getZoomRatio();
   }

   @Override
   public void setZoom(double ratio) {
      setDisplaySettings(getDisplaySettings().copyBuilder().zoomRatio(ratio).build());
   }

   @Override
   public void adjustZoom(double factor) {
      this.setZoom(this.getZoom() * factor);
   }

   @Override
   public void autostretch() {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> autostretch());
         return;
      }

      if (uiController_ == null) {
         return;
      }

      boolean autoStretchOn = getDisplaySettings().isAutostretchEnabled();
      if (!autoStretchOn) { // else: no need to do anything
         // since we want to immediately switch off autostretch after applying it, we can not
         // run this action coalesced, but need to do it directly.
         uiController_.applyDisplaySettings(getDisplaySettings().copyBuilder().autostretch(true)
                 .build());
         setDisplaySettings(getDisplaySettings().copyBuilder().autostretch(false).build());
      }
   }

   @Override
   @Deprecated
   public ImagePlus getImagePlus() {
      if (!SwingUtilities.isEventDispatchThread()) {
         RunnableFuture<ImagePlus> edtFuture = new FutureTask<>(
               this::getImagePlus);
         SwingUtilities.invokeLater(edtFuture);
         try {
            return edtFuture.get();
         } catch (InterruptedException notUsedByUs) {
            Thread.currentThread().interrupt();
            return getImagePlus();
         } catch (ExecutionException unexpected) {
            throw new RuntimeException(unexpected);
         }
      }

      return uiController_.getIJImagePlus();
   }

   @Override
   public boolean requestToClose() {
      if (!SwingUtilities.isEventDispatchThread()) {
         RunnableFuture<Boolean> edtFuture = new FutureTask<>(this::requestToClose);
         SwingUtilities.invokeLater(edtFuture);
         try {
            return edtFuture.get();
         } catch (InterruptedException notUsedByUs) {
            Thread.currentThread().interrupt();
            return requestToClose();
         } catch (ExecutionException unexpected) {
            throw new RuntimeException(unexpected);
         }
      }

      // Copy values to avoid ConcurrentModificationException during window close
      Collection<DataViewerListener> listenersCopy;
      synchronized (listeners_) {
         listenersCopy = new ArrayList<>(listeners_.values());
      }
      for (DataViewerListener listener : listenersCopy) {
         if (listener != null && !listener.canCloseViewer(this)) {
            return false;
         }
      }

      close();
      return true;
   }

   @Override
   public void close() {
      // close is called from DisplayUIController.windowClosing and from
      // store.requestToClose, so we need to accomodate multiple calls
      // This is a workaround a bug...
      synchronized (closeGuard_) {
         if (closeCompleted_) {
            return;
         }
         postEvent(DataViewerWillCloseEvent.create(this));

         // attempt to save Display Settings
         // TODO: Are there problems with multiple viewers on one Datastore?
         if (dataProvider_ instanceof Datastore) {
            Datastore store = (Datastore) dataProvider_;
            if (store.getSavePath() != null) {
               ((DefaultDisplaySettings) getDisplaySettings()).save(store.getSavePath());
            }
            // Since every change in ChannelDisplaySettings is already stored in
            // RememberedDisplaySettings we do not need to do it again here
         }
         dataProvider_.unregisterForEvents(this);
         try {
            computeQueue_.removeListener(this);
            computeQueue_.shutdown();
         } catch (InterruptedException ie) {
            // TODO: report exception
         }

         displayPositionExecutor_.shutdown();
         try {
            if (!displayPositionExecutor_.awaitTermination(2, TimeUnit.SECONDS)) {
               displayPositionExecutor_.shutdownNow();
               // Try again to await termination after shutdownNow
               if (!displayPositionExecutor_.awaitTermination(2, TimeUnit.SECONDS)) {
                  ReportingUtils.logError(
                           "displayPositionExecutor_ did not terminate after shutdownNow(). "
                           + "Possible resource leak.");
               }
            }
         } catch (InterruptedException ie) {
            displayPositionExecutor_.shutdownNow();
            Thread.currentThread().interrupt();
            // Try to await termination after interruption
            try {
               if (!displayPositionExecutor_.awaitTermination(2, TimeUnit.SECONDS)) {
                  ReportingUtils.logError("displayPositionExecutor_ did not terminate "
                           + "after shutdownNow() following InterruptedException. Possible "
                           + "resource leak.");
               }
            } catch (InterruptedException ie2) {
               ReportingUtils.logError("Interrupted again while waiting for "
                        + "displayPositionExecutor_ to terminate. Possible resource leak.");
               Thread.currentThread().interrupt();
            }
         }

         perfMon_ = null;
         animationController_.shutdown();
         animationController_.removeListener(this);
         animationController_ = null;
         controlsFactory_ = null;
         runnablePool_ = null;

         studio_.events().unregisterForEvents(this);

         // need to set the flag before closing the UIController,
         // otherwise we wil re-enter this function and write bad
         // display settings to file
         closeCompleted_ = true;
         if (uiController_ == null) {
            ReportingUtils.logError(
                  "DisplayController's reference to UIController is null where it shouldn't be");
         } else {
            uiController_.close();
            uiController_ = null;
         }
         dispose(); // calls AbstractDataViewer.dispose, which shuts down its eventbus.

         // TODO This event should probably be posted in response to window event
         // (which can be done with a ComponentListener for the JFrame)
         postEvent(DataViewerDidBecomeInvisibleEvent.create(this));
      }
   }

   /**
    * Event signaling that the Datastore would like to close.
    *
    * @param event signals which Datastore it is.
    */
   @Subscribe
   public void onDatastoreClosing(DatastoreClosingEvent event) {
      if (event.getDatastore().equals(dataProvider_)) {
         requestToClose();
      }
   }

   /**
    * Event signaling that the Datastore just was frozen.
    *
    * @param event This one provides nothing, hopefully from our Datastore only.
    */
   @Subscribe
   public void onDatastoreFrozenEvent(DatastoreFrozenEvent event) {
      if (uiController_ != null) {
         Coords.CoordsBuilder cb = studio_.data().coordsBuilder();
         for (String axis : dataProvider_.getAxes()) {
            cb.index(axis, dataProvider_.getNextIndex(axis) - 1);
         }
         SwingUtilities.invokeLater(() -> {
            // uiController_ may have become null in the meantime
            if (uiController_ != null) {
               uiController_.expandDisplayedRangeToInclude(cb.build());
            }
         });
      }
   }

   @Subscribe
   public void onNewDataProviderName(DataProviderHasNewNameEvent dpnne) {
      uiController_.updateTitle();
   }

   @Override
   public void setFullScreen(boolean enable) {
      if (uiController_ == null) {
         return;
      }
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> setFullScreen(enable));
      } else {
         uiController_.setFullScreenMode(enable);
      }
   }

   @Override
   public boolean isFullScreen() {
      if (uiController_ == null) {
         return false;
      }

      return uiController_.isFullScreenMode();
   }

   @Override
   @Deprecated
   public void toggleFullScreen() {
      throw new UnsupportedOperationException();
   }

   @Override
   public DisplayWindow duplicate() {
      DisplayWindow dup = studio_.displays().createDisplay(dataProvider_);
      dup.setDisplaySettings(this.getDisplaySettings());
      return dup;
   }

   @Override
   public void toFront() {
      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(this::toFront);
      }

      if (uiController_ == null) {
         return;
      }
      uiController_.toFront();
   }

   @Override
   public Window getWindow() throws IllegalStateException {
      if (!SwingUtilities.isEventDispatchThread()) {
         RunnableFuture<Window> edtFuture = new FutureTask<>(this::getWindow);
         SwingUtilities.invokeLater(edtFuture);
         try {
            return edtFuture.get();
         } catch (InterruptedException notUsedByUs) {
            Thread.currentThread().interrupt();
            return getWindow();
         } catch (ExecutionException e) {
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
      // TODO: evaulate if this is as intended
      if (dataProvider_ instanceof Datastore) {
         ((Datastore) dataProvider_).setName(title);
      }
   }

   /**
    * Set the key used to save and restore display settings to the
    * user profile for this display.
    *
    * @param key String to use as the key for saving and restoring
    */
   @Override
   public void setDisplaySettingsProfileKey(String key) {
      profileKey_ = key;
   }

   /**
    * Sets a key that will be used to remember Window position in the profile.
    *
    * @param key Key to use for positioning the DisplayWindow.
    *            if called before showing the DisplayWindow,
    *            the DisplayWindow will be positioned at the
    *            remembered position.
    */
   @Override
   public void setWindowPositionKey(String key) {
      windowKey_ = key;
      if (uiController_ != null) {
         uiController_.setWindowPositioning(key);
      }
   }


}