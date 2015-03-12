package org.micromanager.internal;

import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import ij.gui.ImageWindow;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import net.miginfocom.swing.MigLayout;

import org.json.JSONObject;

import org.micromanager.data.internal.StorageRAM;

import org.micromanager.data.AbortEvent;
import org.micromanager.data.Coords;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.RequestToCloseEvent;

import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultImage;

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.DefaultDisplayWindow;

import org.micromanager.events.LiveModeEvent;
import org.micromanager.events.internal.DefaultLiveModeEvent;
import org.micromanager.events.internal.EventManager;

import org.micromanager.internal.interfaces.LiveModeListener;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class is responsible for all logic surrounding live mode and the
 * "snap image" display (which is the same display as that used for live mode).
 */
public class SnapLiveManager implements org.micromanager.SnapLiveManager {
   private CMMCore core_;
   private DisplayWindow display_;
   private DefaultDatastore store_;
   private ArrayList<LiveModeListener> listeners_;
   private boolean isOn_ = false;
   // Suspended means that we *would* be running except we temporarily need
   // to halt for the duration of some action (e.g. changing the exposure
   // time). See setSuspended().
   private boolean isSuspended_ = false;
   private boolean shouldStopGrabberThread_ = false;
   private Thread grabberThread_;
   // Maps channel to the last image we have received for that channel.
   private HashMap<Integer, DefaultImage> channelToLastImage_;

   public SnapLiveManager(CMMCore core) {
      core_ = core;
      channelToLastImage_ = new HashMap<Integer, DefaultImage>();
      listeners_ = new ArrayList<LiveModeListener>();
   }

   @Override
   public void setLiveMode(boolean isOn) {
      if (isOn_ == isOn) {
         return;
      }
      isOn_ = isOn;
      if (isOn_) {
         startLiveMode();
      }
      else {
         stopLiveMode();
      }
      for (LiveModeListener listener : listeners_) {
         listener.liveModeEnabled(isOn_);
      }
      EventManager.post(new DefaultLiveModeEvent(isOn_));
   }

   private void setLiveButtonMode(JButton button, boolean isOn) {
      String label = isOn ? "Stop Live" : "Live";
      String iconPath = isOn ? "/org/micromanager/internal/icons/cancel.png" : "/org/micromanager/internal/icons/camera_go.png";
      button.setIcon(
            SwingResourceManager.getIcon(MMStudio.class, iconPath));
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
      if (shouldSuspend && isOn_) {
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

   @Subscribe
   public void onAbort(AbortEvent event) {
      setLiveMode(false);
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
         catch (Exception e) {
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
      return isOn_;
   }

   /**
    * We need to [re]create the Datastore and its backing storage.
    */
   private void createDatastore() {
      if (store_ != null) {
         store_.unregisterForEvents(this);
      }
      // Note that unlike in most situations, we do *not* ask the DataManager
      // to track this Datastore for us.
      store_ = new DefaultDatastore();
      store_.registerForEvents(this);
      store_.setStorage(new StorageRAM(store_));
   }

   /**
    * We need to [re]create the display and its associated custom controls.
    */
   private void createDisplay() {
      display_ = new DefaultDisplayWindow(store_,
         new ControlsFactory() {
            @Override
            public List<Component> makeControls(DisplayWindow display) {
               return createControls(display);
            }
      });
      display_.registerForEvents(this);
      display_.setCustomTitle("Snap/Live View");
   }

   private List<Component> createControls(final DisplayWindow display) {
      ArrayList<Component> controls = new ArrayList<Component>();
      // This button needs to be enabled/disabled when live mode is turned
      // off/on.
      JButton snapButton = new JButton("Snap",
            SwingResourceManager.getIcon(MMStudio.class,
               "/org/micromanager/internal/icons/camera.png")) {
         @Subscribe
         public void onLiveMode(LiveModeEvent event) {
            setEnabled(!event.getIsOn());
         }
         @Subscribe
         public void onDisplayDestroyed(DisplayDestroyedEvent event) {
            EventManager.unregister(this);
         }
      };
      EventManager.register(snapButton);
      snapButton.setToolTipText("Take a new image");
      snapButton.setPreferredSize(new Dimension(90, 28));
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
            EventManager.unregister(this);
         }
      };
      EventManager.register(liveButton);
      display.registerForEvents(liveButton);
      liveButton.setToolTipText("Continuously acquire new images");
      setLiveButtonMode(liveButton, isOn_);
      liveButton.setPreferredSize(new Dimension(90, 28));
      liveButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setLiveMode(!isOn_);
         }
      });
      controls.add(liveButton);

      JButton toAlbumButton = new JButton("Album",
            SwingResourceManager.getIcon(MMStudio.class,
               "/org/micromanager/internal/icons/arrow_right.png"));
      toAlbumButton.setToolTipText("Add the current image to the Album collection");
      toAlbumButton.setPreferredSize(new Dimension(90, 28));
      toAlbumButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            List<Image> images = snap(false);
            DefaultAlbum.getInstance().addImages(images);
         }
      });
      controls.add(toAlbumButton);
      return controls;
   }

   /**
    * Display the provided image. Due to limitations of ImageJ, if the image's
    * parameters (width, height, or pixel type) change, we have to recreate
    * the display and datastore.
    */
   @Override
   public void displayImage(Image image) {
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
            // Format changing and/or we have no display; we need to recreate
            // everything.
            if (display_ != null) {
               display_.forceClosed();
            }
            createDatastore();
            createDisplay();
            channelToLastImage_.clear();
         }
         else if (display_ == null || display_.getIsClosed()) {
            createDisplay();
         }
         channelToLastImage_.put(newImage.getCoords().getChannel(),
               newImage);
         // This will put the images into the datastore, which in turn will
         // cause them to be displayed.
         newImage.splitMultiComponentIntoStore(store_);
      }
      catch (DatastoreFrozenException e) {
         ReportingUtils.showError(e, "Snap/Live display datastore locked.");
      }
   }

   /**
    * Snap an image, display it if indicated, and return it.
    * TODO: for multichannel images we are just returning the last channel's
    * image.
    */
   @Override
   public List<Image> snap(boolean shouldDisplay) {
      if (core_.getCameraDevice().length() == 0) {
         ReportingUtils.showError("No camera configured.");
         return new ArrayList<Image>();
      }
      if (isOn_) {
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
      display_.forceClosed();
   }
}
