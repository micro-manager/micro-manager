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

import java.awt.Dimension;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import javax.swing.JPanel;
import javax.swing.JScrollBar;
import javax.swing.event.MouseInputAdapter;

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

   private Integer minIndex_ = null;
   private Integer maxIndex_ = null;
   private AdjustmentListener adjustmentListener_;

   ScrollerPanel scrollerPanel_;

   public AxisScroller(ScrollerPanel sp, String axis, int maximum,
           boolean canAnimate) {
      // Only allow the scrollbar (not the buttons on either side) to grow.
      super(new net.miginfocom.swing.MigLayout("insets 0, fillx",
              "[][grow][]"));
      axis_ = axis;
      scrollerPanel_ = sp;
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
               scrollerPanel_.onAnimationToggle(AxisScroller.this, isAnimated_);
            }
         });
      }
      add(animateIcon_, "grow 0");

      scrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, 0, maximum);

      adjustmentListener_ = new java.awt.event.AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(java.awt.event.AdjustmentEvent e) {
            scrollerPanel_.onScrollPositionChanged(AxisScroller.this, scrollbar_.getValue());

         }
      };
      scrollbar_.addAdjustmentListener(adjustmentListener_);

      add(scrollbar_, "growx");

      lock_ = new ScrollbarLockIcon(this, axis);
      add(lock_, "grow 0");

   }

   public void onDisplayClose() {
      for (MouseListener l : animateIcon_.getMouseListeners()) {
         animateIcon_.removeMouseListener(l);
      }
      scrollbar_.removeAdjustmentListener(adjustmentListener_);
      setIsAnimated(false);
      lock_.onDisplayClose();

      scrollerPanel_ = null;
      scrollbar_ = null;
      animateIcon_ = null;
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
   public void onLockToggle(String axis, ScrollbarLockIcon.LockedState lockedState) {
      if (!axis.equals(axis_)) {
         // Ignore; event is for a different axis.
         return;
      }
      if (lock_.getIsLocked() && isAnimated_) {
         // We're locked; cancel active animation.
         isAnimated_ = false;
         animateIcon_.setIsAnimated(isAnimated_);
         scrollerPanel_.onAnimationToggle(this, isAnimated_);
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
   }

   public boolean getIsSuperlocked() {
      return lock_.getLockedState() == ScrollbarLockIcon.LockedState.SUPERLOCKED;
   }

   public int getPosition() {
      return scrollbar_.getValue() + minIndex_;
   }

   /**
    * Set scrollbar position, but don't fire action listener
    *
    * @param position
    */
   public void setPosition(int position) {
      scrollbar_.removeAdjustmentListener(adjustmentListener_);
      // Re-assert the extent along with the value: setValue() alone lets
      // BoundedRangeModel shrink the extent to keep value + extent <= maximum, and an
      // extent of 0 makes the scrollbar one position longer than the data.
      scrollbar_.setValues(position - minIndex_, 1,
              scrollbar_.getMinimum(), scrollbar_.getMaximum());
      scrollbar_.addAdjustmentListener(adjustmentListener_);
   }

   /**
    * Returns the highest position that can actually be displayed on this axis.
    *
    * <p>The underlying scrollbar's maximum is an exclusive bound (its extent is 1, so the
    * highest attainable value is maximum - 1), and it is expressed in scrollbar
    * coordinates. This converts to the same inclusive, image-position coordinates that
    * {@link #getMinimum()} and {@link #getPosition()} use, so callers can treat the pair
    * as an inclusive range.
    */
   public int getMaximum() {
      return scrollbar_.getMaximum() - 1 + minIndex_;
   }

   public int getMinimum() {
      return scrollbar_.getMinimum() + minIndex_;
   }

   public void expandDisplayRangeIfNeeded(int imagePosition) {
      scrollbar_.removeAdjustmentListener(adjustmentListener_);
      if (minIndex_ == null  || maxIndex_ == null) {
         minIndex_ = imagePosition;
         maxIndex_ = imagePosition;
      }
      //Image position can be negative, but min and max must be positive
      minIndex_ = Math.min(imagePosition, minIndex_);
      maxIndex_ = Math.max(imagePosition, maxIndex_);

      // Set value, extent, min and max together. The extent must stay 1 for the highest
      // reachable value to be (maximum - 1), i.e. maxIndex_: BoundedRangeModel adjusts
      // the extent to preserve its own invariants when max/min/value are set separately,
      // and an extent of 0 leaves the scrollbar one position longer than the data, which
      // shows up as a trailing blank frame.
      int newMax = Math.max(maxIndex_ - minIndex_ + 1, scrollbar_.getMaximum());
      int newMin = Math.max(0, Math.min(imagePosition, minIndex_));
      int value = Math.max(newMin, Math.min(scrollbar_.getValue(), newMax - 1));
      scrollbar_.setValues(value, 1, newMin, newMax);

      scrollbar_.addAdjustmentListener(adjustmentListener_);
   }

   public boolean isInitialized() {
      return minIndex_ != null || maxIndex_ != null;
   }

   public boolean isOutOfRange(int imagePosition) {
      if (imagePosition < minIndex_) {
         return true;
      }
      if (imagePosition > maxIndex_) {
         return true;
      }
      return false;
   }

   public void initialize(int imagePosition) {
      minIndex_ = imagePosition;
      maxIndex_ = imagePosition;
   }
}
