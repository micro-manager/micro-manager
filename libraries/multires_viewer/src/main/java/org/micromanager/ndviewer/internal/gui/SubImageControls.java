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
package org.micromanager.ndviewer.internal.gui;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.ndviewer.main.NDViewer;


/**
 * Scrollbars + optional controls for explor acquisitons
 *
 * @author Henry
 */
class SubImageControls extends JPanel {

   private final static int DEFAULT_FPS = 7;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private NDViewer display_;
   private ScrollerPanel scrollerPanel_;
   private int displayHeight_ = -1;
   private JPanel controlsPanel_;

   public SubImageControls(NDViewer disp) {
      super(new FlowLayout(FlowLayout.LEADING));
      display_ = disp;
      try {
         initComponents();
      } catch (Exception e) {
         throw new RuntimeException("Problem initializing subimage controls");
      }

   }
   
   public void onScollPositionChanged(AxisScroller scroller, int value) {
      scrollerPanel_.onScrollPositionChanged( scroller,  value);
   }
   
   public void onDisplayClose() {
      display_ = null;
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

   void expandDisplayedRangeToInclude(List<HashMap<String, Integer>> newIamgeEvents,
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

      scrollerPanel_ = new ScrollerPanel(display_, DEFAULT_FPS);
      controlsPanel_.add(scrollerPanel_, "span, growx, wrap");

      this.setLayout(new BorderLayout());
      this.add(controlsPanel_, BorderLayout.CENTER);
   }

   public void onScrollersAdded() {
      this.setPreferredSize(new Dimension(this.getPreferredSize().width,
              scrollerPanel_.getPreferredSize().height)); //probably not needed
      this.invalidate();
      this.validate();
      this.getParent().doLayout();
   }

   void updateScrollerPositions(DataViewCoords view) {
      for (AxisScroller a : scrollerPanel_.scrollers_) {
         a.setPosition(view.getAxisPosition(a.getAxis()));
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
