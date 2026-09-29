///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
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
//

package org.micromanager.tileddataviewer.internal.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Insets;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.event.ChangeListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.display.DataViewer;
import org.micromanager.internal.utils.PopupButton;
import org.micromanager.tileddataviewer.internal.TiledDataViewer;


/**
 * Scrollbars + optional controls for explor acquisitons.
 *
 * @author Henry
 */
class SubImageControls extends JPanel {

   private static final int DEFAULT_FPS = 7;
   // Matches org.micromanager.data.Coords.TIME_POINT; axis names pass through the
   // AxesBridge unchanged.  Declared here to keep this GUI class free of a Coords import.
   private static final String TIME_AXIS = "time";
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private final Insets buttonInsets_ = new Insets(0, 5, 0, 5);
   private TiledDataViewer display_;
   private ScrollerPanel scrollerPanel_;
   private int displayHeight_ = -1;
   private JPanel controlsPanel_;
   private PopupButton playbackFpsButton_;
   private JSpinner playbackFpsSpinner_;
   private JLabel imageInfoLabel_;
   private JPanel gearButtonPanel_;
   private TiledDataViewerGearButton gearButton_;
   // Last text applied, to avoid pointless setText() during fast playback.
   private String lastImageInfoText_ = "";
   // True while the playback control is being updated programmatically.
   private boolean updatingPlaybackFps_ = false;

   public SubImageControls(TiledDataViewer disp) {
      super(new FlowLayout(FlowLayout.LEADING));
      display_ = disp;
      try {
         initComponents();
      } catch (Exception e) {
         throw new RuntimeException("Problem initializing subimage controls");
      }

   }
   
   public void onScollPositionChanged(AxisScroller scroller, int value) {
      scrollerPanel_.onScrollPositionChanged(scroller, value);
   }
   
   public void onDisplayClose() {
      display_ = null;
      for (ChangeListener l : playbackFpsSpinner_.getChangeListeners()) {
         playbackFpsSpinner_.removeChangeListener(l);
      }
      playbackFpsButton_ = null;
      playbackFpsSpinner_ = null;
      imageInfoLabel_ = null;
      if (gearButton_ != null) {
         gearButton_.cleanup();
         gearButton_ = null;
      }
      gearButtonPanel_ = null;
      controlsPanel_.removeAll();
      this.remove(controlsPanel_);
      scrollerPanel_.onDisplayClose();
      controlsPanel_ = null;
      scrollerPanel_ = null;
   }


   public void unlockAllScrollers() {
      scrollerPanel_.unlockAllScrollers();
   }

   public void superLockAllScroller() {
      scrollerPanel_.superlockAllScrollers();
   }

   void expandDisplayedRangeToInclude(List<HashMap<String, Object>> newIamgeEvents,
                                      List<String> channels) {
      scrollerPanel_.expandDisplayedRangeToInclude(newIamgeEvents, channels);
   }

   public static double parseDouble(String s) {
      try {
         return DecimalFormat.getNumberInstance().parse(s).doubleValue();
      } catch (ParseException ex) {
         throw new RuntimeException(ex);
      }
   }

