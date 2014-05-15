package org.micromanager.imageDisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollBar;

import org.micromanager.utils.ReportingUtils;

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
   // Indicates if the position of the scrollbar is currently locked (which
   // in turn excludes animation).
   private boolean isLocked_;
   // Timer for temporarily overriding our lock.
   private java.util.Timer lockOverrideTimer_ = null;
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
      super(new net.miginfocom.swing.MigLayout("", "0[]0[]0", "0[]0"));
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
               if (!isLocked_) {
                  // Don't allow animation when the axis is locked.
                  isAnimated_ = !isAnimated_;
                  animateIcon_.setIsAnimated(isAnimated_);
               }
               bus.post(new AnimationToggleEvent(AxisScroller.this, isAnimated_));
            }
         });
      }
      add(animateIcon_);

      scrollbar_ = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1, 
            0, maximum);
      scrollbar_.addAdjustmentListener(new java.awt.event.AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(java.awt.event.AdjustmentEvent e) {
            handleScrollerMotion();
         }
      });

      // TODO: hardcoding the width for now. Ideally would autoscale based on
      // image dimensions.
      scrollbar_.setPreferredSize(new Dimension(512, HEIGHT));
      add(scrollbar_);

      lock_ = new ScrollbarLockIcon(axis, bus_);
      add(lock_);

      bus.register(this);
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
      isLocked_ = event.getIsLocked();
      if (isLocked_ && isAnimated_) {
         // Cancel active animation.
         isAnimated_ = false;
         animateIcon_.setIsAnimated(isAnimated_);
         bus_.post(new AnimationToggleEvent(this, isAnimated_));
      }
      animateIcon_.setEnabled(!isLocked_);
   }

   /**
    * Change the position of our scrollbar by the specified offset. Don't
    * move if we're locked, and wrap around if we go off the end. Post a 
    * ScrollPositionEvent if so instructed.
    */
   public void advancePosition(int offset, boolean shouldPostEvent) {
      if (isLocked_) {
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
      if (isLocked_ && isAnimated_) {
         // Disable the lock. 
         lock_.setIsLocked(false);
      }
      bus_.post(new AnimationToggleEvent(this, isAnimated_));
   }

   public boolean getIsAnimated() {
      return isAnimated_;
   }

   public int getPosition() {
      return scrollbar_.getValue();
   }

   /**
    * Set the position of this scroller to the specified position.
    * @param shouldOverrideLockTemporarily - If true, then if we're locked, 
    *        we still move to the given position, but will snap back to the 
    *        locked position after 500ms. If false, then we won't move if 
    *        we're locked.
    */
   public void setPosition(int newPosition, boolean shouldOverrideLockTemporarily) {
      final int origPosition = scrollbar_.getValue();
      if (shouldOverrideLockTemporarily) {
         scrollbar_.setValue(newPosition);
         if (isLocked_) {
            // Set up a timer to snap us back. 
            if (lockOverrideTimer_ != null) {
               lockOverrideTimer_.cancel();
            }
            lockOverrideTimer_ = new java.util.Timer();
            java.util.TimerTask task = new java.util.TimerTask() {
               @Override
                  public void run() {
                     scrollbar_.setValue(origPosition);
                  }
            };
            lockOverrideTimer_.schedule(task, 500);
         }
      }
      else if (!isLocked_) {
         scrollbar_.setValue(newPosition);
      }
   }

   public int getMaximum() {
      return scrollbar_.getMaximum();
   }
   
   public void setMaximum(int newMax) {
      scrollbar_.setMaximum(newMax);
   }
}
