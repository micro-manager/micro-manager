package org.micromanager.internal;

import com.bulenkov.iconloader.IconLoader;
import com.google.common.eventbus.Subscribe;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import javax.swing.JButton;
import javax.swing.JComponent;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.Studio;
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
import org.micromanager.data.internal.StorageRAM;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.PixelsSetEvent;
import org.micromanager.display.RequestToCloseEvent;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.DefaultDisplayWindow;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.events.internal.DefaultLiveModeEvent;
import org.micromanager.events.internal.InternalShutdownCommencingEvent;
import org.micromanager.internal.interfaces.LiveModeListener;
import org.micromanager.internal.navigation.ClickToMoveManager;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.quickaccess.internal.QuickAccessFactory;

/**
 * This class is responsible for all logic surrounding live mode and the
 * "snap image" display (which is the same display as that used for live mode).
 */
public final class SnapLiveManager implements org.micromanager.SnapLiveManager {
   private static final String TITLE = "Snap/Live View";
   private static final int MAX_DISPLAY_HISTORY = 20;

   private final Studio studio_;
   private final CMMCore core_;
   private DisplayWindow display_;
   private final Object displayLock_ = new Object();
   private DefaultRewritableDatastore store_;
   private Pipeline pipeline_;
   private final Object pipelineLock_ = new Object();
   private final ArrayList<LiveModeListener> listeners_;
   private boolean isLiveOn_ = false;
   private final Object liveModeLock_ = new Object();
   private int numCameraChannels_ = -1;
   private double exposureMs_ = 0;
   private boolean shouldStopGrabberThread_ = false;
   private boolean shouldForceReset_ = false;
   private boolean amStartingSequenceAcquisition_ = false;
   private Thread grabberThread_;
   private final ArrayList<Long> displayUpdateTimes_;
   // Maps channel index to the last image we have received for that channel.
   private final HashMap<Integer, DefaultImage> channelToLastImage_;

   // As a (significant) convenience to our clients, we allow live mode to be
   // "suspended" and unsuspended, which amounts to briefly turning live mode
   // off if it is on, and then later turning it back on if it was on when
   // suspended. This gets unexpectedly complicated See setSuspended().
   private int suspendCount_ = 0;