   private void initComponents() {
      controlsPanel_ = new JPanel(new MigLayout("insets 0, fillx, align center", "", "[]0[]0[]"));

      // Status line, mirroring imageInfoLabel_ in the main Micro-Manager viewer:
      // elapsed time and channel name for the position currently displayed.
      imageInfoLabel_ = new JLabel(" ");
      imageInfoLabel_.setFont(imageInfoLabel_.getFont().deriveFont(10.0f));
      imageInfoLabel_.setMinimumSize(new Dimension(0, 10));
      // Indent to clear the window edge; roughly lines the text up with the scrollbars
      // below, which are preceded by their fixed-width animate icon.
      controlsPanel_.add(imageInfoLabel_, "split 3, growx, gapleft 6");

      // Playback speed control, matching the one in the main Micro-Manager viewer.
      // Start from the stored rate so a reopened dataset plays back as it did before.
      // Read via the display model, not getAnimateFPS(): this runs from the GuiManager
      // constructor, so the viewer's guiManager_ field is still null.
      double initialFps = DEFAULT_FPS;
      if (display_ != null && display_.getDisplayModel() != null) {
         initialFps = display_.getDisplayModel().getPlaybackFPS();
      }
      playbackFpsSpinner_ = new JSpinner(
              new FpsSpinnerNumberModel(initialFps, 1.0, 1000.0));
      playbackFpsSpinner_.addChangeListener(e -> handlePlaybackFpsSpinner());
      playbackFpsButton_ = PopupButton.create("", playbackFpsSpinner_);
      playbackFpsButton_.setFont(playbackFpsButton_.getFont().deriveFont(10.0f));
      int width = 24 + playbackFpsButton_.getFontMetrics(
              playbackFpsButton_.getFont()).stringWidth("Playback: 9999.0 fps");
      Dimension fpsButtonSize = new Dimension(width,
              new JLabel(" ").getPreferredSize().height + 12);
      playbackFpsButton_.setMinimumSize(fpsButtonSize);
      playbackFpsButton_.setMaximumSize(fpsButtonSize);
      playbackFpsButton_.setPreferredSize(fpsButtonSize);
      playbackFpsButton_.setMargin(buttonInsets_);
      playbackFpsButton_.addPopupButtonListener((PopupButton button) -> {
         if (display_ != null) {
            playbackFpsSpinner_.setValue(display_.getAnimateFPS());
         }
      });
      playbackFpsButton_.setText(fpsText(initialFps));
      // Only useful once there is an axis to play back along; shown by
      // onScrollersAdded() when the first scroller appears.
      playbackFpsButton_.setVisible(false);
      controlsPanel_.add(playbackFpsButton_, "hidemode 2, align right");
      // Placeholder cell for the gear button, installed later by
      // installGearButton(): the DataViewer and Studio it needs do not exist yet
      // when this panel is built (TiledDataViewer's constructor builds the UI
      // before TiledDataViewerDataViewer finishes constructing).
      gearButtonPanel_ = new JPanel(new MigLayout("insets 0"));
      controlsPanel_.add(gearButtonPanel_, "align right, wrap");

      scrollerPanel_ = new ScrollerPanel(display_, DEFAULT_FPS);
      controlsPanel_.add(scrollerPanel_, "span, growx, wrap");

      this.setLayout(new BorderLayout());
      this.add(controlsPanel_, BorderLayout.CENTER);
   }

   /**
    * Shows the given playback rate in the control without notifying listeners, so that
    * restoring a stored rate does not look like a user edit.
    *
    * @param fps playback rate in frames per second
    */
   void setPlaybackFPSControl(double fps) {
      if (playbackFpsSpinner_ == null || playbackFpsButton_ == null) {
         return; // Display is closing.
      }
      updatingPlaybackFps_ = true;
      try {
         playbackFpsSpinner_.setValue(fps);
         playbackFpsButton_.setText(fpsText(fps));
      } finally {
         updatingPlaybackFps_ = false;
      }
   }

   /**
    * Installs the gear button once the owning DataViewer exists.
    *
    * <p>Deferred because this panel is built from TiledDataViewer's constructor,
    * before TiledDataViewerDataViewer (the DataViewer the plugins act on) has
    * finished constructing.
    *
    * @param viewer viewer whose gear menu this is
    * @param studio the Studio, used to discover gear menu plugins
    */
   void installGearButton(DataViewer viewer, Studio studio) {
      if (gearButtonPanel_ == null || gearButton_ != null) {
         return; // Closing, or already installed.
      }
      gearButton_ = new TiledDataViewerGearButton(viewer, studio);
      gearButton_.setMargin(buttonInsets_);
      // Pin the button to the height the row already has. Left to size itself it
      // is taller than the status label and playback button, which grows the
      // controls panel; onScrollersAdded() then resizes this panel to match, the
      // canvas is resized in turn, and the re-render that follows shows up as a
      // flickering display.
      int rowHeight = playbackFpsButton_ != null
            ? playbackFpsButton_.getPreferredSize().height
            : new JLabel(" ").getPreferredSize().height + 12;
      Dimension gearSize = new Dimension(rowHeight, rowHeight);
      gearButton_.setMinimumSize(gearSize);
      gearButton_.setMaximumSize(gearSize);
      gearButton_.setPreferredSize(gearSize);
      gearButtonPanel_.add(gearButton_);
      gearButtonPanel_.revalidate();
   }

