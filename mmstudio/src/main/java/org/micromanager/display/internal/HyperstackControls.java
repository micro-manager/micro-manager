///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

import com.google.common.base.Joiner;
import com.google.common.eventbus.Subscribe;
import java.awt.Dimension;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import net.miginfocom.swing.MigLayout;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.internal.IncomingImageEvent;
import org.micromanager.data.internal.NewImageEvent;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.PixelsSetEvent;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;
import org.micromanager.display.internal.events.StatusEvent;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This panel contains the ScrollerPanel that handles navigation of a multi-
 * dimensional dataset. It also contains some status text, showing information
 * like pixel intensities, FPS, and some brief information on the displayed
 * image.
 */
public final class HyperstackControls extends JPanel {

   private final DisplayWindow display_;
   private final Datastore store_;

   // Controls common to both control sets
   private ScrollerPanel scrollerPanel_;
   private JLabel fpsLabel_;
   private long msSinceLastFPSUpdate_ = 0;
   private Image imageFromLastFPS_ = null;
   private int imagesReceived_ = 0;
   private int displayUpdates_ = 0;
   private Timer blankingTimer_ = null;
   // Displays the countdown to the next frame.
   private JLabel countdownLabel_;
   // Displays general status information.
   private JLabel statusLabel_;

   /**
    * @param store
    * @param display DisplayWindow we are embedded in
    */
   public HyperstackControls(Datastore store, DisplayWindow display) {
      super(new MigLayout("insets 0, fillx, align center"));
      display_ = display;
      store_ = store;
      store_.registerForEvents(this);
      initComponents();
      display_.registerForEvents(this);
   }

   private void initComponents() {
      Font labelFont = new Font("Lucida Grande", 0, 10);
      // HACK: we allocate excessive height for our text fields on purpose;
      // if we try to make them precise to the text that will be displayed,
      // then we risk them growing when their text changes, and taking space
      // that should be used for the canvas.
      Dimension labelDimension = new Dimension(10, 13);
      JPanel labelsPanel = new JPanel(new MigLayout("fillx, insets 0, gap 0"));

      statusLabel_ = new JLabel();
      statusLabel_.setMinimumSize(labelDimension);
      statusLabel_.setFont(labelFont);
      labelsPanel.add(statusLabel_, "grow, gapright push");

      countdownLabel_ = new JLabel();
      countdownLabel_.setMinimumSize(labelDimension);
      countdownLabel_.setFont(labelFont);
      labelsPanel.add(countdownLabel_, "grow");

      fpsLabel_ = new JLabel();
      fpsLabel_.setHorizontalAlignment(SwingConstants.RIGHT);
      fpsLabel_.setMinimumSize(labelDimension);
      fpsLabel_.setFont(labelFont);
      labelsPanel.add(fpsLabel_, "grow");

      add(labelsPanel, "span, growx, align center, wrap");

      scrollerPanel_ = new ScrollerPanel(store_, display_);
      scrollerPanel_.startUpdateThread();
      store_.registerForEvents(scrollerPanel_);
      display_.registerForEvents(scrollerPanel_);
      add(scrollerPanel_, "span, growx, shrinkx, wrap 0px");
   }

   /**
    * A new image has arrived; we may need to update our data FPS (
    * rate at which new images are being acquired).
    * @param event - NewImageEvent
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      imagesReceived_++;
      updateFPS(event.getImage());
   }

   /**
    * Changed which image is displayed; update our short status line text about
    * the displayed image.
    * @param event - PixelsSetEvent
    */
   @Subscribe
   public void onPixelsSet(PixelsSetEvent event) {
      updateStatus(event.getImage());
   }

   /**
    * Create a timer to show the countdown for incoming images.
    * @param event - IncomingImageEvent
    */
   @Subscribe
   public void onIncomingImage(IncomingImageEvent event) {
      try {
         final Timer countdownTimer = new Timer("Next image countdown");

         final double nextTime = event.getNextImageNanoTime();
         TimerTask task = new TimerTask() {
            @Override
            public void run() {
               double remainingMs = nextTime - (System.nanoTime() / 1000000);
               if (remainingMs <= 0) {
                  // Cancel the timer, so we don't show negative values.
                  countdownTimer.cancel();
                  countdownLabel_.setText("");
                  return;
               }
               String text = elapsedTimeDisplayString(remainingMs / 1000);
               countdownLabel_.setText("Next image: " + text);
            }
         };
         countdownTimer.schedule(task, 500, 100);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error setting up countdown");
      }
   }

   /**
    * The displayed image has changed, so update our display FPS (the rate at
    * which images are displayed).
    * @param event - CanvasDrawCompleteEvent
    */
   @Subscribe
   public void onCanvasDrawComplete(CanvasDrawCompleteEvent event) {
      Image image = display_.getDisplayedImages().get(0);
      displayUpdates_++;
      updateFPS(image);
   }

