/*
 * HyperstackControls.java
 *
 * Created on Jul 15, 2010, 2:54:37 PM
 */
package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.swtdesigner.SwingResourceManager;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentAdapter;
import java.awt.FlowLayout;
import java.awt.Font;
import java.lang.Math;
import java.text.ParseException;
import java.util.concurrent.TimeUnit;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

import net.imglib2.RandomAccess;

import net.miginfocom.swing.MigLayout;

import org.json.JSONException;
import org.json.JSONObject;

import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;
import org.micromanager.api.display.DrawEvent;

import org.micromanager.data.AbortEvent;
import org.micromanager.data.DefaultCoords;
import org.micromanager.data.NewImageEvent;
import org.micromanager.imagedisplay.MouseIntensityEvent;
import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.internalinterfaces.LiveModeListener;
import org.micromanager.MMStudio;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.NumberUtils;
import org.micromanager.utils.ReportingUtils;


public class HyperstackControls extends DisplayControls implements LiveModeListener {

   private final static int DEFAULT_FPS = 10;
   private final static double MAX_FPS = 5000;
   // Height in pixels of our controls, not counting scrollbars.
   private final static int CONTROLS_HEIGHT = 65;

   private EventBus displayBus_;
   private Datastore store_;
   private MMVirtualStack stack_;

   // Last known mouse positions.
   private int mouseX_ = -1;
   private int mouseY_ = -1;

