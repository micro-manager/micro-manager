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
package org.micromanager.imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageWindow;
import ij.io.FileInfo;
import ij.measure.Calibration;

import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;

import mmcorej.TaggedImage;

import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.api.events.PixelSizeChangedEvent;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ImageCacheListener;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.events.DisplayCreatedEvent;
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

   /**
    * Given an ImagePlus, retrieve the associated VirtualAcquisitionDisplay.
    * This only works if the ImagePlus is actually an AcquisitionVirtualStack;
    * otherwise you just get null.
    * @param imgp - Imageplus that we want the VAD for
    * @return - VirtualAcquisitionDisplay associated with this ImagePLus
    */
   public static VirtualAcquisitionDisplay getDisplay(ImagePlus imgp) {
      ImageStack stack = imgp.getStack();
      if (stack instanceof AcquisitionVirtualStack) {
         return ((AcquisitionVirtualStack) stack).getVirtualAcquisitionDisplay();
      } else {
         return null;
      }
   }

   private static final int ANIMATION_AND_LOCK_RESTART_DELAY = 800;
   final ImageCache imageCache_;
   private AcquisitionEngine eng_;
   private boolean isAcquisitionFinished_ = false;
   private boolean promptToSave_ = true;
   private boolean amClosing_ = false;
   // Name of the acquisition we present.
   private final String name_;
   // First component of text displayed in our title bar.
   private String title_;
   private int numComponents_;
   private int pixelType_ = 0;
   // This queue holds images waiting to be displayed.
   private LinkedBlockingQueue<JSONObject> imageTagsQueue_;
   // This thread consumes images from the above queue.
   private Thread displayThread_;
   // This boolean is used to tell the display thread to stop what it's doing.
   private final AtomicBoolean shouldStopDisplayThread_ = new AtomicBoolean(false);
   // We need to track how many images we've received and how many images we've
   // displayed, for FPS display purposes.
   private long lastImageIndex_ = 0;
   private int imagesDisplayed_ = 0;
   // Tracks when we last sent an FPS update.
   private long lastFPSUpdateTimestamp_ = -1;
   private ImagePlus hyperImage_;
   private DisplayControls controls_;
   private boolean shouldUseSimpleControls_ = false;
   public AcquisitionVirtualStack virtualStack_;
   private boolean isMDA_ = false; //flag if display corresponds to MD acquisition
   private MetadataPanel mdPanel_;
   private boolean contrastInitialized_ = false; //used for autostretching on window opening
   private boolean firstImage_ = true;
   private String channelGroup_ = "none";
   private Histograms histograms_;
   private HistogramControlsState histogramControlsState_;
   private boolean albumSaved_ = false;
   private JPopupMenu saveTypePopup_;
   private final AtomicBoolean updatePixelSize_ = new AtomicBoolean(false);
   private final AtomicLong newPixelSize_ = new AtomicLong();
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
    * Standard constructor.
    * @param imageCache
    * @param eng
    * @param name
    * @param shouldUseNameAsTitle
    */
   public VirtualAcquisitionDisplay(ImageCache imageCache,
         AcquisitionEngine eng, String name, boolean shouldUseNameAsTitle) {
      name_ = name;
      title_ = name;
      if (!shouldUseNameAsTitle) {
         title_ = WindowManager.getUniqueName("Untitled");
      }
      imageCache_ = imageCache;
      eng_ = eng;
      isMDA_ = eng != null;
      this.albumSaved_ = imageCache.isFinished();
      setupEventBus();
      setupDisplayThread();
   }

   /**
    * Create a new EventBus that will be used for all events related to this
    * display system.
    */
   private void setupEventBus() {
      bus_ = new EventBus();
      bus_.register(this);
   }

   /**
    * Start the thread that will be used to update our display. This thread
    * extracts the newest image from imageTagsQueue_, displays it, and waits for
    * display to stop, then repeats (all other images in the queue are 
    * discarded). 
    */
   private void setupDisplayThread() {
      imageTagsQueue_ = new LinkedBlockingQueue<JSONObject>();
      displayThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            JSONObject tags = null;
            while (!shouldStopDisplayThread_.get()) {
               boolean haveValidImage = false;
               // Extract images from the queue until we get to the end.
               do {
                  try {
                     // This will block until an image is available or we need
                     // to send a new FPS update.
                     tags = imageTagsQueue_.poll(500, TimeUnit.MILLISECONDS);
                     haveValidImage = (tags != null);
                     if (tags == null) {
                        try {
                           // We still need to generate an FPS update at 
                           // regular intervals; we just have to do it without
                           // any image tags.
                           sendFPSUpdate(null);
                        }
                        catch (Exception e) {
                           // Can't get image tags, apparently; give up.
                           break;
                        }
                        continue;
                     }
                  }
                  catch (InterruptedException e) {
                     // Interrupted while waiting for the queue to be 
                     // populated. 
                     if (shouldStopDisplayThread_.get()) {
                        // Time to stop.
                        return;
                     }
                  }
               } while (imageTagsQueue_.peek() != null);

               if (tags == null || !haveValidImage) {
                  // Nothing to show. 
                  continue;
               }
      
               if (hyperImage_ != null && hyperImage_.getCanvas() != null) {
                  // Wait for the canvas to be available. If we don't do this,
                  // then our framerate tanks, possibly because of repaint
                  // events piling up in the EDT. It's hard to tell. 
                  while (CanvasPaintPending.isMyPaintPending(
                        hyperImage_.getCanvas(), imageReceivedObject_)) {
                     try {
                        Thread.sleep(10);
                     }
                     catch (InterruptedException e) {
                        if (shouldStopDisplayThread_.get()) {
                           // Time to stop.
                           return;
                        }
                     }
                  }
                  CanvasPaintPending.setPaintPending(
                        hyperImage_.getCanvas(), imageReceivedObject_);
               }
               showImage(tags, true);
               imagesDisplayed_++;
               sendFPSUpdate(tags);
            } // End while loop
         }
      }, "VirtualAcquisitionDisplay display thread");
      displayThread_.start();
   }

   /**
    * Send an update on our FPS, both data rate and image display rate. Only
    * if it has been at least 500ms since our last update.
    */
   private void sendFPSUpdate(JSONObject tags) {
      long curTimestamp = System.currentTimeMillis();
      // Hack: if we have null tags, then post a "blank" FPS event.
      if (tags == null) {
         bus_.post(new FPSEvent(0, 0));
         return;
      }
      if (lastFPSUpdateTimestamp_ == -1) {
         // No data to operate on yet.
         lastFPSUpdateTimestamp_ = curTimestamp;
      }
      else if (curTimestamp - lastFPSUpdateTimestamp_ >= 500) {
         // More than 500ms since last update.
         double elapsedTime = (curTimestamp - lastFPSUpdateTimestamp_) / 1000.0;
         try {
            long imageIndex = MDUtils.getSequenceNumber(tags);
            // HACK: Ignore the first FPS display event, to prevent us from
            // showing FPS for the Snap window.
            if (lastImageIndex_ != 0) {
               bus_.post(new FPSEvent((imageIndex - lastImageIndex_) / elapsedTime, 
                        imagesDisplayed_ / elapsedTime));
            }
            lastImageIndex_ = imageIndex;
         }
         catch (Exception e) {
            // Post a "blank" event. This likely happens because the image
            // tags don't contain a sequence number (e.g. during an MDA).
            bus_.post(new FPSEvent(0, 0));
         }
         imagesDisplayed_ = 0;
         lastFPSUpdateTimestamp_ = curTimestamp;
      }
   }

   // Retrieve our EventBus.
   public EventBus getEventBus() {
      return bus_;
   }

   // Prepare for a drawing event.
   @Subscribe
   public void onDraw(DrawEvent event) {
      if (!amClosing_) {
         imageChangedUpdate();
      }
   }

   /**
    * This constructor is used for the Snap and Live views. The main
    * differences:
    * - eng_ is null
    * - We subscribe to the "pixel size changed" event. 
    * - Later, when we create controls_, it will be told to use the 
    *   Snap/Live buttons (because shouldUseSimpleControls_ is true).
    */
   @SuppressWarnings("LeakingThisInConstructor")
   public VirtualAcquisitionDisplay(ImageCache imageCache, String name) throws MMScriptException {
      imageCache_ = imageCache;
      name_ = name;
      title_ = name;
      isMDA_ = false;
      shouldUseSimpleControls_ = true;
      this.albumSaved_ = imageCache.isFinished();
      setupEventBus();
      setupDisplayThread();
      // Also register us for pixel size change events on the global EventBus.
      EventManager.register(this);
   }
  
   /**
    * Extract a lot of fields from the provided metadata (or, failing that, 
    * from getSummaryMetadata()), and set up our controls and view window.
    */
   private void startup(JSONObject firstImageMetadata, AcquisitionVirtualStack virtualStack) {
      mdPanel_ = MMStudio.getInstance().getMetadataPanel();
      JSONObject summaryMetadata = getSummaryMetadata();
      int numSlices = 1;
      int numFrames = 1;
      int numChannels = 1;
      int numGrayChannels;
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
         numFrames = imageCache_.lastAcquiredFrame();
         if (numFrames <= 0) {
            numFrames = Math.max(summaryMetadata.getInt("Frames"), 1);
         }

         numChannels = Math.max(1 + imageChannelIndex,
                 Math.max(summaryMetadata.getInt("Channels"), 1));
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
      pixelType_ = type;
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

      hyperImage_ = createHyperImage(createMMImagePlus(virtualStack_),
              numGrayChannels, numSlices, numFrames, virtualStack_);

      // Hack: allow controls_ to be already set, so that overriding classes
      // can implement their own custom controls.
      if (controls_ == null) {
         controls_ = new HyperstackControls(this, bus_, 
               shouldUseSimpleControls_, isMDA_);
      }

      applyPixelSizeCalibration(hyperImage_);

      histogramControlsState_ =  mdPanel_.getContrastPanel().createDefaultControlsState();
      createWindow();
      windowToFront();

      updateAndDraw(true);
      updateWindowTitleAndStatus();
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
   public void imageReceived(final TaggedImage taggedImage) {
      updateDisplay(taggedImage);
   }

   /**
    * Method required by ImageCacheListener
    * @param path
    */
   @Override
   public void imagingFinished(String path) {
      if (amClosing_) {
         // Don't care, we'll be closing soon anyway.
         return;
      }
      updateDisplay(null);
      if (!(eng_ != null && eng_.abortRequested())) {
         updateWindowTitleAndStatus();
      }
   }

   /**
    * A new image has arrived; toss it onto our queue for display.
    */
   public void updateDisplay(TaggedImage taggedImage) {
      JSONObject tags;
      if (taggedImage == null || taggedImage.tags == null) {
         tags = imageCache_.getLastImageTags();
      }
      else {
         tags = taggedImage.tags;
      }
      if (tags == null) {
         // No valid tags, ergo no valid image, ergo give up.
         return;
      }
      try {
         imageTagsQueue_.add(tags);
      }
      catch (IllegalStateException e) {
         // The queue was full. This should never happen as the queue has
         // MAXINT size. 
         ReportingUtils.logError("Ran out of space in the imageQueue! Inconceivable!");
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
            pixSizeUm = MDUtils.getPixelSizeUm(summary);
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

   @Subscribe
   public void onUpdateTitleEvent(UpdateTitleEvent event) {
      updateWindowTitleAndStatus();
   }

   public void updateWindowTitleAndStatus() {
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
            isAcquisitionFinished_ = true;
         }
      } else {
         if (isAcquisitionFinished_ == true) {
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
              ? new File(imageCache_.getDiskLocation()).getName() : title_;

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
    * This is a wrapper around doShowImage() that sets and unsets 
    * isDoShowImageRunning_ to indicate when the display is complete.
    */
   private void showImage(final JSONObject tags, final boolean waitForDisplay) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            doShowImage(tags, waitForDisplay);
         }
      });
   }
   
   private void doShowImage(final JSONObject tags, boolean waitForDisplay) {
      updateWindowTitleAndStatus();

      if (tags == null) {
         return;
      }

      if (hyperImage_ == null) {
         startup(tags, null);
      }

      int channel = 0, frame = 0, slice = 0, position = 0;
      try {
         frame = MDUtils.getFrameIndex(tags);
         slice = MDUtils.getSliceIndex(tags);
         channel = MDUtils.getChannelIndex(tags);
         position = MDUtils.getPositionIndex(tags);
         // Construct a mapping of axis to position so we can post an 
         // event informing others of the new image.
         HashMap<String, Integer> axisToPosition = new HashMap<String, Integer>();
         axisToPosition.put("channel", channel);
         axisToPosition.put("position", position);
         axisToPosition.put("time", frame);
         axisToPosition.put("z", slice);
         bus_.post(new NewImageEvent(axisToPosition));
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }

      //make sure pixels get properly set
      if (hyperImage_ != null && hyperImage_.getProcessor() != null && 
            frame == 0) {
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

      if (hyperImage_ != null) {
         IMMImagePlus immi = (IMMImagePlus) hyperImage_;
         // Ensure proper dimensions are set on the image.
         if (immi.getNFramesUnverified() <= frame + 1) {
            immi.setNFramesUnverified(frame + 1);
         }  
         if (immi.getNSlicesUnverified() <= slice + 1) {
            immi.setNSlicesUnverified(slice + 1);
         }  
         if (immi.getNChannelsUnverified() <= channel + 1) {
            immi.setNChannelsUnverified(channel + 1);
         }
      }

      //get channelgroup name for use in loading contrast setttings
      if (firstImage_) {
         try {
            channelGroup_ = MDUtils.getChannelGroup(tags);
         } catch (JSONException ex) {
            ReportingUtils.logError("Couldn't find Core-ChannelGroup in image metadata");
         }
         firstImage_ = false;
      }

      if (frame == 0) {
         initializeContrast();
      }

      updateAndDraw(true);
   }

   private void initializeContrast() {
      if (contrastInitialized_ ) {
         return;
      }
      int numChannels = imageCache_.getNumDisplayChannels();
      
      for (int channel = 0; channel < numChannels; channel++) {
         String channelName = imageCache_.getChannelName(channel);
         HistogramSettings settings = MMStudio.getInstance().loadStoredChannelHistogramSettings(
                 channelGroup_, channelName, isMDA_);
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
         return; //don't erroneously initialize contrast
      }
      // store for this dataset
      // TODO: why is this necessary? We get NullPointerExceptions if we don't
      // do this check. 
      if (imageCache_.getDisplayAndComments() != null) {
         imageCache_.storeChannelDisplaySettings(channelIndex, min, max, gamma, histMax, displayMode);
         //store global preference for channel contrast settings
         if (isMDA_) {
            //only store for datasets that were just acquired or snap/live (i.e. no loaded datasets)
            MMStudio.getInstance().saveChannelHistogramSettings(channelGroup_, 
                    imageCache_.getChannelName(channelIndex), isMDA_,
                    new HistogramSettings(min,max, gamma, histMax, displayMode));    
         }
      }
   }

   public void updatePosition(int p) {
      if (virtualStack_.getPositionIndex() == p) {
         // Already on this position; skip doing anything. This minor 
         // optimization makes a display bug (some kind of corruption of the
         // CompositeImage) sufficiently rare as to be nigh-impossible to 
         // reproduce.
         // TODO: That doesn't mean the bug is *gone*. Removing this return
         // statement and running a multi-channel, multi-timepoint, multi-Z
         // acquisition while playing with the scrollbars and locks should 
         // eventually reproduce the bug (wherein the display will stop 
         // updating).
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

   // TODO: remove this function and all others that adjust the image index 
   // via the VirtualAcquisitionDisplay. It should not know or care about this
   // kind of thing.
   public void setPosition(int p) {
      controls_.setPosition(p);
   }

   public int getPosition() {
      return controls_.getPosition();
   }

   public void setSliceIndex(int i) {
      final int f = hyperImage_.getFrame();
      final int c = hyperImage_.getChannel();
      hyperImage_.setPosition(c, i + 1, f);
   }

   public int getSliceIndex() {
      return hyperImage_.getSlice() - 1;
   }

   public void setChannel(int c) {
      controls_.setChannel(c);
   }

   public boolean pause() {
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
      if (pixelType_ != 2) {
         menu.add(multi);
      }
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
                 MMStudio.MM_DATA_SET);
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
      MMStudio.getInstance().setAcqDirectory(root);
      updateWindowTitleAndStatus();
      return true;
   }

   final public MMImagePlus createMMImagePlus(AcquisitionVirtualStack virtualStack) {
      MMImagePlus img = new MMImagePlus(imageCache_.getDiskLocation(), 
            virtualStack, virtualStack.getVirtualAcquisitionDisplay().getEventBus());
      FileInfo fi = new FileInfo();
      fi.width = virtualStack.getWidth();
      fi.height = virtualStack.getHeight();
      String diskLocation = imageCache_.getDiskLocation();
      if (diskLocation != null) {
         File tmp = new File(diskLocation);
         fi.fileName = tmp.getName();
         fi.directory = tmp.getParent();
      }
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
               title_, bus_);
         hyperImage.setOpenAsHyperStack(true);
      } else {
         hyperImage = mmIP;
         mmIP.setOpenAsHyperStack(true);
      }
      return hyperImage;
   }

   private void createWindow() {
      makeHistograms();
      DisplayWindow win = new DisplayWindow(hyperImage_, controls_, bus_);

      mdPanel_.displayChanged(win);
      imageChangedUpdate();
      EventManager.post(new DisplayCreatedEvent(this, win));
   }

   // A window wants to close; check if it's okay. If it is, then we call its
   // forceClosed() function.
   // TODO: for now, assuming we only have one window.
   @Subscribe
   public void onWindowClose(DisplayWindow.RequestToCloseEvent event) {
      if (eng_ != null && eng_.isAcquisitionRunning()) {
         if (!abort()) {
            // Can't close now; the acquisition is still running.
            return;
         }
      }
      // Ask if the user wants to save data.
      if (imageCache_.getDiskLocation() == null && 
            promptToSave_ && !albumSaved_) {
         String[] options = { "Save Separate Files", "Save Stack File", "Discard", "Cancel" };
         int result = JOptionPane.showOptionDialog(event.window_,
               "Do you want to save this data set before closing?",
               "Micro-Manager", 
               JOptionPane.DEFAULT_OPTION,
	       JOptionPane.QUESTION_MESSAGE, null,
               options, options[1]);
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

      // Go ahead with closing.
      amClosing_ = true;
      // Tell our display thread to stop what it's doing.
      shouldStopDisplayThread_.set(true);
      displayThread_.interrupt();
      // Wait for the display thread to exit.
      try {
         displayThread_.join();
      }
      catch (InterruptedException e) {
         // Wait, what? This should never happen.
         ReportingUtils.logError(e, "Display thread interrupted while waiting for it to finish on its own");
      }
      // Remove us from the CanvasPaintPending system; prevents a memory leak.
      // We could equivalently do this in the display thread, but it has
      // multiple exit points depending on what it was doing when we
      // interrupted it.
      CanvasPaintPending.removeAllPaintPending(hyperImage_.getCanvas());
      bus_.unregister(this);
      imageCache_.finished();

      removeFromAcquisitionManager(MMStudio.getInstance());

      // Shut down our controls.
      controls_.prepareForClose();

      //Call this because for some reason WindowManager doesnt always fire
      mdPanel_.displayChanged(null);

      // Now that we have shut down everything that may access the images,
      // we can close the dataset.
      imageCache_.close();

      // Finally, tell the window to close now.
      DisplayWindow window = event.window_;
      window.forceClosed();
   }

   /*
    * Removes the VirtualAcquisitionDisplay from the Acquisition Manager.
    */
   private void removeFromAcquisitionManager(ScriptInterface gui) {
      if (gui.acquisitionExists(name_)) {
         try {
            gui.closeAcquisition(name_);
         } catch (MMScriptException ex) {
            ReportingUtils.logError(ex);
         }
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
      return hyperImage_ == null ? 1 : ((IMMImagePlus) hyperImage_).getNSlicesUnverified();
   }

   public int getNumFrames() {
      return ((IMMImagePlus) hyperImage_).getNFramesUnverified();
   }

   public int getNumPositions() throws JSONException {
      return MDUtils.getNumPositions(imageCache_.getSummaryMetadata());
   }

   public ImagePlus getImagePlus() {
      return hyperImage_;
   }

   public ImageCache getImageCache() {
      return imageCache_;
   }

   public ImagePlus getImagePlus(int position) {
      virtualStack_.setPositionIndex(position);
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
   
   /**
    * Closes the ImageWindow and associated ImagePlus
    * 
    * @return false if canceled by user, true otherwise 
    */
   public boolean close() {
      try {
         if (hyperImage_ != null) {
            if (hyperImage_.getWindow() != null && 
                  !hyperImage_.getWindow().close()) {
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

   public void setWindowTitle(String title) {
      title_ = title;
      updateWindowTitleAndStatus();
   }

   public void displayStatusLine(String status) {
      controls_.setImageInfoLabel(status);
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
            double zStep = cal.pixelHeight;
            JSONObject tags = this.getCurrentMetadata();
            if (tags != null) {
               try {
                  double imgPixSize = MDUtils.getPixelSizeUm(tags);
                  if (calPixSize != imgPixSize) {
                     applyPixelSizeCalibration(hyperImage_);
                  }
                  double imgZStep = MDUtils.getZStepUm(tags);
                  if (imgZStep != zStep) {
                     applyPixelSizeCalibration(hyperImage_);
                  }
               } catch (JSONException ex) {
                  // this is not strictly an error since it is OK not to have 
                  // these tags. so just continue...
                  //ReportingUtils.logError("Found Image without PixelSizeUm or zStep tag");
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

   /**
    * Retrieve the displayed intensity at the specified coordinates.
    * TODO: for now only returning the value in the first channel.
    */
   public int getIntensityAt(int x, int y) {
      if (hyperImage_ == null) {
         return -1;
      }
      return ((IMMImagePlus) hyperImage_).getPixelIntensities(x, y)[0];
   }
}
