package org.micromanager.imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import java.awt.Dimension;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.util.Collections;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;

import net.miginfocom.swing.MigLayout;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollBar;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.NewImageEvent;
import org.micromanager.data.DefaultCoords;
import org.micromanager.imagedisplay.events.DefaultRequestToDrawEvent;
import org.micromanager.imagedisplay.events.FPSEvent;
import org.micromanager.imagedisplay.events.LayoutChangedEvent;
import org.micromanager.utils.ReportingUtils;


/**
 * This class displays a grid of scrollbars for selecting which images in a
 * Datastore to show.
 */
class ScrollerPanel extends JPanel {
   private Datastore store_;
   private EventBus displayBus_;

   /**
    * This class tracks relevant state for a single axis' set of controls.
    */
   private class AxisState {
      boolean isAnimated_;
      JLabel label_;
      JScrollBar scrollbar_;
      ScrollbarLockIcon.LockedState lockState_;
      // The saved position is the position we need to snap back to later.
      int savedPosition_;
      // The cached position is the position we last recorded for the
      // scrollbar.
      int cachedPosition_;
      
      public AxisState(JLabel label, JScrollBar scrollbar) {
         isAnimated_ = false;
         label_ = label;
         scrollbar_ = scrollbar;
         lockState_ = ScrollbarLockIcon.LockedState.UNLOCKED;
         savedPosition_ = 0;
         cachedPosition_ = 0;
      }
   }

   private HashMap<String, AxisState> axisToState_;
   private HashMap<String, Integer> axisToSavedPosition_;
   private JButton fpsButton_;
   private FPSPopupMenu fpsMenu_;
   private int fps_;

   private Timer snapbackTimer_;
   private Timer animationTimer_;
   // We turn this off when we want to update the position of several
   // scrollbars in rapid succession, so that we don't post multiple spurious
   // draw requests.
   private boolean shouldPostEvents_ = true;

   public ScrollerPanel(Datastore store, EventBus displayBus) {
      store_ = store;
      displayBus_ = displayBus;

      axisToState_ = new HashMap<String, AxisState>();
      axisToSavedPosition_ = new HashMap<String, Integer>();

      // Only the scrollbar column is allowed to grow in width
      setLayout(new MigLayout("insets 0", 
               "[][][grow, shrink][][]"));
      // Don't prevent other components from shrinking
      setMinimumSize(new Dimension(1, 1));

      ArrayList<String> axes = new ArrayList<String>(store_.getAxes());
      Collections.sort(axes);
      for (String axis : axes) {
         // Don't bother creating scrollers for axes with a length of 1.
         if (store_.getAxisLength(axis) > 1) {
            addScroller(axis);
         }
      }

      // TODO: hardcoded initial FPS for now.
      fps_ = 10;
      fpsMenu_ = new FPSPopupMenu(displayBus_, fps_);

      store_.registerForEvents(this, 100);
      displayBus_.register(this);
   }