   private void handlePlaybackFpsSpinner() {
      if (updatingPlaybackFps_) {
         return; // Programmatic update, not a user edit.
      }
      if (display_ == null || playbackFpsSpinner_ == null) {
         return; // Display is closing.
      }
      double fps = ((Number) playbackFpsSpinner_.getValue()).doubleValue();
      display_.setAnimateFPS(fps);
      playbackFpsButton_.setText(fpsText(fps));
   }

   private static String fpsText(double fps) {
      return String.format("Playback: %.1f fps", fps);
   }

   public void onScrollersAdded() {
      playbackFpsButton_.setVisible(true);
      this.setPreferredSize(new Dimension(this.getPreferredSize().width,
              controlsPanel_.getPreferredSize().height));
      this.invalidate();
      this.validate();
      this.getParent().doLayout();
   }

   void updateScrollerPositions(DataViewCoords view) {
      for (AxisScroller a : scrollerPanel_.scrollers_) {
         Object axisPosition = view.getAxisPosition(a.getAxis());
         if (axisPosition instanceof Integer) {
            a.setPosition((Integer) axisPosition);
         } else {
            a.setPosition(display_.getDisplayModel().getIntegerPositionFromStringPosition(
                     a.getAxis(), (String) axisPosition));
         }
      }
      updateImageInfoLabel(view);
   }

   /**
    * Updates the status line with the elapsed time and channel name of the position
    * being displayed, in the style of the main Micro-Manager viewer.  Each part is
    * only shown when the corresponding axis exists and has more than one position.
    */
   private void updateImageInfoLabel(DataViewCoords view) {
      if (imageInfoLabel_ == null || display_ == null) {
         return; // Display is closing.
      }
      StringBuilder sb = new StringBuilder();
      if (hasMultiplePositions(TIME_AXIS)) {
         String elapsed = display_.getElapsedTimeLabel();
         if (elapsed != null && !elapsed.isEmpty()) {
            sb.append(elapsed).append(" ");
         } else {
            // No usable timestamp (e.g. datasets written by the Stitch plugin carry no
            // ElapsedTime-ms tag).  Show the time index instead, as the main viewer does,
            // rather than a misleading "0ms".
            Object t = view.getAxisPosition(TIME_AXIS);
            if (t instanceof Integer) {
               sb.append("t=").append(t).append(" ");
            }
         }
      }
      if (hasMultiplePositions(TiledDataViewer.CHANNEL_AXIS)) {
         Object channel = view.getAxisPosition(TiledDataViewer.CHANNEL_AXIS);
         if (channel instanceof String && !TiledDataViewer.NO_CHANNEL.equals(channel)) {
            sb.append((String) channel).append(" ");
         }
      }
      String text = sb.toString();
      if (text.isEmpty()) {
         text = " "; // Keep the label's height stable.
      }
      if (!text.equals(lastImageInfoText_)) {
         lastImageInfoText_ = text;
         imageInfoLabel_.setText(text);
      }
   }

   /**
    * Returns true when the given axis has a scroller spanning more than one position.
    * The main viewer suppresses status-line entries for singleton axes.
    */
   private boolean hasMultiplePositions(String axis) {
      for (AxisScroller a : scrollerPanel_.scrollers_) {
         if (a.getAxis().equals(axis)) {
            // getMinimum()/getMaximum() are an inclusive range, so two or more positions
            // means the span is at least 1.
            return a.getMaximum() - a.getMinimum() > 0;
         }
      }
      return false;
   }

   boolean isScrollerLocked(String axis) {
      for (AxisScroller a : scrollerPanel_.scrollers_) {
         if (a.getAxis().equals(axis)) {
            return a.getIsSuperlocked();
         }
      }
      throw new RuntimeException("uknown axis " + axis);
   }

}
