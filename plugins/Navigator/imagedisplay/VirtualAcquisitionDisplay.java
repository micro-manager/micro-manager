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
package imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.WindowManager;
import ij.gui.ImageCanvas;
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
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.*;
import javax.swing.event.MouseInputAdapter;
import mmcloneclasses.acquisition.MMImageCache;
import mmcloneclasses.events.DisplayCreatedEvent;
import mmcloneclasses.graph.HistogramControlsState;
import mmcloneclasses.graph.HistogramSettings;
import mmcloneclasses.graph.MultiChannelHistograms;
import mmcloneclasses.graph.SingleChannelHistogram;
import mmcloneclasses.internalinterfaces.DisplayControls;
import mmcloneclasses.internalinterfaces.Histograms;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudio;
import org.micromanager.acquisition.AcquisitionEngine;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.api.ImageCache;
import org.micromanager.api.ImageCacheListener;
import org.micromanager.api.ScriptInterface;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.api.events.PixelSizeChangedEvent;
import org.micromanager.events.EventManager;
import org.micromanager.utils.*;

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
   final MMImageCache imageCache_;
   private boolean isAcquisitionFinished_ = false;
   private boolean promptToSave_ = true;
   private boolean amClosing_ = false;
   // Name of the acquisition we present.
   private final String name_;
   // First component of text displayed in our title bar.
   private String title_;
   private int numComponents_;
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
   private DisplayControls subImageControls_;
   public AcquisitionVirtualStack virtualStack_;
   private boolean isMDA_ = false; //flag if display corresponds to MD acquisition
   private ContrastMetadataCommentsPanel cmcPanel_;
   private boolean contrastInitialized_ = false; //used for autostretching on window opening
   private boolean firstImage_ = true;
   private String channelGroup_ = "none";
   private JPopupMenu saveTypePopup_;
   private final AtomicBoolean updatePixelSize_ = new AtomicBoolean(false);
   private final AtomicLong newPixelSize_ = new AtomicLong();
   private final Object imageReceivedObject_ = new Object();
   private int numGrayChannels_;
   protected ImageCanvas canvas_;

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
   public VirtualAcquisitionDisplay(MMImageCache imageCache,
           AcquisitionEngine eng, String name, boolean shouldUseNameAsTitle, JSONObject summaryMD) {
      try {
         numComponents_ = Math.max(MDUtils.getNumberOfComponents(summaryMD), 1);
         int numChannels = Math.max(summaryMD.getInt("Channels"), 1);
         numGrayChannels_ = numComponents_ * numChannels;
      } catch (Exception ex) {
         ReportingUtils.showError("Couldn't read summary md");
      }
      name_ = name;
      title_ = name;
      if (!shouldUseNameAsTitle) {
         title_ = WindowManager.getUniqueName("Untitled");
      }
      imageCache_ = imageCache;
      isMDA_ = eng != null;
      setupEventBus();
      setupDisplayThread();
   }
   
   public void setCMCPanel(ContrastMetadataCommentsPanel panel) {
      cmcPanel_ = panel;
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
      });
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
    * Extract a lot of fields from the provided metadata (or, failing that, 
    * from getSummaryMetadata()), and set up our controls and view window.
    */
   private void startup(JSONObject firstImageMetadata, AcquisitionVirtualStack virtualStack) {
         
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
         numFrames = Math.max(summaryMetadata.getInt("Frames"), 1);

         numChannels = Math.max(1 + imageChannelIndex,
                 Math.max(summaryMetadata.getInt("Channels"), 1));
      } catch (JSONException e) {
         ReportingUtils.showError(e);
      } 
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
         
      virtualStack_ = virtualStack;

      hyperImage_ = createHyperImage(createMMImagePlus(virtualStack_),numGrayChannels, numSlices, numFrames);
      canvas_ = hyperImage_.getCanvas();
      
      applyPixelSizeCalibration(hyperImage_);

      createWindows();
      windowToFront();

      updateAndDraw(true);
      updateWindowTitleAndStatus();
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
      updateWindowTitleAndStatus();    
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
            subImageControls_.newImageUpdate(md);
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
      //TODO
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
         //TODO: intial contrast settings
         
//         HistogramSettings settings = MMStudio.getInstance().loadStoredChannelHistogramSettings(
//                 channelGroup_, channelName, isMDA_);
//         histograms_.setChannelContrast(channel, settings.min_, settings.max_, settings.gamma_);
//         histograms_.setChannelHistogramDisplayMax(channel, settings.histMax_);
//         if (imageCache_.getNumDisplayChannels() > 1) {
//            setDisplayMode(settings.displayMode_);
//         }
      }
      cmcPanel_.applyLUTToImage();
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
            //TODO: load/save contrast settings;
//            MMStudio.getInstance().saveChannelHistogramSettings(channelGroup_, 
//                    imageCache_.getChannelName(channelIndex), isMDA_,
//                    new HistogramSettings(min,max, gamma, histMax, displayMode));    
         }
      }
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
      subImageControls_.setChannel(c);
   }

   public boolean pause() {
//      if (eng_ != null) {
//         if (eng_.isPaused()) {
//            eng_.setPause(false);
//         } else {
//            eng_.setPause(true);
//         }
//         updateWindowTitleAndStatus();
//         return (eng_.isPaused());
//      }
      return false;
   }

   public boolean abort() {
//      if (eng_ != null) {
//         if (eng_.abortRequest()) {
//            updateWindowTitleAndStatus();
//            return true;
//         }
//      }
      return false;
   }

   public boolean acquisitionIsRunning() {
//      if (eng_ != null) {
//         return eng_.isAcquisitionRunning();
//      } else {
         return false;
//      }
   }

   private boolean isPaused() {
//      if (eng_ != null) {
//         return eng_.isPaused();
//      } else {
         return false;
//      }
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

   final public ImagePlus createHyperImage(MMImagePlus mmIP, int channels, int slices, int frames) {
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

   private void createWindows() {
      DisplayWindow win = new DisplayWindow(hyperImage_, bus_, (DisplayPlus) this );   
      subImageControls_ = win.getSubImageControls();
      imageChangedUpdate();
      EventManager.post(new DisplayCreatedEvent(this, win));
   }

   // A window wants to close; check if it's okay. If it is, then we call its
   // forceClosed() function.
   // TODO: for now, assuming we only have one window.
   @Subscribe
   public void onWindowClose(DisplayWindow.RequestToCloseEvent event) {    
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
      subImageControls_.prepareForClose();

      //Call this because for some reason WindowManager doesnt always fire
//      metadataWindow_.setVisible(false);
//      metadataWindow_.dispose();

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

   public MMImageCache getImageCache() {
      return imageCache_;
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
         startup(null, virtualStack);
      }
      hyperImage_.show();
      hyperImage_.getWindow().toFront();
   }

   public int getNumChannels() {
      return hyperImage_ == null ? numGrayChannels_ : ((IMMImagePlus) hyperImage_).getNChannelsUnverified();
   }

   public int getNumGrayChannels() {
      return getNumChannels();
   }

   public void setWindowTitle(String title) {
      title_ = title;
      updateWindowTitleAndStatus();
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
      if (cmcPanel_ != null) {            
         cmcPanel_.imageChangedUpdate(this);
         if (updatePixelSize) {
            cmcPanel_.redrawSizeBar();
         }
      }
      imageChangedWindowUpdate(); //used to update status line
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
