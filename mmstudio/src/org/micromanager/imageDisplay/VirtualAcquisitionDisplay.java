///////////////////////////////////////////////////////////////////////////////
//FILE:          VirtualAcquisitionDisplay.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//               Arthur Edelstein, arthuredelstein@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2013
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
package org.micromanager.imageDisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.ScrollbarWithLabel;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import ij.io.FileInfo;
import ij.measure.Calibration;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AdjustmentEvent;
import java.awt.event.AdjustmentListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.prefs.Preferences;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import mmcorej.TaggedImage;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.api.events.PixelSizeChangedEvent;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ImageCacheListener;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.events.EventManager;
import org.micromanager.graph.HistogramControlsState;
import org.micromanager.graph.HistogramSettings;
import org.micromanager.graph.MultiChannelHistograms;
import org.micromanager.graph.SingleChannelHistogram;
import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.internalinterfaces.Histograms;
import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.ContrastSettings;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class VirtualAcquisitionDisplay implements ImageCacheListener {

   public static VirtualAcquisitionDisplay getDisplay(ImagePlus imgp) {
      ImageStack stack = imgp.getStack();
      if (stack instanceof AcquisitionVirtualStack) {
         return ((AcquisitionVirtualStack) stack).getVirtualAcquisitionDisplay();
      } else {
         return null;
      }
   }

   // This class is used to signal when the animation state of our display
   // (potentially) changes.
   public class AnimationSetEvent {
      public boolean isAnimated_;
      public AnimationSetEvent(boolean isAnimated) {
         isAnimated_ = isAnimated;
      }
   }

   private static final int ANIMATION_AND_LOCK_RESTART_DELAY = 800;
   final ImageCache imageCache_;
   final Preferences prefs_ = Preferences.userNodeForPackage(this.getClass());
   private static final String SIMPLE_WIN_X = "simple_x";
   private static final String SIMPLE_WIN_Y = "simple_y";
   private AcquisitionEngine eng_;
   private boolean finished_ = false;
   private boolean promptToSave_ = true;
   private String name_;
   private long lastDisplayTime_;
   private int lastFrameShown_ = 0;
   private int lastSliceShown_ = 0;
   private int lastPositionShown_ = 0;
   private int lockedSlice_ = -1, lockedPosition_ = -1, lockedChannel_ = -1, lockedFrame_ = -1;;
   private int numComponents_;
   private ImagePlus hyperImage_;
   private ScrollbarWithLabel pSelector_;
   private ScrollbarWithLabel tSelector_;
   private ScrollbarWithLabel zSelector_;
   private ScrollbarWithLabel cSelector_;
   private DisplayControls controls_;
   public AcquisitionVirtualStack virtualStack_;
   private boolean isSimpleDisplay_ = false;
   private boolean mda_ = false; //flag if display corresponds to MD acquisition
   private MetadataPanel mdPanel_;
   private boolean contrastInitialized_ = false; //used for autostretching on window opening
   private boolean firstImage_ = true;
   private String channelGroup_ = "none";
   private double framesPerSec_ = 7;
   private java.util.Timer animationTimer_ = new java.util.Timer();
   private boolean zAnimated_ = false, tAnimated_ = false;
   private int animatedSliceIndex_ = -1, animatedFrameIndex_ = -1;
   private Component zAnimationIcon_, pIcon_, tAnimationIcon_, cIcon_;
   private Component zLockIcon_, cLockIcon_, pLockIcon_, tLockIcon_;
   private Timer resetToLockedTimer_;
   private Histograms histograms_;
   private HistogramControlsState histogramControlsState_;
   private boolean albumSaved_ = false;
   private static double snapWinMag_ = -1;
   private JPopupMenu saveTypePopup_;
   private AtomicBoolean updatePixelSize_ = new AtomicBoolean(false);
   private AtomicLong newPixelSize_ = new AtomicLong();
   private final Object imageReceivedObject_ = new Object();

   private EventBus bus_;

   @Subscribe
   public void onPixelSizeChanged(PixelSizeChangedEvent event) {
      // Signal that pixel size has changed so that the next image will update
      // metadata and scale bar
      newPixelSize_.set(Double.doubleToLongBits(event.getNewPixelSizeUm()));
      updatePixelSize_.set(true);
   }

   /**
    * Constructor that doesn't provide a title; an automatically-generated one
    * is used instead.
    */
   public VirtualAcquisitionDisplay(ImageCache imageCache, AcquisitionEngine eng) {
      this(imageCache, eng, WindowManager.getUniqueName("Untitled"));
      setupEventBus();
   }

   /**
    * Standard constructor.
    */
   public VirtualAcquisitionDisplay(ImageCache imageCache, AcquisitionEngine eng, String name) {
      name_ = name;
      imageCache_ = imageCache;
      eng_ = eng;
      pSelector_ = createPositionScrollbar();
      mda_ = eng != null;
      this.albumSaved_ = imageCache.isFinished();
      setupEventBus();
   }

   /**
    * Create a new EventBus that will be used for all events related to this
    * display system.
    */
   private void setupEventBus() {
      bus_ = new EventBus();
      bus_.register(this);
   }

   // Retrieve our EventBus.
   public EventBus getEventBus() {
      return bus_;
   }

   // Prepare for a drawing event.
   @Subscribe
   public void onDraw(DrawEvent event) {
      imageChangedUpdate();
   }

   /**
    * This constructor is used for the Snap and Live views. The main
    * differences:
    * - eng_ is null
    * - isSimpleDisplay_ is true
    * - We subscribe to the "pixel size changed" event. 
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public VirtualAcquisitionDisplay(ImageCache imageCache, String name) throws MMScriptException {
      isSimpleDisplay_ = true;
      imageCache_ = imageCache;
      name_ = name;
      mda_ = false;
      this.albumSaved_ = imageCache.isFinished();
      setupEventBus();
      // Also register us for pixel size change events on the global EventBus.
      EventManager.register(this);
   }
  
   /**
    * Extract a lot of fields from the provided metadata (or, failing that, 
    * from getSummaryMetadata()), and set up our controls and view window.
    */
   private void startup(JSONObject firstImageMetadata, AcquisitionVirtualStack virtualStack) {
      mdPanel_ = MMStudioMainFrame.getInstance().getMetadataPanel();
      JSONObject summaryMetadata = getSummaryMetadata();
      int numSlices = 1;
      int numFrames = 1;
      int numChannels = 1;
      int numGrayChannels;
      int numPositions = 1;
      int width = 0;
      int height = 0;
      int numComponents = 1;
      try {
         int imageChannelIndex;
         if (firstImageMetadata != null) {
            width = MDUtils.getWidth(firstImageMetadata);
            height = MDUtils.getHeight(firstImageMetadata);
            try {
               imageChannelIndex = MDUtils.getChannelIndex(firstImageMetadata);
            } catch (JSONException e) {
               imageChannelIndex = -1;
            }
         } else {
            width = MDUtils.getWidth(summaryMetadata);
            height = MDUtils.getHeight(summaryMetadata);
            imageChannelIndex = -1;
         }
         numSlices = Math.max(summaryMetadata.getInt("Slices"), 1);
         numFrames = Math.max(summaryMetadata.getInt("Frames"), 1);

         numChannels = Math.max(1 + imageChannelIndex,
                 Math.max(summaryMetadata.getInt("Channels"), 1));
         numPositions = Math.max(summaryMetadata.getInt("Positions"), 1);
         numComponents = Math.max(MDUtils.getNumberOfComponents(summaryMetadata), 1);
      } catch (JSONException e) {
         ReportingUtils.showError(e);
      } catch (MMScriptException e) {
         ReportingUtils.showError(e);
      }
      numComponents_ = numComponents;
      numGrayChannels = numComponents_ * numChannels;

      if (imageCache_.getDisplayAndComments() == null || 
            imageCache_.getDisplayAndComments().isNull("Channels")) {
         try {
            imageCache_.setDisplayAndComments(DisplaySettings.getDisplaySettingsFromSummary(summaryMetadata));
         } catch (Exception ex) {
            ReportingUtils.logError(ex, "Problem setting display and Comments");
         }
      }

      int type = 0;
      try {
         if (firstImageMetadata != null) {
            type = MDUtils.getSingleChannelType(firstImageMetadata);
         } else {
            type = MDUtils.getSingleChannelType(summaryMetadata);
         }
      } catch (JSONException ex) {
         ReportingUtils.showError(ex, "Unable to determine acquisition type.");
      } catch (MMScriptException ex) {
         ReportingUtils.showError(ex, "Unable to determine acquisition type.");
      }
      if (virtualStack != null) {
         virtualStack_ = virtualStack;
      } else {
         virtualStack_ = new AcquisitionVirtualStack(width, height, type, null,
                 imageCache_, numGrayChannels * numSlices * numFrames, this);
      }
      if (summaryMetadata.has("PositionIndex")) {
         try {
            virtualStack_.setPositionIndex(
                  MDUtils.getPositionIndex(summaryMetadata));
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
      }
      // Hack: allow controls_ to be already set, so that overriding classes
      // can implement their own custom controls.
      if (controls_ == null) {
         if (isSimpleDisplay_) {
            controls_ = new SimpleWindowControls(this, bus_);
         } else {
            controls_ = new HyperstackControls(this, bus_);
         }
      }
      hyperImage_ = createHyperImage(createMMImagePlus(virtualStack_),
              numGrayChannels, numSlices, numFrames, virtualStack_);

      applyPixelSizeCalibration(hyperImage_);

      histogramControlsState_ =  mdPanel_.getContrastPanel().createDefaultControlsState();
      createWindow();
      windowToFront();

      cSelector_ = getSelector("c");
      if (!isSimpleDisplay_) {
         tSelector_ = getSelector("t");
         zSelector_ = getSelector("z");

         if (imageCache_.lastAcquiredFrame() > 0) {
            setNumFrames(1 + imageCache_.lastAcquiredFrame());
         } else {
            setNumFrames(1);
         }
         configureAnimationControls();
         setNumPositions(numPositions);
      }

      updateAndDraw(true);
      updateWindowTitleAndStatus();

      forceControlsRepaint();
   }

   /*
    * Set display to one of three modes:
    * ij.CompositeImage.COMPOSITE
    * ij.CompositeImage.GRAYSCALE
    * ij.CompositeImage.COLOR
    */
   public void setDisplayMode(int displayMode) {
      mdPanel_.getContrastPanel().setDisplayMode(displayMode);
   }

   ///////////////////////////////////////////////////////
   ///////Scrollbars and animation controls section///////
   ///////////////////////////////////////////////////////
   /**
    * Force all of our controls to repaint themselves, and block until they
    * have finished doing so. 
    */
   private void forceControlsRepaint() {
      Runnable forcePaint = new Runnable() {

         @Override
         public void run() {
            if (zAnimationIcon_ != null) {
               zAnimationIcon_.repaint();
            }
            if (tAnimationIcon_ != null) {
               tAnimationIcon_.repaint();
            }
            if (tLockIcon_ != null) {
               tLockIcon_.repaint();
            }
            if (cIcon_ != null) {
               cIcon_.repaint();
            }
            if (cLockIcon_ != null) {
               cLockIcon_.repaint();
            }
            if (zLockIcon_ != null) {
               zLockIcon_.repaint();
            }
            if (pLockIcon_ != null) {
               pLockIcon_.repaint();
            }
            if (controls_ != null) {
               controls_.repaint();
            }
            if (pIcon_ != null && pIcon_.isValid()) {
               pIcon_.repaint();
            }
         }
      };

      try {
         GUIUtils.invokeAndWait(forcePaint);
      } catch (InterruptedException ex) {
         ReportingUtils.logError(ex);
      } catch (InvocationTargetException ex) {
         ReportingUtils.logError(ex);
      }
   }

   /**
    * Toggle animation of Z-slices on and off.
    */
   private synchronized void setStackAnimation(final boolean shouldAnimate) {
      if (!shouldAnimate) {
         // Just shut down current animation.
         animationTimer_.cancel();
         setZAnimated(false);
         refreshScrollbarIcons();
         moveScrollBarsToLockedPositions();
      } else {
         // Disable timewise animation now. 
         setTimepointAnimation(false);
         // Create a new animation timer that steps through Z.
         setAnimationTimer(1, 0);
         setZAnimated(true);
         refreshScrollbarIcons();
      }
   }

   /**
    * As setStackAnimation, except that we animate across timepoints instead.
    */
   private synchronized void setTimepointAnimation(final boolean shouldAnimate) {
      if (!shouldAnimate) {
         // Just shut down current animation.
         animationTimer_.cancel();
         setTAnimated(false);
         refreshScrollbarIcons();
         moveScrollBarsToLockedPositions();
      } else {
         // Disable stack animation now. 
         setStackAnimation(false);
         // Create a new animation timer that steps through T.
         setAnimationTimer(0, 1);
         setTAnimated(true);
         refreshScrollbarIcons();
      }
   }

   /**
    * Set up animationTimer_ to update our display according to the provided
    * settings. As a general rule, one of zStep or tStep will be 0 and the 
    * other will be 1, but theoretically we could call this function to step
    * backwards or to step through both "dimensions" at the same time. 
    */
   private synchronized void setAnimationTimer(final int zStep, final int tStep) {
      animationTimer_ = new java.util.Timer();
      final int framesPerStep;
      long interval = (long) (1000.0 / framesPerSec_);
      if (interval < 33) {
         // Enforce a maximum displayed framerate of 30FPS, but skip over 
         // images to make the perceived animation faster. 
         interval = 33;
         framesPerStep = (int) Math.round(framesPerSec_ * 33.0 / 1000.0);
      } else {
         framesPerStep = 1;
      }
      TimerTask task = new TimerTask() {
         @Override
         public void run() {
            int channel = lockedChannel_ == -1 ? hyperImage_.getChannel() : lockedChannel_;
            int slice = lockedSlice_ == -1 ? hyperImage_.getSlice() : lockedSlice_;
            int frame = lockedFrame_ == -1 ? hyperImage_.getFrame() : lockedFrame_;
            if (lockedSlice_ == -1 && zSelector_ != null) {
               // Advance the slice position.
               slice += framesPerStep * zStep;
               if (slice >= zSelector_.getMaximum()) {
                  // Wrap around to the other end of the stack
                  slice = 1;
               }
            }
            if (lockedFrame_ == -1 && tSelector_ != null) {
               // Advance the timepoint position.
               frame += framesPerStep * tStep;
               if (frame >= tSelector_.getMaximum()) {
                  // Wrap around to the other end of the timeseries
                  frame = 1;
               }
            }
            hyperImage_.setPosition(channel, slice, frame);
         }
      };
      animationTimer_.schedule(task, 0, interval);
   }

   /**
    * Repaint all of our icons related to the scrollbars. Non-blocking.
    */
   private void refreshScrollbarIcons() {
      if (zAnimationIcon_ != null) {
         zAnimationIcon_.repaint();
      }
      if (tAnimationIcon_ != null) {
         tAnimationIcon_.repaint();
      }
      if (zLockIcon_ != null) {
         zLockIcon_.repaint();
      }
      if (cLockIcon_ != null) {
         cLockIcon_.repaint();
      }
      if (pLockIcon_ != null) {
         pLockIcon_.repaint();
      }
      if (tLockIcon_ != null) {
         tLockIcon_.repaint();
      }
   }

   /**
    * Set up the handling of the animation buttons, to turn animation on and 
    * off for Z and Time.
    */
   private void configureAnimationControls() {
      if (zAnimationIcon_ != null) {
         zAnimationIcon_.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               setStackAnimation(!zAnimated_);
            }
         });
      }
      if (tAnimationIcon_ != null) {
         tAnimationIcon_.addMouseListener(new MouseInputAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
               setTimepointAnimation(!tAnimated_);
            }
         });
      }
   }

   /**
    * Update the number of sites this display handles. 
    */
   private void setNumPositions(int n) {
      if (isSimpleDisplay_) {
         return;
      }
      pSelector_.setMinimum(0);
      pSelector_.setMaximum(n);
      ImageWindow win = hyperImage_.getWindow();
      if (n > 1 && pSelector_.getParent() == null) {
         // Make certain that the site selector scrollbar is displayed.
         win.add(pSelector_, win.getComponentCount() - 1);
      } else if (n <= 1 && pSelector_.getParent() != null) {
         // Conversely, make certain the site selector scrollbar *isn't*
         // displayed, because there's only one site. 
         win.remove(pSelector_);
      }
      win.pack();
   }

   /**
    * Update the number of timepoints this display handles.
    */
   private void setNumFrames(int n) {
      if (isSimpleDisplay_) {
         return;
      }
      if (tSelector_ != null) {
         ((IMMImagePlus) hyperImage_).setNFramesUnverified(n);
         tSelector_.setMaximum(n + 1);
      }
   }

   /**
    * Update the number of Z slices this display handles.
    */
   private void setNumSlices(int n) {
      if (isSimpleDisplay_) {
         return;
      }
      if (zSelector_ != null) {
         ((IMMImagePlus) hyperImage_).setNSlicesUnverified(n);
         zSelector_.setMaximum(n + 1);
      }
   }

   /**
    * Update the number of channels this display handles.
    */
   private void setNumChannels(int n) {
      if (cSelector_ != null) {
         ((IMMImagePlus) hyperImage_).setNChannelsUnverified(n);
         cSelector_.setMaximum(1 + n);
      }
   }
 
   /**
    * If animation was running prior to showImage, restarts it with sliders at
    * appropriate positions.
    */
   private void restartAnimation(int frame, int slice, 
         boolean areFramesAnimated, boolean areSlicesAnimated) {
      if (areFramesAnimated) {
         hyperImage_.setPosition(hyperImage_.getChannel(), 
               hyperImage_.getSlice(), frame + 1);
         setTimepointAnimation(true);
      } else if (areSlicesAnimated) {
         hyperImage_.setPosition(hyperImage_.getChannel(), 
               slice + 1, hyperImage_.getFrame());
         setStackAnimation(true);
      }
      animatedSliceIndex_ = -1;
      animatedFrameIndex_ = -1;
   }

   /**
    * Reset the positions of the scrollbars to where they were when they were
    * locked. 
    */
   private void moveScrollBarsToLockedPositions() {
      int c = lockedChannel_ == -1 ? hyperImage_.getChannel() : lockedChannel_;
      int s = lockedSlice_ == -1 ? hyperImage_.getSlice() : lockedSlice_;
      int f = lockedFrame_ == -1 ? hyperImage_.getFrame() : lockedFrame_;
      hyperImage_.setPosition(c, 
            zAnimated_ ? hyperImage_.getSlice() : s, 
            tAnimated_ ? hyperImage_.getFrame() : f);
      if (pSelector_ != null && lockedPosition_ > -1) {
         setPosition(lockedPosition_);
      }
   }

   /**
    * If we have active locks, set up a timer to reset our scrollbars to their
    * locked positions if no new images arrive within 
    * ANIMATION_AND_LOCK_RESTART_DELAY milliseconds. 
    */
   private void resumeLocksAndAnimationAfterImageArrival() {
      //if no locks activated or previous animation running, dont execute
      if (lockedFrame_ == -1 && lockedChannel_ == -1 && lockedSlice_ == -1 && 
            lockedPosition_ == -1 && animatedSliceIndex_ == -1 && 
            animatedFrameIndex_ == -1) {
         return;
      }
      
      //If no new images have arrived for some time, reset to locked positions
      if (resetToLockedTimer_ == null) {
         // Create a new timer to perform the reset after the specified delay.
         resetToLockedTimer_ = new Timer(ANIMATION_AND_LOCK_RESTART_DELAY, 
               new ActionListener() {
                  @Override
                  public void actionPerformed(ActionEvent e) {
                     moveScrollBarsToLockedPositions();
                     restartAnimation(animatedFrameIndex_, animatedSliceIndex_,
                           animatedFrameIndex_ > -1,
                           animatedSliceIndex_ > -1);
                     resetToLockedTimer_.stop();
                  }
               }
         );
      }
      if (resetToLockedTimer_.isRunning()) {
         // Already have a timer running; just reset its time-to-wait. 
         resetToLockedTimer_.restart();
      } else {
         resetToLockedTimer_.start();
      }
   }

   /**
    * Generate a scrollbar for navigating between the different 
    * timepoints/Z-slices/colors of the stack, as appropriate. 
    * @param label Label of the scrollbar; expected to be "t", "z", or "c".
    * @return an ij.gui.ScrollbarWithLabel, with its events set up to adjust
    *         the appropriate view axis when scrolled.
    */
    private ScrollbarWithLabel getSelector(String label) {
      ScrollbarWithLabel selector = null;
      ImageWindow win = hyperImage_.getWindow();
      int slices = ((IMMImagePlus) hyperImage_).getNSlicesUnverified();
      int frames = ((IMMImagePlus) hyperImage_).getNFramesUnverified();
      int channels = ((IMMImagePlus) hyperImage_).getNChannelsUnverified();
      if (win instanceof StackWindow) {
         try {
            //ImageJ bug workaround
            if (frames > 1 && slices == 1 && channels == 1 && label.equals("t")) {
               selector = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue((StackWindow) win, StackWindow.class, "zSelector");
            } else {
               selector = (ScrollbarWithLabel) JavaUtils.getRestrictedFieldValue((StackWindow) win, StackWindow.class, label + "Selector");
            }
         } catch (NoSuchFieldException ex) {
            selector = null;
            ReportingUtils.logError(ex);
         }
      }
      //replace default icon with custom one
      if (selector != null) {
         try {
            Component icon = (Component) JavaUtils.getRestrictedFieldValue(
                    selector, ScrollbarWithLabel.class, "icon");
            selector.remove(icon);
         } catch (NoSuchFieldException ex) {
            ReportingUtils.logError(ex);
         }
         ScrollbarAnimateIcon newIcon = new ScrollbarAnimateIcon(label.charAt(0), this);
         if (label.equals("z")) {
            zAnimationIcon_ = newIcon;
         } else if (label.equals("t")) {
            tAnimationIcon_ = newIcon;
         } else if (label.equals("c")) {
            cIcon_ = newIcon;
         }

         selector.add(newIcon, BorderLayout.WEST);
         addSelectorLockIcon(selector, label);
         
         //add adjustment so locks respond to mouse input
         selector.addAdjustmentListener(new AdjustmentListener() {
            @Override
            public void adjustmentValueChanged(AdjustmentEvent e) {
               if (lockedSlice_ != -1) {
                  lockedSlice_ = zSelector_.getValue();
               }
               if (lockedChannel_ != -1) {
                  lockedChannel_ = cSelector_.getValue();
               }
               if (lockedFrame_ != -1) {
                  lockedFrame_ = tSelector_.getValue();
               }
            }  
         });
                
         selector.invalidate();
         selector.validate();
      }
      return selector;
   }
   
   /** 
    * Generate the icon to lock one of the scrollbars (for position, 
    * Z, channel, or T). Set the appropriate member field.
    * @param label Indicates which scrollbar this lock is for; must be one of 
    *        "p", "z", "c", or "t".
    */
   private void addSelectorLockIcon(ScrollbarWithLabel selector, 
         final String label) {
      final ScrollbarLockIcon icon = new ScrollbarLockIcon(this, label);
      selector.add(icon, BorderLayout.EAST);
      if (label.equals("p")) {
         pLockIcon_ = icon;
      } else if (label.equals("z")) {
         zLockIcon_ = icon;
      } else if (label.equals("c")) {
         cLockIcon_ = icon;
      } else if (label.equals("t")) {
         tLockIcon_ = icon;
      }
      
      icon.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mouseClicked(MouseEvent e) {
            if (label.equals("p")) {
               if (lockedPosition_ == -1) {
                  lockedPosition_ = pSelector_.getValue();
               } else {
                  lockedPosition_ = -1;
               }
            } else if (label.equals("z")) {
               if (lockedSlice_ == -1) {
                  if (isZAnimated()) {
                     setStackAnimation(false);
                  }
                  lockedSlice_ = zSelector_.getValue();
               } else {
                  lockedSlice_ = -1;
               }
            } else if (label.equals("c")) {
               if (lockedChannel_ == -1) {
                  lockedChannel_ = cSelector_.getValue();
               } else {
                  lockedChannel_ = -1;
               }
            } else if (label.equals("t")) {
               if (lockedFrame_ == -1) {
                  lockedFrame_ = tSelector_.getValue();
               } else {
                  lockedFrame_ = -1;
               }
            }
            resumeLocksAndAnimationAfterImageArrival();
            refreshScrollbarIcons();
         }
      }); 
   }
   
   //Used by icon to know hoe to paint itself
   boolean isScrollbarLocked(String label) {
      if (label.equals("z")) {
         return lockedSlice_ > -1;
      } else if (label.equals("c")) {
         return lockedChannel_ > -1;
      } else if (label.equals("p")) {
         return lockedPosition_ > -1;
      } else {
         return lockedFrame_ > -1;
      }
   }

   private ScrollbarWithLabel createPositionScrollbar() {
      final ScrollbarWithLabel pSelector = new ScrollbarWithLabel(null, 1, 1, 1, 2, 'p') {
         @Override
         public void setValue(int v) {
            if (this.getValue() != v) {
               super.setValue(v);
               updatePosition(v);
            }
         }
      };

      // prevents scroll bar from blinking on Windows:
      pSelector.setFocusable(false);
      pSelector.setUnitIncrement(1);
      pSelector.setBlockIncrement(1);
      pSelector.addAdjustmentListener(new AdjustmentListener() {
         @Override
         public void adjustmentValueChanged(AdjustmentEvent e) {
            if (lockedPosition_ != -1) {
               lockedPosition_ = pSelector_.getValue();
            }
            updatePosition(pSelector.getValue());
         }
      });

      try {
         Component icon = (Component) JavaUtils.getRestrictedFieldValue(
                 pSelector, ScrollbarWithLabel.class, "icon");
         pSelector.remove(icon);
      } catch (NoSuchFieldException ex) {
         ReportingUtils.logError(ex);
      }

      pIcon_ = new ScrollbarAnimateIcon('p', this);
      pSelector.add(pIcon_, BorderLayout.WEST);
      addSelectorLockIcon(pSelector, "p");
      pSelector.invalidate();
      pSelector.validate();


      return pSelector;
   }
   ////////////////////////////////////////////////////////////////////////////////
   ////////End of animation controls and scrollbars section///////////////////////
   ////////////////////////////////////////////////////////////////////////////////
   
   /**
    * Allows bypassing the prompt to Save
    * @param promptToSave boolean flag
    */
   public void promptToSave(boolean promptToSave) {
      promptToSave_ = promptToSave;
   }

   /**
    * required by ImageCacheListener
    * @param taggedImage 
    */
   @Override
   public  void imageReceived(final TaggedImage taggedImage) {
      if (hyperImage_ == null) {
         updateDisplay(taggedImage, false);
         return;
      }
      if (!CanvasPaintPending.isMyPaintPending(hyperImage_.getCanvas(), imageReceivedObject_)) {
         // If we do not sleep here, the window never updates
         // I do not understand why, but this fixes it
         try {
            Thread.sleep(25);
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex, "Sleeping Thread was woken");
         }
         CanvasPaintPending.setPaintPending(hyperImage_.getCanvas(), imageReceivedObject_);
         updateDisplay(taggedImage, false);
         
      }
   }

   /**
    * Method required by ImageCacheListener
    * @param path
    */
   @Override
   public void imagingFinished(String path) {
      updateDisplay(null, true);
      updateAndDraw(true);
      if (!(eng_ != null && eng_.abortRequested())) {
         updateWindowTitleAndStatus();
      }
   }

   private void updateDisplay(TaggedImage taggedImage, boolean finalUpdate) {
      try {
         long t = System.currentTimeMillis();
         JSONObject tags;
         if (taggedImage != null) {
            tags = taggedImage.tags;
         } else {
            tags = imageCache_.getLastImageTags();
         }
         if (tags == null) {
            return;
         }
         int frame = MDUtils.getFrameIndex(tags);
         int ch = MDUtils.getChannelIndex(tags);
         int slice = MDUtils.getSliceIndex(tags);
         int position = MDUtils.getPositionIndex(tags);

         int updateTime = 30;
         //update display if: final update, frame is 0, more than 30 ms since last update, 
         //last channel for given frame/slice/position, or final slice and channel for first frame and position
         boolean show = finalUpdate || frame == 0 || (Math.abs(t - lastDisplayTime_) > updateTime)
                 || (ch == getNumChannels() - 1 && lastFrameShown_ == frame && lastSliceShown_ == slice && lastPositionShown_ == position)
                 || (slice == getNumSlices() - 1 && frame == 0 && position == 0 && ch == getNumChannels() - 1);

         if (show) {
            showImage(tags, true);
            lastFrameShown_ = frame;
            lastSliceShown_ = slice;
            lastPositionShown_ = position;
            lastDisplayTime_ = t;
         }  
      } catch (JSONException e) {
         ReportingUtils.logError(e);
      } catch (InterruptedException e) {
         ReportingUtils.logError(e);
      } catch (InvocationTargetException e) {
         ReportingUtils.logError(e);
      }
   }

   public int rgbToGrayChannel(int channelIndex) {
      try {
         if (MDUtils.getNumberOfComponents(imageCache_.getSummaryMetadata()) == 3) {
            return channelIndex * 3;
         }
         return channelIndex;
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
         return 0;
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
         return 0;
      }
   }

   public int grayToRGBChannel(int grayIndex) {
      try {
         if (imageCache_ != null) {
            if (imageCache_.getSummaryMetadata() != null)
            if (MDUtils.getNumberOfComponents(imageCache_.getSummaryMetadata()) == 3) {
               return grayIndex / 3;
            }
         }
         return grayIndex;
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
         return 0;
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
         return 0;
      }
   }

   /**
    * Sets ImageJ pixel size calibration
    * @param hyperImage
    */
   private void applyPixelSizeCalibration(final ImagePlus hyperImage) {
      final String pixSizeTag = "PixelSizeUm";
      try {
         JSONObject tags = this.getCurrentMetadata();
         JSONObject summary = getSummaryMetadata();
         double pixSizeUm;
         if (tags != null && tags.has(pixSizeTag)) {
            pixSizeUm = tags.getDouble(pixSizeTag);
         } else {
            pixSizeUm = summary.getDouble("PixelSize_um");
         }
         if (pixSizeUm > 0) {
            Calibration cal = new Calibration();
            cal.setUnit("um");
            cal.pixelWidth = pixSizeUm;
            cal.pixelHeight = pixSizeUm;
            String intMs = "Interval_ms";
            if (summary.has(intMs))
               cal.frameInterval = summary.getDouble(intMs) / 1000.0;
            String zStepUm = "z-step_um";
            if (summary.has(zStepUm))
               cal.pixelDepth = summary.getDouble(zStepUm);
            hyperImage.setCalibration(cal);
            // this call is needed to update the top status line with image size
            ImageWindow win = hyperImage.getWindow();
            if (win != null) {
               win.repaint();
            }
         }
      } catch (JSONException ex) {
         // no pixelsize defined.  Nothing to do
      }
   }

   public ImagePlus getHyperImage() {
      return hyperImage_;
   }

   public int getStackSize() {
      if (hyperImage_ == null) {
         return -1;
      }
      int s = hyperImage_.getNSlices();
      int c = hyperImage_.getNChannels();
      int f = hyperImage_.getNFrames();
      if ((s > 1 && c > 1) || (c > 1 && f > 1) || (f > 1 && s > 1)) {
         return s * c * f;
      }
      return Math.max(Math.max(s, c), f);
   }

   private void imageChangedWindowUpdate() {
      if (hyperImage_ != null && hyperImage_.isVisible()) {
         JSONObject md = getCurrentMetadata();
         if (md != null) {
            controls_.newImageUpdate(md);
         }
      }
   }
   
   public void updateAndDraw(boolean force) {
      imageChangedUpdate();
      if (hyperImage_ != null && hyperImage_.isVisible()) {  
         if (hyperImage_ instanceof MMCompositeImage) {                   
            ((MMCompositeImage) hyperImage_).updateAndDraw(force);
         } else {
            hyperImage_.updateAndDraw();
         }
      }
   }

   public void updateWindowTitleAndStatus() {
      if (isSimpleDisplay_) {
         int mag = (int) (100 * hyperImage_.getCanvas().getMagnification());
         String title = hyperImage_.getTitle() + " ("+mag+"%)";
         hyperImage_.getWindow().setTitle(title);
         return;
      }
      if (controls_ == null) {
         return;
      }

      String status = "";
      final AcquisitionEngine eng = eng_;

      if (eng != null) {
         if (acquisitionIsRunning()) {
            if (!abortRequested()) {
               controls_.acquiringImagesUpdate(true);
               if (isPaused()) {
                  status = "paused";
               } else {
                  status = "running";
               }
            } else {
               controls_.acquiringImagesUpdate(false);
               status = "interrupted";
            }
         } else {
            controls_.acquiringImagesUpdate(false);
            if (!status.contentEquals("interrupted")) {
               if (eng.isFinished()) {
                  status = "finished";
                  eng_ = null;
               }
            }
         }
         status += ", ";
         if (eng.isFinished()) {
            eng_ = null;
            finished_ = true;
         }
      } else {
         if (finished_ == true) {
            status = "finished, ";
         }
         controls_.acquiringImagesUpdate(false);
      }
      if (isDiskCached() || albumSaved_) {
         status += "on disk";
      } else {
         status += "not yet saved";
      }

      controls_.imagesOnDiskUpdate(imageCache_.getDiskLocation() != null);
      String path = isDiskCached()
              ? new File(imageCache_.getDiskLocation()).getName() : name_;

      if (hyperImage_.isVisible()) {
         int mag = (int) (100 * hyperImage_.getCanvas().getMagnification());
         hyperImage_.getWindow().setTitle(path + " (" + status + ") (" + mag + "%)" );
      }

   }

   private void windowToFront() {
      if (hyperImage_ == null || hyperImage_.getWindow() == null) {
         return;
      }
      hyperImage_.getWindow().toFront();
   }

   /**
    * Displays tagged image in the multi-D viewer
    * Will wait for the screen update
    *      
    * @param taggedImg
    * @throws Exception 
    */
   public void showImage(TaggedImage taggedImg) throws Exception {
      showImage(taggedImg, true);
   }

   /**
    * Displays tagged image in the multi-D viewer
    * Optionally waits for the display to draw the image
    *     * 
    * @param taggedImg 
    * @param waitForDisplay 
    * @throws java.lang.InterruptedException 
    * @throws java.lang.reflect.InvocationTargetException 
    */
   public void showImage(TaggedImage taggedImg, boolean waitForDisplay) throws InterruptedException, InvocationTargetException {
      showImage(taggedImg.tags, waitForDisplay);
   }
   
   public void showImage(final JSONObject tags, boolean waitForDisplay) throws InterruptedException, InvocationTargetException {
      SwingUtilities.invokeLater( new Runnable() {
         @Override
         public void run() {

            updateWindowTitleAndStatus();

            if (tags == null) {
               return;
            }

            if (hyperImage_ == null) {
               // this has to run on the EDT
               startup(tags, null);
            }

            int channel = 0, frame = 0, slice = 0, position = 0, superChannel = 0;
            try {
               frame = MDUtils.getFrameIndex(tags);
               slice = MDUtils.getSliceIndex(tags);
               channel = MDUtils.getChannelIndex(tags);
               position = MDUtils.getPositionIndex(tags);
               superChannel = VirtualAcquisitionDisplay.this.rgbToGrayChannel(
                       MDUtils.getChannelIndex(tags));
            } catch (JSONException ex) {
               ReportingUtils.logError(ex);
            }

            // This block allows animation to be reset to where it was before
            // images were added
            if (isTAnimated()) {
               animatedFrameIndex_ = hyperImage_.getFrame();
               setTimepointAnimation(false);
            }
            if (isZAnimated()) {
               animatedSliceIndex_ = hyperImage_.getSlice();
               setStackAnimation(false);
            }

            //make sure pixels get properly set
            if (hyperImage_ != null && frame == 0) {
               IMMImagePlus img = (IMMImagePlus) hyperImage_;
               if (img.getNChannelsUnverified() == 1) {
                  if (img.getNSlicesUnverified() == 1) {
                     hyperImage_.getProcessor().setPixels(virtualStack_.getPixels(1));
                  }
               } else if (hyperImage_ instanceof MMCompositeImage) {
                  //reset rebuilds each of the channel ImageProcessors with the correct pixels
                  //from AcquisitionVirtualStack
                  MMCompositeImage ci = ((MMCompositeImage) hyperImage_);
                  ci.reset();
                  //This line is neccessary for image processor to have correct pixels in grayscale mode
                  ci.getProcessor().setPixels(virtualStack_.getPixels(ci.getCurrentSlice()));
               }
            } else if (hyperImage_ instanceof MMCompositeImage) {
               MMCompositeImage ci = ((MMCompositeImage) hyperImage_);
               ci.reset();
            }



            if (cSelector_ != null) {
               if (cSelector_.getMaximum() <= (1 + superChannel)) {
                  VirtualAcquisitionDisplay.this.setNumChannels(1 + superChannel);
                  ((CompositeImage) hyperImage_).reset();
                  //JavaUtils.invokeRestrictedMethod(hyperImage_, CompositeImage.class,
                  //       "setupLuts", 1 + superChannel, Integer.TYPE);
               }
            }

            if (!isSimpleDisplay_) {
               if (tSelector_ != null) {
                  if (tSelector_.getMaximum() <= (1 + frame)) {
                     VirtualAcquisitionDisplay.this.setNumFrames(1 + frame);
                  }
               }
               if (position + 1 > getNumPositions()) {
                  setNumPositions(position + 1);
               }
               setPosition(position);
               hyperImage_.setPosition(1 + superChannel, 1 + slice, 1 + frame);
            }
            
            if (frame == 0) {
               initializeContrast();
            }

            //dont't force an update with live win
            boolean forceUpdate = ! isSimpleDisplay_;
            if (isSimpleDisplay_ && !MMStudioMainFrame.getInstance().isLiveModeOn()) {
               //unless this is a snap or live mode has stopped
               forceUpdate = true;
            }     
            updateAndDraw(forceUpdate);
            resumeLocksAndAnimationAfterImageArrival();
                      
            //get channelgroup name for use in loading contrast setttings
            if (firstImage_) {
               try {
                  channelGroup_ = tags.getString("Core-ChannelGroup");
               } catch (JSONException ex) {
                  ReportingUtils.logError("Couldn't find Core-ChannelGroup in image metadata");
               }
               firstImage_ = false;
            }
            
            if (cSelector_ != null) {
               if (histograms_.getNumberOfChannels() < (1 + superChannel)) {
                  if (histograms_ != null) {
                     histograms_.setupChannelControls(imageCache_);
                  }
               }
            }

         }
      });
   }

   private void initializeContrast() {
      if (contrastInitialized_ ) {
         return;
      }
      int numChannels = imageCache_.getNumDisplayChannels();
      
      for (int channel = 0; channel < numChannels; channel++) {
         String channelName = imageCache_.getChannelName(channel);
         HistogramSettings settings = MMStudioMainFrame.getInstance().loadStoredChannelHisotgramSettings(
                 channelGroup_, channelName, mda_);
         histograms_.setChannelContrast(channel, settings.min_, settings.max_, settings.gamma_);
         histograms_.setChannelHistogramDisplayMax(channel, settings.histMax_);
         if (imageCache_.getNumDisplayChannels() > 1) {
            setDisplayMode(settings.displayMode_);
         }
      }
      histograms_.applyLUTToImage();
      contrastInitialized_ = true;
   }

   public void storeChannelHistogramSettings(int channelIndex, int min, int max, 
           double gamma, int histMax, int displayMode) {
     if (!contrastInitialized_ ) {
        return; //don't erroneously initialize c   ontrast
     }
      //store for this dataset
      imageCache_.storeChannelDisplaySettings(channelIndex, min, max, gamma, histMax, displayMode);
      //store global preference for channel contrast settings
      if (mda_ || isSimpleDisplay_) {
         //only store for datasets that were just acquired or snap/live (i.e. no loaded datasets)
         MMStudioMainFrame.getInstance().saveChannelHistogramSettings(channelGroup_, 
                 imageCache_.getChannelName(channelIndex), mda_,
                 new HistogramSettings(min,max, gamma, histMax, displayMode));    
      }   
   }

   private void updatePosition(int p) {
      if (isSimpleDisplay_) {
         return;
      }
      virtualStack_.setPositionIndex(p);
      if (!hyperImage_.isComposite()) {
         Object pixels = virtualStack_.getPixels(hyperImage_.getCurrentSlice());
         hyperImage_.getProcessor().setPixels(pixels);
      } else {
         CompositeImage ci = (CompositeImage) hyperImage_;
         if (ci.getMode() == CompositeImage.COMPOSITE) {
            for (int i = 0; i < ((MMCompositeImage) ci).getNChannelsUnverified(); i++) {
               //Dont need to set pixels if processor is null because it will get them from stack automatically  
               if (ci.getProcessor(i + 1) != null)                
                  ci.getProcessor(i + 1).setPixels(virtualStack_.getPixels(ci.getCurrentSlice() - ci.getChannel() + i + 1));
            }
         }
         ci.getProcessor().setPixels(virtualStack_.getPixels(hyperImage_.getCurrentSlice()));
      }
      //need to call this even though updateAndDraw also calls it to get autostretch to work properly
      imageChangedUpdate();
      updateAndDraw(true);
   }

   public void setPosition(int p) {
      if (isSimpleDisplay_) {
         return;
      }
      pSelector_.setValue(p);
   }

   public void setSliceIndex(int i) {
      if (isSimpleDisplay_) {
         return;
      }
      final int f = hyperImage_.getFrame();
      final int c = hyperImage_.getChannel();
      hyperImage_.setPosition(c, i + 1, f);
   }

   public int getSliceIndex() {
      return hyperImage_.getSlice() - 1;
   }

   boolean pause() {
      if (eng_ != null) {
         if (eng_.isPaused()) {
            eng_.setPause(false);
         } else {
            eng_.setPause(true);
         }
         updateWindowTitleAndStatus();
         return (eng_.isPaused());
      }
      return false;
   }

   public boolean abort() {
      if (eng_ != null) {
         if (eng_.abortRequest()) {
            updateWindowTitleAndStatus();
            return true;
         }
      }
      return false;
   }

   public boolean acquisitionIsRunning() {
      if (eng_ != null) {
         return eng_.isAcquisitionRunning();
      } else {
         return false;
      }
   }

   public long getNextWakeTime() {
      return eng_.getNextWakeTime();
   }

   public boolean abortRequested() {
      if (eng_ != null) {
         return eng_.abortRequested();
      } else {
         return false;
      }
   }

   private boolean isPaused() {
      if (eng_ != null) {
         return eng_.isPaused();
      } else {
         return false;
      }
   }

   public void albumChanged() {
      albumSaved_ = false;
   }
   
   private Class createSaveTypePopup() {
      if (saveTypePopup_ != null) {
         saveTypePopup_.setVisible(false);
         saveTypePopup_ = null;
      }
      final JPopupMenu menu = new JPopupMenu();
      saveTypePopup_ = menu;
      JMenuItem single = new JMenuItem("Save as separate image files");
      JMenuItem multi = new JMenuItem("Save as image stack file");
      JMenuItem cancel = new JMenuItem("Cancel");
      menu.add(single);
      menu.add(multi);
      menu.addSeparator();
      menu.add(cancel);
      final AtomicInteger ai = new AtomicInteger(-1);
      cancel.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            ai.set(0);
         }
      });
      single.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            ai.set(1);
         }
      });
      multi.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            ai.set(2);
         }
      });
      MouseInputAdapter highlighter = new MouseInputAdapter() {
         @Override
         public void mouseEntered(MouseEvent e) {
            ((JMenuItem) e.getComponent()).setArmed(true);
         }
         @Override
         public void mouseExited(MouseEvent e) {
            ((JMenuItem) e.getComponent()).setArmed(false);
         }       
      };
      single.addMouseListener(highlighter);
      multi.addMouseListener(highlighter);
      cancel.addMouseListener(highlighter);  
      Point mouseLocation = MouseInfo.getPointerInfo().getLocation();
      menu.show(null, mouseLocation.x, mouseLocation.y);
      while (ai.get() == -1) {
         try {
            Thread.sleep(10);
         } catch (InterruptedException ex) {}
         if (!menu.isVisible()) {
            return null;
         }
      }
      menu.setVisible(false);
      saveTypePopup_ = null;
      if (ai.get() == 0) {
         return null;
      } else if (ai.get() == 1) {
         return TaggedImageStorageDiskDefault.class;
      } else {
         return TaggedImageStorageMultipageTiff.class;
      }  
   }

   boolean saveAs() {
      return saveAs(null,true);
   }

   boolean saveAs(boolean pointToNewStorage) {
      return saveAs(null, pointToNewStorage);
   }

   private boolean saveAs(Class<?> storageClass, boolean pointToNewStorage) {
      if (eng_ != null && eng_.isAcquisitionRunning()) {
         JOptionPane.showMessageDialog(null, 
                 "Data can not be saved while acquisition is running.");
         return false;
      }
      if (storageClass == null) {
         storageClass = createSaveTypePopup();
      }
      if (storageClass == null) {
         return false;
      }
      String prefix;
      String root;
      for (;;) {
         File f = FileDialogs.save(hyperImage_.getWindow(),
                 "Please choose a location for the data set",
                 MMStudioMainFrame.MM_DATA_SET);
         if (f == null) // Canceled.
         {
            return false;
         }
         prefix = f.getName();
         root = new File(f.getParent()).getAbsolutePath();
         if (f.exists()) {
            ReportingUtils.showMessage(prefix
                    + " is write only! Please choose another name.");
         } else {
            break;
         }
      }

      try {
         if (getSummaryMetadata() != null) {
            getSummaryMetadata().put("Prefix", prefix);
         }
         TaggedImageStorage newFileManager =
                 (TaggedImageStorage) storageClass.getConstructor(
                 String.class, Boolean.class, JSONObject.class).newInstance(
                 root + "/" + prefix, true, getSummaryMetadata());
         if (pointToNewStorage) {
            albumSaved_ = true;
         }

         imageCache_.saveAs(newFileManager, pointToNewStorage);
      } catch (IllegalAccessException ex) {
         ReportingUtils.showError(ex, "Failed to save file");
      } catch (IllegalArgumentException ex) {
         ReportingUtils.showError(ex, "Failed to save file");
      } catch (InstantiationException ex) {
         ReportingUtils.showError(ex, "Failed to save file");
      } catch (NoSuchMethodException ex) {
         ReportingUtils.showError(ex, "Failed to save file");
      } catch (SecurityException ex) {
         ReportingUtils.showError(ex, "Failed to save file");
      } catch (InvocationTargetException ex) {
         ReportingUtils.showError(ex, "Failed to save file");
      } catch (JSONException ex) {
         ReportingUtils.showError(ex, "Failed to save file");
      }
      MMStudioMainFrame.getInstance().setAcqDirectory(root);
      updateWindowTitleAndStatus();
      return true;
   }

   final public MMImagePlus createMMImagePlus(AcquisitionVirtualStack virtualStack) {
      MMImagePlus img = new MMImagePlus(imageCache_.getDiskLocation(), 
            virtualStack, virtualStack.getVirtualAcquisitionDisplay().getEventBus());
      FileInfo fi = new FileInfo();
      fi.width = virtualStack.getWidth();
      fi.height = virtualStack.getHeight();
      fi.fileName = virtualStack.getDirectory();
      fi.url = null;
      img.setFileInfo(fi);
      return img;
   }

   final public ImagePlus createHyperImage(MMImagePlus mmIP, int channels, int slices,
           int frames, final AcquisitionVirtualStack virtualStack) {
      final ImagePlus hyperImage;
      mmIP.setNChannelsUnverified(channels);
      mmIP.setNFramesUnverified(frames);
      mmIP.setNSlicesUnverified(slices);
      if (channels > 1) {        
         hyperImage = new MMCompositeImage(mmIP, imageCache_.getDisplayMode(), 
               name_, bus_);
         hyperImage.setOpenAsHyperStack(true);
      } else {
         hyperImage = mmIP;
         mmIP.setOpenAsHyperStack(true);
      }
      return hyperImage;
   }

   public void liveModeEnabled(boolean enabled) {
      if (isSimpleDisplay_) {
         controls_.acquiringImagesUpdate(enabled);
      }
   }

   private void createWindow() {
      makeHistograms();
      final DisplayWindow win = new DisplayWindow(hyperImage_, bus_);
      win.getCanvas().addMouseListener(new MouseInputAdapter() {
         //used to store preferred zoom
         @Override
         public void mousePressed(MouseEvent me) {
            if (Toolbar.getToolId() == 11) {//zoom tool selected
               storeWindowSizeAfterZoom(win);
            }
            updateWindowTitleAndStatus();
         }

         //updates the histogram after an ROI is drawn
         @Override
         public void mouseReleased(MouseEvent me) {
            if (hyperImage_ instanceof MMCompositeImage) {
            ((MMCompositeImage) hyperImage_).updateAndDraw(true);
            } else {
               hyperImage_.updateAndDraw();
            }
         }
      });

      win.setBackground(MMStudioMainFrame.getInstance().getBackgroundColor());
      MMStudioMainFrame.getInstance().addMMBackgroundListener(win);

      win.add(controls_);
      win.pack();

       if (isSimpleDisplay_) {
           win.setLocation(prefs_.getInt(SIMPLE_WIN_X, 0), prefs_.getInt(SIMPLE_WIN_Y, 0));
       }

      //Set magnification
      zoomToPreferredSize(win);
      
   
      mdPanel_.displayChanged(win);
      imageChangedUpdate();
   }
   
   public void storeWindowSizeAfterZoom(ImageWindow win) {
      if (isSimpleDisplay_) {
         snapWinMag_ = win.getCanvas().getMagnification();
      }
   }
   
   private void zoomToPreferredSize(DisplayWindow win) {
      Point location = win.getLocation();
      win.setLocation(new Point(0,0));
      
      double mag;
      if (isSimpleDisplay_ && snapWinMag_ != -1) {
         mag = snapWinMag_;
      } else {
         mag = MMStudioMainFrame.getInstance().getPreferredWindowMag();
      }

      ImageCanvas canvas = win.getCanvas();
      if (mag < canvas.getMagnification()) {
         while (mag < canvas.getMagnification()) {
            canvas.zoomOut(canvas.getWidth() / 2, canvas.getHeight() / 2);
         }
      } else if (mag > canvas.getMagnification()) {

         while (mag > canvas.getMagnification()) {
            canvas.zoomIn(canvas.getWidth() / 2, canvas.getHeight() / 2);
         }
      }

      //Make sure the window is fully on the screen
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Point newLocation = new Point(location.x,location.y);
      if (newLocation.x + win.getWidth() > screenSize.width && win.getWidth() < screenSize.width) {
          newLocation.x = screenSize.width - win.getWidth();
      }
      if (newLocation.y + win.getHeight() > screenSize.height && win.getHeight() < screenSize.height) {
          newLocation.y = screenSize.height - win.getHeight();
      }
      
      win.setLocation(newLocation);
   }

   // A window wants to close; check if it's okay. If it is, then we call its
   // forceClosed() function.
   // TODO: for now, assuming we only have one window.
   @Subscribe
   public void onWindowClose(DisplayWindow.WindowClosingEvent event) {
      if (eng_ != null && eng_.isAcquisitionRunning()) {
         if (!abort()) {
            // Can't close now; the acquisition is still running.
            return;
         }
      }
      // Ask if the user wants to save data.

      if (imageCache_.getDiskLocation() == null && 
            promptToSave_ && !albumSaved_) {
         String[] options = {"Save single", "Save multi", "No", "Cancel"};
         int result = JOptionPane.showOptionDialog(
               event.window_, "This data set has not yet been saved. " + 
               "Do you want to save it?\n" + 
               "Data can be saved as single-image files or multi-image files.",
               "Micro-Manager", 
               JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null,
               options, options[0]);

         if (result == 0) {
            if (!saveAs(TaggedImageStorageDiskDefault.class, true)) {
               return;
            }
         } else if (result == 1) {
            if (!saveAs(TaggedImageStorageMultipageTiff.class, true)) {
               return;
            }
         } else if (result == 3) {
            return;
         }
      }

      // Record window position information.
      if (isSimpleDisplay_ && hyperImage_ != null && hyperImage_.getWindow() != null && 
            hyperImage_.getWindow().getLocation() != null) {
         Point loc = hyperImage_.getWindow().getLocation();
         prefs_.putInt(SIMPLE_WIN_X, loc.x);
         prefs_.putInt(SIMPLE_WIN_Y, loc.y);
      }

      if (imageCache_ != null) {
         imageCache_.close();
      }

      removeFromAcquisitionManager(MMStudioMainFrame.getInstance());

      //Call this because for some reason WindowManager doesnt always fire
      mdPanel_.displayChanged(null);
      animationTimer_.cancel();
      animationTimer_.cancel();

      // Finally, tell the window to close now.
      DisplayWindow window = event.window_;
      window.forceClosed();
   }

   // Toggle one of our animation bits. Publish an event so our DisplayWindows
   // (and anyone else who cares) can be notified.
   private void setTAnimated(boolean isTAnimated) {
      tAnimated_ = isTAnimated;
      bus_.post(new AnimationSetEvent(isAnimated()));
   }

   // Toggle one of our animation bits. Publish an event so our DisplayWindows
   // (and anyone else who cares) can be notified.
   private void setZAnimated(boolean isZAnimated) {
      zAnimated_ = isZAnimated;
      bus_.post(new AnimationSetEvent(isAnimated()));
   }
   
   // Animation needs to be turned on/off.
   @Subscribe
   public void setAnimated(DisplayWindow.ToggleAnimatedEvent event) {
      if (((IMMImagePlus) hyperImage_).getNFramesUnverified() > 1) {
         setTimepointAnimation(event.shouldSetAnimated_);
      } else {
         setStackAnimation(event.shouldSetAnimated_);
      }
   }

   /*
    * Removes the VirtualAcquisitionDisplay from the Acquisition Manager.
    */
   private void removeFromAcquisitionManager(ScriptInterface gui) {
      try {
         if (gui.acquisitionExists(name_)) {
            gui.closeAcquisition(name_);
         }
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
      }
   }

   //Return metadata associated with image currently shown in the viewer
   public JSONObject getCurrentMetadata() {
      if (hyperImage_ != null) {
         JSONObject md = virtualStack_.getImageTags(hyperImage_.getCurrentSlice());
         return md;
      } else {
         return null;
      }
   }

   public int getCurrentPosition() {
      return virtualStack_.getPositionIndex();
   }

   public int getNumSlices() {
      if (isSimpleDisplay_) {
         return 1;
      }
      return hyperImage_ == null ? 1 : ((IMMImagePlus) hyperImage_).getNSlicesUnverified();
   }

   public int getNumFrames() {
      if (isSimpleDisplay_) {
         return 1;
      }
      return ((IMMImagePlus) hyperImage_).getNFramesUnverified();
   }

   public int getNumPositions() {
      if (isSimpleDisplay_) {
         return 1;
      }
      return pSelector_.getMaximum();
   }

   public ImagePlus getImagePlus() {
      return hyperImage_;
   }

   public ImageCache getImageCache() {
      return imageCache_;
   }

   public ImagePlus getImagePlus(int position) {
      ImagePlus iP = new ImagePlus();
      iP.setStack(virtualStack_);
      iP.setDimensions(numComponents_ * getNumChannels(), getNumSlices(), getNumFrames());
      iP.setFileInfo(hyperImage_.getFileInfo());
      return iP;
   }

   public void setComment(String comment) throws MMScriptException {
      try {
         getSummaryMetadata().put("Comment", comment);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
   }

   public final JSONObject getSummaryMetadata() {
      return imageCache_.getSummaryMetadata();
   }
   
   /*
   public final JSONObject getImageMetadata(int channel, int slice, int frame, int position) {
      return imageCache_.getImageTags(channel, slice, frame, position);
   }
    */

   /**
    * Closes the ImageWindow and associated ImagePlus
    * 
    * @return false if canceled by user, true otherwise 
    */
   public boolean close() {
      try {
         if (hyperImage_ != null) {
            if (!hyperImage_.getWindow().close()) {
               return false;
            }
            hyperImage_.close();
         }
      } catch (NullPointerException npe) {
         // instead of handing when exiting MM, log the issue
         ReportingUtils.logError(npe);
      }
      return true;
   }

   public synchronized boolean windowClosed() {
      if (hyperImage_ != null) {
         ImageWindow win = hyperImage_.getWindow();
         return (win == null || win.isClosed());
      }
      return true;
   }

   public void showFolder() {
      if (isDiskCached()) {
         try {
            File location = new File(imageCache_.getDiskLocation());
            if (JavaUtils.isWindows()) {
               Runtime.getRuntime().exec("Explorer /n,/select," + location.getAbsolutePath());
            } else if (JavaUtils.isMac()) {
               if (!location.isDirectory()) {
                  location = location.getParentFile();
               }
               Runtime.getRuntime().exec("open " + location.getAbsolutePath());
            }
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
   }

   public void setPlaybackFPS(double fps) {
      framesPerSec_ = fps;
      if (zAnimated_ || tAnimated_) {
         setTimepointAnimation(false);
         setTimepointAnimation(true);
      }
   }

   public double getPlaybackFPS() {
      return framesPerSec_;
   }

   public boolean isZAnimated() {
      return zAnimated_;
   }

   public boolean isTAnimated() {
      return tAnimated_;
   }

   public boolean isAnimated() {
      return isTAnimated() || isZAnimated();
   }

   public String getSummaryComment() {
      return imageCache_.getComment();
   }

   public void setSummaryComment(String comment) {
      imageCache_.setComment(comment);
   }

   void setImageComment(String comment) {
      imageCache_.setImageComment(comment, getCurrentMetadata());
   }

   String getImageComment() {
      try {
         return imageCache_.getImageComment(getCurrentMetadata());
      } catch (NullPointerException ex) {
         return "";
      }
   }

   public boolean isDiskCached() {
      ImageCache imageCache = imageCache_;
      if (imageCache == null) {
         return false;
      } else {
         return imageCache.getDiskLocation() != null;
      }
   }

   //This method exists in addition to the other show method
   // so that plugins can utilize virtual acqusition display with a custom virtual stack
   //allowing manipulation of displayed images without changing underlying data
   //should probably be reconfigured to work through some sort of interface in the future
   public void show(final AcquisitionVirtualStack virtualStack) {
      if (hyperImage_ == null) {
         try {
            GUIUtils.invokeAndWait(new Runnable() {

               @Override
               public void run() {
                  startup(null, virtualStack);
               }
            });
         } catch (InterruptedException ex) {
            ReportingUtils.logError(ex);
         } catch (InvocationTargetException ex) {
            ReportingUtils.logError(ex);
         }

      }
      hyperImage_.show();
      hyperImage_.getWindow().toFront();
   }
   
   public void show() {
      show(null);
   }

   public int getNumChannels() {
      return hyperImage_ == null ? 1 : ((IMMImagePlus) hyperImage_).getNChannelsUnverified();
   }

   public int getNumGrayChannels() {
      return getNumChannels();
   }

   public void setWindowTitle(String name) {
      name_ = name;
      updateWindowTitleAndStatus();
   }

   public boolean isSimpleDisplay() {
      return isSimpleDisplay_;
   }

   public void displayStatusLine(String status) {
      controls_.setStatusLabel(status);
   }

   public void setChannelContrast(int channelIndex, int min, int max, double gamma) {
      histograms_.setChannelContrast(channelIndex, min, max, gamma);
      histograms_.applyLUTToImage();
      drawWithoutUpdate();
   }
   
   public void updateChannelNamesAndColors() {
      if (histograms_ != null && histograms_ instanceof MultiChannelHistograms) {
         ((MultiChannelHistograms) histograms_).updateChannelNamesAndColors();
      }
   }
   
   public void setChannelHistogramDisplayMax(int channelIndex, int histMax) {
      histograms_.setChannelHistogramDisplayMax(channelIndex, histMax);
   }

   /*
    * Called just before image is drawn.  Notifies metadata panel to update
    * metadata or comments if this display is the active window.  Notifies histograms
    * that image is change to create appropriate LUTs and to draw themselves if this
    * is the active window
    */
   private void imageChangedUpdate() {
      boolean updatePixelSize = updatePixelSize_.get();

      if (updatePixelSize) {
         try {
            JSONObject summary = getSummaryMetadata();
            if (summary != null) {
               summary.put("PixelSize_um", Double.longBitsToDouble(newPixelSize_.get()));
            }
            if (hyperImage_ != null) {
               applyPixelSizeCalibration(hyperImage_);
            }
            
         } catch (JSONException ex) {
            ReportingUtils.logError("Error in imageChangedUpdate in VirtualAcquisitionDisplay.java");
         } 
         updatePixelSize_.set(false);
      } else {
         if (hyperImage_ != null) {
            Calibration cal = hyperImage_.getCalibration();
            double calPixSize = cal.pixelWidth;
            JSONObject tags = this.getCurrentMetadata();
            if (tags != null) {
               try {
                  double imgPixSize = tags.getDouble("PixelSizeUm");
                  if (calPixSize != imgPixSize) {
                     applyPixelSizeCalibration(hyperImage_);
                  }
               } catch (JSONException ex) {
                  ReportingUtils.logError("Found Image without PixelSizeUm tag");
               }
            }
         }
      }
      if (histograms_ != null) {
         histograms_.imageChanged();
      }      
      if (isActiveDisplay()) {         
         mdPanel_.imageChangedUpdate(this);
         if (updatePixelSize) {
            mdPanel_.redrawSizeBar();
         }
      }      
      imageChangedWindowUpdate(); //used to update status line
   }
   
   public boolean isActiveDisplay() {
      if (hyperImage_ == null || hyperImage_.getWindow() == null)
           return false;
       return hyperImage_.getWindow() == mdPanel_.getCurrentWindow();
   }

   /*
    * Called when contrast changes as a result of user or programmtic input, but underlying pixels 
    * remain unchanges
    */
   public void drawWithoutUpdate() {
      if (hyperImage_ != null) {
         ((IMMImagePlus) hyperImage_).drawWithoutUpdate();
      }
   }
   
   private void makeHistograms() {
      if (getNumChannels() == 1 )
           histograms_ = new SingleChannelHistogram(this);
       else
           histograms_ = new MultiChannelHistograms(this);
   }
   
   public Histograms getHistograms() {
       return histograms_;
   }
   
   public HistogramControlsState getHistogramControlsState() {
       return histogramControlsState_;
   }
   
   public void disableAutoStretchCheckBox() {
       if (isActiveDisplay() ) {
          mdPanel_.getContrastPanel().disableAutostretch();
       } else {
          histogramControlsState_.autostretch = false;
       }
   }
   
   public ContrastSettings getChannelContrastSettings(int channel) {
      return histograms_.getChannelContrastSettings(channel);
   }           
}
