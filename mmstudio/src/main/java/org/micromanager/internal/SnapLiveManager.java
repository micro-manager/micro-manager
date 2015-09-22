package org.micromanager.internal;

import com.bulenkov.iconloader.IconLoader;

import com.google.common.eventbus.Subscribe;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Insets;
import java.awt.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import javax.swing.ImageIcon;

import javax.swing.JButton;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.json.JSONException;


import org.json.JSONObject;

import org.micromanager.data.internal.StorageRAM;

import org.micromanager.data.Coords;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.RequestToCloseEvent;

import org.micromanager.data.NewPipelineEvent;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultImage;

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.DefaultDisplayWindow;

import org.micromanager.events.LiveModeEvent;
import org.micromanager.events.internal.DefaultLiveModeEvent;
import org.micromanager.events.internal.DefaultEventManager;

import org.micromanager.Studio;

import org.micromanager.internal.interfaces.LiveModeListener;
import org.micromanager.internal.navigation.ClickToMoveManager;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.MMScriptException;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class is responsible for all logic surrounding live mode and the
 * "snap image" display (which is the same display as that used for live mode).
 */
public class SnapLiveManager implements org.micromanager.SnapLiveManager {
   private final Studio studio_;
   private final CMMCore core_;
   private DisplayWindow display_;
   private DefaultDatastore store_;
   private Pipeline pipeline_;
   private Object pipelineLock_ = new Object();
   private final ArrayList<LiveModeListener> listeners_;
   private boolean isLiveOn_ = false;
   // Suspended means that we *would* be running except we temporarily need
   // to halt for the duration of some action (e.g. changing the exposure
   // time). See setSuspended().
   private boolean isSuspended_ = false;
   private boolean shouldStopGrabberThread_ = false;
   private boolean shouldForceReset_ = false;
   private Thread grabberThread_;
   // Maps channel to the last image we have received for that channel.
   private final HashMap<Integer, DefaultImage> channelToLastImage_;

   public SnapLiveManager(Studio studio, CMMCore core) {
      studio_ = studio;
      core_ = core;
      channelToLastImage_ = new HashMap<Integer, DefaultImage>();
      listeners_ = new ArrayList<LiveModeListener>();
      studio_.events().registerForEvents(this);
   }

