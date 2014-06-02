package org.micromanager.imageDisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JPanel;

import org.micromanager.utils.ReportingUtils;

/**
 * This class is responsible for containing and managing groups of 
 * AxisScrollers, and how they affect the display of a collection of images.
 */
public class ScrollerPanel extends JPanel {
   
   /**
    * This class signifies that the currently-displayed image needs to be 
    * updated.
    */
   public static class SetImageEvent {
      // Maps axis labels to their positions. 
      private HashMap<String, Integer> axisToPosition_;
      public SetImageEvent(HashMap<String, Integer> axisToPosition) {
         axisToPosition_ = axisToPosition;
      }
      /**
       * Retrieve the desired position along the specified axis, or 0 if we 
       * don't have a marker for that axis.
       */
      public Integer getPositionForAxis(String axis) {
         if (axisToPosition_.containsKey(axis)) {
            return axisToPosition_.get(axis);
         }
         return 0;
      }
   }

   /**
    * This class signifies that our layout has changed and our owner needs to 
    * revalidate.
    */
   public static class LayoutChangedEvent {
      private Dimension preferredSize_;
      public LayoutChangedEvent(Dimension preferredSize) {
         preferredSize_ = preferredSize;
      }

      public Dimension getPreferredSize() {
         return preferredSize_;
      }
   }

   // We'll be communicating with our owner and with our AxisScrollers via
   // this bus.
   private EventBus bus_;
   // All AxisScrollers we manage.
   private ArrayList<AxisScroller> scrollers_;
   // Timer for handling animation.
   private Timer animationUpdateTimer_ = null;
   // Timer for restoring scrollbars after forcing their positions.
   private Timer snapBackTimer_ = null;
   // Rate at which we update images when animating. Defaults to 10.
   private double framesPerSec_;

   /**
    * @param axes List of Strings labeling the axes that the caller wants to 
    *        create AxisScrollers for. 
    * @param maximums List of Integers indicating the number of images along
    *        that axis. 
    */
   public ScrollerPanel(EventBus bus, String[] axes, Integer[] maximums, 
         double framesPerSec) {
      // Minimize whitespace around our components.
      super(new net.miginfocom.swing.MigLayout("", "0[]0", "0[]0"));
      
      bus_ = bus;
      bus_.register(this);
      framesPerSec_ = framesPerSec;
      scrollers_ = new ArrayList<AxisScroller>();

      // Create all desired AxisScrollers. Use the first character of the 
      // axis as the label. Default all scrollers to invisible unless they have
      // at least 2, um, "ticks"; they'll be shown once there's more than one
      // option along that axis.
      // TODO: for now assuming all axes can animate.
      for (int i = 0; i < maximums.length; ++i) {
         AxisScroller scroller = new AxisScroller(axes[i], 
               maximums[i], bus, true);
         if (maximums[i] <= 1) {
            scroller.setVisible(false);
         }
         else {
            add(scroller, "wrap 0px");
         }
         scrollers_.add(scroller);
      }
   }

   /**
    * One of our AxisScrollers changed position; update the image.
    */
   @Subscribe
   public void onScrollPositionChanged(AxisScroller.ScrollPositionEvent event) {
      postSetImageEvent();
   }

   private void postSetImageEvent() {
      HashMap<String, Integer> axisToPosition = new HashMap<String, Integer>();
      for (AxisScroller scroller : scrollers_) {
         axisToPosition.put(scroller.getAxis(), scroller.getPosition());
      }
      bus_.post(new SetImageEvent(axisToPosition));
   }

   /**
    * One of our AxisScrollers toggled animation status; replace our
    * animation timer.
    */
   @Subscribe
   public void onAnimationToggle(AxisScroller.AnimationToggleEvent event) {
      resetAnimationTimer();
   }

   /**
    * The window we're in is closing; cancel animations and timers.
    */
   public void prepareForClose() {
      for (AxisScroller scroller : scrollers_) {
         scroller.setIsAnimated(false);
      }
      if (animationUpdateTimer_ != null) {
         animationUpdateTimer_.cancel();
      }
      if (snapBackTimer_ != null) {
         snapBackTimer_.cancel();
      }
   }

