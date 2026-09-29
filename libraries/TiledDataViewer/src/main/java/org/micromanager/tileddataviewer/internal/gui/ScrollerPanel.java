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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import javax.swing.JPanel;
import org.micromanager.tileddataviewer.internal.TiledDataViewer;

/**
 * This class is responsible for containing and managing groups of
 * AxisScrollers, and how they affect the display of a collection of images.
 */
class ScrollerPanel extends JPanel {

   // All AxisScrollers we manage. protected visibility to allow subclassing (Magellan plugin)
   public ArrayList<AxisScroller> scrollers_;
   // A mapping of axis identifiers to their positions as of the last time
   // checkForImagePositionChanged() was called.
   private HashMap<String, Object> lastImagePosition_ = null;
   // This will get set to false in prepareForClose, in turn barring any more
   // timers from getting created.
   private boolean canMakeTimers_ = true;
   // Timer for handling animation.
   private Timer animationUpdateTimer_ = null;
   // Timer for restoring scrollbars after forcing their positions.
   private Timer snapBackTimer_ = null;
   // Rate at which we update images when animating. Defaults to 10.
   private double framesPerSec_;
   private TiledDataViewer display_;

   public ScrollerPanel(TiledDataViewer display, double framesPerSec) {
      // Minimize whitespace around our components.
      super(new net.miginfocom.swing.MigLayout("insets 0, fillx"));
      display_ = display;

      framesPerSec_ = framesPerSec;
      scrollers_ = new ArrayList<AxisScroller>();

   }

   private void addScroller(String axis) {

      AxisScroller scroller = new AxisScroller(this, axis, 1, true);

      scroller.setVisible(false);
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
         lastImagePosition_ = new HashMap<String, Object>();
      }
      for (AxisScroller scroller : scrollers_) {
         String axis = scroller.getAxis();
         Object position = scroller.getPosition();
         // Convert string positions to integer positions
         if (!display_.getDisplayModel().isIntegerAxis(axis)) {
            position = display_.getDisplayModel().getStringPositionFromIntegerPosition(axis,
                     (Integer) position);
         }
         if (!lastImagePosition_.containsKey(axis)
                 || !lastImagePosition_.get(axis).equals(position)) {
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

   void expandDisplayedRangeToInclude(List<HashMap<String, Object>> newIamgeEvents,
                                      List<String> channels) {
      for (int i = 0; i < newIamgeEvents.size(); i++) {
         HashMap<String, Object> axes = newIamgeEvents.get(i);

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
            int imagePosition;
            if (display_.getDisplayModel().isIntegerAxis(scroller.getAxis())) {
               imagePosition = (Integer) axes.get(scroller.getAxis());
            } else {
               imagePosition = display_.getDisplayModel().getIntegerPositionFromStringPosition(
                       scroller.getAxis(), (String) axes.get(scroller.getAxis()));
            }

            if (!scroller.isInitialized()) {
               scroller.initialize(imagePosition);
            }
            if (scroller.isOutOfRange(imagePosition)) {
               if (!scroller.isVisible()) {
                  // This scroller was previously hidden and needs to be shown now.
                  scroller.setVisible(true);
                  add(scroller, "wrap 0px, align center, growx");
                  didShowNewScrollers = true;
               }
               // expand display range
               scroller.expandDisplayRangeIfNeeded(imagePosition);

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

}
