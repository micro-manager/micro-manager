/*
 * HyperstackControls.java
 *
 * Created on Jul 15, 2010, 2:54:37 PM
 */
package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentAdapter;
import java.awt.FlowLayout;
import java.lang.Math;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;

import org.micromanager.data.NewImageEvent;
import org.micromanager.imagedisplay.FPSEvent;
import org.micromanager.imagedisplay.MouseMovedEvent;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;


public class HyperstackControls extends JPanel {

   private final static int DEFAULT_FPS = 10;
   private final static double MAX_FPS = 5000;
   // Height in pixels of our controls, not counting scrollbars.
   private final static int CONTROLS_HEIGHT = 65;

   private EventBus displayBus_;
   private Datastore store_;
   private MMVirtualStack stack_;

   // Last known mouse positions.
   private int mouseX_ = 0;
   private int mouseY_ = 0;

   // JPanel that holds all controls.
   private JPanel subPanel_;
   // Controls common to both control sets
   private ScrollerPanel scrollerPanel_;
   private JLabel pixelInfoLabel_;
   private JLabel fpsLabel_;
   // Displays the countdown to the next frame.
   private JLabel countdownLabel_;
   // Displays general status information.
   private JLabel statusLabel_;
   private JButton showFolderButton_;
   private JButton saveButton_;

   // Standard control set
   private javax.swing.JTextField fpsField_;
   private JButton abortButton_;
   private javax.swing.JToggleButton pauseAndResumeToggleButton_;

   // Snap/live control set
   private JButton snapButton_;
   private JButton snapToAlbumButton_;
   private JButton liveButton_;

   /**
    * @param shouldUseLiveControls - indicates if we should use the buttons for 
    *        the "Snap/Live" window or the buttons for normal displays.
    */
   public HyperstackControls(Datastore store, MMVirtualStack stack,
         EventBus displayBus, boolean shouldUseLiveControls) {
      super(new FlowLayout(FlowLayout.LEADING));
      displayBus_ = displayBus;
      store_ = store;
      store_.registerForEvents(this, 100);
      stack_ = stack;
      initComponents(shouldUseLiveControls);
      displayBus_.register(this);
   }

   private void initComponents(final boolean shouldUseLiveControls) {
      // This layout minimizes space between components.
      subPanel_ = new JPanel(new MigLayout("insets 0, fillx, align center"));

      java.awt.Font labelFont = new java.awt.Font("Lucida Grande", 0, 10);
      String labelString = "                                            ";
      Dimension labelDimension = new Dimension(150, 10);
      JPanel labelsPanel = new JPanel(new MigLayout("insets 0"));
      pixelInfoLabel_ = new JLabel(labelString);
      pixelInfoLabel_.setMinimumSize(labelDimension);
      pixelInfoLabel_.setFont(labelFont);
      labelsPanel.add(pixelInfoLabel_);

      fpsLabel_ = new JLabel(labelString);
      fpsLabel_.setMinimumSize(labelDimension);
      fpsLabel_.setFont(labelFont);
      labelsPanel.add(fpsLabel_);
      
      countdownLabel_ = new JLabel(labelString);
      countdownLabel_.setMinimumSize(labelDimension);
      countdownLabel_.setFont(labelFont);
      labelsPanel.add(countdownLabel_);

      statusLabel_ = new JLabel(labelString);
      statusLabel_.setMinimumSize(labelDimension);
      statusLabel_.setFont(labelFont);
      labelsPanel.add(statusLabel_);

      subPanel_.add(labelsPanel, "span, growx, align center, wrap");

      scrollerPanel_ = new ScrollerPanel(store_, displayBus_, DEFAULT_FPS);
      subPanel_.add(scrollerPanel_, "span, growx, wrap 0px");
      add(subPanel_);

      // Propagate resizing through to our JPanel, adjusting slightly the 
      // amount of space we give them to create a border.
      addComponentListener(new ComponentAdapter() {
         public void componentResized(ComponentEvent e) {
            Dimension curSize = getSize();
            subPanel_.setPreferredSize(new Dimension(curSize.width - 10, curSize.height - 10));
            invalidate();
            validate();
         }
      });
   }

   /**
    * User moused over the display; update our indication of pixel intensities.
    */
   @Subscribe
   public void onMouseMoved(MouseMovedEvent event) {
      try {
         mouseX_ = event.getX();
         mouseY_ = event.getY();
         setPixelInfo(mouseX_, mouseY_);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to get image pixel info");
      }
   }

   public String getIntensityString(int x, int y) {
      int numChannels = store_.getMaxIndex("channel") + 1;
      if (numChannels > 1) {
         // Multi-channel case: display each channel with a "/" in-between.
         String intensity = "[";
         for (int i = 0; i < numChannels; ++i) {
            Coords imageCoords = stack_.getCurrentImageCoords().copy().position("channel", i).build();
            intensity += store_.getImage(imageCoords).getIntensityStringAt(x, y);
            if (i != numChannels - 1) {
               intensity += "/";
            }
         }
         intensity += "]";
         return intensity;
      }
      else {
         // Single-channel case; simple.
         return store_.getImage(stack_.getCurrentImageCoords()).getIntensityStringAt(x, y);
      }
   }

   /**
    * Update our pixel info text.
    */
   private void setPixelInfo(int x, int y) {
      String intensity = getIntensityString(x, y);
      pixelInfoLabel_.setText(String.format("x=%d, y=%d, value=%s",
               x, y, intensity));
      // This validate call reduces the chance that the text will be truncated.
      validate();
   }

   /**
    * A new image has been made available. Update our pixel info, assuming
    * we have a valid mouse position.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      Image image = event.getImage();
      if (mouseX_ >= 0 && mouseX_ < image.getWidth() &&
            mouseY_ >= 0 && mouseY_ < image.getHeight()) {
         try {
            setPixelInfo(mouseX_, mouseY_);
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Error in HyperstackControls onNewImage");
         }
      }
   }

   /**
    * New information on our FPS; update a label.
    */
   @Subscribe
   public void onFPSUpdate(FPSEvent event) {
      // Default to assuming we'll be blanking the label.
      String newLabel = "";
      if (event.getDataFPS() != 0) {
         newLabel = String.format("FPS: %.1f (display %.1f)",
               event.getDataFPS(), event.getDisplayFPS());
      }
      else if (event.getDisplayFPS() != 0) {
         newLabel = String.format("Display FPS: %.1f", event.getDisplayFPS());
      }
      fpsLabel_.setText(newLabel);
      validate();
   }

   @Subscribe
   public void onStatus(StatusEvent event) {
      statusLabel_.setText(event.getStatus());
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
      if (hours == 0 && fraction > 0.01) {
         secondsString = String.format("%.2fs", wholeSeconds + fraction);
      }
      else {
         secondsString = wholeSeconds + "s";
      }

      return hoursString + minutesString + secondsString;
   }

   public void cleanup() {
      scrollerPanel_.cleanup();
      displayBus_.unregister(this);
      store_.unregisterForEvents(this);
   }
}