   @Override
   public void setLiveMode(boolean isOn) {
      if (isLiveOn_ == isOn) {
         return;
      }
      isLiveOn_ = isOn;
      if (isLiveOn_) {
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

   private void setLiveButtonMode(JButton button, boolean isOn) {
      String label = isOn ? "Stop Live" : "Live";
      String iconPath = isOn ? "/org/micromanager/icons/cancel.png" : 
              "/org/micromanager/icons/camera_go.png";
      button.setIcon(IconLoader.getIcon(iconPath));
      button.setText(label);
   }

   /**
    * If live mode needs to temporarily stop for some action (e.g. changing
    * the exposure time), then clients can blindly call setSuspended(true)
    * to stop it and then setSuspended(false) to resume-only-if-necessary.
    * Note that this function will not notify listeners.
    */
   @Override
   public void setSuspended(boolean shouldSuspend) {
      if (shouldSuspend && isLiveOn_) {
         // Need to stop now.`
         stopLiveMode();
         isSuspended_ = true;
      }
      else if (!shouldSuspend && isSuspended_) {
         // Need to resume now.
         startLiveMode();
         isSuspended_ = false;
      }
   }

   private void startLiveMode() {
      // First, ensure that any extant grabber thread is dead.
      stopLiveMode();
      shouldStopGrabberThread_ = false;
      grabberThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            grabImages();
         }
      });
      grabberThread_.start();
      try {
         core_.startContinuousSequenceAcquisition(0);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't start live mode sequence acquisition");
      }
   }

   private void stopLiveMode() {
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
      if (grabberThread_ != null) {
         shouldStopGrabberThread_ = true;
         try {
            grabberThread_.join();
         }
         catch (InterruptedException e) {
            ReportingUtils.logError(e, "Interrupted while waiting for grabber thread to end");
         }
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
      // No point in grabbing things faster than we can display, which is
      // currently around 30FPS.
      int interval = 33;
      try {
         interval = (int) Math.max(interval, core_.getExposure());
      }
      catch (Exception e) {
         // Getting exposure time failed; go with the default.
         ReportingUtils.logError(e, "Couldn't get exposure time for live mode.");
      }
      long numChannels = core_.getNumberOfCameraChannels();
      String camName = core_.getCameraDevice();
      while (!shouldStopGrabberThread_) {
         try {
            // We scan over 2*numChannels here because, in multi-camera
            // setups, one camera could be generating images faster than the
            // other(s). Of course, 2x isn't guaranteed to be enough here,
            // either, but it's what we've historically used.
            HashSet<Integer> channelsSet = new HashSet<Integer>();
            for (int c = 0; c < 2 * numChannels; ++c) {
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
               displayImage(image.copyAtCoords(newCoords));
               channelsSet.add(imageChannel);
               if (channelsSet.size() == numChannels) {
                  // Got every channel.
                  break;
               }
            }
            try {
               Thread.sleep(interval);
            }
            catch (InterruptedException e) {}
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Exception in image grabber thread.");
         } catch (MMScriptException e) {
            ReportingUtils.logError(e, "Exception in image grabber thread.");
         }
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
         if (store_ != null) {
            store_.unregisterForEvents(this);
         }
         // Note that unlike in most situations, we do *not* ask the
         // DataManager to track this Datastore for us.
         store_ = new DefaultDatastore();
         store_.registerForEvents(this);
         store_.setStorage(new StorageRAM(store_));
         if (pipeline_ != null) {
            pipeline_.halt();
         }
         // Use a synchronous pipeline for live mode.
         pipeline_ = studio_.data().copyApplicationPipeline(store_, true);
      }
   }

   /**
    * We need to [re]create the display and its associated custom controls.
    */
   private synchronized void createDisplay() {
      display_ = studio_.displays().createDisplay(store_,
         new ControlsFactory() {
            @Override
            public List<Component> makeControls(DisplayWindow display) {
               return createControls(display);
            }
      });
      display_.registerForEvents(this);
      display_.setCustomTitle("Snap/Live View");
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
      // This button needs to be enabled/disabled when live mode is turned
      // off/on.
      // The icon is based on the public-domain icon at
      // https://openclipart.org/detail/34051/digicam
      JButton snapButton = new JButton("Snap",
            IconLoader.getIcon("/org/micromanager/icons/camera.png")) {
         @Subscribe
         public void onLiveMode(LiveModeEvent event) {
            setEnabled(!event.getIsOn());
         }
         @Subscribe
         public void onDisplayDestroyed(DisplayDestroyedEvent event) {
            DefaultEventManager.getInstance().unregisterForEvents(this);
         }
      };
      DefaultEventManager.getInstance().registerForEvents(snapButton);
      snapButton.setToolTipText("Take a new image");
      snapButton.setPreferredSize(new Dimension(90, 28));
      snapButton.setFont(GUIUtils.buttonFont);
      snapButton.setMargin(zeroInsets);
      snapButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            snap(true);
         }
      });
      controls.add(snapButton);

      // This button needs to change when live mode is turned on/off.
      JButton liveButton = new JButton() {
         @Subscribe
         public void onLiveMode(LiveModeEvent event) {
            setLiveButtonMode(this, event.getIsOn());
         }
         @Subscribe
         public void onDisplayDestroyed(DisplayDestroyedEvent event) {
            display.unregisterForEvents(this);
            DefaultEventManager.getInstance().unregisterForEvents(this);
         }
      };
      DefaultEventManager.getInstance().registerForEvents(liveButton);
      display.registerForEvents(liveButton);
      liveButton.setToolTipText("Continuously acquire new images");
      setLiveButtonMode(liveButton, isLiveOn_);
      liveButton.setPreferredSize(new Dimension(90, 28));
      liveButton.setFont(GUIUtils.buttonFont);
      liveButton.setMargin(zeroInsets);
      liveButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setLiveMode(!isLiveOn_);
         }
      });
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
    * the display and datastore.
    * @param image Image to be displayed
    */
   @Override
   public synchronized void displayImage(Image image) {
      // Check for changes in the number of channels, indicating e.g. changing
      // multicamera.
      boolean shouldReset = shouldForceReset_;
      long numChannels = core_.getNumberOfCameraChannels();
      try {
         DefaultImage newImage = new DefaultImage(image, image.getCoords(), image.getMetadata());
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
            // Format changing, channel changing, and/or we have no display; we
            // need to recreate everything.
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
            for (Image subImage : newImage.splitMultiComponent()) {
               try {
                  pipeline_.insertImage(subImage);
               }
               catch (PipelineErrorException e) {
                  // Notify the user, then continue on.
                  studio_.logs().showError(e,
                        "An error occurred while processing images.");
                  pipeline_.clearExceptions();
               }
            }
         }
      }
      catch (DatastoreFrozenException e) {
         // Datastore has been frozen (presumably the user saved a snapped
         // image); replace it.
         reset();
         displayImage(image);
      }
   }

   /**
    * Reset our display and datastore.
    */
   private void reset() {
      // Remember the position of the window.
      Point displayLoc = null;
      if (display_ != null) {
         displayLoc = display_.getAsWindow().getLocation();
         display_.forceClosed();
      }
      createDatastore();
      createDisplay();
      if (displayLoc != null) {
         display_.getAsWindow().setLocation(displayLoc);
      }
      channelToLastImage_.clear();
      shouldForceReset_ = false;
   }

   /**
    * Snap an image, display it if indicated, and return it.
    */
   @Override
   public List<Image> snap(boolean shouldDisplay) {
      if (core_.getCameraDevice().length() == 0) {
         ReportingUtils.showError("No camera configured.");
         return new ArrayList<Image>();
      }
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
         core_.snapImage();
         ArrayList<Image> result = new ArrayList<Image>();
         for (int c = 0; c < core_.getNumberOfCameraChannels(); ++c) {
            TaggedImage tagged = core_.getTaggedImage(c);
            Image temp = new DefaultImage(tagged);
            Coords newCoords = temp.getCoords().copy().channel(c).build();
            temp = temp.copyAtCoords(newCoords);
            result.add(temp);
         }
         if (shouldDisplay) {
            for (Image image : result) {
               displayImage(image);
            }
            display_.toFront();
         }
         return result;
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
}
