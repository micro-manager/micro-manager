package org.micromanager;

import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import ij.gui.ImageWindow;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JPanel;

import mmcorej.CMMCore;
import mmcorej.TaggedImage;

import net.miginfocom.swing.MigLayout;

import org.micromanager.acquisition.StorageRAM;

import org.micromanager.api.data.AbortEvent;
import org.micromanager.api.data.Coords;
import org.micromanager.api.data.DatastoreLockedException;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.SummaryMetadata;
import org.micromanager.api.display.DisplayWindow;
import org.micromanager.api.display.RequestToCloseEvent;

import org.micromanager.data.DefaultCoords;
import org.micromanager.data.DefaultDatastore;
import org.micromanager.data.DefaultImage;

import org.micromanager.imagedisplay.dev.DisplayStarter;

import org.micromanager.internalinterfaces.LiveModeListener;

import org.micromanager.utils.ReportingUtils;

/**
 * This class is responsible for all logic surrounding live mode and the
 * "snap image" display (which is the same display as that used for live mode).
 */
public class SnapLiveManager {
   // Maximum number of timepoints to keep in the Datastore at a time before
   // we start overwriting images.
   private static final int MAX_TIMEPOINTS = 50;
   
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
   private int lastTimepoint_ = 0;
   private Image lastImage_ = null;

   private JButton snapButton_;
   private JButton liveButton_;
   private JButton toAlbumButton_;

   public SnapLiveManager(CMMCore core) {
      core_ = core;
      store_ = new DefaultDatastore();
      store_.registerForEvents(this, 100);
      store_.setStorage(new StorageRAM(store_));
      // Update the summary metadata so that the "filename" field is used to
      // generate an appropriate display window title.
      SummaryMetadata summary = store_.getSummaryMetadata();
      summary = summary.copy().fileName("Snap/Live View").build();
      try {
         store_.setSummaryMetadata(summary);
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.showError(e, "Failed to set snap/live title; what on Earth is going on?");
      }
      listeners_ = new ArrayList<LiveModeListener>();
   }

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

      // Update our buttons, if they exist yet.
      if (snapButton_ != null) {
         snapButton_.setEnabled(!isOn_);
         String label = isOn_ ? "Stop Live" : "Live";
         String iconPath = isOn_ ? "/org/micromanager/icons/cancel.png" : "/org/micromanager/icons/camera_go.png";
         liveButton_.setIcon(
               SwingResourceManager.getIcon(MMStudio.class, iconPath));
         liveButton_.setText(label);
      }
   }

   /**
    * If live mode needs to temporarily stop for some action (e.g. changing
    * the exposure time), then clients can blindly call setSuspended(true)
    * to stop it and then setSuspended(false) to resume-only-if-necessary.
    * Note that this function will not notify listeners.
    */
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
      if (display_ == null || display_.getIsClosed()) {
         createDisplay(); // See comments on this function.
      }
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
      while (!shouldStopGrabberThread_) {
         try {
            for (int c = 0; c < numChannels; ++c) {
               TaggedImage tagged;
               try {
                  tagged = core_.getNBeforeLastTaggedImage(c);
               }
               catch (Exception e) {
                  // No image in the sequence buffer.
                  continue;
               }
               DefaultImage image = new DefaultImage(tagged);
               Coords newCoords = image.getCoords().copy()
                  .position("time", lastTimepoint_ % MAX_TIMEPOINTS)
                  .position("channel", c).build();
               displayImage(image.copyAtCoords(newCoords));
            }
            lastTimepoint_++;
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

   public boolean getIsLiveModeOn() {
      return isOn_;
   }

   public ImageWindow getSnapLiveWindow() {
      if (display_ != null && !display_.getIsClosed()) {
         return display_.getImageWindow();
      }
      return null;
   }

   /**
    * We need to [re]create the display. Unfortunately it won't actually
    * appear until images are available (since we can't create the ImagePlus
    * used for drawing things until after at least one image is in the
    * Datastore), so we'll be setting display_ later on in displayImage().
    */
   private void createDisplay() {
      JPanel controlPanel = new JPanel(new MigLayout());
      snapButton_ = new JButton("Snap",
            SwingResourceManager.getIcon(MMStudio.class,
               "/org/micromanager/icons/camera.png"));
      snapButton_.setPreferredSize(new Dimension(90, 28));
      snapButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            snap(true);
         }
      });
      controlPanel.add(snapButton_);

      liveButton_ = new JButton("Live",
            SwingResourceManager.getIcon(MMStudio.class,
               "/org/micromanager/icons/camera_go.png"));
      liveButton_.setPreferredSize(new Dimension(90, 28));
      liveButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            setLiveMode(!isOn_);
         }
      });
      controlPanel.add(liveButton_);

      toAlbumButton_ = new JButton("Album",
            SwingResourceManager.getIcon(MMStudio.class,
               "/org/micromanager/icons/arrow_right.png"));
      toAlbumButton_.setPreferredSize(new Dimension(90, 28));
      toAlbumButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent event) {
            MMStudio.getInstance().doSnap(true);
         }
      });
      controlPanel.add(toAlbumButton_);

      new DisplayStarter(store_, controlPanel);
   }

   public void displayImage(Image image) {
      try {
         DefaultImage temp = new DefaultImage(image, image.getCoords(), image.getMetadata());
         List<Image> splitted = temp.splitMultiComponentIntoStore(store_);
         lastImage_ = splitted.get(splitted.size() - 1);
      }
      catch (DatastoreLockedException e) {
         ReportingUtils.showError(e, "Snap/Live display datastore locked.");
      }
      if (display_ == null || display_.getIsClosed()) {
         // Now that we know that the DisplayStarter we made earlier will
         // have made a DisplayWindow, access it for ourselves.
         List<DisplayWindow> displays = store_.getDisplays();
         if (displays.size() > 0) {
            display_ = displays.get(0);
            display_.registerForEvents(this);
         }
      }
   }

   public void moveDisplayToFront() {
      if (display_ != null && !display_.getIsClosed()) {
         display_.toFront();
      }
   }

   /**
    * Snap an image, display it if indicated, and return it.
    * TODO: for multichannel images we are just returning the last channel's
    * image.
    */
   public Image snap(boolean shouldDisplay) {
      if (isOn_) {
         // Just return the most recent image.
         return lastImage_;
      }
      try {
         if (shouldDisplay) {
            if (display_ == null || display_.getIsClosed()) {
               // Assume that there's no DisplayStarter waiting to create a
               // DisplayWindow, and create one now.
               createDisplay();
            }
            else {
               display_.toFront();
            }
         }
         core_.snapImage();
         Image result = null;
         for (int c = 0; c < core_.getNumberOfCameraChannels(); ++c) {
            TaggedImage tagged = core_.getTaggedImage(c);
            result = new DefaultImage(tagged);
            Coords newCoords = result.getCoords().copy()
               .position("channel", c).build();
            result = result.copyAtCoords(newCoords);
            if (shouldDisplay) {
               displayImage(result);
            }
         }
         return result;
      }
      catch (Exception e) {
         ReportingUtils.showError(e, "Failed to snap image");
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
