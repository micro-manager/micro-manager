///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
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

package edu.ucsf.valelab.mmclearvolumeplugin.uielements;

import com.google.common.eventbus.Subscribe;
import edu.ucsf.valelab.mmclearvolumeplugin.CVViewer;
import edu.ucsf.valelab.mmclearvolumeplugin.events.CanvasDrawCompleteEvent;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.AdjustmentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import net.miginfocom.swing.MigLayout;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollBar;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerModel;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.display.DataViewer;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.internal.utils.PopupButton;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.internal.MMStudio;

/**
 * This class displays a grid of scrollbars for selecting which images in a
 * Datastore to show.
 */
public class ScrollerPanel extends JPanel {

   private static final long serialVersionUID = -2504635696950982031L;

  // private final Studio studio_;
   private final DataProvider dataProvider_;
   private final DataViewer display_;
   private final Thread updateThread_;
   private final LinkedBlockingQueue<Coords> updateQueue_;
   private final AtomicBoolean shouldStopUpdates_;

   private final HashMap<String, AxisState> axisToState_;
   private PopupButton fpsButton_;

   private Timer animationTimer_;
   private double animationFPS_ = 3.0;
   private int animationStepSize_ = 0;
   private long lastAnimationTimeMs_ = 0;
   private long lastShownTimePoint_ = 0;;
   private static final int MAXFPS = 30;
   
   private final String CV_ANIMATION_FPS = "Animation fps";

   // We turn this off when we want to update the position of several
   // scrollbars in rapid succession, so that we don't post multiple spurious
   // draw requests.
   private boolean shouldPostEvents_ = true;

   @SuppressWarnings("LeakingThisInConstructor")
   public ScrollerPanel(final DataViewer display) {
      dataProvider_ = display.getDataProvider();
      display_ = display;

      updateQueue_ = new LinkedBlockingQueue<>();
      shouldStopUpdates_ = new AtomicBoolean(false);

      axisToState_ = new HashMap<>();

      // Only the scrollbar column is allowed to grow in width
      // Columns are animate, current position, max position, scrollbar,
      // lock, link
      super.setLayout(new MigLayout("insets 0", 
               "[][][][grow, shrink][][]"));
      // Don't prevent other components from shrinking
      super.setMinimumSize(new Dimension(1, 1));

      // Although it would be nice to rely on AnimationFPS stored in the display-
      // setting, it is most often set too high for our use.  Better to keep a 
      // separate fps for 3D

      animationFPS_ = MMStudio.getInstance().profile().
              getSettings(ScrollerPanel.class).getDouble(CV_ANIMATION_FPS, animationFPS_);
      
      List<String> axes;
      List<String> axisOrder = dataProvider_.getSummaryMetadata().getOrderedAxes();
      if (axisOrder != null) {
         axes = axisOrder;
      }
      else {
         axes = new ArrayList<>(dataProvider_.getAxes());
      }
      for (String axis : axes) {
         // Don't bother creating scrollers for axes with a length of 1.
         if (dataProvider_.getAxisLength(axis) > 1 && !axis.equals(Coords.Z)) {
            addScroller(axis);
         }
      }

      // Spin up a new thread to handle changes to the scrollbar positions.
      // See runUpdateThread() for more information.
      updateThread_ = new Thread(() -> {
         runUpdateThread();
      }, "ClearVolume Scrollbar panel update thread");
      updateThread_.start();
      dataProvider_.registerForEvents(this);
      display_.registerForEvents(this);
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
      final ScrollBarAnimateIcon animateIcon = new ScrollBarAnimateIcon(
            axis, this);
      animateIcon.initialize();
      add(animateIcon, "grow 0");

      // This button displays the current position along the axis, and when
      // clicked, pops up a text field for the user to edit in a new position.
      // Normally we would just use the text field directly; however, then we
      // run into the problem that the setPosition() method calls the non-
      // thread-safe JTextField.setText() method, potentially leading to EDT
      // hangs (because setText() acquires a document lock that the drawing
      // thread needs). And we can't just run setPosition() in the EDT because
      // that causes a gigantic pileup of events on the EDT and a horribly
      // buggy display (which continues making seemingly-random display updates
      // long after an acquisition is finished, for example).
      // The JButton approach dodges this issue because the text field we
      // create on click is not modified by setPosition(), and there are no
      // locks associated with changing the label on the JButton.
      final JButton positionButton = new JButton("1");
      //positionButton.setFont(GUIUtils.buttonFont);
      positionButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
      positionButton.setMinimumSize(new Dimension(30, 18));
      positionButton.setPreferredSize(new Dimension(30, 18));
      positionButton.addActionListener((ActionEvent e) -> {
         new PositionPopup(axis, positionButton).show(
                 positionButton, 0, positionButton.getHeight());
      });
      add(positionButton, "grow 0");

      JLabel maxLabel = new JLabel("/ " + dataProvider_.getAxisLength(axis));
      add(maxLabel, "grow 0");

      final JScrollBar scrollbar = new JScrollBar(JScrollBar.HORIZONTAL, 0, 1,
            0, dataProvider_.getAxisLength(axis));

      scrollbar.setMinimumSize(new Dimension(1, 1));
      scrollbar.addAdjustmentListener((AdjustmentEvent e) -> {
         setPosition(axis, scrollbar.getValue());
      });
      add(scrollbar, "shrinkx, growx, wrap");

      /* Think about:
      ScrollbarLockIcon lock = new ScrollbarLockIcon(axis, display_);
      add(lock, "grow 0");

      LinkButton linker = new LinkButton(
            new ImageCoordsLinker(axis, display_, this),
            display_);
      add(linker, "grow 0, wrap");
      */
      axisToState_.put(axis, new AxisState(positionButton, scrollbar, maxLabel));



      if (fpsButton_ == null) {
         
         // We have at least one scroller, so add our FPS control button.
         SpinnerModel fpsModel = new FpsSpinnerNumberModel(animationFPS_, 1.0, 50.0);
         final JSpinner playbackFpsSpinner = new JSpinner(fpsModel);
         fpsButton_ = PopupButton.create("FPS: " + animationFPS_, playbackFpsSpinner);
         playbackFpsSpinner.addChangeListener(new ChangeListener() {
            @Override
            public void stateChanged(ChangeEvent e) {
               animationFPS_ = (Double) playbackFpsSpinner.getValue();
               display_.setDisplaySettings(display_.
                       getDisplaySettings().copyBuilder().playbackFPS(animationFPS_).build());
               fpsButton_.setText("FPS: " + animationFPS_);
               MMStudio.getInstance().profile().
                     getSettings(ScrollerPanel.class).putDouble(CV_ANIMATION_FPS, 
                          animationFPS_);
            }
         });

         fpsButton_.setFont(fpsButton_.getFont().deriveFont(10.0f));
         int width = 24 + fpsButton_.getFontMetrics(
                 fpsButton_.getFont()).stringWidth("FPS: 100.0");
         fpsButton_.addPopupButtonListener(new PopupButton.Listener() {
            @Override
            public void popupButtonWillShowPopup(PopupButton button) {
               playbackFpsSpinner.setValue(display_.getDisplaySettings().getPlaybackFPS());
            }
         });
/*
         fpsButton_.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               playbackFpsSpinner.setVisible(true);
            }
         });
         */
         add(fpsButton_, "dock east, growy");
      }

   }
   