   // JPanel that holds all controls.
   private JPanel subPanel_;
   // Controls common to both control sets
   private ScrollerPanel scrollerPanel_;
   private JLabel pixelInfoLabel_;
   // Displays information on the currently-displayed image.
   private JLabel imageInfoLabel_;
   // Displays the countdown to the next frame.
   private JLabel countdownLabel_;
   private JButton showFolderButton_;
   private JButton saveButton_;
   private JLabel fpsLabel_;

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
      MMStudio.getInstance().getSnapLiveManager().addLiveModeListener(this);
   }

   private void initComponents(final boolean shouldUseLiveControls) {
      // This layout minimizes space between components.
      subPanel_ = new JPanel(new MigLayout("insets 0, fillx, align center"));

      JPanel labelsPanel = new JPanel(new MigLayout("insets 0"));
      pixelInfoLabel_ = new JLabel("                                         ");
      pixelInfoLabel_.setMinimumSize(new Dimension(150, 10));
      pixelInfoLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      labelsPanel.add(pixelInfoLabel_);
      
      imageInfoLabel_ = new JLabel("                                         ");
      imageInfoLabel_.setMinimumSize(new Dimension(150, 10));
      imageInfoLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      labelsPanel.add(imageInfoLabel_);
      
      countdownLabel_ = new JLabel("                                         ");
      countdownLabel_.setMinimumSize(new Dimension(150, 10));
      countdownLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      labelsPanel.add(countdownLabel_);

      subPanel_.add(labelsPanel, "span, growx, align center, wrap");

      scrollerPanel_ = new ScrollerPanel(store_, displayBus_, DEFAULT_FPS);
      subPanel_.add(scrollerPanel_, "span, growx, wrap 0px");

      // Hacky layout to minimize gaps between components. 
      JPanel buttonPanel = new JPanel(new MigLayout("insets 0", 
               "[]0[]0[]0[]0[]0[]"));

      showFolderButton_ = new JButton();
      saveButton_ = new JButton();

      buttonPanel.add(showFolderButton_);
      buttonPanel.add(saveButton_);

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
            saveButtonActionPerformed(evt, shouldUseLiveControls);
         }
      });

      // This control is added by both Snap/Live, and Standard, but in 
      // different places on each. 
      fpsLabel_ = new JLabel("                      ", SwingConstants.RIGHT);
      fpsLabel_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      fpsLabel_.setFocusable(false);

      if (shouldUseLiveControls) {
         makeSnapLiveControls(buttonPanel);
      }
      else {
         makeStandardControls(buttonPanel);
      }
      
      subPanel_.add(buttonPanel);
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
    * Generate the controls used for the "Snap/Live" window.
    */
   private void makeSnapLiveControls(JPanel buttonPanel) {
      snapButton_ = new JButton();
      snapButton_.setFocusable(false);
      snapButton_.setIconTextGap(6);
      snapButton_.setText("Snap");
      snapButton_.setMinimumSize(new Dimension(90,28));
      snapButton_.setPreferredSize(new Dimension(90,28));
      snapButton_.setMaximumSize(new Dimension(90,28));
      snapButton_.setIcon(SwingResourceManager.getIcon(
            MMStudio.class, "/org/micromanager/icons/camera.png"));
      snapButton_.setFont(new Font("Arial", Font.PLAIN, 10));
      snapButton_.setToolTipText("Snap single image");
      snapButton_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            MMStudio.getInstance().doSnap();
         }

      });

      liveButton_ = new JButton();
      liveButton_.setIcon(SwingResourceManager.getIcon(
            MMStudio.class,
            "/org/micromanager/icons/camera_go.png"));
      liveButton_.setIconTextGap(6);
      liveButton_.setText("Live");
      liveButton_.setMinimumSize(new Dimension(90,28));
      liveButton_.setPreferredSize(new Dimension(90,28));
      liveButton_.setMaximumSize(new Dimension(90,28));
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
      snapToAlbumButton_.setIcon(SwingResourceManager.getIcon(MMStudio.class,
              "/org/micromanager/icons/arrow_right.png"));
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

      buttonPanel.add(snapButton_);
      buttonPanel.add(liveButton_);
      buttonPanel.add(snapToAlbumButton_);
      fpsLabel_.setText("                          ");
      buttonPanel.add(fpsLabel_, "span, wrap, width 120px, align right");
   }

   /**
    * Generate the controls used on a standard dataset display (i.e. not the 
    * snap/live window).
    */
   private void makeStandardControls(JPanel buttonPanel) {
      fpsField_ = new javax.swing.JTextField(String.valueOf(DEFAULT_FPS), 4);
      abortButton_ = new JButton();
      pauseAndResumeToggleButton_ = new javax.swing.JToggleButton();
      
      buttonPanel.add(abortButton_);
      buttonPanel.add(pauseAndResumeToggleButton_);
      // Make a new panel to hold the FPS info, since they need to be 
      // together.
      JPanel fpsPanel = new JPanel(new MigLayout("insets 0"));
      fpsPanel.add(fpsLabel_);
      fpsPanel.add(fpsField_);

      buttonPanel.add(fpsPanel, "span, gapleft push, wrap");

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
      mouseX_ = event.x_;
      mouseY_ = event.y_;
      setPixelInfo(mouseX_, mouseY_, event.intensities_[0]);
   }

   /**
    * Update our pixel info text.
    */
   private void setPixelInfo(int x, int y, int intensity) {
      pixelInfoLabel_.setText(String.format("x=%d, y=%d, value=%d",
               x, y, intensity));
   }

   /**
    * Our ScrollerPanel is informing us that we need to display a different
    * image.
    */
   @Subscribe
   public void onSetImage(ScrollerPanel.SetImageEvent event) {
      DefaultCoords.Builder builder = new DefaultCoords.Builder();
      for (String axis : event.getAxes()) {
         builder.position(axis, event.getPositionForAxis(axis));
      }
      stack_.setCoords(builder.build());
      displayBus_.post(new DrawEvent());
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
            int intensity = (int) image.getIntensityAt(mouseX_, mouseY_);
            setPixelInfo(mouseX_, mouseY_, intensity);
         }
         catch (Exception e) {
            ReportingUtils.logError(e, "Error in HyperstackControls onNewImage");
         }
      }
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
      ReportingUtils.logError("TODO: show folder button");
   }

   private void fpsField_FocusLost(java.awt.event.FocusEvent evt) {
      updateFPS();
   }

   private void fpsField_KeyReleased(java.awt.event.KeyEvent evt) {
      updateFPS();
   }

   private void abortButtonActionPerformed(java.awt.event.ActionEvent evt) {
      store_.publishEvent(new AbortEvent());
   }

   private void pauseAndResumeToggleButtonActionPerformed(java.awt.event.ActionEvent evt) {
      ReportingUtils.logError("TODO: pause button");
   }

   private void saveButtonActionPerformed(java.awt.event.ActionEvent evt, final boolean isSimpleDisplay) {
      new Thread() {
         @Override
         public void run() {
            // We don't want to tie the Snap/Live display to a specific
            // file since its contents get overwritten regularly.
            ReportingUtils.logError("TODO: save button");
         }
      }.start();
   }

   private void snapToAlbumButtonActionPerformed() {
      try {
         ReportingUtils.logError("TODO: snap to album");
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   private void liveButtonAction() {
       MMStudio.getInstance().enableLiveMode(!MMStudio.getInstance().isLiveModeOn());
    }

   private void updateFPS() {
      // There's no FPS field when using the Snap/Live window
      if (fpsField_ != null) {
         try {
            double fps = NumberUtils.displayStringToDouble(fpsField_.getText());
            // Constrain the FPS to a sane range.
            fps = Math.max(1.0, Math.min(fps, MAX_FPS));
            scrollerPanel_.setFramesPerSecond(fps);
         } catch (ParseException ex) {
            // No recognizable number (e.g. because the field is empty); just
            // do nothing.
         }
      }
   }

   @Override
   public synchronized void setImageInfoLabel(String text) {
      imageInfoLabel_.setText(text);
   }

   private void updateStatusLine(JSONObject tags) {
      String status = "";
      try {
         String xyPosition;
         try {
            xyPosition = MDUtils.getPositionName(tags);
            if (xyPosition != null && !xyPosition.contentEquals("null")) {
               status += xyPosition + ", ";
            }
         } catch (Exception e) {
            //Oh well...
         }

         try {
            double seconds = MDUtils.getElapsedTimeMs(tags) / 1000;
            status += elapsedTimeDisplayString(seconds);
         } catch (JSONException ex) {
            ReportingUtils.logError("MetaData did not contain ElapsedTime-ms field");
         }

         String zPosition;
         try {
            zPosition = NumberUtils.doubleToDisplayString(MDUtils.getZPositionUm(tags));
            status += ", z: " + zPosition + " um";
         } catch (Exception e) {
         }
         String chan;
         try {
            chan = MDUtils.getChannelName(tags);
            if (chan != null && !chan.contentEquals("null")) {
               status += ", " + chan;
            }
         } catch (Exception ex) {
         }

         setImageInfoLabel(status);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }

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

   @Override
   public void newImageUpdate(JSONObject tags) {
      ReportingUtils.logError("TODO: new image update");
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
      displayBus_.unregister(this);
      MMStudio.getInstance().getSnapLiveManager().removeLiveModeListener(this);
   }

   @Override
   public void setPosition(int p) {
      scrollerPanel_.setPosition("position", p);
   }

   @Override
   public int getPosition() {
      return scrollerPanel_.getPosition("position");
   }

   @Override
   public void setChannel(int c) {
      scrollerPanel_.setPosition("channel", c);
   }

   // TODO: implement after this replaces the existing display system.
//   /**
//    * New information on our FPS; update a label.
//    */
//   @Subscribe
//   public void onFPSUpdate(FPSEvent event) {
//      // Default to assuming we'll be blanking the label.
//      String newLabel = "";
//      if (event.getDataFPS() != 0) {
//         newLabel = String.format("FPS: %.1f (display %.1f)", 
//               event.getDataFPS(), event.getDisplayFPS());
//      }
//      else if (fpsField_ != null) {
//         // No new data, but we do have an FPS text field for animations, so
//         // switch fpsLabel_ to being an indicator for that. 
//         newLabel = "Playback FPS:";
//      }
//      fpsLabel_.setText(newLabel);
//   }

   /**
    * Live mode was toggled; if we have a "live mode" button, it needs to be 
    * toggled on/off; likewise, the Snap button should be disabled/enabled.
    */
   public void liveModeEnabled(boolean isEnabled) {
      if (liveButton_ == null) {
         return;
      }
      String label = isEnabled ? "Stop Live" : "Live";
      String iconPath = isEnabled ? "/org/micromanager/icons/cancel.png" : "/org/micromanager/icons/camera_go.png";
      liveButton_.setIcon(
            SwingResourceManager.getIcon(MMStudio.class, iconPath));
      liveButton_.setText(label);
      if (snapButton_ != null) {
         snapButton_.setEnabled(!isEnabled);
      }
   }

   public int getNumPositions() {
      return scrollerPanel_.getMaxPosition("position");
   }
}
