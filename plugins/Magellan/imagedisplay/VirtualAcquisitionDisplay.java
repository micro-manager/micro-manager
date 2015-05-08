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
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageCanvas;
import ij.io.FileInfo;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import acq.MMImageCache;
import misc.JavaUtils;
import misc.Log;
import misc.MD;
import mmcloneclasses.graph.HistogramSettings;
import mmcloneclasses.graph.MultiChannelHistograms;
import mmcloneclasses.graph.Histograms;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;

public abstract class VirtualAcquisitionDisplay{

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
   // First component of text displayed in our title bar.
   protected String title_;
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
   protected SubImageControls subImageControls_;
   public AcquisitionVirtualStack virtualStack_;
   private ContrastMetadataPanel cmcPanel_;
   private boolean contrastInitialized_ = false; //used for autostretching on window opening
   private boolean firstImage_ = true;
   private String channelGroup_ = "none";
   private JPopupMenu saveTypePopup_;
   private final Object imageReceivedObject_ = new Object();
   private int numGrayChannels_;
   protected ImageCanvas canvas_;
   private static HashMap<String, HistogramSettings> contrastSettings_ = new HashMap<String, HistogramSettings>();

   private EventBus bus_;

//   @Subscribe
//   public void onPixelSizeChanged(PixelSizeChangedEvent event) {
//      // Signal that pixel size has changed so that the next image will update
//      // metadata and scale bar
//      newPixelSize_.set(Double.doubleToLongBits(event.getNewPixelSizeUm()));
//      updatePixelSize_.set(true);
//   }

   /**
    * Standard constructor.
    * @param imageCache
    * @param eng
    * @param name
    * @param shouldUseNameAsTitle
    */
   public VirtualAcquisitionDisplay(MMImageCache imageCache, String name, JSONObject summaryMD) {
      try {
         numComponents_ = Math.max(MD.getNumberOfComponents(summaryMD), 1);
         int numChannels = Math.max(summaryMD.getInt("Channels"), 1);
         numGrayChannels_ = numComponents_ * numChannels;
      } catch (Exception ex) {
         Log.log("Couldn't read summary md");
      }
      title_ = name;
      imageCache_ = imageCache;
      setupEventBus();
      setupDisplayThread();
   }
   
   public String getTitle() {
      return title_;
   }
   
