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
package org.micromanager.plugins.magellan.imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JPanel;
import javax.swing.JScrollBar;

/**
 * This class provides a scrollbar and associated buttons for navigating
 * different "axes" of a data set (for example: Z slice, timepoint, channel).
 * Communication with the parent is handled by way of events using an EventBus.
 */
public class AxisScroller extends JPanel {
   /**
    * This class is used to communicate with our master when the animation
    * button is clicked.
    */
   public static class AnimationToggleEvent {
      private boolean isAnimated_;
      public AxisScroller scroller_;
      public AnimationToggleEvent(AxisScroller scroller, boolean isAnimated) {
         scroller_ = scroller;
         isAnimated_ = isAnimated;
      }
      
      public boolean getIsAnimated() {
         return isAnimated_;
      }
   }

   /**
    * This class is used to communicate with our master when the scrollbar is
    * moved to a different position.
    */
   public static class ScrollPositionEvent {
      private int value_;
      public AxisScroller scroller_;
      public ScrollPositionEvent(AxisScroller scroller, int value) {
         scroller_ = scroller;
         value_ = value;
      }

      public int getValue() {
         return value_;
      }
   }
   
   // This string will be used to identify this scrollbar, and thus should be
   // unique across all created scrollbars for your image display.
   private String axis_; 
   // We'll use this bus to communicate with our master.
   private EventBus bus_;
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
   final private JScrollBar scrollbar_;
   private ScrollbarLockIcon lock_;

   // Height of components in the panel. 
   private static final int HEIGHT = 14;
   // Width of the label/animate button.
   private static final int LABEL_WIDTH = 24;

   public AxisScroller(String axis, int maximum, final EventBus bus, 
         boolean canAnimate) {
      // Only allow the scrollbar (not the buttons on either side) to grow.
      super(new net.miginfocom.swing.MigLayout("insets 0, fillx",
               "[][grow][]"));
      axis_ = axis;
      bus_ = bus;
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
               bus.post(new AnimationToggleEvent(AxisScroller.this, isAnimated_));
            }
         });
      }
      add(animateIcon_, "grow 0");

      scrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, 0, maximum);
      scrollbar_.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(java.awt.event.AdjustmentEvent e) {
            handleScrollerMotion();
         }
      });

      add(scrollbar_, "growx");

      lock_ = new ScrollbarLockIcon(axis, bus_);
      add(lock_, "grow 0");

      bus.register(this);
   }
   
   public JScrollBar getScrollBar() {
      return scrollbar_;
   }

   /**
    * The scroller position was updated; we post an event iff we were not 
    * instructed to ignore the next event (see advancePosition()). 
    */
   private void handleScrollerMotion() {
      if (shouldIgnoreScrollbarEvent_) {
         // Ignoring this one, but we'll publish the next one, unless this
         // variable gets reset of course.
         shouldIgnoreScrollbarEvent_ = false;
      }
      else {
         bus_.post(new ScrollPositionEvent(this, scrollbar_.getValue()));
      }
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
   public void onLockToggle(ScrollbarLockIcon.LockEvent event) {
      if (!event.getAxis().equals(axis_)) {
         // Ignore; event is for a different axis.
         return;
      }
      if (lock_.getIsLocked() && isAnimated_) {
         // We're locked; cancel active animation.
         isAnimated_ = false;
         animateIcon_.setIsAnimated(isAnimated_);
         bus_.post(new AnimationToggleEvent(this, isAnimated_));
      }
      animateIcon_.setEnabled(!lock_.getIsLocked());
   }

   /**
    * Change the position of our scrollbar by the specified offset. Don't
    * move if we're locked, and wrap around if we go off the end. Post a 
    * ScrollPositionEvent if so instructed.
    */
   public void advancePosition(int offset, boolean shouldPostEvent) {
      if (lock_.getIsLocked()) {
         // Currently locked, ergo we cannot move.
         return;
      }
      shouldIgnoreScrollbarEvent_ = shouldIgnoreScrollbarEvent_ || shouldPostEvent;
      int target = (scrollbar_.getValue() + offset) % scrollbar_.getMaximum();
      scrollbar_.setValue(target);
   }

   public String getAxis() {
      return axis_;
   }

   public void setIsAnimated(boolean isAnimated) {
      isAnimated_ = isAnimated;
      animateIcon_.setIsAnimated(isAnimated);
      if (lock_.getIsLocked() && isAnimated_) {
         // Disable the lock. 
         lock_.setLockedState(ScrollbarLockIcon.LockedState.UNLOCKED);
      }
      bus_.post(new AnimationToggleEvent(this, isAnimated_));
   }

   public boolean getIsAnimated() {
      return isAnimated_;
   }

   public boolean getIsSuperlocked() {
      return lock_.getLockedState() == ScrollbarLockIcon.LockedState.SUPERLOCKED;
   }

   public int getPosition() {
      return scrollbar_.getValue();
   }

   /**
    * Set the position of this scroller to the specified position. Do not 
    * move, if we are locked.
    */
   public void setPosition(int newPosition) {
      if (!lock_.getIsLocked()) {
         scrollbar_.setValue(newPosition);
      }
   }

   /**
    * Set the position of this scroller to the specified position, even if 
    * we are locked -- but remember our original position so it can be 
    * restored later. Only remember if we don't already have a remembered
    * position (i.e. don't overwrite existing remembered positions), and we 
    * are currently animated or locked -- i.e. we have a position we would 
    * rather be at than the one we're being told to be at. 
    */
   public void forcePosition(int newPosition) {
      if (rememberedPosition_ == -1 && (isAnimated_ || lock_.getIsLocked())) {
         rememberedPosition_ = scrollbar_.getValue();
      }
      scrollbar_.setValue(newPosition);
   }

   /**
    * Restore a position that we remembered previously, and then delete the 
    * remembered position so it can be re-set by a future call to 
    * forcePosition.
    */
   public void restorePosition() {
      if (rememberedPosition_ != -1) {
         scrollbar_.setValue(rememberedPosition_);
         rememberedPosition_ = -1;
      }
   }

   public int getMaximum() {
      return scrollbar_.getMaximum();
   }
   
   public void setMaximum(int newMax) {
      scrollbar_.setMaximum(newMax);
   }
}