   /**
    * Generate a new AnimationTimer that updates each active (i.e. animated)
    * scroller according to our update rate (FPS). 
    */
   private void resetAnimationTimer() {
      if (animationUpdateTimer_ != null) {
         // Stop the previous timer.
         animationUpdateTimer_.cancel();
      }
      animationUpdateTimer_ = new Timer();
      // Enforce a maximum displayed framerate of 30FPS; for higher rates, we
      // instead skip over images in animation.
      int stepSize = 1;
      long interval = (long) (1000.0 / framesPerSec_);
      if (interval < 33) {
         interval = 33; 
         stepSize = (int) Math.round(framesPerSec_ * 33.0 / 1000.0);
      }
      // This is going to be how much we adjust each scroller's position each
      // tick of the animation.
      final int[] offsets = new int[scrollers_.size()];
      for (int i = 0; i < offsets.length; ++i) {
         // Let me just take a moment to note here that Java
         // can't cast a boolean to an int.
         offsets[i] = (scrollers_.get(i).getIsAnimated() ? 1 : 0) * stepSize;
      }
      TimerTask task = new TimerTask() {
         @Override
         public void run() {
            for (int i = 0; i < scrollers_.size(); ++i) {
               if (offsets[i] != 0) {
                  // Note that the scroller handles wrapping around to the 
                  // beginning, and also whether or not to move at all due to
                  // being locked. 
                  scrollers_.get(i).advancePosition(offsets[i], false);
               }
            }
            postSetImageEvent();
         }
      };
      animationUpdateTimer_.schedule(task, 0, interval);
   }

   /**
    * A new image has been made available; we need to adjust our scrollbars
    * to suit. We only show the new image (i.e. update scrollbar positions)
    * if none of our scrollers are superlocked.
    */
   @Subscribe
   public void onNewImageEvent(NewImageEvent event) {
      boolean didShowNewScrollers = false;
      boolean canShowNewImage = true;
      for (AxisScroller scroller : scrollers_) {
         if (scroller.getIsSuperlocked()) {
            canShowNewImage = false;
            break;
         }
      }
      int height = 0;
      for (AxisScroller scroller : scrollers_) {
         int imagePosition = event.getPositionForAxis(scroller.getAxis());
         if (scroller.getMaximum() <= imagePosition) {
            if (scroller.getMaximum() == 1) {
               // This scroller was previously hidden and needs to be shown now.
               scroller.setVisible(true);
               add(scroller, "wrap 0px");
               didShowNewScrollers = true;
            }
            // This image is further along the axis for this scrollbar than 
            // the current maximum, so we need a new maximum.
            scroller.setMaximum(imagePosition + 1);
         }
         if (canShowNewImage) {
            scroller.forcePosition(imagePosition);
         }
         if (scroller.isVisible()) {
            height += scroller.getPreferredSize().height;
         }
      }
      if (didShowNewScrollers) {
         // Post an event informing our masters of our new preferred size.
         Dimension size = new Dimension(512, height);
         setPreferredSize(size);
         bus_.post(new LayoutChangedEvent(size));
      }
      if (canShowNewImage) {
         // Start up a timer to restore the scrollers to their original
         // positions, if applicable. 
         if (snapBackTimer_ != null) {
            snapBackTimer_.cancel();
         }
         snapBackTimer_ = new Timer();
         TimerTask task = new TimerTask() {
            @Override
            public void run() {
               for (AxisScroller scroller : scrollers_) {
                  scroller.restorePosition();
               }
            }
         };
         snapBackTimer_.schedule(task, 500);
      }
   }

   /**
    * Set a new animation rate.
    */
   public void setFramesPerSecond(double newFPS) {
      framesPerSec_ = newFPS;
      resetAnimationTimer();
   }

   /**
    * Set the scroller with the given axis to the specified position.
    */
   public void setPosition(String axis, int position) {
      for (AxisScroller scroller : scrollers_) {
         if (scroller.getAxis().equals(axis)) {
            scroller.setPosition(position);
            break;
         }
      }
   }

   /**
    * Return the position of the scroller for the specified axis, or 0 if 
    * we have no scroller for that axis.
    */
   public int getPosition(String axis) {
      for (AxisScroller scroller : scrollers_) {
         if (scroller.getAxis().equals(axis)) {
            return scroller.getPosition();
         }
      }
      return 0;
   }
}