   /**
    * Update the FPS display label. We use sequence number and elapsedTimeMs
    * when they're available, and otherwise just the number of images added to
    * the datastore and the system time change.
    * @param newImage Newly added image for which we should adjust the fps
    */
   public void updateFPS(Image newImage) {
      if (System.currentTimeMillis() - msSinceLastFPSUpdate_ < 500) {
         // Too soon since the last FPS update.
         return;
      }
      if (blankingTimer_ == null) {
         blankingTimer_ = new Timer("FPS display blank");
      }
      if (imageFromLastFPS_ == null) {
         imageFromLastFPS_ = newImage;
         // Can't display any meaningful FPS yet.
         return;
      }

      // Cancel the timer that will blank the FPS, as we have updated info.
      blankingTimer_.cancel();

      // Calculate our data FPS and display FPS. The two use different
      // methodologies: data FPS prefers to operate from the image metadata
      // if possible (to determine how much time has passed and how many images
      // the camera snapped), while display FPS operates based on our
      // measurement of elapsed time and number of display operations performed
      String dataFPS = "";
      String displayFPS = "";

      double displaySec = (System.currentTimeMillis() - msSinceLastFPSUpdate_) / 1000.0;
      double dataSec = displaySec;
      Metadata newMetadata = newImage.getMetadata();
      Metadata oldMetadata = imageFromLastFPS_.getMetadata();
      if (newMetadata.getElapsedTimeMs() != null &&
            oldMetadata.getElapsedTimeMs() != null) {
         dataSec = (newMetadata.getElapsedTimeMs() - oldMetadata.getElapsedTimeMs()) / 1000.0;
      }
      long dataImages = imagesReceived_;
      // Only attempt to set data FPS if we know that at least one image has
      // arrived during this interval, so we don't show data FPS during display
      // updates after data acquisition has finished.
      if (imagesReceived_ != 0 && newMetadata.getImageNumber() != null &&
            oldMetadata.getImageNumber() != null) {
         dataImages = newMetadata.getImageNumber() - oldMetadata.getImageNumber();
      }

      String newLabel = "";
      if (dataImages != 0) {
         newLabel += String.format("Data %.1ffps   ", dataImages / dataSec);
      }
      if (displayUpdates_ != 0) {
         newLabel += String.format("Display %.1ffps", displayUpdates_ / displaySec);
      }
      fpsLabel_.setText(newLabel);
      msSinceLastFPSUpdate_ = System.currentTimeMillis();
      imagesReceived_ = 0;
      displayUpdates_ = 0;
      imageFromLastFPS_ = newImage;
      // Set up a timer to blank the FPS after awhile (1s).
      TimerTask blankingTask = new TimerTask() {
         @Override
         public void run() {
            fpsLabel_.setText("");
         }
      };
      blankingTimer_ = new Timer("FPS display blank");
      blankingTimer_.schedule(blankingTask, 1000);
   }

   @Subscribe
   public void onStatus(StatusEvent event) {
      statusLabel_.setText(event.getStatus());
   }

   /**
    * Update our status line to show some information about the image:
    * channel name, position name, acquisition time, Z altitude, and camera
    * name.
    */
   private void updateStatus(Image image) {
      ArrayList<String> tokens = new ArrayList<String>();
      Metadata metadata = image.getMetadata();
      String name = store_.getSummaryMetadata().getSafeChannelName(
            image.getCoords().getChannel());
      tokens.add(name);
      if (metadata.getPositionName() != null) {
         tokens.add(metadata.getPositionName());
      }
      if (metadata.getElapsedTimeMs() != null) {
         tokens.add(String.format("t=%s",
                  elapsedTimeDisplayString(metadata.getElapsedTimeMs() / 1000)));
      }
      if (metadata.getZPositionUm() != null) {
         tokens.add(String.format("z=%.2f\u00b5m", metadata.getZPositionUm()));
      }
      if (metadata.getCamera() != null && !metadata.getCamera().equals("")) {
         tokens.add(metadata.getCamera());
      }
      statusLabel_.setText(Joiner.on("   ").join(tokens));
   }

   public static String elapsedTimeDisplayString(double seconds) {
      // Use "12.34s" up to 60 s; "12m 34.56s" up to 1 h, and
      // "1h 23m 45s" beyond that.

      long wholeSeconds = (long) Math.floor(seconds);
      double fraction = seconds - wholeSeconds;

      long hours = TimeUnit.SECONDS.toHours(wholeSeconds);
      wholeSeconds -= TimeUnit.HOURS.toSeconds(hours);
      String hoursString = "";
      if (hours > 0) {
         hoursString = hours + "h ";
      }

      long minutes = TimeUnit.SECONDS.toMinutes(wholeSeconds);
      wholeSeconds -= TimeUnit.MINUTES.toSeconds(minutes);
      String minutesString = "";
      if (minutes > 0) {
         minutesString = minutes + "m ";
      }

      String secondsString;
      if (hours == 0) {
         secondsString = String.format("%.2fs", wholeSeconds + fraction);
      }
      else {
         secondsString = wholeSeconds + "s";
      }

      return hoursString + minutesString + secondsString;
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      display_.unregisterForEvents(this);
      store_.unregisterForEvents(this);
      if (blankingTimer_ != null) {
         blankingTimer_.cancel();
      }
   }
}