   public SnapLiveManager(Studio studio, CMMCore core) {
      studio_ = studio;
      core_ = core;
      channelToLastImage_ = new HashMap<Integer, DefaultImage>();
      listeners_ = new ArrayList<LiveModeListener>();
      displayUpdateTimes_ = new ArrayList<Long>();
      studio_.events().registerForEvents(this);
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
         for (LiveModeListener listener : listeners_) {
            listener.liveModeEnabled(isLiveOn_);
         }
         DefaultEventManager.getInstance().post(new DefaultLiveModeEvent(isLiveOn_));
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
         studio_.logs().logDebugMessage("Skipping startLiveMode as startContinuousSequenceAcquisition is in process");
         return;
      }
      // First, ensure that any extant grabber thread is dead.
      stopLiveMode();
      shouldStopGrabberThread_ = false;
      grabberThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            grabImages();
         }
      }, "Live mode image grabber");
      // NOTE: start the grabber thread *after* the sequence acquisition
      // starts, because the grabber thread will need to acquire a core camera
      // lock as part of its setup (to get e.g. the number of core camera
      // channels), and that lock is also acquired as part of
      // startSequenceAcquisition. Thus if we start the grabber thread first,
      // in certain circumstances we can get a deadlock.
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
      grabberThread_.start();
      if (display_ != null) {
         display_.toFront();
      }
   }

   private void stopLiveMode() {
      if (amStartingSequenceAcquisition_) {
         // HACK: if startContinuousSequenceAcquisition results in a core
         // callback, then we can end up trying to start live mode when we're
         // already "in" startLiveMode somewhere above us in the call stack.
         // See similar comment/block in startLiveMode(), above.
         studio_.logs().logDebugMessage("Skipping stopLiveMode as startContinuousSequenceAcquisition is in process");
         return;
      }
      // Kill the grabber thread before we stop the sequence acquisition, to
      // ensure we don't try to grab images while stopping the acquisition.
      if (grabberThread_ != null) {
         shouldStopGrabberThread_ = true;
         // We can in rare cases be stopped from within the grabber thread;
         // in such cases joining it is obviously futile.
         if (Thread.currentThread() != grabberThread_) {
            try {
               grabberThread_.join();
            }
            catch (InterruptedException e) {
               ReportingUtils.logError(e, "Interrupted while waiting for grabber thread to end");
            }
         }
      }
      try {
         if (core_.isSequenceRunning()) {
            core_.stopSequenceAcquisition();
         }
      }
      catch (Exception e) {
         // TODO: in prior versions we tried to stop the sequence acquisition
         // again, after waiting 1s, with claims that the error was caused by
         // failing to close a shutter. I've left that out of this version.
         ReportingUtils.showError(e, "Failed to stop sequence acquisition. Double-check shutter status.");
      }
   }

   /**
    * This function is expected to run in its own thread. It continuously
    * polls the core for new images, which then get inserted into the
    * Datastore (which in turn propagates them to the display).
    * TODO: our polling approach blindly assigns images to channels on the
    * assumption that a) images always arrive from cameras in the same order,
    * and b) images don't arrive in the middle of our polling action. Obviously
    * this breaks down sometimes, which can cause images to "swap channels"
    * in the display. Fixing it would require the core to provide blocking
    * "get next image" calls, though, which it doesn't.
    */
   private void grabImages() {
      // Reset our list of when images have updated.
      synchronized(displayUpdateTimes_) {
         displayUpdateTimes_.clear();
      }

      long coreCameras = core_.getNumberOfCameraChannels();
      if (coreCameras != numCameraChannels_) {
         // Number of camera channels has changed; need to reset the display.
         shouldForceReset_ = true;
      }
      numCameraChannels_ = (int) coreCameras;
      try {
         exposureMs_ = core_.getExposure();
      }
      catch (Exception e) {
         studio_.logs().showError(e, "Unable to determine exposure time");
         return;
      }
      String camName = core_.getCameraDevice();
      while (!shouldStopGrabberThread_) {
         waitForNextDisplay();
         grabAndAddImages(camName);
      }
   }

   /**
    * This method waits for the next time at which we should grab images. It
    * takes as a parameter the current known rate at which the display is
    * showing images (which thus takes into account delays e.g. due to image
    * processing), and returns the updated display rate.
    */
   private void waitForNextDisplay() {
      long shortestWait = -1;
      // Determine our sleep time based on image display times (a.k.a. the
      // amount of time passed between PixelsSetEvents).
      synchronized(displayUpdateTimes_) {
         for (int i = 0; i < displayUpdateTimes_.size() - 1; ++i) {
            long delta = displayUpdateTimes_.get(i + 1) - displayUpdateTimes_.get(i);
            if (shortestWait == -1) {
               shortestWait = delta;
            }
            else {
               shortestWait = Math.min(shortestWait, delta);
            }
         }
      }
      // Sample faster than shortestWait because glitches should not cause the
      // system to permanently bog down; this allows us to recover if we
      // temporarily end up displaying images slowly than we normally can.
      // On the other hand, we don't want to sample faster than the exposure
      // time, or slower than 2x/second.
      int rateLimit = (int) Math.max(33, exposureMs_);
      int waitTime = (int) Math.min(500, Math.max(rateLimit, shortestWait * .75));
      try {
         Thread.sleep(waitTime);
      }
      catch (InterruptedException e) {}
   }

   /**
    * This method takes images out of the Core and inserts them into our
    * pipeline.
    */
   private void grabAndAddImages(String camName) {
      try {
         // We scan over 2*numCameraChannels here because, in multi-camera
         // setups, one camera could be generating images faster than the
         // other(s). Of course, 2x isn't guaranteed to be enough here, either,
         // but it's what we've historically used.
         HashSet<Integer> channelsSet = new HashSet<Integer>();
         for (int c = 0; c < 2 * numCameraChannels_; ++c) {
            TaggedImage tagged;
            try {
               tagged = core_.getNBeforeLastTaggedImage(c);
            }
            catch (Exception e) {
               // No image in the sequence buffer.
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
            Coords newCoords = image.getCoords().copy()
               .time(0)
               .channel(imageChannel).build();
            // Generate a new UUID for the image, so that our histogram
            // update code realizes this is a new image.
            Metadata newMetadata = image.getMetadata().copy()
               .uuid(UUID.randomUUID()).build();
            displayImage(image.copyWith(newCoords, newMetadata));
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

   public void addLiveModeListener(LiveModeListener listener) {
      if (!listeners_.contains(listener)) {
         listeners_.add(listener);
      }
   }

   public void removeLiveModeListener(LiveModeListener listener) {
      if (listeners_.contains(listener)) {
         listeners_.remove(listener);
      }
   }

   @Override
   public boolean getIsLiveModeOn() {
      return isLiveOn_;
   }

   /**
    * We need to [re]create the Datastore and its backing storage.
    */
   private void createDatastore() {
      synchronized(pipelineLock_) {
         if (pipeline_ != null) {
            pipeline_.halt();
         }
         // Note that unlike in most situations, we do *not* ask the
         // DataManager to track this Datastore for us.
         store_ = new DefaultRewritableDatastore();
         store_.setStorage(new StorageRAM(store_));
         // Use a synchronous pipeline for live mode.
         pipeline_ = studio_.data().copyLivePipeline(store_, true);
      }
   }

   /**
    * We need to [re]create the display and its associated custom controls.
    */
   private void createDisplay() {
      synchronized(displayLock_) {
         display_ = studio_.displays().createDisplay(store_,
            new ControlsFactory() {
               @Override
               public List<Component> makeControls(DisplayWindow display) {
                  return createControls(display);
               }
         });
         // Store our display settings separately in the profile from other
         // displays.
         ((DefaultDisplayWindow) display_).setDisplaySettingsKey(TITLE);
         // HACK: coerce single-camera setups to grayscale (instead of the
         // default of composite mode) if there is no existing profile settings
         // for the user and we do not have a multicamera setup.
         DisplaySettings.ColorMode mode = DefaultDisplaySettings.getStandardColorMode(TITLE, null);
         if (numCameraChannels_ == -1) {
            // Haven't yet figured out how many camera channels there are.
            numCameraChannels_ = (int) core_.getNumberOfCameraChannels();
         }
         if (mode == null && numCameraChannels_ == 1) {
            DisplaySettings settings = display_.getDisplaySettings();
            settings = settings.copy()
               .channelColorMode(DisplaySettings.ColorMode.GRAYSCALE)
               .build();
            display_.setDisplaySettings(settings);
         }
         display_.registerForEvents(this);
         display_.setCustomTitle(TITLE);
      }
   }

   /**
    * HACK: in addition to providing the snap/live/album buttons for the
    * display, we also set it up for click-to-move at this point.
    * We do this because duplicates of the snap/live window also need
    * click-to-move to be enabled.
    */
   private List<Component> createControls(final DisplayWindow display) {
      ClickToMoveManager.getInstance().activate((DefaultDisplayWindow) display);
      ArrayList<Component> controls = new ArrayList<Component>();
      Insets zeroInsets = new Insets(0, 0, 0, 0);
      JComponent snapButton = QuickAccessFactory.makeGUI(
            studio_.plugins().getQuickAccessPlugins().get(
               "org.micromanager.quickaccess.internal.controls.SnapButton"));
      snapButton.setPreferredSize(new Dimension(90, 28));
      controls.add(snapButton);

      JComponent liveButton = QuickAccessFactory.makeGUI(
            studio_.plugins().getQuickAccessPlugins().get(
               "org.micromanager.quickaccess.internal.controls.LiveButton"));
      liveButton.setPreferredSize(new Dimension(90, 28));
      controls.add(liveButton);

      JButton toAlbumButton = new JButton("Album",
            IconLoader.getIcon(
               "/org/micromanager/icons/camera_plus_arrow.png"));
      toAlbumButton.setToolTipText("Add the current image to the Album collection");
      toAlbumButton.setPreferredSize(new Dimension(90, 28));
      toAlbumButton.setFont(GUIUtils.buttonFont);
      toAlbumButton.setMargin(zeroInsets);
      toAlbumButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            // Send all images at current channel to the album.
            Coords.CoordsBuilder builder = studio_.data().getCoordsBuilder();
            for (int i = 0; i < store_.getAxisLength(Coords.CHANNEL); ++i) {
               builder.channel(i);
               DefaultAlbum.getInstance().addImages(store_.getImagesMatching(
                     builder.build()));
            }
         }
      });
      controls.add(toAlbumButton);
      return controls;
   }

   /**
    * Display the provided image. Due to limitations of ImageJ, if the image's
    * parameters (width, height, or pixel type) change, we have to recreate
    * the display and datastore. We also do this if the channel names change,
    * as an inefficient way to force the channel colors to update.
    * @param image Image to be displayed
    */
   @Override
   public void displayImage(Image image) {
      synchronized(displayLock_) {
         boolean shouldReset = shouldForceReset_;
         if (store_ != null) {
            String[] channelNames = store_.getSummaryMetadata().getChannelNames();
            String curChannel = "";
            try {
               curChannel = core_.getCurrentConfig(core_.getChannelGroup());
            }
            catch (Exception e) {
               ReportingUtils.logError(e, "Error getting current channel");
            }
            for (int i = 0; i < numCameraChannels_; ++i) {
               String name = makeChannelName(curChannel, i);
               if (i >= channelNames.length || !name.equals(channelNames[i])) {
                  // Channel name changed.
                  shouldReset = true;
               }
            }
         }
         try {
            DefaultImage newImage = new DefaultImage(image, image.getCoords(),
                  studio_.acquisitions().generateMetadata(image, true));
            // Find any image to compare against, at all.
            DefaultImage lastImage = null;
            if (channelToLastImage_.keySet().size() > 0) {
               int channel = new ArrayList<Integer>(channelToLastImage_.keySet()).get(0);
               lastImage = channelToLastImage_.get(channel);
            }
            if (lastImage == null ||
                  newImage.getWidth() != lastImage.getWidth() ||
                  newImage.getHeight() != lastImage.getHeight() ||
                  newImage.getNumComponents() != lastImage.getNumComponents() ||
                  newImage.getBytesPerPixel() != lastImage.getBytesPerPixel()) {
               // Format changing, channel changing, and/or we have no display;
               // we need to recreate everything.
               shouldReset = true;
            }
            if (shouldReset) {
               reset();
            }
            // Check for display having been closed on us by the user.
            else if (display_ == null || display_.getIsClosed()) {
               createDisplay();
            }
            channelToLastImage_.put(newImage.getCoords().getChannel(),
                  newImage);
            synchronized(pipelineLock_) {
               try {
                  pipeline_.insertImage(newImage);
               }
               catch (DatastoreRewriteException e) {
                  // This should never happen, because we use an erasable
                  // Datastore.
                  studio_.logs().showError(e,
                        "Unable to insert image into pipeline; this should never happen.");
               }
               catch (PipelineErrorException e) {
                  // Notify the user, and halt live.
                  studio_.logs().showError(e,
                        "An error occurred while processing images.");
                  stopLiveMode();
                  pipeline_.clearExceptions();
               }
            }
         }
         catch (DatastoreFrozenException e) {
            // Datastore has been frozen (presumably the user saved a snapped
            // image); replace it.
            reset();
            displayImage(image);
         }
         catch (Exception e) {
            // Error getting metadata from the system state cache.
            studio_.logs().logError(e, "Error drawing image in snap/live view");
         }
      }
   }

   /**
    * Reset our display and datastore.
    */
   private void reset() {
      // Remember the position of the window.
      Point displayLoc = null;
      createDatastore();
      if (display_ != null) {
         displayLoc = display_.getAsWindow().getLocation();
         display_.forceClosed();
      }
      createDisplay();
      displayUpdateTimes_.clear();
      if (displayLoc != null) {
         display_.getAsWindow().setLocation(displayLoc);
      }
      channelToLastImage_.clear();

      // Set up the channel names in the store's summary metadata. This will
      // as a side-effect ensure that our channels are displayed with the
      // correct colors.
      try {
         String channel = core_.getCurrentConfig(core_.getChannelGroup());
         if (numCameraChannels_ == -1) {
            // Haven't yet figured out how many camera channels there are.
            numCameraChannels_ = (int) core_.getNumberOfCameraChannels();
         }
         String[] channelNames = new String[numCameraChannels_];
         for (int i = 0; i < numCameraChannels_; ++i) {
            channelNames[i] = makeChannelName(channel, i);
         }
         try {
            store_.setSummaryMetadata(store_.getSummaryMetadata().copy()
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
      shouldForceReset_ = false;
   }

   /**
    * Make a name up for the given channel/camera number combination.
    */
   private String makeChannelName(String channel, int cameraIndex) {
      String result = channel;
      if (numCameraChannels_ > 1) {
         result = result + " " + cameraIndex;
      }
      return result;
   }

   /**
    * Snap an image, display it if indicated, and return it.
    */
   @Override
   public List<Image> snap(boolean shouldDisplay) {
      if (isLiveOn_) {
         // Just return the most recent images.
         ArrayList<Image> result = new ArrayList<Image>();
         ArrayList<Integer> keys = new ArrayList<Integer>(channelToLastImage_.keySet());
         Collections.sort(keys);
         for (Integer i : keys) {
            result.add(channelToLastImage_.get(i));
         }
         return result;
      }
      try {
         List<Image> images = studio_.acquisitions().snap();
         if (shouldDisplay) {
            for (Image image : images) {
               displayImage(image);
            }
            display_.toFront();
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
      if (display_ != null && !display_.getIsClosed()) {
         return display_;
      }
      return null;
   }

   @Subscribe
   public void onDisplayUpdated(PixelsSetEvent event) {
      synchronized(displayUpdateTimes_) {
         displayUpdateTimes_.add(System.currentTimeMillis());
         while (displayUpdateTimes_.size() > MAX_DISPLAY_HISTORY) {
            // Limit the history we maintain.
            displayUpdateTimes_.remove(displayUpdateTimes_.get(0));
         }
      }
   }

   @Subscribe
   public void onRequestToClose(RequestToCloseEvent event) {
      // Closing is fine by us, but we need to stop live mode first.
      setLiveMode(false);
      event.getDisplay().forceClosed();
      // Force a reset for next time, in case of changes that we don't pick up
      // on (e.g. a processor that failed to notify us of changes in image
      // parameters.
      shouldForceReset_ = true;
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
}
