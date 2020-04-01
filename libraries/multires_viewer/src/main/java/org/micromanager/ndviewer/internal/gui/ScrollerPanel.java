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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;
import javax.swing.JPanel;
import org.micromanager.ndviewer.main.NDViewer;

/**
 * This class is responsible for containing and managing groups of
 * AxisScrollers, and how they affect the display of a collection of images.
 */
class ScrollerPanel extends JPanel {

   // All AxisScrollers we manage. protected visibility to allow subclassing (Magellan plugin)
   public ArrayList<AxisScroller> scrollers_;
   // A mapping of axis identifiers to their positions as of the last time
   // checkForImagePositionChanged() was called.
   private HashMap<String, Integer> lastImagePosition_ = null;
   // This will get set to false in prepareForClose, in turn barring any more
   // timers from getting created.
   private boolean canMakeTimers_ = true;
   // Timer for handling animation.
   private Timer animationUpdateTimer_ = null;
   // Timer for restoring scrollbars after forcing their positions.
   private Timer snapBackTimer_ = null;
   // Rate at which we update images when animating. Defaults to 10.
   private double framesPerSec_;
   private NDViewer display_;

   /**
    * @param axes List of Strings labeling the axes that the caller wants to
    * create AxisScrollers for.
    * @param maximums List of Integers indicating the number of images along
    * that axis.
    */
   public ScrollerPanel(NDViewer display, double framesPerSec) {
      // Minimize whitespace around our components.
      super(new net.miginfocom.swing.MigLayout("insets 0, fillx"));
      display_ = display;

      framesPerSec_ = framesPerSec;
      scrollers_ = new ArrayList<AxisScroller>();

   }

   private void addScroller(String axis) {

      AxisScroller scroller = new AxisScroller(this, axis, 1, true);

//         if (max <= 1) {
      scroller.setVisible(false);
//         } else {
//            add(scroller, "wrap 0px, align center, growx");
//         }
      scrollers_.add(scroller);

   }

   public void onDisplayClose() {
      canMakeTimers_ = false;
      if (animationUpdateTimer_ != null) {
         animationUpdateTimer_.cancel();
      }
      if (snapBackTimer_ != null) {
         snapBackTimer_.cancel();
      }
      for (AxisScroller s : scrollers_) {
         s.onDisplayClose();
      }
      display_ = null;
   }

   /**
    * One of our AxisScrollers changed position; update the image.
    */
   public void onScrollPositionChanged(AxisScroller scroller, int value) {
      checkForImagePositionChanged();
   }

   private void checkForImagePositionChanged() {
      boolean shouldPostEvent = false;
      if (lastImagePosition_ == null) {
         lastImagePosition_ = new HashMap<String, Integer>();
      }
      String channel = null;
      for (AxisScroller scroller : scrollers_) {
//         if (scroller.getAxis().equals("c")) {
//            channel = display_.getChannelName(scroller.getPosition());
//         }
         String axis = scroller.getAxis();
         Integer position = scroller.getPosition();
         if (!lastImagePosition_.containsKey(axis)
                 || lastImagePosition_.get(axis) != position) {
            // Position along this axis has changed; we need to refresh.
            shouldPostEvent = true;
         }
         lastImagePosition_.put(axis, position);
      }
      if (shouldPostEvent) {
         
         display_.setImageEvent(lastImagePosition_, true);
      }
   }

   /**
    * One of our AxisScrollers toggled animation status; replace our animation
    * timer.
    */
   public void onAnimationToggle(AxisScroller scoller, boolean isAnimated) {
//      resetAnimationTimer();

      //turn off the other ones
      if (isAnimated) {
         for (AxisScroller sc : scrollers_) {
            if (scoller != sc) {
               sc.setIsAnimated(false);
            }
         }
      }
      display_.onAnimationToggle(scoller, isAnimated);
   }

   public void superlockAllScrollers() {
      for (AxisScroller s : scrollers_) {
         s.superlock();
      }
   }

   public void unlockAllScrollers() {
      for (AxisScroller s : scrollers_) {
         s.unlock();
      }
   }

   void expandDisplayedRangeToInclude(List<HashMap<String, Integer>> newIamgeEvents,
           List<String> channels) {
      for (int i = 0; i < newIamgeEvents.size(); i++) {
         HashMap<String, Integer> axes = newIamgeEvents.get(i);
//         convert channel name to coords
//         int cIndex = display_.getChannelIndex(channels.get(i));
//         axes.put("c", cIndex);
         
         boolean didShowNewScrollers = false;
         //create new scrollers for any axes not yet seen
         for (String axis : axes.keySet()) {
            boolean newAxis = true;
            for (AxisScroller scroller : scrollers_) {
               if (scroller.getAxis().equals(axis)) {
                  newAxis = false;
               }
            }
            if (newAxis) {
               addScroller(axis);
            }
         }

         for (AxisScroller scroller : scrollers_) {
            if (!axes.containsKey(scroller.getAxis())) {
               continue; //these events have no information pertinent to this scroller
            }
            int imagePosition = axes.get(scroller.getAxis());
            if (scroller.getMaximum() <= imagePosition || scroller.getMinimum() > imagePosition) {
               if (!scroller.isVisible()) {
                  // This scroller was previously hidden and needs to be shown now.
                  scroller.setVisible(true);
                  add(scroller, "wrap 0px, align center, growx");
                  didShowNewScrollers = true;
               }
               // xpand display range
               scroller.setMaximum(Math.max(imagePosition + 1, scroller.getMaximum()));
               scroller.setMinimum(Math.min(imagePosition, scroller.getMinimum()));
            }

         }
         if (didShowNewScrollers) {
            // Post an event informing our masters that our layout has changed.
            display_.onScollersAdded();
         }
      }

   }

   /**
    * Return the position of the scroller for the specified axis, or 0 if we
    * have no scroller for that axis.
    */
   public int getPosition(String axis) {
      for (AxisScroller scroller : scrollers_) {
         if (scroller.getAxis().equals(axis)) {
            return scroller.getPosition();
         }
      }
      return 0;
   }

   /**
    * Return the maximum position for the specified axis, or 0 if we have no
    * scroller for that axis.
    */
   public int getMaxPosition(String axis) {
      for (AxisScroller scroller : scrollers_) {
         if (scroller.getAxis().equals(axis)) {
            return scroller.getMaximum();
         }
      }
      return 0;
   }

   /**
    * Resize scroller to new maximum size
    */
   public void setMaxPosition(String axis, int max) {
      for (AxisScroller scroller : scrollers_) {
         if (scroller.getAxis().equals(axis)) {
            scroller.setMaximum(max);
         }
      }
   }

}
