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
package org.micromanager.magellan.internal.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.text.DecimalFormat;
import java.text.ParseException;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.JTextField;
import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;
import org.micromanager.magellan.internal.magellanacq.MagellanDataManager;
import org.micromanager.magellan.internal.main.Magellan;
import org.micromanager.magellan.internal.misc.JavaUtils;
import org.micromanager.magellan.internal.misc.Log;

/**
 * controls for explor acquisitons
 *
 * @author Henry
 */
class ExploreZSliders extends JPanel {

   private final static int DEFAULT_FPS = 7;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private MagellanDataManager manager_;
   private JPanel sliderPanel_;
   private JScrollBar zTopScrollbar_, zBottomScrollbar_;
   private JTextField zTopTextField_, zBottomTextField_;
   private double zStep_, zOrigin_;
   private int displayHeight_ = -1;
   //thread safe fields for currently displaye dimage
   private int minZExplored_ = Integer.MAX_VALUE, maxZExplored_ = Integer.MIN_VALUE;
   private JPanel controlsPanel_;

   public ExploreZSliders(MagellanDataManager manager) {
      super(new FlowLayout(FlowLayout.LEADING));
      manager_ = manager;
      zStep_ = manager.getZStep();
      try {
         initComponents();
      } catch (Exception e) {
         throw new RuntimeException("Problem initializing subimage controls");
      }

   }

   @Override
   public Dimension getPreferredSize() {
      return new Dimension(this.getParent().getSize().width, super.getPreferredSize().height);
   }

   public void onDisplayClose() {
      manager_ = null;
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
      controlsPanel_ = null;
   }

   private void updateZTopAndBottom() {
      //Update the text fields next to the sliders in response to adjustment
      double zBottom = zStep_ * zBottomScrollbar_.getValue() + zOrigin_;
      zBottomTextField_.setText(TWO_DECIMAL_FORMAT.format(zBottom));
      double zTop = zStep_ * zTopScrollbar_.getValue() + zOrigin_;
      zTopTextField_.setText(TWO_DECIMAL_FORMAT.format(zTop));
      //Update the acquisition 
      manager_.setExploreZLimits(zTop, zBottom);
      //update colored areas on z scrollbar
      //convert to 0 based index based on which slices have been explored
   }

   public void updateExploreZControls(int currentZ) {
      //convert slice index to explore scrollbar index       
      minZExplored_ = Math.min(minZExplored_, currentZ);
      maxZExplored_ = Math.max(maxZExplored_, currentZ);
      ((ColorableScrollbarUI) zTopScrollbar_.getUI()).setHighlightedIndices(currentZ, minZExplored_, maxZExplored_);
      ((ColorableScrollbarUI) zBottomScrollbar_.getUI()).setHighlightedIndices(currentZ, minZExplored_, maxZExplored_);
      expandZLimitSliders(currentZ);
      this.repaint();
   }

   public void expandZLimitSliders(int sliceIndex) {
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
//      zBottomScrollbar_.setValue(sliceIndex);
//      zTopScrollbar_.setValue(sliceIndex);
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

   private void zTopTextFieldAction() {
      //check if new position is outside bounds of current z range
      //and if so expand sliders as needed
      double val = parseDouble(zTopTextField_.getText());
      int newSliderindex = (int) Math.round((val - zOrigin_) / zStep_);
      expandZLimitsIfNeeded(newSliderindex, zBottomScrollbar_.getValue());
      //now that scollbar expanded, set value
      zTopScrollbar_.setValue(newSliderindex);
      updateZTopAndBottom();
   }

   private void zBottomTextFieldAction() {
      //check if new position is outside bounds of current z range
      //and if so expand sliders as needed
      double val = parseDouble(zBottomTextField_.getText());
      int newSliderindex = (int) Math.round((val - zOrigin_) / zStep_);
      expandZLimitsIfNeeded(zTopScrollbar_.getValue(), newSliderindex);
      zBottomScrollbar_.setValue(newSliderindex);
      updateZTopAndBottom();

   }

   private static double parseDouble(String s) {
      try {
         return DecimalFormat.getNumberInstance().parse(s).doubleValue();
      } catch (ParseException ex) {
         throw new RuntimeException(ex);
      }
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
         throw new RuntimeException("problem creating z limit scrollbars");
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

      this.setLayout(new BorderLayout());
      this.add(controlsPanel_, BorderLayout.CENTER);
   }

}
