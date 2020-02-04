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

import org.micromanager.magellan.internal.imagedisplay.events.LockEvent;
import org.micromanager.magellan.internal.imagedisplay.events.ScrollPositionEvent;
import org.micromanager.magellan.internal.imagedisplay.events.AnimationToggleEvent;
import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JPanel;
import javax.swing.JScrollBar;

/**
 * This class provides a scrollbar and associated buttons for navigating
 * different "axes" of a data set (for example: Z slice, timepoint, channel).
 * Communication with the parent is handled by way of events using an EventBus.
 */
public class AxisScroller extends JPanel {

   // Height of components in the panel. 
   private static final int HEIGHT = 14;
   // Width of the label/animate button.
   private static final int LABEL_WIDTH = 24;

   // This string will be used to identify this scrollbar, and thus should be
   // unique across all created scrollbars for your image display.
   private String axis_;
   // Indicates if animation is currently ongoing.
   private boolean isAnimated_;
   // A scrollbar position that we remembered, for later use. This is used so
   // that we can snap to a new image when it arrives, and then restore our
   // original position after awhile (see the ScrollerPanel which handles the
   // relevant timer).
   // We use -1 to indicate "we have no remembered position currently".
   private int rememberedPosition_ = -1;
   // Indicates if we should ignore the next scrollbar position update event
   // we receive.
   private boolean shouldIgnoreScrollbarEvent_;
   // Used to turn animation on/off.
   private ScrollbarAnimateIcon animateIcon_;
   // Used to select an image to view along our axis.
   private JScrollBar scrollbar_;
   private ScrollbarLockIcon lock_;

   private int minOffset_ = 0;
   private AdjustmentListener adjustmentListener_;

   MagellanDisplayController display_;

   public AxisScroller(MagellanDisplayController display, String axis, int maximum,
           boolean canAnimate) {
      // Only allow the scrollbar (not the buttons on either side) to grow.
      super(new net.miginfocom.swing.MigLayout("insets 0, fillx",
              "[][grow][]"));
      axis_ = axis;
      display_ = display;
      display_.registerForEvents(this);
      shouldIgnoreScrollbarEvent_ = false;

      animateIcon_ = new ScrollbarAnimateIcon(axis);
      Dimension size = new Dimension(LABEL_WIDTH, HEIGHT);
      animateIcon_.setPreferredSize(size);
      animateIcon_.setMaximumSize(size);
      if (canAnimate) {
         animateIcon_.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               if (!lock_.getIsLocked()) {
                  // Don't allow animation when the axis is locked.
                  isAnimated_ = !isAnimated_;
                  animateIcon_.setIsAnimated(isAnimated_);
               }
               display_.postEvent(new AnimationToggleEvent(AxisScroller.this, isAnimated_));
            }
         });
      }
      add(animateIcon_, "grow 0");

      scrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, 0, maximum);

      adjustmentListener_ = new java.awt.event.AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(java.awt.event.AdjustmentEvent e) {
            handleScrollerMotion();
         }
      };
      scrollbar_.addAdjustmentListener(adjustmentListener_);

      add(scrollbar_, "growx");

      lock_ = new ScrollbarLockIcon(display_, axis);
      add(lock_, "grow 0");

   }

   public void onDisplayClose() {
      for (MouseListener l : animateIcon_.getMouseListeners()) {
         animateIcon_.removeMouseListener(l);
      }
         scrollbar_.removeAdjustmentListener(adjustmentListener_);
      setIsAnimated(false);
      display_.unregisterForEvents(this);

      display_ = null;
      scrollbar_ = null;
      animateIcon_ = null;
   }

   public JScrollBar getScrollBar() {
      return scrollbar_;
   }

   /**
    * The scroller position was updated; we post an event iff we were not
    * instructed to ignore the next event (see advancePosition()).
    */
   private void handleScrollerMotion() {
      display_.postEvent(new ScrollPositionEvent(this, scrollbar_.getValue()));
   }

   public void superlock() {
      lock_.setLockedState(ScrollbarLockIcon.LockedState.SUPERLOCKED);
   }

   public void unlock() {
      lock_.setLockedState(ScrollbarLockIcon.LockedState.UNLOCKED);
   }

   /**
    * The lock icon was clicked; update our state to suit. Locking cancels
    * animations and disables the animate button.
    */
   @Subscribe
   public void onLockToggle(LockEvent event) {
      if (!event.getAxis().equals(axis_)) {
         // Ignore; event is for a different axis.
         return;
      }
      if (lock_.getIsLocked() && isAnimated_) {
         // We're locked; cancel active animation.
         isAnimated_ = false;
         animateIcon_.setIsAnimated(isAnimated_);
         display_.postEvent(new AnimationToggleEvent(this, isAnimated_));
      }
      animateIcon_.setEnabled(!lock_.getIsLocked());
   }

   public String getAxis() {
      return axis_;
   }

   public void setIsAnimated(boolean isAnimated) {
      isAnimated_ = isAnimated;
      if (animateIcon_ != null) {
         animateIcon_.setIsAnimated(isAnimated);
      }
      if (lock_.getIsLocked() && isAnimated_) {
         // Disable the lock. 
         lock_.setLockedState(ScrollbarLockIcon.LockedState.UNLOCKED);
      }
//      display_.postEvent(new AnimationToggleEvent(this, isAnimated_));
   }

   public boolean getIsAnimated() {
      return isAnimated_;
   }

   public boolean getIsSuperlocked() {
      return lock_.getLockedState() == ScrollbarLockIcon.LockedState.SUPERLOCKED;
   }

   public int getPosition() {
      return scrollbar_.getValue() + minOffset_;
   }

   /**
    * Set scrollbar position, but don't fire action listener
    *
    * @param position
    */
   public void setPosition(int position) {
      scrollbar_.removeAdjustmentListener(adjustmentListener_);
      scrollbar_.setValue(position - minOffset_);
      scrollbar_.addAdjustmentListener(adjustmentListener_);
   }

   public int getMaximum() {
      return scrollbar_.getMaximum();
   }

   public int getMinimum() {
      return scrollbar_.getMinimum() + minOffset_;
   }

   public void setMinimum(int newMin) {
      scrollbar_.removeAdjustmentListener(adjustmentListener_);
      int expandBy = Math.max(0, minOffset_ - newMin);
      scrollbar_.setMaximum(scrollbar_.getMaximum() + expandBy);
      minOffset_ = newMin;
      scrollbar_.addAdjustmentListener(adjustmentListener_);
   }

   public void setMaximum(int newMax) {
      scrollbar_.removeAdjustmentListener(adjustmentListener_);
      scrollbar_.setMaximum(newMax - minOffset_);
      scrollbar_.addAdjustmentListener(adjustmentListener_);
   }
}
