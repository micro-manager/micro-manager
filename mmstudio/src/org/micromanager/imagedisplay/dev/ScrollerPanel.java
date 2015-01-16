package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.JPanel;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.data.DefaultCoords;
import org.micromanager.data.NewImageEvent;
import org.micromanager.imagedisplay.AxisScroller;
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
      private Coords coords_;

      public SetImageEvent(Coords coords) {
         coords_ = coords;
      }

      public Coords getCoords() {
         return coords_;
      }
   }

   /**
    * This class signifies that our layout has changed and our owner needs to 
    * revalidate.
    */
   public static class LayoutChangedEvent {}

   private Datastore store_;
   // We'll be communicating with our owner and with our AxisScrollers via
   // this bus.
   private EventBus displayBus_;
   // All AxisScrollers we manage. protected visibility to allow subclassing
   // (c.f. Navigator plugin)
   protected ArrayList<AxisScroller> scrollers_;
   // The positions of all our scrollers the last time
   // checkForImagePositionChanged() was called.
   private DefaultCoords lastImagePosition_;
   // This will get set to false in cleanup, in turn barring any more
   // timers from getting created.
   private boolean canMakeTimers_ = true;
   // Timer for handling animation.
   private Timer animationUpdateTimer_ = null;
   // Timer for restoring scrollbars after forcing their positions.
   private Timer snapBackTimer_ = null;
   // Rate at which we update images when animating. Defaults to 10.
   private double framesPerSec_;

   /**
    * @param axisToLength Mapping of axis labels to the number of images along
    *        that axis.
    */
   public ScrollerPanel(Datastore store, EventBus displayBus,
         double framesPerSec) {
      // Minimize whitespace around our components.
      super(new net.miginfocom.swing.MigLayout("insets 0, fillx"));

      store_ = store;
      store.registerForEvents(this, 100);
      displayBus_ = displayBus;
      displayBus_.register(this);
      framesPerSec_ = framesPerSec;
      scrollers_ = new ArrayList<AxisScroller>();

      // Create all desired AxisScrollers. Use the first character of the 
      // axis as the label. Default all scrollers to invisible unless they have
      // at least 2, um, "ticks"; they'll be shown once there's more than one
      // option along that axis.
      for (String axis : store_.getAxes()) {
         int max = store_.getMaxIndex(axis) + 1;
         AxisScroller scroller = new AxisScroller(axis, max, displayBus, true);
         if (max <= 1) {
            scroller.setVisible(false);
         }
         else {
            add(scroller, "wrap 0px, align center, growx");
         }
         scrollers_.add(scroller);
      }
   }

   /**
    * One of our AxisScrollers changed position; update the image.
    */
   @Subscribe
   public void onScrollPositionChanged(AxisScroller.ScrollPositionEvent event) {
      checkForImagePositionChanged();
   }

   /**
    * Check to see if the image we are currently "pointing to" with the 
    * scrollers is different from the image that we were last pointing to
    * when this function was called. If so, then we need to post a
    * SetImageEvent to the displayBus so that the image display gets updated.
    */
   private void checkForImagePositionChanged() {
      boolean shouldPostEvent = false;
      if (lastImagePosition_ == null) {
         lastImagePosition_ = (new DefaultCoords.Builder()).build();
      }
      DefaultCoords.Builder newPositionBuilder = new DefaultCoords.Builder();
      for (AxisScroller scroller : scrollers_) {
         String axis = scroller.getAxis();
         Integer position = scroller.getPosition();
         newPositionBuilder.position(axis, position);
         if (lastImagePosition_.getPositionAt(axis) != position) {
            // Position along this axis has changed; we need to refresh.
            shouldPostEvent = true;
         }
      }
      lastImagePosition_ = newPositionBuilder.build();
      if (shouldPostEvent) {
         displayBus_.post(new SetImageEvent(lastImagePosition_));
      }
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
    * The window we're in is closing; cancel animations and timers, and ensure
    * that no new ones can get created.
    */
   public void cleanup() {
      store_.unregisterForEvents(this);
      canMakeTimers_ = false;
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
      if (!canMakeTimers_) {
         // Not allowed to make new timers because we'll be closing soon.
         return;
      }
      // Enforce a maximum displayed framerate of 30FPS; for higher rates, we
      // instead skip over images in animation.
      int stepSize = 1;
      long interval = (long) (1000.0 / framesPerSec_);
      if (interval < 33) {
         interval = 33; 
         stepSize = (int) Math.round(framesPerSec_ * 33.0 / 1000.0);
      }
      boolean isAnimated = false;
      // This is going to be how much we adjust each scroller's position each
      // tick of the animation.
      final int[] offsets = new int[scrollers_.size()];
      for (int i = 0; i < offsets.length; ++i) {
         if (scrollers_.get(i).getIsAnimated()) {
            isAnimated = true;
            offsets[i] = stepSize;
         }
         else {
            offsets[i] = 0;
         }
      }
      if (isAnimated) {
         animationUpdateTimer_ = new Timer();
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
               checkForImagePositionChanged();
            }
         };
         animationUpdateTimer_.schedule(task, 0, interval);
      }
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

      Coords imageCoords = event.getCoords();
      for (AxisScroller scroller : scrollers_) {
         int imagePosition = imageCoords.getPositionAt(scroller.getAxis());
         if (scroller.getMaximum() <= imagePosition) {
            if (scroller.getMaximum() == 1) {
               // This scroller was previously hidden and needs to be shown now.
               scroller.setVisible(true);
               add(scroller, "wrap 0px, align center, growx");
               didShowNewScrollers = true;
            }
            // This image is further along the axis for this scrollbar than 
            // the current maximum, so we need a new maximum.
            scroller.setMaximum(imagePosition + 1);
         }
         if (canShowNewImage) {
            scroller.forcePosition(imagePosition);
         }
      }
      if (didShowNewScrollers) {
         // Post an event informing our masters that our layout has changed.
         displayBus_.post(new LayoutChangedEvent());
      }
      if (canShowNewImage && canMakeTimers_) {
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

   /**
    * Return the maximum position for the specified axis, or 0 if we have
    * no scroller for that axis.
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
