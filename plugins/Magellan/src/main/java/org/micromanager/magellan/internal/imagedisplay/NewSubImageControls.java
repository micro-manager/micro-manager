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
package org.micromanager.magellan.internal.imagedisplay;

import org.micromanager.magellan.internal.imagedisplay.events.ExploreZLimitsChangedEvent;
import org.micromanager.magellan.internal.imagedisplay.events.ScrollersAddedEvent;
import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.text.DecimalFormat;
import java.util.List;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.JavaUtils;
import org.micromanager.magellan.internal.misc.Log;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.micromanager.magellan.internal.imagedisplay.events.MagellanScrollbarPosition;
import org.micromanager.magellan.internal.misc.NumberUtils;

/**
 * Scrollbars + optional controls for explor acquisitons
 *
 * @author Henry
 */
class NewSubImageControls extends JPanel {

   private final static int DEFAULT_FPS = 7;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private MagellanDisplayController display_;
   private ScrollerPanel scrollerPanel_;
   private JPanel sliderPanel_;
   private JScrollBar zTopScrollbar_, zBottomScrollbar_;
   private JTextField zTopTextField_, zBottomTextField_;
   private double zStep_, zOrigin_;
   private int displayHeight_ = -1;
   //thread safe fields for currently displaye dimage
   private final boolean explore_;
   private int minZExplored_ = Integer.MAX_VALUE, maxZExplored_ = Integer.MIN_VALUE;
   private JPanel controlsPanel_;

   public NewSubImageControls(MagellanDisplayController disp, double zStep, boolean explore) {
      super(new FlowLayout(FlowLayout.LEADING));
      explore_ = explore;
      display_ = disp;
      display_.registerForEvents(this);
      zStep_ = zStep;
      try {
         initComponents();
      } catch (Exception e) {
         Log.log("Problem initializing subimage controls");
         Log.log(e);
      }

   }

   public void onDisplayClose() {
      display_.unregisterForEvents(this);
      display_ = null;
      controlsPanel_.removeAll();
      this.remove(controlsPanel_);
      if (zBottomScrollbar_ != null) {
         for (AdjustmentListener l : zBottomScrollbar_.getAdjustmentListeners()) {
            zBottomScrollbar_.removeAdjustmentListener(l);
         }
         for (AdjustmentListener l : zTopScrollbar_.getAdjustmentListeners()) {
            zTopScrollbar_.removeAdjustmentListener(l);
         }
         for (ActionListener l : zBottomTextField_.getActionListeners()) {
            zBottomTextField_.removeActionListener(l);
         }
         for (ActionListener l : zTopTextField_.getActionListeners()) {
            zTopTextField_.removeActionListener(l);
         }
      }
      scrollerPanel_.onDisplayClose();
      controlsPanel_ = null;
      scrollerPanel_ = null;
   }

   /**
    * used for forcing scrollbars to show when opening dataset on disk
    */
   public void makeScrollersAppear(int numChannels, int numSlices, int numFrames) {
      for (AxisScroller s : scrollerPanel_.scrollers_) {
         if (numChannels > 1 && s.getAxis().equals("c")) {
            s.setVisible(true);
            s.setMaximum(numChannels);
            scrollerPanel_.add(s, "wrap 0px, align center, growx");
         } else if (numFrames > 1 && s.getAxis().equals("t")) {
            s.setVisible(true);
            s.setMaximum(numFrames);
            scrollerPanel_.add(s, "wrap 0px, align center, growx");
         } else if (numSlices > 1 && s.getAxis().equals("z")) {
            s.setVisible(true);
            s.setMaximum(numSlices);
            scrollerPanel_.add(s, "wrap 0px, align center, growx");
         }
      }
      display_.postEvent(new ScrollersAddedEvent());
   }

   public void unlockAllScrollers() {
      scrollerPanel_.unlockAllScrollers();
   }

   public void superLockAllScroller() {
      scrollerPanel_.superlockAllScrollers();
   }

   public void setAnimateFPS(double fps) {
      scrollerPanel_.setFramesPerSecond(fps);
   }

   private void updateZTopAndBottom() {
      //Update the text fields next to the sliders in response to adjustment
      double zBottom = zStep_ * zBottomScrollbar_.getValue() + zOrigin_;
      zBottomTextField_.setText(TWO_DECIMAL_FORMAT.format(zBottom));
      double zTop = zStep_ * zTopScrollbar_.getValue() + zOrigin_;
      zTopTextField_.setText(TWO_DECIMAL_FORMAT.format(zTop));
      //Update the acquisition 
      display_.postEvent(new ExploreZLimitsChangedEvent(zTop, zBottom));
      //update colored areas on z scrollbar
      //convert to 0 based index based on which slices have been explored
   }