   /**
    * Add a scroller for the specified axis. Scrollers are a row in our
    * grid-based layout, and consist of:
    * - a start/stop button for animating display of the axis
    * - a label indicating the current displayed index
    * - a scrollbar for moving through the axis
    * - a lock icon for preventing the display from changing
    */
   private void addScroller(final String axis) {
      final ScrollbarAnimateIcon animateIcon = new ScrollbarAnimateIcon(axis);
      Dimension size = new Dimension(24, 14);
      animateIcon.setPreferredSize(size);
      animateIcon.setMaximumSize(size);
      animateIcon.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            toggleAnimation(axis, animateIcon);
            animateIcon.setIsAnimated(axisToState_.get(axis).isAnimated_);
         }
      });
      add(animateIcon, "grow 0");

      JLabel positionLabel = new JLabel();
      add(positionLabel, "grow 0");

      final JScrollBar scrollbar = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1,
            0, store_.getAxisLength(axis));
      scrollbar.setMinimumSize(new Dimension(1, 1));
      scrollbar.addAdjustmentListener(new AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(AdjustmentEvent e) {
            onScrollbarMoved(axis, scrollbar);
         }
      });
      add(scrollbar, "shrinkx, growx");

      ScrollbarLockIcon lock = new ScrollbarLockIcon(axis, displayBus_);
      add(lock, "grow 0, wrap");

      axisToState_.put(axis, new AxisState(positionLabel, scrollbar));

      if (fpsButton_ == null) {
         // We have at least one scroller, so add our FPS control button.
         fpsButton_ = new JButton("FPS: " + fps_);
         fpsButton_.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               fpsMenu_.show(fpsButton_, e.getX(), e.getY());
            }
         });
         add(fpsButton_, "dock east, growy");
      }
   }

   /**
    * One of our animation icons has changed state; adjust animation for that
    * icon.
    */
   private void toggleAnimation(String axis, ScrollbarAnimateIcon icon) {
      axisToState_.get(axis).isAnimated_ = !axisToState_.get(axis).isAnimated_;
      resetAnimation();
   }

   /**
    * One of our scrollbars has changed position; request drawing the
    * appropriate image.
    */
   private void onScrollbarMoved(String axis, JScrollBar scrollbar) {
      // TODO: for some reason we get events telling us the scrollbar has
      // moved to a position it already has. Don't bother publishing redundant
      // events.
      int pos = scrollbar.getValue();
      if (pos == axisToState_.get(axis).cachedPosition_) {
         return;
      }
      axisToState_.get(axis).cachedPosition_ = pos;
      axisToState_.get(axis).label_.setText(String.valueOf(pos));
      postDrawEvent();
   }

   /**
    * Post a draw event, if shouldPostEvents_ is set.
    */
   private void postDrawEvent() {
      if (!shouldPostEvents_) {
         return;
      }

      DefaultCoords.Builder builder = new DefaultCoords.Builder();
      // Fill in default positions for all axes, including those we don't have
      // scrollbars for.
      for (String axis : store_.getAxes()) {
         builder.position(axis, 0);
      }
      for (String axis : axisToState_.keySet()) {
         int pos = axisToState_.get(axis).scrollbar_.getValue();
         builder.position(axis, pos);
      }
      displayBus_.post(new DefaultRequestToDrawEvent(builder.build()));
   }

   /**
    * One of our lock icons changed state; update lock statuses.
    */
   @Subscribe
   public void onLockChanged(ScrollbarLockIcon.LockEvent event) {
      String axis = event.getAxis();
      ScrollbarLockIcon.LockedState lockState = event.getLockedState();
      axisToState_.get(axis).lockState_ = lockState;
      resetAnimation();
   }

   /**
    * Animation state or lock state has changed; reset our animation timers.
    */
   private void resetAnimation() {
      if (animationTimer_ != null) {
         animationTimer_.cancel();
      }

      boolean amAnimated = false;
      for (String axis : axisToState_.keySet()) {
         if (axisToState_.get(axis).isAnimated_) {
            amAnimated = true;
            break;
         }
      }
      if (!amAnimated) {
         // No animation is currently running.
         return;
      }

      // Calculate the update rate and the number of images to step forward
      // by for each animation tick. We cap at an update rate of 30FPS;
      // past that, we step forward by more than one image per step to
      // compensate.
      int updateRate = Math.min(30, fps_);
      final int updateStep = (int) Math.max(1,
            Math.round(updateRate * fps_ / 1000.0));

      animationTimer_ = new Timer();
      TimerTask task = new TimerTask() {
         @Override
         public void run() {
            shouldPostEvents_ = false;
            for (String axis : axisToState_.keySet()) {
               if (axisToState_.get(axis).isAnimated_ &&
                     axisToState_.get(axis).lockState_ == ScrollbarLockIcon.LockedState.UNLOCKED) {
                  advancePosition(axis, updateStep);
               }
            }
            shouldPostEvents_ = true;
            postDrawEvent();
         }
      };

      animationTimer_.schedule(task, 0, (int) (1000.0 / updateRate));
   }

   /**
    * Animation FPS has changed. Redo our button label and reset animations.
    */
   @Subscribe
   public void onNewFPS(FPSPopupMenu.FPSEvent event) {
      fps_ = event.getFPS();
      fpsButton_.setText("FPS: " + fps_);
      resetAnimation();
   }

   /**
    * A new image has arrived; update our scrollbar maxima and positions. Add
    * new scrollbars as needed.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      try {
         Coords coords = event.getImage().getCoords();
         Coords.CoordsBuilder displayedBuilder = coords.copy();
         boolean didAddScrollers = false;
         for (String axis : store_.getAxes()) {
            int newPos = coords.getPositionAt(axis);
            if (!axisToState_.containsKey(axis)) {
               if (newPos != 0) {
                  // Now have at least two positions along this axis; add a
                  // scroller.
                  addScroller(axis);
                  didAddScrollers = true;
               }
               else {
                  // Don't care about this axis as we have no scrollbar to
                  // manipulate.
                  continue;
               }
            }
            JScrollBar scrollbar = axisToState_.get(axis).scrollbar_;
            if (scrollbar.getMaximum() < coords.getPositionAt(axis) + 1) {
               // Expand the range on the scrollbar.
               scrollbar.setMaximum(coords.getPositionAt(axis) + 1);
            }
            int pos = scrollbar.getValue();
            ScrollbarLockIcon.LockedState lockState = axisToState_.get(axis).lockState_;
            if (lockState == ScrollbarLockIcon.LockedState.SUPERLOCKED) {
               // This axis is not allowed to move.
               displayedBuilder.position(axis, pos);
            }
            else if (lockState == ScrollbarLockIcon.LockedState.LOCKED) {
               // This axis can change, but must be snapped back later. Only if
               // we don't already have a saved position, though.
               if (!axisToSavedPosition_.containsKey(axis)) {
                  axisToSavedPosition_.put(axis, pos);
               }
               scrollbar.setValue(newPos);
            }
            else {
               // This axis is allowed to move and we don't need to snap it
               // back later.
               scrollbar.setValue(newPos);
            }
         }
         if (didAddScrollers) {
            // Ensure new scrollers get displayed properly.
            displayBus_.post(new LayoutChangedEvent());
         }
         displayBus_.post(new DefaultRequestToDrawEvent(displayedBuilder.build()));

         // Set up snapping back to our current positions. 
         if (axisToSavedPosition_.size() > 0) {
            if (snapbackTimer_ != null) {
               snapbackTimer_.cancel();
            }
            snapbackTimer_ = new Timer();
            TimerTask task = new TimerTask() {
               @Override
               public void run() {
                  shouldPostEvents_ = false;
                  for (String axis : axisToSavedPosition_.keySet()) {
                     int pos = axisToSavedPosition_.get(axis);
                     axisToState_.get(axis).scrollbar_.setValue(pos);
                  }
                  shouldPostEvents_ = true;
                  postDrawEvent();
               }
            };
            snapbackTimer_.schedule(task, 500);
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error in onNewImage for ScrollerPanel");
      }
   }

   /**
    * Push the relevant scrollbar forward, wrapping around when it hits the
    * end.
    */
   private void advancePosition(String axis, int offset) {
      JScrollBar scrollbar = axisToState_.get(axis).scrollbar_;
      int target = (scrollbar.getValue() + offset) % scrollbar.getMaximum();
      scrollbar.setValue(target);
   }

   public void cleanup() {
      store_.unregisterForEvents(this);
      displayBus_.unregister(this);
      if (animationTimer_ != null) {
         animationTimer_.cancel();
      }
      if (snapbackTimer_ != null) {
         snapbackTimer_.cancel();
      }
   }
}