   public void stopUpdateThread() {
      shouldStopUpdates_.set(true);      
      // also stop all ongoing animations
      // the alternative would be to keep track of all viewers that we control,
      // instead only animate the active viewer
      for (String axis : axisToState_.keySet()) {
         axisToState_.get(axis).isAnimated_ = false;
      }
   }

   /**
    * One of our animation icons has changed state; adjust animation for that
    * icon. Called from the ScrollbarAnimateIcon class.
    * @param axis
    */
   public void toggleAnimation(String axis) {
      axisToState_.get(axis).isAnimated_ = !axisToState_.get(axis).isAnimated_;
      updateAnimation();
   }

   /**
    * Change the displayed image coordinate along the given axis to the
    * specified position. Even if we don't actually change the displayed
    * image, we still need to update our GUI components (scrollbar and
    * button).
    */
   private void setPosition(String axis, int pos) {
      if (pos == axisToState_.get(axis).cachedIndex_) {
         // We're already where we were told to go.
         return;
      }
      boolean didChange = axisToState_.get(axis).cachedIndex_ != pos;
      axisToState_.get(axis).cachedIndex_ = pos;
      // Update controls: the scrollbar and text field. Avoid redundant set
      // commands to avoid posting redundant events (which could hang the EDT).
      JScrollBar scrollbar = axisToState_.get(axis).scrollbar_;
      if (scrollbar.getValue() != pos) {
         scrollbar.setValue(pos);
      }
      // Add one so displayed values are 1-indexed.
      String newText = String.valueOf(Math.min(dataProvider_.getAxisLength(axis), Math.max(0, pos + 1)));
      JButton button = axisToState_.get(axis).posButton_;
      if (!button.getText().contentEquals(newText)) {
         button.setText(newText);
      }
      if (didChange) {
         postDrawEvent();
      }
   }

