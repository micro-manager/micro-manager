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
import org.micromanager.internal.utils.PopupButton;
import org.micromanager.tileddataviewer.internal.TiledDataViewer;


/**
 * Scrollbars + optional controls for explor acquisitons.
 *
 * @author Henry
 */
class SubImageControls extends JPanel {

   private static final int DEFAULT_FPS = 7;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private final Insets buttonInsets_ = new Insets(0, 5, 0, 5);
   private TiledDataViewer display_;
   private ScrollerPanel scrollerPanel_;
   private int displayHeight_ = -1;
   private JPanel controlsPanel_;
   private PopupButton playbackFpsButton_;
   private JSpinner playbackFpsSpinner_;

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

      // Playback speed control, matching the one in the main Micro-Manager viewer.
      playbackFpsSpinner_ = new JSpinner(
              new FpsSpinnerNumberModel(DEFAULT_FPS, 1.0, 1000.0));
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
      playbackFpsButton_.setText(fpsText(DEFAULT_FPS));
      // Only useful once there is an axis to play back along; shown by
      // onScrollersAdded() when the first scroller appears.
      playbackFpsButton_.setVisible(false);
      controlsPanel_.add(playbackFpsButton_, "hidemode 2, align right, wrap");

      scrollerPanel_ = new ScrollerPanel(display_, DEFAULT_FPS);
      controlsPanel_.add(scrollerPanel_, "span, growx, wrap");

      this.setLayout(new BorderLayout());
      this.add(controlsPanel_, BorderLayout.CENTER);
   }

   private void handlePlaybackFpsSpinner() {
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
