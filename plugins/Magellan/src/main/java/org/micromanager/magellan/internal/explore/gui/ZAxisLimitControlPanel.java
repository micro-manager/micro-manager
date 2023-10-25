package org.micromanager.magellan.internal.explore.gui;

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
import net.miginfocom.swing.MigLayout;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.acqj.internal.ZAxis;
import org.micromanager.magellan.internal.explore.ExploreAcquisition;

public class ZAxisLimitControlPanel extends JPanel {

   private JScrollBar zTopScrollbar_;
   private JScrollBar zBottomScrollbar_;
   private JTextField zTopTextField_;
   private JTextField zBottomTextField_;

   private double zStep_;
   private double zOrigin_;
   private int displayHeight_ = -1;
   //thread safe fields for currently displaye dimage
   private int minZExplored_ = Integer.MAX_VALUE;
   private int maxZExplored_ = Integer.MIN_VALUE;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");

   private final String name_;
   private final ExploreAcquisition acquisition_;

   public ZAxisLimitControlPanel(ExploreAcquisition acquisition, ZAxis zaxis) {
      super(new MigLayout("insets 0", "[][][grow]", ""));

      name_ = zaxis.name_;
      zStep_ = zaxis.zStep_um_;
      zOrigin_ = zaxis.zOrigin_um_;
      acquisition_ = acquisition;

      //Initialize z to current position with space to move one above or below
      //value, extent, min, max
      //max value of scrollbar is max - extent
      try {
         zTopScrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, -1, 2);
         zBottomScrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, -1, 2);
         zTopScrollbar_.setUI(new ColorableScrollbarUI(zaxis.name_));
         zBottomScrollbar_.setUI(new ColorableScrollbarUI(zaxis.name_));
      } catch (Exception e) {
         throw new RuntimeException("problem creating z limit scrollbars");
      }
      zTopTextField_ = new JTextField(TWO_DECIMAL_FORMAT.format(zOrigin_));
      zTopTextField_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent ae) {
            zTopTextFieldAction();
         }
      });
      zBottomTextField_ = new JTextField(TWO_DECIMAL_FORMAT.format(zOrigin_));
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
         zPos = Engine.getCore().getPosition(name_);
      } catch (Exception ex) {
         throw new RuntimeException(ex);
      }
      zTopTextField_.setText(TWO_DECIMAL_FORMAT.format(zPos));
      zBottomTextField_.setText(TWO_DECIMAL_FORMAT.format(zPos));
      zTopTextField_.getActionListeners()[0].actionPerformed(null);
      zBottomTextField_.getActionListeners()[0].actionPerformed(null);

      add(new JLabel(zaxis.name_ + " limits"), "span 1 2");
      if (isMac()) {
         add(zTopTextField_, "w 80!");
         add(zTopScrollbar_, "growx, wrap");
         add(zBottomTextField_, "w 80!");
         add(zBottomScrollbar_, "growx");
      } else {
         add(zTopTextField_, "w 50!");
         add(zTopScrollbar_, "growx, wrap");
         add(zBottomTextField_, "w 50!");
         add(zBottomScrollbar_, "growx");

      }
   }

   private static boolean isMac() {
      String os = System.getProperty("os.name").toLowerCase();
      return (os.indexOf("mac") >= 0);
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


   private void updateZTopAndBottom() {
      //Update the text fields next to the sliders in response to adjustment
      double zBottom = zStep_ * zBottomScrollbar_.getValue() + zOrigin_;
      zBottomTextField_.setText(TWO_DECIMAL_FORMAT.format(zBottom));
      double zTop = zStep_ * zTopScrollbar_.getValue() + zOrigin_;
      zTopTextField_.setText(TWO_DECIMAL_FORMAT.format(zTop));
      //Update the acquisition
      acquisition_.setZLimits(name_, zTop, zBottom);
      //update colored areas on z scrollbar
      //convert to 0 based index based on which slices have been explored
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
      this.repaint();

   }

   private void expandZLimitsIfNeeded(int topScrollbarIndex, int bottomScrollbarIndex) {
      //extent of 1 needs to be accounted for on top
      if (topScrollbarIndex >= zTopScrollbar_.getMaximum() - 1
              || bottomScrollbarIndex >= zBottomScrollbar_.getMaximum() - 1) {
         zTopScrollbar_.setMaximum(Math.max(topScrollbarIndex, bottomScrollbarIndex) + 2);
         zBottomScrollbar_.setMaximum(Math.max(topScrollbarIndex, bottomScrollbarIndex) + 2);
      }
      if (bottomScrollbarIndex <= zBottomScrollbar_.getMinimum()
              || topScrollbarIndex <= zTopScrollbar_.getMinimum()) {
         zTopScrollbar_.setMinimum(Math.min(bottomScrollbarIndex, topScrollbarIndex) - 1);
         zBottomScrollbar_.setMinimum(Math.min(bottomScrollbarIndex, topScrollbarIndex) - 1);
      }
      this.repaint();
   }

   public void onDisplayClose() {
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

   public void updateZDrivelocation(Integer zIndex) {
      //convert slice index to explore scrollbar index
      minZExplored_ = Math.min(minZExplored_, zIndex);
      maxZExplored_ = Math.max(maxZExplored_, zIndex);
      ((ColorableScrollbarUI) zTopScrollbar_.getUI()).setHighlightedIndices(zIndex,
              minZExplored_, maxZExplored_);
      ((ColorableScrollbarUI) zBottomScrollbar_.getUI()).setHighlightedIndices(zIndex,
              minZExplored_, maxZExplored_);
      expandZLimitSliders(zIndex);
      this.repaint();
   }
}