   /**
    * Post a draw event, if shouldPostEvents_ is set.
    * TODO: this seems to randomly interfere with animations, when the
    * animated axis is linked.
    */
   private void postDrawEvent() {
      if (!shouldPostEvents_) {
         return;
      }

      DefaultCoords.Builder builder = new DefaultCoords.Builder();
      // Fill in default positions for all axes, including those we don't have
      // scrollbars for.
      for (String axis : dataProvider_.getAxes()) {
         builder.index(axis, 0);
      }
      for (String axis : axisToState_.keySet()) {
         int pos = axisToState_.get(axis).scrollbar_.getValue();
         builder.index(axis, pos);
      }
      Coords target = builder.build();
      // Coordinate our linkers.
      //display_.postEvent(new ImageCoordsEvent(target));
      display_.setDisplayPosition(target);
   }

   /**
    * One of our lock icons changed state; update lock statuses.
    */
   /*
   @Subscribe
   public void onLockChanged(ScrollbarLockIcon.LockEvent event) {
      String axis = event.getAxis();
      ScrollbarLockIcon.LockedState lockState = event.getLockedState();
      axisToState_.get(axis).lockState_ = lockState;
      updateAnimation();
   }
*/
   /**
    * Display settings have changed; check for new FPS.
    * @param event
  
   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      if ( event.getDisplay().equals(display_) ) {
         DisplaySettings settings = event.getDisplaySettings();
         if (settings.getAnimationFPS() != null &&
                     settings.getAnimationFPS() != animationFPS_) {
            animationFPS_ = settings.getAnimationFPS();
            animationFPS_ = Math.min(animationFPS_, MAXFPS);
            fpsButton_.setText("FPS: " + animationFPS_);
            updateAnimation();
            studio_.profile().setDouble(this.getClass(), CV_ANIMATION_FPS, 
                 animationFPS_);
         }
      }
   }
   *   */
   

   /**
    * The drawn image has (potentially) changed; start the process of updating
    * our scrollbars to match.
    */
   /*
   @Subscribe
   public void onPixelsSet(PixelsSetEvent event) {
      Coords coords = event.getImage().getCoords();
      try {
         updateQueue_.put(coords);
      }
      catch (InterruptedException e) {} // Ignore it.
   }
   */

   /**
    * We need to keep our scrollbars up-to-date with the currently-displayed
    * images. However, this takes time, and generates Swing events for each
    * scrollbar (which we don't do anything with, but nonetheless they can
    * clog up the EDT). Since images can change rapidly, we don't want to
    * change the scrollbars for each and every new image. Hence this
    * thread-and-queue system, which ignores image updates that we can't
    * respond to quickly enough.
    */
   private void runUpdateThread() {
      while(!shouldStopUpdates_.get()) {
         try {
            // Limit ourselves to about 30 FPS.
            Thread.sleep(1000/MAXFPS);
         }
         catch (InterruptedException e) {} // Ignore it.
         Coords coords = null;
         // Chew through the queue and take only the last item.
         while (!updateQueue_.isEmpty()) {
            coords = updateQueue_.poll();
         }
         if (coords == null) {
            continue;
         }
         synchronized(this) {
            shouldPostEvents_ = false;
            for (String axis : axisToState_.keySet()) {
               JScrollBar scroller = axisToState_.get(axis).scrollbar_;
               if (scroller.getValue() != coords.getIndex(axis)) {
                  scroller.setValue(coords.getIndex(axis));
               }
            }
            shouldPostEvents_ = true;
         }
      }
   }
   
   private boolean isAnimating() {
      boolean isAnimating = false;
      for (String axis : axisToState_.keySet()) {
         if (axisToState_.get(axis).isAnimated_) {
            isAnimating = true;
         }
      }
      return isAnimating;
   }

   /**
    * Update the specified  scrollbar to the desired position. 
    * Lengthen the scrollbar if necessary,
    * and if it doesn't exist then create it. Returns true if a new scrollbar
    * was created.
    */
   private boolean updateScrollbar(String axis, int newPos) {
      // We never want to draw a z Scrollbar, so immediatley return if the
      // request axis is z
      if (axis.equals(Coords.Z)) {
         return false;
      }
      boolean didAddScroller = false;
      if (!axisToState_.containsKey(axis)) {
         if (newPos != 0) {
            // Now have at least two positions along this axis; add a
            // scroller.
            addScroller(axis);
            didAddScroller = true;
         } else {
            // Don't care about this axis as we have no scrollbar to
            // manipulate.
            return false;
         }
      }
      JScrollBar scrollbar = axisToState_.get(axis).scrollbar_;
      int axisLen = newPos + 1;
      if (scrollbar.getMaximum() < axisLen) {
         // Expand the range on the scrollbar.
         scrollbar.setMaximum(axisLen);
         axisToState_.get(axis).maxLabel_.setText(
                 "/ " + (String.valueOf(axisLen)));
      }
      synchronized (this) {
         if (!isAnimating()) {
            scrollbar.setValue(newPos);
         }

      }
      if (axis.equals(Coords.T)) {
         lastShownTimePoint_ = newPos;
      }
      return didAddScroller;
   }
   