   public void setZLimitSliderValues(int sliceIndex) {
      //first expand scrollbars as needed
      if (zTopScrollbar_.getMaximum() < sliceIndex + 1) {
         zTopScrollbar_.setMaximum(sliceIndex + 2);
      }
      if (zBottomScrollbar_.getMaximum() < sliceIndex + 1) {
         zBottomScrollbar_.setMaximum(sliceIndex + 2);
      }
      if (zTopScrollbar_.getMinimum() > sliceIndex - 1) {
         zTopScrollbar_.setMinimum(sliceIndex - 1);
      }
      if (zBottomScrollbar_.getMinimum() > sliceIndex - 1) {
         zBottomScrollbar_.setMinimum(sliceIndex - 1);
      }
//       now set sliders to current position 
      zBottomScrollbar_.setValue(sliceIndex);
      zTopScrollbar_.setValue(sliceIndex);
      this.repaint();

   }

   private void expandZLimitsIfNeeded(int topScrollbarIndex, int bottomScrollbarIndex) {
      //extent of 1 needs to be accounted for on top
      if (topScrollbarIndex >= zTopScrollbar_.getMaximum() - 1 || bottomScrollbarIndex >= zBottomScrollbar_.getMaximum() - 1) {
         zTopScrollbar_.setMaximum(Math.max(topScrollbarIndex, bottomScrollbarIndex) + 2);
         zBottomScrollbar_.setMaximum(Math.max(topScrollbarIndex, bottomScrollbarIndex) + 2);
      }
      if (bottomScrollbarIndex <= zBottomScrollbar_.getMinimum() || topScrollbarIndex <= zTopScrollbar_.getMinimum()) {
         zTopScrollbar_.setMinimum(Math.min(bottomScrollbarIndex, topScrollbarIndex) - 1);
         zBottomScrollbar_.setMinimum(Math.min(bottomScrollbarIndex, topScrollbarIndex) - 1);
      }
      this.repaint();
   }

   void expandDisplayedRangeToInclude(List<MagellanScrollbarPosition> newIamgeEvents) {
      scrollerPanel_.expandDisplayedRangeToInclude(newIamgeEvents);
   }

   private void zTopTextFieldAction() {
      //check if new position is outside bounds of current z range
      //and if so expand sliders as needed
      double val = NumberUtils.parseDouble(zTopTextField_.getText());
      int newSliderindex = (int) Math.round((val - zOrigin_) / zStep_);
      expandZLimitsIfNeeded(newSliderindex, zBottomScrollbar_.getValue());
      //now that scollbar expanded, set value
      zTopScrollbar_.setValue(newSliderindex);
      updateZTopAndBottom();
   }

   private void zBottomTextFieldAction() {
      //check if new position is outside bounds of current z range
      //and if so expand sliders as needed
      double val = NumberUtils.parseDouble(zBottomTextField_.getText());
      int newSliderindex = (int) Math.round((val - zOrigin_) / zStep_);
      expandZLimitsIfNeeded(zTopScrollbar_.getValue(), newSliderindex);
      zBottomScrollbar_.setValue(newSliderindex);
      updateZTopAndBottom();

   }

   private void zTopSliderAdjustment() {
      //Top must be <= to bottom
      if (zTopScrollbar_.getValue() > zBottomScrollbar_.getValue()) {
         zBottomScrollbar_.setValue(zTopScrollbar_.getValue());
      }
      expandZLimitsIfNeeded(zTopScrollbar_.getValue(), zBottomScrollbar_.getValue());
      updateZTopAndBottom();
   }

   private void zBottomSliderAdjustment() {
      //Top must be <= to bottom
      if (zTopScrollbar_.getValue() > zBottomScrollbar_.getValue()) {
         zTopScrollbar_.setValue(zBottomScrollbar_.getValue());
      }
      expandZLimitsIfNeeded(zTopScrollbar_.getValue(), zBottomScrollbar_.getValue());
      updateZTopAndBottom();
   }

