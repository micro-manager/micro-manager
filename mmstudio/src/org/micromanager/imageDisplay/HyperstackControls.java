

/*
 * HyperstackControls.java
 *
 * Created on Jul 15, 2010, 2:54:37 PM
 */
package org.micromanager.imageDisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.FlowLayout;
import java.awt.Font;
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
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;


public class HyperstackControls extends DisplayControls {

   private final VirtualAcquisitionDisplay display_;
   private EventBus bus_;

   // Controls common to both control sets
   private ScrollerPanel scrollerPanel_;
   private JLabel pixelInfoLabel_;
   private JButton showFolderButton_;
   private JButton saveButton_;
   private JLabel statusLineLabel_;

   // Standard control set
   private JLabel fpsLabel_;
   private javax.swing.JTextField fpsField_;
   private JButton abortButton_;
   private javax.swing.JToggleButton pauseAndResumeToggleButton_;

   // Snap/live control set
   private JButton snapButton_;
   private JButton snapToAlbumButton_;
   private JButton liveButton_;

   /**
    * @param shouldUseLiveButtons - indicates if we should use the buttons for 
    *        the "Snap/Live" window or the buttons for normal displays.
    */
   public HyperstackControls(VirtualAcquisitionDisplay display, 
         EventBus bus, boolean shouldUseLiveButtons) {
      super(new FlowLayout(FlowLayout.LEADING));
      bus_ = bus;
      initComponents(shouldUseLiveButtons);
      display_ = display;
      bus_.register(this);
   }