   @Subscribe
   public void onDataProviderHasNewImage(DataProviderHasNewImageEvent newImage) {
      if (dataProvider_ != newImage.getDataProvider()){
         return;
      }
      Coords newImageCoords = newImage.getCoords();
      if (CVViewer.timePointComplete(newImageCoords.getT(), dataProvider_, 
              ReportingUtils.getWrapper())) {
         updateScrollbar(Coords.T, newImageCoords.getT());
      } else if (newImageCoords.getT() > lastShownTimePoint_ + 1) {
         updateScrollbar (Coords.T, newImageCoords.getT() - 1);
      }
   }       
   
  
   /**
    * The canvas has finished drawing an image; move to the next one in our
    * animation.
    *
    * @param event
    */
   @Subscribe
   public void onCanvasDrawComplete(CanvasDrawCompleteEvent event) {
      updateAnimation();
   }

   /**
    * Move another step forward in our animation (assuming we are animated).
    */
   private synchronized void updateAnimation() {
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
         lastAnimationTimeMs_ = 0;
         return;
      }

      // Calculate the update rate and the number of images to step forward
      // by for each animation tick. We cap at an update rate of 30FPS;
      // past that, we step forward by more than one image per step to
      // compensate.
      int updateFPS = Math.min(MAXFPS, (int) animationFPS_);
      animationStepSize_ = (int) Math.max(1,
            Math.round(updateFPS * animationFPS_ / 1000.0));

      animationTimer_ = new Timer("Scroller panel animation");
      // This task simply pushes the scrollbars forward and then requests
      // drawing at the new coordinates.
      TimerTask task = new TimerTask() {
         @Override
         public void run() {
            lastAnimationTimeMs_ = System.currentTimeMillis();
            shouldPostEvents_ = false;
            for (String axis : axisToState_.keySet()) {
                if (axisToState_.get(axis).isAnimated_) {
                    advancePosition(axis, animationStepSize_);
                }
            }
            shouldPostEvents_ = true;
            postDrawEvent();
         }
      };

      // Schedule the update to happen either immediately (for the first time
      // we run) or a set time after the last time we ran (for continuous
      // running).
      if (lastAnimationTimeMs_ == 0) {
         animationTimer_.schedule(task, 0);
      }
      else {
         // Target delay between updates
         int delayMs = 1000 / updateFPS;
         long sleepTime = Math.max(0,
               delayMs - (System.currentTimeMillis() - lastAnimationTimeMs_));
         animationTimer_.schedule(task, sleepTime);
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



   // Used by the ImageCoordsLinker to query position.
   public int getIndex(String axis) {
      return axisToState_.get(axis).scrollbar_.getValue();
   }
   
   /**
    * This class tracks relevant state for a single axis' set of controls.
    */
   private class AxisState {
      boolean isAnimated_;
      JButton posButton_;
      JLabel maxLabel_;
      JScrollBar scrollbar_;
      //ScrollbarLockIcon.LockedState lockState_;
      // The saved index is the index we need to snap back to later.
      int savedIndex_;
      // The cached index is the index we last recorded for the
      // scrollbar.
      int cachedIndex_;
      
      public AxisState(JButton posButton, JScrollBar scrollbar, JLabel maxLabel) {
         isAnimated_ = false;
         posButton_ = posButton;
         scrollbar_ = scrollbar;
         maxLabel_ = maxLabel;
         //lockState_ = ScrollbarLockIcon.LockedState.UNLOCKED;
         savedIndex_ = 0;
         cachedIndex_ = 0;
      }
   }

   /**
    * This class shows a popup menu to set the exact location of an axis.
    */
   public class PositionPopup extends JPopupMenu {
      private static final long serialVersionUID = -9058662228484402861L;
      
      public PositionPopup(final String axis, JButton button) {
         super.add(new JLabel("Set index: "));
         final JTextField field = new JTextField(button.getText());
         field.addKeyListener(new KeyAdapter() {
            @Override
            public void keyReleased(KeyEvent event) {
               try {
                  // Subtract one because displayed indices are 1-indexed
                  // while internal are 0-indexed.
                  int newPos = Integer.parseInt(field.getText()) - 1;
                  setPosition(axis, newPos);
               }
               catch (NumberFormatException e) {
                  // Ignore it
               }
            }
         });
         super.add(field);
      }
   }
   
}
