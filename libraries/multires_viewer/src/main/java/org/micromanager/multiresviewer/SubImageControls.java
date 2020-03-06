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
package org.micromanager.multiresviewer;

import com.google.common.eventbus.Subscribe;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.HashMap;
import java.util.List;
import javax.swing.JPanel;
import net.miginfocom.swing.MigLayout;
import org.micromanager.multiresviewer.events.ScrollersAddedEvent;


/**
 * Scrollbars + optional controls for explor acquisitons
 *
 * @author Henry
 */
class SubImageControls extends JPanel {

   private final static int DEFAULT_FPS = 7;
   private static final DecimalFormat TWO_DECIMAL_FORMAT = new DecimalFormat("0.00");
   private MagellanDisplayController display_;
   private ScrollerPanel scrollerPanel_;
   private int displayHeight_ = -1;
   private JPanel controlsPanel_;

   public SubImageControls(MagellanDisplayController disp) {
      super(new FlowLayout(FlowLayout.LEADING));
      display_ = disp;
      display_.registerForEvents(this);
      try {
         initComponents();
      } catch (Exception e) {
         throw new RuntimeException("Problem initializing subimage controls");
      }

   }

   public void onDisplayClose() {
      display_.unregisterForEvents(this);
      display_ = null;
      controlsPanel_.removeAll();
      this.remove(controlsPanel_);
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

   void expandDisplayedRangeToInclude(List<HashMap<String, Integer>> newIamgeEvents) {
      scrollerPanel_.expandDisplayedRangeToInclude(newIamgeEvents);
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

      scrollerPanel_ = new ScrollerPanel(display_, new String[]{"c", "t", "r", "z"}, new Integer[]{1, 1, 1, 1}, DEFAULT_FPS);
      controlsPanel_.add(scrollerPanel_, "span, growx, wrap");

      this.setLayout(new BorderLayout());
      this.add(controlsPanel_, BorderLayout.CENTER);
   }

   @Subscribe
   public void onScrollersAdded(ScrollersAddedEvent event) {
      this.setPreferredSize(new Dimension(this.getPreferredSize().width,
              scrollerPanel_.getPreferredSize().height)); //probably not needed
      this.invalidate();
      this.validate();
      this.getParent().doLayout();
   }

   void updateScrollerPositions(DataViewCoords view) {
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