   private void initComponents() {
      controlsPanel_ = new JPanel(new MigLayout("insets 0, fillx, align center", "", "[]0[]0[]"));

      scrollerPanel_ = new ScrollerPanel(display_, new String[]{"c", "t", "r", "z"}, new Integer[]{1, 1, 1, 1}, DEFAULT_FPS);
      controlsPanel_.add(scrollerPanel_, "span, growx, wrap");

      if (explore_) {
         sliderPanel_ = new JPanel(new MigLayout("insets 0", "[][][grow]", ""));

         CMMCore core = Magellan.getCore();
         String z = core.getFocusDevice();
         try {
            zOrigin_ = core.getPosition(z);
         } catch (Exception ex) {
            Log.log("couldn't get z postition from core", true);
         }
         //Initialize z to current position with space to move one above or below           
         //value, extent, min, max
         //max value of scrollbar is max - extent
         try {
            zTopScrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, -1, 2);
            zBottomScrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, -1, 2);
            zTopScrollbar_.setUI(new ColorableScrollbarUI());
            zBottomScrollbar_.setUI(new ColorableScrollbarUI());
         } catch (Exception e) {
            Log.log("problem creating z limit scrollbars", true);
            Log.log(e);
         }
         zTopTextField_ = new JTextField(zOrigin_ + "");
         zTopTextField_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
               zTopTextFieldAction();
            }
         });
         zBottomTextField_ = new JTextField(zOrigin_ + "");
         zBottomTextField_.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ae) {
               zBottomTextFieldAction();
            }
         });
         zTopScrollbar_.addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent ae) {
               zTopSliderAdjustment();
            }
         });
         zBottomScrollbar_.addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent ae) {
               zBottomSliderAdjustment();
            }
         });
         double zPos;
         try {
            //initialize properly
            zPos = Magellan.getCore().getPosition();
         } catch (Exception ex) {
            throw new RuntimeException(ex);
         }
         zTopTextField_.setText(zPos + "");
         zBottomTextField_.setText(zPos + "");
         zTopTextField_.getActionListeners()[0].actionPerformed(null);
         zBottomTextField_.getActionListeners()[0].actionPerformed(null);

         sliderPanel_.add(new JLabel("Z limits"), "span 1 2");
         if (JavaUtils.isMac()) {
            sliderPanel_.add(zTopTextField_, "w 80!");
            sliderPanel_.add(zTopScrollbar_, "growx, wrap");
            sliderPanel_.add(zBottomTextField_, "w 80!");
            sliderPanel_.add(zBottomScrollbar_, "growx");
         } else {
            sliderPanel_.add(zTopTextField_, "w 50!");
            sliderPanel_.add(zTopScrollbar_, "growx, wrap");
            sliderPanel_.add(zBottomTextField_, "w 50!");
            sliderPanel_.add(zBottomScrollbar_, "growx");

         }
         controlsPanel_.add(sliderPanel_, "span, growx, align center, wrap");

      }

      this.setLayout(new BorderLayout());
      this.add(controlsPanel_, BorderLayout.CENTER);

      // Propagate resizing through to our JPanel
      this.addComponentListener(new ComponentAdapter() {

         public void componentResized(ComponentEvent e) {
            Dimension curSize = getSize(); //size of subimage controls
            //expand window when new scrollbars shown for fixed acq
            if (!explore_) {
               if (displayHeight_ == -1) {
                  displayHeight_ = curSize.height;
               } else if (curSize.height != displayHeight_) {
                  //don't expand window bigger that max viewable area on scren
                  int maxHeight = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds().height;
                  JFrame window = (JFrame) SwingUtilities.getWindowAncestor(NewSubImageControls.this);
                  window.setSize(new Dimension(window.getWidth(),
                          Math.min(maxHeight, window.getHeight() + (curSize.height - displayHeight_))));
                  displayHeight_ = curSize.height;
               }
            }
            NewSubImageControls.this.getParent().invalidate();
            NewSubImageControls.this.getParent().validate();
         }
      });
   }

   public void updateExploreZControls(int currentZ) {
      if (!explore_) {
         return;
      }
      //convert slice index to explore scrollbar index       
      minZExplored_ = Math.min(minZExplored_, currentZ);
      maxZExplored_ = Math.max(maxZExplored_, currentZ);
      ((ColorableScrollbarUI) zTopScrollbar_.getUI()).setHighlightedIndices(currentZ, minZExplored_, maxZExplored_);
      ((ColorableScrollbarUI) zBottomScrollbar_.getUI()).setHighlightedIndices(currentZ, minZExplored_, maxZExplored_);
      this.repaint();
   }

   @Subscribe
   public void onScrollersAdded(ScrollersAddedEvent event) {
      this.setPreferredSize(new Dimension(this.getPreferredSize().width,
              scrollerPanel_.getPreferredSize().height + (sliderPanel_ != null ? sliderPanel_.getPreferredSize().height : 0)));
      this.invalidate();
      this.validate();
      this.getParent().doLayout();
//      SwingUtilities.invokeLater(new Runnable() {
//
//         @Override
//         public void run() {
//            if (explore_) {
//               display_.fitExploreCanvasToWindow();
//            }
//         }
//      });
   }

   void updateScrollerPositions(MagellanDataViewCoords view) {
      for (AxisScroller a : scrollerPanel_.scrollers_) {
         if (a.getAxis().equals("c")) {
            a.setPosition(view.getAxisPosition("c"));
         } else if (a.getAxis().equals("z")) {
            a.setPosition(view.getAxisPosition("z"));
         } else if (a.getAxis().equals("t")) {
            a.setPosition(view.getAxisPosition("t"));
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