   public void setCMCPanel(ContrastMetadataPanel panel) {
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
                  CanvasPaintPending.setPaintPending(hyperImage_.getCanvas(), imageReceivedObject_);
               }
               showImage(tags, true);
               imagesDisplayed_++;
            } // End while loop
         }
      });
      displayThread_.start();
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
      int numFrames = 1;
      int numChannels = 1;
      int numGrayChannels;
 
      try {
         int imageChannelIndex;
         if (firstImageMetadata != null) {          
            imageChannelIndex = MD.getChannelIndex(firstImageMetadata);
         } else {
            imageChannelIndex = -1;
         }
         numFrames = 1;

         numChannels = Math.max(1 + imageChannelIndex,
                 Math.max(summaryMetadata.getInt("Channels"), 1));
      } catch (JSONException e) {
         Log.log(e);
      } 
      numGrayChannels = numComponents_ * numChannels;

      if (imageCache_.getDisplayAndComments() == null || 
            imageCache_.getDisplayAndComments().isNull("Channels")) {
         try {
            imageCache_.setDisplayAndComments(DisplaySettings.getDisplaySettingsFromSummary(summaryMetadata));
         } catch (Exception ex) {
            Log.log(ex);
         }
      }
         
      virtualStack_ = virtualStack;

      //always say numslices is 1...it inevitably gets changed anyway
      hyperImage_ = createHyperImage(createMMImagePlus(virtualStack_),numGrayChannels, 1, numFrames);
      canvas_ = hyperImage_.getCanvas();
      
      applyPixelSizeCalibration();

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
   public void imageReceived(final TaggedImage taggedImage) {
      updateDisplay(taggedImage);
   }

   /**
    * Method required by ImageCacheListener
    * @param path
    */
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
         Log.log("Ran out of space in the imageQueue! Inconceivable!",true);
      }
   }

   public int rgbToGrayChannel(int channelIndex) {
      if (MD.getNumberOfComponents(imageCache_.getSummaryMetadata()) == 3) {
         return channelIndex * 3;
      }
      return channelIndex;
   }

   public int grayToRGBChannel(int grayIndex) {
      if (imageCache_ != null) {
         if (imageCache_.getSummaryMetadata() != null) {
            if (MD.getNumberOfComponents(imageCache_.getSummaryMetadata()) == 3) {
               return grayIndex / 3;
            }
         }
      }
      return grayIndex;
   }

   protected abstract void applyPixelSizeCalibration();
   
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
//         JSONObject md = getCurrentMetadata();
//         if (md != null) {
//            subImageControls_.newImageUpdate(md);
//         }
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

   protected abstract void updateWindowTitleAndStatus();

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
      if (shouldStopDisplayThread_.get()) {
         // Time to stop.
         return;
      }
      
      updateWindowTitleAndStatus();

      if (tags == null) {
         return;
      }

      if (hyperImage_ == null) {
         startup(tags, null);
      }

      int frame = MD.getFrameIndex(tags);
      int slice = MD.getSliceIndex(tags);
      int channel = MD.getChannelIndex(tags);
      int position = MD.getPositionIndex(tags);
      // Construct a mapping of axis to position so we can post an 
      // event informing others of the new image.
      HashMap<String, Integer> axisToPosition = new HashMap<String, Integer>();
      axisToPosition.put("channel", channel);
      axisToPosition.put("position", position);
      axisToPosition.put("time", frame);
      axisToPosition.put("z", slice);
      bus_.post(new NewImageEvent(axisToPosition));

      //make sure pixels get properly set
      if (hyperImage_ != null && hyperImage_.getProcessor() != null && 
            frame == 0) {
         IMMImagePlus img = (IMMImagePlus) hyperImage_;
         if (img.getNChannelsUnverified() == 1) {
               hyperImage_.getProcessor().setPixels(virtualStack_.getPixels(hyperImage_.getCurrentSlice()));
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

      if (frame == 0) {
         initializeContrast();
      }

      updateAndDraw(true);
      ((DisplayPlus) this).drawOverlay();
      hyperImage_.getWindow().repaint();
   }

   private void initializeContrast() {
      if (contrastInitialized_ ) {
         return;
      }
      int numChannels = imageCache_.getNumDisplayChannels();
      Histograms histograms = cmcPanel_.getHistograms();
      for (int channel = 0; channel < numChannels; channel++) {
         String id = channelGroup_ + "-" + imageCache_.getChannelName(channel);
         HistogramSettings settings = contrastSettings_.get(id);
         if (settings != null) {
            histograms.setChannelContrast(channel, settings.min_, settings.max_, settings.gamma_);
            histograms.setChannelHistogramDisplayMax(channel, settings.histMax_);
            if (histograms instanceof MultiChannelHistograms) {
               ((MultiChannelHistograms) histograms).setDisplayMode(settings.displayMode_);
            }
         }
      }
      histograms.applyLUTToImage();
      contrastInitialized_ = true;
   }

   public void storeChannelHistogramSettings(int channelIndex, int min, int max, 
           double gamma, int histMax, int displayMode) {
      if (!contrastInitialized_ ) {
         return; //don't erroneously initialize contrast
      }
      // store for this dataset
      if (imageCache_.getDisplayAndComments() != null) {
         imageCache_.storeChannelDisplaySettings(channelIndex, min, max, gamma, histMax, displayMode);
         //store global preference for channel contrast settings
         String channelID = channelGroup_ +"-" + imageCache_.getChannelName(channelIndex);
         contrastSettings_.put(channelID, new HistogramSettings(min,max, gamma, histMax, displayMode));          
      }
   }

   public void setSliceIndex(int i) {
      final int f = hyperImage_.getFrame();
      final int c = hyperImage_.getChannel();
      hyperImage_.setPosition(c, i + 1, f);
   }

   /**
    * used by checkboxes on contrast panel when in color mode
    * @param c 
    */
   public void setChannel(int c) {
      //TODO: make this respond to checkboxes in channel control panels
//      for (AxisScroller scroller : scrollers_) {
//         String axis = scroller.getAxis();
//         Integer position = scroller.getPosition();
//    
//         lastImagePosition_.put(axis, position);
//      }
//      bus_.post(new Scroll);
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
         hyperImage = new MMCompositeImage(mmIP, imageCache_.getDisplayMode(), title_, bus_);
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
   }

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
         Log.log(e);
      }
      // Remove us from the CanvasPaintPending system; prevents a memory leak.
      // We could equivalently do this in the display thread, but it has
      // multiple exit points depending on what it was doing when we
      // interrupted it.
      CanvasPaintPending.removeAllPaintPending(hyperImage_.getCanvas());
      bus_.unregister(this);
      imageCache_.finished();

      // Shut down our controls.
      subImageControls_.prepareForClose();

      // Now that we have shut down everything that may access the images,
      // we can close the dataset.
      imageCache_.close();

      // Finally, tell the window to close now.
      DisplayWindow window = event.window_;
      window.forceClosed();
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

   public ImagePlus getImagePlus() {
      return hyperImage_;
   }

   public MMImageCache getImageCache() {
      return imageCache_;
   }

   public final JSONObject getSummaryMetadata() {
      return imageCache_.getSummaryMetadata();
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
            Log.log(ex);
         }
      }
   }

   public boolean isDiskCached() {
      MMImageCache imageCache = imageCache_;
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

   /*
    * Called just before image is drawn.  Notifies metadata panel to update
    * metadata or comments if this display is the active window.  Notifies histograms
    * that image is change to create appropriate LUTs and to draw themselves if this
     is the active window
    */
   private void imageChangedUpdate() {
      if (hyperImage_ != null) {
         applyPixelSizeCalibration();
      }
      if (cmcPanel_ != null) {
         cmcPanel_.imageChangedUpdate(this);
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
   
}
