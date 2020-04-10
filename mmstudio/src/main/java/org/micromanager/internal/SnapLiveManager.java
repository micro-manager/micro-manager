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

package org.micromanager.internal;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.SwingUtilities;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.NewPipelineEvent;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.data.internal.DefaultRewritableDatastore;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.data.internal.StorageRAM;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.events.internal.DefaultLiveModeEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MustCallOnEDT;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.utils.ThreadFactoryFactory;
import org.micromanager.internal.utils.performance.PerformanceMonitor;
import org.micromanager.internal.utils.performance.gui.PerformanceMonitorUI;
import org.micromanager.quickaccess.internal.QuickAccessFactory;
import org.micromanager.display.DisplayWindowControlsFactory;
import org.micromanager.display.internal.RememberedSettings;
import org.micromanager.events.internal.MouseMovesStageStateChangeEvent;
import org.micromanager.internal.navigation.UiMovesStageManager;

/**
 * This class is responsible for all logic surrounding live mode and the
 * "snap image" display (which is the same display as that used for live mode).
 *
 * @author Chris Weisiger and Mark A. Tsuchida
 */
public final class SnapLiveManager extends DataViewerListener 
        implements org.micromanager.SnapLiveManager {
   private static final String TITLE = "Preview";

   private static final double MIN_GRAB_DELAY_MS = 1000.0 / 60.0;
   private static final double MAX_GRAB_DELAY_MS = 300.0;

   // What quantile of actual paint interval to use as the interval for image
   // retrieval. Too high will cause display rate to take a long time to climb
   // up to optimum. Too low can cause jittery display due to frames being
   // skipped.
   private static final double DISPLAY_INTERVAL_ESTIMATE_Q = 0.25;

   private final MMStudio mmStudio_;
   private final CMMCore core_;
   private final UiMovesStageManager uiMovesStageManager_;
   private DisplayController display_;
   private DefaultRewritableDatastore store_;
   private Pipeline pipeline_;
   private final Object pipelineLock_ = new Object();
   private boolean isLiveOn_ = false;
   private final Object liveModeLock_ = new Object();
   private int numCameraChannels_ = -1;
   private boolean shouldForceReset_ = true;
   private boolean amStartingSequenceAcquisition_ = false;

   private final List<DefaultImage> lastImageForEachChannel_ = new ArrayList<>();

   private final ScheduledExecutorService scheduler_ =
         Executors.newSingleThreadScheduledExecutor(
               ThreadFactoryFactory.createThreadFactory("SnapLiveManager"));
   // Guarded by monitor on this
   private ScheduledFuture<?> scheduledGrab_;
   // Counter for live acquisitions started, needed to synchronize across
   // a stopped and rapidly restarted run of live mode.
   // Guarded by monitor on this
   private long liveModeStartCount_ = 0;

   // As a (significant) convenience to our clients, we allow live mode to be
   // "suspended" and unsuspended, which amounts to briefly turning live mode
   // off if it is on, and then later turning it back on if it was on when
   // suspended. This gets unexpectedly complicated See setSuspended().
   private int suspendCount_ = 0;

   private final PerformanceMonitor perfMon_ =
         PerformanceMonitor.createWithTimeConstantMs(1000.0);
   private final PerformanceMonitorUI pmUI_ =
         PerformanceMonitorUI.create(perfMon_, "SnapLiveManager Performance");
   private DisplayInfo displayInfo_;
   private final Object displayInfoLock_;
   
   private class DisplayInfo {
      private int width_;
      private int height_;
      private int numComponents_;
      private int bytesPerPixel_;
      final private Map<Integer, Long> imageNumber_ = new HashMap<>();
      

      public int getWidth() { return width_; }
      public int getHeight() { return height_; }
      public int getNumComponents() { return numComponents_; }
      public int getBytesPerPixel() { return bytesPerPixel_; }
      public Long getImageNr(int ch) { return imageNumber_.get(ch); }
      public void setImageInfo (final int width, final int height, 
              final int numComponents, final int bytesPerPixel) {
         width_ = width;
         height_ = height;
         numComponents_ = numComponents;
         bytesPerPixel_ = bytesPerPixel;
      }
      public void setImageNumber(int ch, Long imageNumber) { imageNumber_.put(ch, imageNumber); }
      
         
   }

   public SnapLiveManager(MMStudio mmStudio, CMMCore core) {
      mmStudio_ = mmStudio;
      core_ = core;
      uiMovesStageManager_ = mmStudio_.getUiMovesStageManager();
      displayInfoLock_ = new Object();
   }

   @Override
   public void setLiveMode(boolean isOn) {
      synchronized(liveModeLock_) {
         if (isLiveOn_ == isOn) {
            return;
         }
         isLiveOn_ = isOn;
         // Only actually start live mode now if we aren't currently
         // suspended.
         if (isLiveOn_ && suspendCount_ == 0) {
            startLiveMode();
         }
         else {
            stopLiveMode();
         }
         mmStudio_.events().post(new DefaultLiveModeEvent(isLiveOn_));
      }
   }

   /**
    * If live mode needs to temporarily stop for some action (e.g. changing
    * the exposure time), then clients can blindly call setSuspended(true)
    * to stop it and then setSuspended(false) to resume-only-if-necessary.
    * Note that this function will not notify listeners.
    * We need to handle the case where we get nested calls to setSuspended,
    * hence the reference count. And if we *are* suspended and someone tries
    * to start live mode (even though it wasn't running when we started the
    * suspension), then we should automatically start live mode when the
    * suspension ends. Thus, isLiveOn_ tracks the "nominal" state of live mode,
    * irrespective of whether or not it is currently suspended. When
    * suspendCount_ hits zero, we match up the actual state of live mode with
    * the nominal state.
    */
   @Override
   public void setSuspended(boolean shouldSuspend) {
      synchronized(liveModeLock_) {
         if (suspendCount_ == 0 && shouldSuspend && isLiveOn_) {
            // Need to stop now.
            stopLiveMode();
         }
         suspendCount_ += shouldSuspend ? 1 : -1;
         if (suspendCount_ == 0 && isLiveOn_) {
            // Need to resume now.
            startLiveMode();
         }
      }
   }

   private void startLiveMode() {
      if (amStartingSequenceAcquisition_) {
         // HACK: if startContinuousSequenceAcquisition results in a core
         // callback, then we can end up trying to start live mode when we're
         // already "in" startLiveMode somewhere above us in the call stack.
         // That is extremely prone to causing deadlocks between the image
         // grabber thread (which needs the Core camera lock) and our thread
         // (which already has the lock, due to
         // startContinuousSequenceAcquisition) -- and our thread is about to
         // join the grabber thread when stopLiveMode is called in a few lines.
         // Hence we use this sentinel value to check if we are actually
         // supposed to be starting live mode.
         mmStudio_.logs().logDebugMessage("Skipping startLiveMode as startContinuousSequenceAcquisition is in process");
         return;
      }

      stopLiveMode(); // Make sure

      try {
         amStartingSequenceAcquisition_ = true;
         core_.startContinuousSequenceAcquisition(0);
         amStartingSequenceAcquisition_ = false;
      }
      catch (Exception e) {
         ReportingUtils.showError(e, "Couldn't start live mode sequence acquisition");
         // Give up on starting live mode.
         amStartingSequenceAcquisition_ = false;
         setLiveMode(false);
         return;
      }

      long coreCameras = core_.getNumberOfCameraChannels();
      if (coreCameras != numCameraChannels_) {
         // Number of camera channels has changed; need to reset the display.
         shouldForceReset_ = true;
      }
      numCameraChannels_ = (int) coreCameras;
      final double exposureMs;
      try {
         exposureMs = core_.getExposure();
      }
      catch (Exception e) {
         mmStudio_.logs().showError(e, "Unable to determine exposure time");
         return;
      }
      final String camName = core_.getCameraDevice();
      
      synchronized (displayInfoLock_) {
         if (displayInfo_ != null) {
             for (int c = 0; c < numCameraChannels_; c++) {
                displayInfo_.setImageNumber(c, new Long(0));
             }
         }
      }

      if (display_ != null) {
         display_.resetDisplayIntervalEstimate();
      }

      synchronized (this) {
         final long liveModeCount = ++liveModeStartCount_;
         final Runnable grab;
         grab = new Runnable() {
            @Override
            public void run() {
               // We are started from within the monitor. Wait until that
               // monitor is released before starting.
               synchronized (SnapLiveManager.this) {
                  if (scheduledGrab_ == null ||
                        liveModeStartCount_ != liveModeCount) {
                     return;
                  }
               }
               grabAndAddImages(camName, liveModeCount);

               // Choose an interval within the absolute bounds, and at least as
               // long as the exposure. Within that range, try to match the
               // actual frequency at which the images are getting displayed.

               double displayIntervalLowQuantileMs;
               if (display_ != null) {
                  displayIntervalLowQuantileMs =
                        display_.getDisplayIntervalQuantile(
                              DISPLAY_INTERVAL_ESTIMATE_Q);
               }
               else {
                  displayIntervalLowQuantileMs = 0.0;
               }

               long delayMs;
               synchronized (SnapLiveManager.this) {
                  if (scheduledGrab_ == null ||
                        liveModeStartCount_ != liveModeCount) {
                     return;
                  }
                  delayMs = computeGrabDelayMs(exposureMs,
                        displayIntervalLowQuantileMs,
                        -scheduledGrab_.getDelay(TimeUnit.MILLISECONDS));
                  scheduledGrab_ = scheduler_.schedule(this,
                        delayMs, TimeUnit.MILLISECONDS);
               }
               perfMon_.sample("Grab schedule delay (ms)", delayMs);
            }
         };
         scheduledGrab_ = scheduler_.schedule(grab, 0, TimeUnit.MILLISECONDS);
      }

      if (display_ != null) {
         display_.toFront();
      }
   }

   private static long computeGrabDelayMs(double exposureMs,
         double displayIntervalMs, double alreadyElapsedMs)
   {
      double delayMs = Math.max(exposureMs, displayIntervalMs);
      delayMs -= alreadyElapsedMs;

      // Clip to allowed range
      delayMs = Math.max(MIN_GRAB_DELAY_MS, delayMs);
      if (delayMs > MAX_GRAB_DELAY_MS) {
         // A trick to get an interval that is less likely to frequently (and
         // noticeable) skip frames when the frame rate is low.
         delayMs /= Math.ceil(delayMs / MAX_GRAB_DELAY_MS);
      }

      return Math.round(delayMs);
   }

   private void stopLiveMode() {
      if (amStartingSequenceAcquisition_) {
         // HACK: if startContinuousSequenceAcquisition results in a core
         // callback, then we can end up trying to start live mode when we're
         // already "in" startLiveMode somewhere above us in the call stack.
         // See similar comment/block in startLiveMode(), above.
         mmStudio_.logs().logDebugMessage("Skipping stopLiveMode as startContinuousSequenceAcquisition is in process");
         return;
      }

      synchronized (this) {
         if (scheduledGrab_ != null) {
            scheduledGrab_.cancel(false);
            scheduledGrab_ = null;
         }
      }

      try {
         if (core_.isSequenceRunning()) {
            core_.stopSequenceAcquisition();
         }
         while (core_.isSequenceRunning()) {
            core_.sleep(2);
         }
      }
      catch (Exception e) {
         ReportingUtils.showError(e, "Failed to stop sequence acquisition. Double-check shutter status.");
      }
   }

   /**
    * This method takes images out of the Core and inserts them into our
    * pipeline.
    */
   private void grabAndAddImages(String camName, final long liveModeCount) {
      try {
         // We scan over 2*numCameraChannels here because, in multi-camera
         // setups, one camera could be generating images faster than the
         // other(s). Of course, 2x isn't guaranteed to be enough here, either,
         // but it's what we've historically used.
         HashSet<Integer> channelsSet = new HashSet<>();
         for (int c = 0; c < 6 * numCameraChannels_; ++c) {
            TaggedImage tagged;
            try {
               tagged = core_.getNBeforeLastTaggedImage(c);
               perfMon_.sampleTimeInterval("getNBeforeLastTaggedImage");
               perfMon_.sample("No image in sequence buffer (%)", 0.0);
            }
            catch (Exception e) {
               // No image in the sequence buffer.
               perfMon_.sample("No image in sequence buffer (%)", 100.0);
               continue;
            }
            JSONObject tags = tagged.tags;
            int imageChannel = c;
            if (tags.has(camName + "-CameraChannelIndex")) {
               imageChannel = tags.getInt(camName + "-CameraChannelIndex");
            }
            if (channelsSet.contains(imageChannel)) {
               // Already provided a more recent version of this channel.
               continue;
            }
            DefaultImage image = new DefaultImage(tagged);
            final Long seqNr = image.getMetadata().getImageNumber();
            perfMon_.sample("Image missing ImageNumber (%)",
                  seqNr == null ? 100.0 : 0.0);
            Coords newCoords = image.getCoords().copyBuilder()
               .t(0)
               .c(imageChannel).build();
              // Generate a new UUID for the image, so that our histogram
              // update code realizes this is a new image.
              Metadata newMetadata = image.getMetadata().copyBuilderWithNewUUID()
                      .build();
              final Image newImage = image.copyWith(newCoords, newMetadata);

              try {
                  SwingUtilities.invokeAndWait(() -> {
                     synchronized (SnapLiveManager.this) {
                        if (scheduledGrab_ == null
                                || liveModeStartCount_ != liveModeCount) {
                           throw new CancellationException();
                        }
                     }
                     displayImage(newImage);
                  });

              } catch (InterruptedException unexpected) {
                  Thread.currentThread().interrupt();
              } catch (InvocationTargetException e) {
                  if (e.getCause() instanceof CancellationException) {
                      return;
                  }
                  throw new RuntimeException(e.getCause());
              }
              channelsSet.add(imageChannel);
              if (channelsSet.size() == numCameraChannels_) {
                  // Got every channel.
                  break;
              }
          }
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Exception in image grabber thread.");
      }
   }

   @Override
   public boolean getIsLiveModeOn() {
      return isLiveOn_;
   }

   /**
    * [re]create the Datastore and its backing storage.
    */
   private void createDatastore() {
      synchronized(pipelineLock_) {
         if (pipeline_ != null) {
            pipeline_.halt();
         }
         // Note that unlike in most situations, we do *not* ask the
         // DataManager to track this Datastore for us.
         // TODO: remove MMStudio cast
         store_ = new DefaultRewritableDatastore(mmStudio_);
         store_.setStorage(new StorageRAM(store_));
         store_.setName("Snap/Live");
         // Use a synchronous pipeline for live mode.
         pipeline_ = mmStudio_.data().copyLivePipeline(store_, true);
      }
   }

   private void createDisplay() {
      DisplayWindowControlsFactory controlsFactory = 
              (DisplayWindow display) -> createControls();
      display_ = new DisplayController.Builder(store_).
            controlsFactory(controlsFactory).
            shouldShow(true).build(mmStudio_);
      DisplaySettings ds = DefaultDisplaySettings.restoreFromProfile(
              mmStudio_.profile(), 
              PropertyKey.SNAP_LIVE_DISPLAY_SETTINGS.key() );
      if (ds == null) {
         ds = DefaultDisplaySettings.builder().colorMode(
                 DisplaySettings.ColorMode.GRAYSCALE).build();
      }
      for (int ch = 0; ch < store_.getSummaryMetadata().getChannelNameList().size(); ch++) {
         ds = ds.copyBuilderWithChannelSettings(ch, 
                 RememberedSettings.loadChannel(mmStudio_, 
                         store_.getSummaryMetadata().getChannelGroup(), 
                         store_.getSummaryMetadata().getChannelNameList().get(ch))).
                 build();
      }
      display_.setDisplaySettings(ds);
      mmStudio_.displays().addViewer(display_);

      display_.registerForEvents(this);
      display_.addListener(this, 1);
      display_.setCustomTitle(TITLE);
      if (mmStudio_.getMMMenubar().getToolsMenu().getMouseMovesStage() && display_ != null) {
         uiMovesStageManager_.activate(display_);
      }
      
      synchronized (lastImageForEachChannel_) {
         lastImageForEachChannel_.clear();
      }
      
      synchronized (displayInfoLock_) {
         displayInfo_ = null;
      }
      
      
   }
   
   @Subscribe
   public void onMouseMovesStageStateChange(MouseMovesStageStateChangeEvent e) {
      if (display_ != null) {
         if (e.getIsEnabled()) {
            uiMovesStageManager_.activate(display_);
         } else {
            uiMovesStageManager_.deActivate(display_);
         }
      }
   }


   /**
    * Provide snap/live/album buttons for the display.
    *  
    */
   private List<Component> createControls() {
      ArrayList<Component> controls = new ArrayList<>();
      Insets zeroInsets = new Insets(0, 0, 0, 0);
      Dimension buttonSize = new Dimension(90, 28);

      JComponent snapButton = QuickAccessFactory.makeGUI(mmStudio_.plugins().getQuickAccessPlugins().get(
               "org.micromanager.quickaccess.internal.controls.SnapButton"));
      snapButton.setPreferredSize(buttonSize);
      snapButton.setMinimumSize(buttonSize);
      controls.add(snapButton);

      JComponent liveButton = QuickAccessFactory.makeGUI(mmStudio_.plugins().getQuickAccessPlugins().get(
               "org.micromanager.quickaccess.internal.controls.LiveButton"));
      liveButton.setPreferredSize(buttonSize);
      liveButton.setMinimumSize(buttonSize);
      controls.add(liveButton);

      JButton toAlbumButton = new JButton("Album",
            IconLoader.getIcon(
               "/org/micromanager/icons/camera_plus_arrow.png"));
      toAlbumButton.setToolTipText("Add the current image to the Album collection");
      toAlbumButton.setPreferredSize(buttonSize);
      toAlbumButton.setMinimumSize(buttonSize);
      toAlbumButton.setFont(GUIUtils.buttonFont);
      toAlbumButton.setMargin(zeroInsets);
      toAlbumButton.addActionListener((ActionEvent event) -> {
         // Send all images at current channel to the album.
         Coords.CoordsBuilder builder = Coordinates.builder();
         boolean hadChannels = false;
         for (int i = 0; i < store_.getAxisLength(Coords.CHANNEL); ++i) {
            builder.channel(i);
            try {
               mmStudio_.album().addImages(store_.getImagesMatching(
                       builder.build()));
               hadChannels = true;
            }
            catch (IOException e) {
               ReportingUtils.showError(e, "There was an error grabbing the images");
            }
         }
         try {
            if (!hadChannels) {
               mmStudio_.album().addImages(store_.getImagesMatching(
                       Coordinates.builder().build()));
            }
         }
         catch (IOException e) {
            ReportingUtils.showError(e, "There was an error grabbing the image");
         }
      });
      controls.add(toAlbumButton);
      return controls;
   }

   /**
    * Display the provided image. Due to limitations of ImageJ, if the image's
    * parameters (width, height, or pixel type) change, we have to recreate
    * the display and datastore. 
    * @param image Image to be displayed
    */
   @Override
   public void displayImage(final Image image) {

      if (!SwingUtilities.isEventDispatchThread()) {
         SwingUtilities.invokeLater(() -> {
            displayImage(image);
         });
         return;
      }

      boolean shouldReset = shouldForceReset_;
      if (store_ != null) {
         List<String> channelNames = store_.getSummaryMetadata().getChannelNameList();
         String curChannel = "";
         try {
            curChannel = core_.getCurrentConfig(core_.getChannelGroup());
         } catch (Exception e) {
            ReportingUtils.logError(e, "Error getting current channel");
         }
         for (int camCh = 0; camCh < numCameraChannels_; ++camCh) {
            String name = makeChannelName(curChannel, core_.getCameraChannelName(camCh));
            if (channelNames == null || camCh >= channelNames.size()) {
               shouldReset = true;
            } else if (!name.equals(channelNames.get(camCh))) {
               // Channel name changed.
               if (display_ != null && !display_.isClosed()) {
                  RememberedSettings.storeChannel(mmStudio_, 
                          store_.getSummaryMetadata().getChannelGroup(), 
                          store_.getSummaryMetadata().getChannelNameList().get(camCh),
                          display_.getDisplaySettings().getChannelSettings(camCh));
                  ChannelDisplaySettings newCD = RememberedSettings.loadChannel(
                          mmStudio_, 
                          core_.getChannelGroup(),
                          name);
                  display_.setDisplaySettings(display_.getDisplaySettings().
                          copyBuilderWithChannelSettings(camCh, newCD).build());
               }               
               channelNames.set(camCh, name);
               store_.setSummaryMetadata(store_.getSummaryMetadata().
                       copyBuilder().channelGroup(core_.getChannelGroup()).
                       channelNames(channelNames).build());
            }
         }
      }

      try {
         DefaultImage newImage = new DefaultImage(image, image.getCoords(),
               mmStudio_.acquisitions().generateMetadata(image, true));

         int newImageChannel = newImage.getCoords().getChannel();

         if ( (displayInfo_ != null) &&
                 (newImage.getWidth() != displayInfo_.getWidth()
                 || newImage.getHeight() != displayInfo_.getHeight()
                 || newImage.getNumComponents() != displayInfo_.getNumComponents()
                 || newImage.getBytesPerPixel() != displayInfo_.getBytesPerPixel())) {
            // Format changing, channel changing, and/or we have no display;
            // we need to recreate everything.
            shouldReset = true;
         } else if (displayInfo_ != null) {
            Long prevSeqNr = displayInfo_.getImageNr(newImageChannel);
            Long newSeqNr = newImage.getMetadata().getImageNumber();
            // NS, 05-20-2019: This code rejected images when their seqNr is lower than the 
            // previous seq nr.  However, this results in live mode display
            // stopping to update when the circular buffer is reset!
            // Very bad, especially for cameras with large frames
            // for now, we will only reject when the sequence nr is identical to the previous one.
            if (prevSeqNr != null && newSeqNr != null) {
               if (Objects.equals(prevSeqNr, newSeqNr)) {
                  perfMon_.sample(
                          "Image rejected based on ImageNumber (%)", 100.0);
                  return; // Already displayed this image
               }
               perfMon_.sample("Frames dropped at sequence buffer exit (%)",
                       100.0 * (newSeqNr - prevSeqNr - 1) / (newSeqNr - prevSeqNr));
            }
         }
         perfMon_.sample("Image rejected based on ImageNumber (%)", 0.0);

         if (shouldReset) {
            createOrResetDatastoreAndDisplay();
         } // Check for display having been closed on us by the user.
         else if (display_ == null || display_.isClosed()) {
            createDisplay();
            int numComponents = image.getNumComponents();
            if (numComponents > 1) {
               DisplaySettings ds = display_.getDisplaySettings();
               ChannelDisplaySettings.Builder cb = ds.getChannelSettings(0).copyBuilder();
               for (int i=0; i < numComponents; i++) {
                  cb.component(i);
               }
               display_.setDisplaySettings(ds.copyBuilder().channel(0, cb.build()).build());
            }
         }

         synchronized (displayInfoLock_) {
            if (displayInfo_ == null) {
               displayInfo_ = new DisplayInfo();
            }
            displayInfo_.setImageInfo(newImage.getWidth(),
                    newImage.getHeight(), newImage.getNumComponents(),
                    newImage.getBytesPerPixel()) ;
            displayInfo_.setImageNumber(newImageChannel, newImage.getMetadata().getImageNumber());

            synchronized(lastImageForEachChannel_) {
               if (lastImageForEachChannel_.size() > newImageChannel) {
                  lastImageForEachChannel_.set(newImageChannel, newImage);
               }
               else {
                  lastImageForEachChannel_.add(newImageChannel, newImage);
               }
            }
         }

         synchronized (pipelineLock_) {
            try {
               pipeline_.insertImage(newImage);
               perfMon_.sampleTimeInterval("Image inserted in pipeline");
            } catch (DatastoreRewriteException e) {
               // This should never happen, because we use an erasable
               // Datastore.
               mmStudio_.logs().showError(e,
                       "Unable to insert image into pipeline; this should never happen.");
            } catch (PipelineErrorException e) {
               // Notify the user, and halt live.
               mmStudio_.logs().showError(e,
                       "An error occurred while processing images.");
               stopLiveMode();
               pipeline_.clearExceptions();
            }
         }
      }
      catch (DatastoreFrozenException e) {
         // Datastore has been frozen (presumably the user saved a snapped
         // image); replace it.
         createOrResetDatastoreAndDisplay();
         displayImage(image);
      }
      catch (Exception e) {
         // Error getting metadata from the system state cache.
         mmStudio_.logs().logError(e, "Error drawing image in snap/live view");
      }
   }

   @MustCallOnEDT
   private void createOrResetDatastoreAndDisplay() {
      if (numCameraChannels_ == -1) {
         numCameraChannels_ = (int) core_.getNumberOfCameraChannels();
      }

      setSuspended(true);
      if (display_ != null && !display_.isClosed()) {
         //displayLoc = display_.getWindow().getLocation();
         saveDisplaySettings();
         display_.close();
      }

      createDatastore();

      // Set up the channel names in the store's summary metadata. This will
      // as a side-effect ensure that our channels are displayed with the
      // correct colors.
      try {
         String channel = core_.getCurrentConfig(core_.getChannelGroup());
         String[] channelNames = new String[numCameraChannels_];
         for (int i = 0; i < numCameraChannels_; ++i) {
            channelNames[i] = makeChannelName(channel, core_.getCameraChannelName(i));
         }
         try {
            store_.setSummaryMetadata(store_.getSummaryMetadata().copyBuilder()
                    .channelGroup(core_.getChannelGroup())
                    .channelNames(channelNames).build());
         }
         catch (DatastoreFrozenException e) {
            ReportingUtils.logError(e,
                  "Unable to update store summary metadata");
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error getting channel name");
      }
      
      createDisplay();
      
      shouldForceReset_ = false;
      setSuspended(false);
   }

   /**
    * Make a name for the given channel/camera number combination.
    * This tries to replicate the code in the acquisition engine.
    * TODO: Combine code in acq engine and this function
    */
   private String makeChannelName(String channel, String cameraChannelName) {
      String result;
      if (numCameraChannels_ > 1) {
         if (channel.isEmpty()) {
            result = cameraChannelName;
         } else {
            result = channel + "-" + cameraChannelName;
         }
      } else {
         result = channel;
         if (result.isEmpty()) {
            result = "Default";
         }
      }
      return result;
   }

   /**
    * Snap an image, display it if indicated, and return it.
    */
   @Override
   public List<Image> snap(boolean shouldDisplay) {
      if (isLiveOn_ && suspendCount_ == 0) {
         // Just return the most recent images.
         // BUG: In theory this could transiently contain nulls
         synchronized (lastImageForEachChannel_) {
            return new ArrayList<>(lastImageForEachChannel_);
         }
      }
      try {
         List<Image> images = mmStudio_.acquisitions().snap();
         if (shouldDisplay) {
            long coreCameras = core_.getNumberOfCameraChannels();
            if (coreCameras != numCameraChannels_) {
               // Number of camera channels has changed; need to reset the display.
               shouldForceReset_ = true;
            }
            numCameraChannels_ = (int) coreCameras;
            if (display_ != null) {
               display_.resetDisplayIntervalEstimate();
            }
            for (Image image : images) {
               displayImage(image);
            }
            // If we are not on the EDT, the display is constructed 
            // asynchronously, so there is no guarantee that the display 
            // object exists after calling displayImage. 
            // This leads to inconsistent behavior, since the display is brought 
            // to the front only if it was there already or made quickly enough
            if (display_ != null) {
               display_.toFront();
            }
         }
         return images;
      }
      catch (Exception e) {
         ReportingUtils.showError(e, "Failed to snap image");
      }
      return null;
   }

   @Override
   public DisplayWindow getDisplay() {
      if (display_ == null || display_.isClosed()) {
         return null;
      }
      return display_;
   }

   @Subscribe
   public void onPipelineChanged(NewPipelineEvent event) {
      // This will make us pick up the new pipeline the next time we get a
      // chance.
      shouldForceReset_ = true;
   }

   @Subscribe
   public void onShutdownCommencing(InternalShutdownCommencingEvent event) {
      if (!event.getIsCancelled()) {
         setLiveMode(false);
      }
   }

   private void saveDisplaySettings() {
      if (display_.getDisplaySettings() instanceof DefaultDisplaySettings) {
         ((DefaultDisplaySettings) display_.getDisplaySettings()).
                 saveToProfile(mmStudio_.profile(), PropertyKey.SNAP_LIVE_DISPLAY_SETTINGS.key());
      }
   }

   @Override
   public boolean canCloseViewer(DataViewer viewer) {
      if (viewer instanceof DisplayWindow && viewer.equals(display_)) {
         saveDisplaySettings();
         setLiveMode(false);
      }
      return true;
   }
}