   private void initComponents(final boolean shouldUseLiveButtons) {
      // This layout minimizes space between components.
      JPanel subPanel = new JPanel(new MigLayout("", "0[]", "0[]0[]0[]0"));
      subPanel.setPreferredSize(new Dimension(512, 100));

      pixelInfoLabel_ = new JLabel("                                     ");
      pixelInfoLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      subPanel.add(pixelInfoLabel_, "span, wrap");

      scrollerPanel_ = new ScrollerPanel(
               bus_, new String[]{"channel", "position", "time", "z"}, 
               new Integer[]{1, 1, 1, 1});
      subPanel.add(scrollerPanel_, "span, growx, wrap 0px");

      showFolderButton_ = new JButton();
      saveButton_ = new JButton();
      statusLineLabel_ = new JLabel();

      subPanel.add(showFolderButton_);
      subPanel.add(saveButton_);

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
            showFolderButtonActionPerformed(evt);
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
            saveButtonActionPerformed(evt, shouldUseLiveButtons);
         }
      });

      if (shouldUseLiveButtons) {
         makeSnapLiveButtons(subPanel);
      }
      else {
         makeStandardButtons(subPanel);
      }
      
      statusLineLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      statusLineLabel_.setHorizontalTextPosition(
            javax.swing.SwingConstants.LEFT);
      subPanel.add(statusLineLabel_);

      add(subPanel);
   }

   /**
    * Generate the controls used for the "Snap/Live" window.
    */
   private void makeSnapLiveButtons(JPanel subPanel) {
      snapButton_ = new JButton();
      snapButton_.setFocusable(false);
      snapButton_.setIconTextGap(6);
      snapButton_.setText("Snap");
      snapButton_.setMinimumSize(new Dimension(99,28));
      snapButton_.setPreferredSize(new Dimension(99,28));
      snapButton_.setMaximumSize(new Dimension(99,28));
      snapButton_.setIcon(SwingResourceManager.getIcon(
            MMStudioMainFrame.class, "/org/micromanager/icons/camera.png"));
      snapButton_.setFont(new Font("Arial", Font.PLAIN, 10));
      snapButton_.setToolTipText("Snap single image");
      snapButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            MMStudioMainFrame.getInstance().doSnap();
         }

      });

      liveButton_ = new JButton();
      liveButton_.setIcon(SwingResourceManager.getIcon(
            MMStudioMainFrame.class,
            "/org/micromanager/icons/camera_go.png"));
      liveButton_.setIconTextGap(6);
      liveButton_.setText("Live");
      liveButton_.setMinimumSize(new Dimension(99,28));
      liveButton_.setPreferredSize(new Dimension(99,28));
      liveButton_.setMaximumSize(new Dimension(99,28));
      liveButton_.setFocusable(false);
      liveButton_.setToolTipText("Continuous live view");
      liveButton_.setFont(new Font("Arial", Font.PLAIN, 10));
      liveButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            liveButtonAction();
         }
      });
    
      snapToAlbumButton_ = new JButton("Album");
      snapToAlbumButton_.setIcon(SwingResourceManager.getIcon(MMStudioMainFrame.class,
              "/org/micromanager/icons/camera_plus_arrow.png"));
      snapToAlbumButton_.setIconTextGap(6);
      snapToAlbumButton_.setToolTipText("Add current image to album");
      snapToAlbumButton_.setFocusable(false);
      snapToAlbumButton_.setMaximumSize(new Dimension(90, 28));
      snapToAlbumButton_.setMinimumSize(new Dimension(90, 28));
      snapToAlbumButton_.setPreferredSize(new Dimension(90, 28));
      snapToAlbumButton_.setFont(new Font("Arial", Font.PLAIN, 10));
      snapToAlbumButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            snapToAlbumButtonActionPerformed();
         }
      });

      subPanel.add(snapButton_);
      subPanel.add(liveButton_);
      subPanel.add(snapToAlbumButton_);
   }

   /**
    * Generate the controls used on a standard dataset display (i.e. not the 
    * snap/live window).
    */
   private void makeStandardButtons(JPanel subPanel) {
      fpsField_ = new javax.swing.JTextField(8);
      fpsLabel_ = new JLabel();
      abortButton_ = new JButton();
      pauseAndResumeToggleButton_ = new javax.swing.JToggleButton();
      
      subPanel.add(abortButton_);
      subPanel.add(pauseAndResumeToggleButton_);
      subPanel.add(fpsLabel_);
      subPanel.add(fpsField_);

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
            abortButtonActionPerformed(evt);
         }
      });

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
            pauseAndResumeToggleButtonActionPerformed(evt);
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
      int position = event.getPositionForAxis("position");
      display_.updatePosition(position);
      // Positions for ImageJ are 1-indexed but positions from the event are 
      // 0-indexed.
      int channel = event.getPositionForAxis("channel") + 1;
      int frame = event.getPositionForAxis("time") + 1;
      int slice = event.getPositionForAxis("z") + 1;
      display_.getHyperImage().setPosition(channel, slice, frame);
   }

   /**
    * Our ScrollerPanel is informing us that its layout has changed.
    */
   @Subscribe
   public void onLayoutChange(ScrollerPanel.LayoutChangedEvent event) {
      invalidate();
      validate();
   }

   private void showFolderButtonActionPerformed(java.awt.event.ActionEvent evt) {
      display_.showFolder();
   }

   private void fpsField_FocusLost(java.awt.event.FocusEvent evt) {
      updateFPS();
   }

   private void fpsField_KeyReleased(java.awt.event.KeyEvent evt) {
      updateFPS();
   }

   private void abortButtonActionPerformed(java.awt.event.ActionEvent evt) {
      display_.abort();
   }

   private void pauseAndResumeToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {
      display_.pause();
}

   private void saveButtonActionPerformed(java.awt.event.ActionEvent evt, final boolean isSimpleDisplay) {
      new Thread() {
         @Override
         public void run() {
            // We don't want to tie the Snap/Live display to a specific
            // file since its contents get overwritten regularly.
            display_.saveAs(!isSimpleDisplay);
         }
      }.start();
   }

   private void snapToAlbumButtonActionPerformed() {
      try {
         MMStudioMainFrame gui = MMStudioMainFrame.getInstance();
         gui.copyFromLiveModeToAlbum(display_);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void liveButtonAction() {
       MMStudioMainFrame.getInstance().enableLiveMode(!MMStudioMainFrame.getInstance().isLiveModeOn());
    }

   private void updateFPS() {
      // There's no FPS field when using the Snap/Live window
      if (fpsField_ != null) {
         try {
            double fps = NumberUtils.displayStringToDouble(fpsField_.getText());
            scrollerPanel_.setFramesPerSecond(fps);
         } catch (ParseException ex) {
            // No recognizable number (e.g. because the field is empty); just
            // do nothing.
         }
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
      // NB currently there's no situation in which one of these will be null
      // when the other isn't, but on the other hand who knows what the future
      // will bring?
      if (abortButton_ != null) {
         abortButton_.setEnabled(state);
      }
      if (pauseAndResumeToggleButton_ != null) {
         pauseAndResumeToggleButton_.setEnabled(state);
      }
   }

   @Override
   public void imagesOnDiskUpdate(boolean enabled) {
      showFolderButton_.setEnabled(enabled);
   }

   public void prepareForClose() {
      scrollerPanel_.prepareForClose();
      bus_.unregister(this);
   }
}
