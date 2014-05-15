

/*
 * HyperstackControls.java
 *
 * Created on Jul 15, 2010, 2:54:37 PM
 */
package org.micromanager.imageDisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.ParseException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;


public class HyperstackControls extends DisplayControls {

   private final VirtualAcquisitionDisplay display_;
   private JLabel pixelInfoLabel_;
   private JLabel statusLineLabel_;
   private javax.swing.JTextField fpsField_;
   private JButton showFolderButton_;
   private JButton saveButton_;
   private JLabel fpsLabel_;
   private JButton abortButton_;
   private javax.swing.JToggleButton pauseAndResumeToggleButton_;

   /** Create the object. We need the bus so we can listen to 
    * MouseIntensityEvents and display pixel intensity information for our
    * image.
    */
   public HyperstackControls(VirtualAcquisitionDisplay display, 
         EventBus bus) {
      super(new FlowLayout(FlowLayout.LEADING));
      initComponents(bus);
      display_ = display;
      fpsField_.setText(NumberUtils.doubleToDisplayString(display_.getPlaybackFPS()));
      bus.register(this);
   }

   private void initComponents(EventBus bus) {
      // This layout minimizes space between components.
      JPanel subPanel = new JPanel(
            new MigLayout("", "0[]", "0[]0[]0[]0"));
      subPanel.setPreferredSize(new Dimension(512, 100));

      pixelInfoLabel_ = new JLabel("                                     ");
      pixelInfoLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      subPanel.add(pixelInfoLabel_, "span 5, wrap");

      subPanel.add(new ScrollerPanel(
               bus, new String[]{"channel", "position", "time", "z"}, 
               new Integer[]{1, 1, 1, 1}), 
            "wrap 0px");

      showFolderButton_ = new JButton();
      saveButton_ = new JButton();
      fpsField_ = new javax.swing.JTextField();
      fpsLabel_ = new JLabel();
      abortButton_ = new JButton();
      statusLineLabel_ = new JLabel();
      pauseAndResumeToggleButton_ = new javax.swing.JToggleButton();
      
      subPanel.add(showFolderButton_);
      subPanel.add(saveButton_);
      subPanel.add(abortButton_);
      subPanel.add(pauseAndResumeToggleButton_);
      subPanel.add(fpsLabel_);
      subPanel.add(fpsField_);
      subPanel.add(statusLineLabel_);

      add(subPanel);

      showFolderButton_.setBackground(new java.awt.Color(255, 255, 255));
      showFolderButton_.setIcon(
            new javax.swing.ImageIcon(
               getClass().getResource("/org/micromanager/icons/folder.png")));
      showFolderButton_.setToolTipText("Show containing folder");
      showFolderButton_.setFocusable(false);
      showFolderButton_.setHorizontalTextPosition(
            javax.swing.SwingConstants.CENTER);
      showFolderButton_.setMaximumSize(new java.awt.Dimension(30, 28));
      showFolderButton_.setMinimumSize(new java.awt.Dimension(30, 28));
      showFolderButton_.setPreferredSize(new java.awt.Dimension(30, 28));
      showFolderButton_.setVerticalTextPosition(
            javax.swing.SwingConstants.BOTTOM);
      showFolderButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            showFolderButton_ActionPerformed(evt);
         }
      });

      saveButton_.setBackground(new java.awt.Color(255, 255, 255));
      saveButton_.setIcon(
            new javax.swing.ImageIcon(
               getClass().getResource("/org/micromanager/icons/disk.png")));
      saveButton_.setToolTipText("Save as...");
      saveButton_.setFocusable(false);
      saveButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      saveButton_.setMaximumSize(new java.awt.Dimension(30, 28));
      saveButton_.setMinimumSize(new java.awt.Dimension(30, 28));
      saveButton_.setPreferredSize(new java.awt.Dimension(30, 28));
      saveButton_.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
      saveButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveButton_ActionPerformed(evt);
         }
      });

      fpsField_.setToolTipText(
            "Set the speed at which the acquisition is played back.");
      fpsField_.addFocusListener(new java.awt.event.FocusAdapter() {
         @Override
         public void focusLost(java.awt.event.FocusEvent evt) {
            fpsField_FocusLost(evt);
         }
      });
      fpsField_.addKeyListener(new java.awt.event.KeyAdapter() {
         @Override
         public void keyReleased(java.awt.event.KeyEvent evt) {
            fpsField_KeyReleased(evt);
         }
      });

      fpsLabel_.setText("playback fps:");
      fpsLabel_.setFocusable(false);

      abortButton_.setBackground(new java.awt.Color(255, 255, 255));
      abortButton_.setIcon(
            new javax.swing.ImageIcon(
               getClass().getResource("/org/micromanager/icons/cancel.png")));
      abortButton_.setToolTipText("Stop acquisition");
      abortButton_.setFocusable(false);
      abortButton_.setHorizontalTextPosition(javax.swing.SwingConstants.CENTER);
      abortButton_.setMaximumSize(new java.awt.Dimension(30, 28));
      abortButton_.setMinimumSize(new java.awt.Dimension(30, 28));
      abortButton_.setPreferredSize(new java.awt.Dimension(30, 28));
      abortButton_.setVerticalTextPosition(javax.swing.SwingConstants.BOTTOM);
      abortButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            abortButton_ActionPerformed(evt);
         }
      });

      statusLineLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      statusLineLabel_.setHorizontalTextPosition(
            javax.swing.SwingConstants.LEFT);

      pauseAndResumeToggleButton_.setIcon(
            new javax.swing.ImageIcon(getClass().getResource(
                  "/org/micromanager/icons/control_pause.png")));
      pauseAndResumeToggleButton_.setToolTipText("Pause acquisition");
      pauseAndResumeToggleButton_.setFocusable(false);
      pauseAndResumeToggleButton_.setMargin(new java.awt.Insets(0, 0, 0, 0));
      pauseAndResumeToggleButton_.setMaximumSize(new java.awt.Dimension(30, 28));
      pauseAndResumeToggleButton_.setMinimumSize(new java.awt.Dimension(30, 28));
      pauseAndResumeToggleButton_.setPreferredSize(
            new java.awt.Dimension(30, 28));
      pauseAndResumeToggleButton_.setPressedIcon(
            new javax.swing.ImageIcon(getClass().getResource(
                  "/org/micromanager/icons/resultset_next.png")));
      pauseAndResumeToggleButton_.setSelectedIcon(
            new javax.swing.ImageIcon(getClass().getResource(
                  "/org/micromanager/icons/resultset_next.png")));
      pauseAndResumeToggleButton_.addActionListener(
            new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            pauseAndResumeToggleButton_ActionPerformed(evt);
         }
      });

   }

   /**
    * User moused over the display; update our indication of pixel intensities.
    * TODO: only providing the first intensity; what about multichannel 
    * images?
    */
   @Subscribe
   public void onMouseMoved(MouseIntensityEvent event) {
      pixelInfoLabel_.setText(String.format(
               "<%d, %d>: %d", event.x_, event.y_, event.intensities_[0]));
   }
  
   /**
    * Our ScrollerPanel is informing us that we need to display a different
    * image.
    */
   @Subscribe
   public void onSetImage(ScrollerPanel.SetImageEvent event) {
      // Positions for ImageJ are 1-indexed but positions from the event are 
      // 0-indexed.
      int channel = event.getPositionForAxis("channel") + 1;
      int frame = event.getPositionForAxis("time") + 1;
      int slice = event.getPositionForAxis("z") + 1;
      display_.getHyperImage().setPosition(channel, slice, frame);
   }

   private void showFolderButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      display_.showFolder();
   }

   private void fpsField_FocusLost(java.awt.event.FocusEvent evt) {
      updateFPS();
   }

   private void fpsField_KeyReleased(java.awt.event.KeyEvent evt) {
      updateFPS();
   }

   private void abortButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      display_.abort();
   }

   private void pauseAndResumeToggleButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      display_.pause();
}

   private void saveButton_ActionPerformed(java.awt.event.ActionEvent evt) {
      new Thread() {
         @Override
         public void run() {
            display_.saveAs();
         }
      }.start();
   }

   private void updateFPS() {
      try {
         double fps = NumberUtils.displayStringToDouble(fpsField_.getText());
         display_.setPlaybackFPS(fps);
      } catch (ParseException ex) {
      }
   }

   @Override
   public synchronized void setStatusLabel(String text) {
      statusLineLabel_.setText(text);
   }

   private void updateStatusLine(JSONObject tags) {
      String status = "";
      try {
         String xyPosition;
         try {
            xyPosition = tags.getString("PositionName");
            if (xyPosition != null && !xyPosition.contentEquals("null")) {
               status += xyPosition + ", ";
            }
         } catch (Exception e) {
            //Oh well...
         }

         try {
            double seconds = tags.getDouble("ElapsedTime-ms") / 1000;
            status += elapsedTimeDisplayString(seconds);
         } catch (JSONException ex) {
            ReportingUtils.logError("MetaData did not contain ElapsedTime-ms field");
         }

         String zPosition;
         try {
            zPosition = NumberUtils.doubleStringCoreToDisplay(tags.getString("ZPositionUm"));
            status += ", z: " + zPosition + " um";
         } catch (Exception e) {
            try {
               zPosition = NumberUtils.doubleStringCoreToDisplay(tags.getString("Z-um"));
               status += ", z: " + zPosition + " um";
            } catch (Exception e1) {
               // Do nothing...
            }
         }
         String chan;
         try {
            chan = MDUtils.getChannelName(tags);
            if (chan != null && !chan.contentEquals("null")) {
               status += ", " + chan;
            }
         } catch (Exception ex) {
         }

         setStatusLabel(status);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }

   }

   public static String elapsedTimeDisplayString(double seconds) {
      // Use "12.3456s" up to 60 s; "12m 34.5678s" up to 1 h, and
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
      if (hours == 0 && fraction > 0.0001) {
         secondsString = NumberUtils.doubleToDisplayString(wholeSeconds + fraction) + "s";
      }
      else {
         secondsString = wholeSeconds + "s";
      }

      return hoursString + minutesString + secondsString;
   }

   @Override
   public void newImageUpdate(JSONObject tags) {
      if (tags == null) {
         return;
      }
      updateStatusLine(tags);
      try {
         if (display_.acquisitionIsRunning() && display_.getNextWakeTime() > 0) {
            final long nextImageTime = display_.getNextWakeTime();
            if (System.nanoTime() / 1000000 < nextImageTime) {
               final Timer timer = new Timer("Next frame display");
               TimerTask task = new TimerTask() {

                  @Override
                  public void run() {
                     double timeRemainingS = (nextImageTime - System.nanoTime() / 1000000) / 1000;
                     if (timeRemainingS > 0 && display_.acquisitionIsRunning()) {
                        setStatusLabel("Next frame: " + NumberUtils.doubleToDisplayString(1 + timeRemainingS) + " s");
                     } else {
                        timer.cancel();
                        setStatusLabel("");
                     }
                  }
               };
               timer.schedule(task, 2000, 100);
            }
         }

      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }


   @Override
   public void acquiringImagesUpdate(boolean state) {
      abortButton_.setEnabled(state);
      pauseAndResumeToggleButton_.setEnabled(state);
   }

   @Override
   public void imagesOnDiskUpdate(boolean enabled) {
      showFolderButton_.setEnabled(enabled);
   }
}
