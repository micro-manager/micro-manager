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
package org.micromanager.magellan.imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.gui.ImageCanvas;
import ij.io.FileInfo;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.SwingUtilities;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.magellan.imagedisplaynew.MagellanImageCache;
import org.micromanager.magellan.misc.JavaUtils;
import org.micromanager.magellan.misc.Log;
import org.micromanager.magellan.misc.MD;

public abstract class VirtualAcquisitionDisplay {

   /**
    * Given an ImagePlus, retrieve the associated VirtualAcquisitionDisplay.
    * This only works if the ImagePlus is actually an AcquisitionVirtualStack;
    * otherwise you just get null.
    *
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
   final MagellanImageCache imageCache_;
   private boolean amClosing_ = false;
   // First component of text displayed in our title bar.
   protected String title_;
   private int numComponents_;
   // This thread consumes images from the above queue.
   private Thread displayThread_;
   // This boolean is used to tell the display thread to stop what it's doing.
   private final AtomicBoolean shouldStopDisplayThread_ = new AtomicBoolean(false);

   private MMCompositeImage mmCompositeImage_;
//   protected DisplayWindowControls dispWindControls_;
//   protected SubImageControls subImageControls_;
   public AcquisitionVirtualStack virtualStack_;
   private boolean contrastInitialized_ = false; //used for autostretching on window opening
   private String channelGroup_ = "none";
   private final Object imageReceivedObject_ = new Object();
   protected ImageCanvas canvas_;
   private LinkedBlockingQueue<JSONObject> acquiredTagsQueue_ = new LinkedBlockingQueue<JSONObject>();
   private int maxChannelIndex_ = 0;
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
    *
    * @param imageCache
    * @param eng
    * @param name
    * @param shouldUseNameAsTitle
    */
   public VirtualAcquisitionDisplay(MagellanImageCache imageCache, String name, JSONObject summaryMD) {
      try {
         numComponents_ = Math.max(MD.getNumberOfComponents(summaryMD), 1);
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

//   public void setControls(DisplayWindowControls dwc) {
//      dispWindControls_ = dwc;
//   }

   /**
    * Create a new EventBus that will be used for all events related to this
    * display system.
    */
   private void setupEventBus() {
      bus_ = new EventBus();
      bus_.register(this);
   }

   /**
    * This thread is to monitor saved images that chagne the scroller positions
    */
   private void setupDisplayThread() {
      displayThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            JSONObject tags = null;
            while (!shouldStopDisplayThread_.get()) {
//               //empty out excess tags and dont bother creating events
//               while (acquiredTagsQueue_.size() > 1) {
//                  acquiredTagsQueue_.poll();
//               }
               try {
                  tags = acquiredTagsQueue_.take();
//                  System.out.println("image tags take time " + (System.currentTimeMillis() % 10000));
               } catch (InterruptedException ex) {
                  // Interrupted while waiting for the queue to be 
                  // populated. 
                  if (shouldStopDisplayThread_.get()) {
                     // Time to stop.
                     return;
                  }
               }
               if (mmCompositeImage_ != null && mmCompositeImage_.getCanvas() != null) {

                  // Wait for the canvas to be available. If we don't do this,
                  // then our framerate tanks, possibly because of repaint
                  // events piling up in the EDT. It's hard to tell. 
                  while (CanvasPaintPending.isMyPaintPending(mmCompositeImage_.getCanvas(), imageReceivedObject_)) {
                     try {
                        Thread.sleep(10);
                     } catch (InterruptedException e) {
                        if (shouldStopDisplayThread_.get()) {
                           // Time to stop.
                           return;
                        }
                     }
                  }

                  CanvasPaintPending.setPaintPending(mmCompositeImage_.getCanvas(), imageReceivedObject_);
               }

               imageAcquiredDisplayUpdate(tags);
            } // End while loop
         }
      }, "Virtual acquisition display thread");
      displayThread_.start();
   }

   // Retrieve our EventBus.
   public EventBus getEventBus() {
      return bus_;
   }

//   public int getNumChannels() {
//      if (dispWindControls_ == null) {
//         return 0;
//      }
//      return dispWindControls_.getNumChannels();
//   }
   
   // Prepare for a drawing event.
   @Subscribe
   public void onDraw(DrawEvent event) {
      if (!amClosing_) {
         imageChangedUpdate();
      }
   }

   /**
    * Extract a lot of fields from the provided metadata (or, failing that, from
    * getSummaryMetadata()), and set up our controls and view window.
    */
   private void startup(AcquisitionVirtualStack virtualStack) {
      int numFrames = 1;
      virtualStack_ = virtualStack;

      //always say numslices is 1...it inevitably gets changed anyway
      mmCompositeImage_ = createHyperImage(createMMImagePlus(virtualStack_), 1, numFrames);
      canvas_ = mmCompositeImage_.getCanvas();

      try {
      applyPixelSizeCalibration();
      } catch (Exception e) {
         e.printStackTrace();
      }

//      createWindows();
      windowToFront();

      updateAndDraw(true);
      updateWindowTitleAndStatus();
   }

   /**
    * Used to enable scrollbar movement image showing events
    */
   public void imageReceived(final TaggedImage magellanTaggedImage) {
      if (magellanTaggedImage != null) {
         try {
            acquiredTagsQueue_.put(magellanTaggedImage.tags);
         } catch (InterruptedException ex) {
//            Log.log("Huh? Shouldnt happen because queue is unbounded");
         }
      }
   }

   /**
    * Method required by ImageCacheListener
    *
    * @param path
    */
   public void imagingFinished(String path) {
      if (amClosing_) {
         // Don't care, we'll be closing soon anyway.
         return;
      }
      updateWindowTitleAndStatus();
   }

   public int rgbToGrayChannel(int channelIndex) {
      if (MD.getNumberOfComponents(imageCache_.getSummaryMD()) == 3) {
         return channelIndex * 3 + 2;
      }
      return channelIndex;
   }

   public int grayToRGBChannel(int grayIndex) {
      if (imageCache_ != null) {
//         if (imageCache_.getSummaryMetadata() != null) {
//            if (MD.getNumberOfComponents(imageCache_.getSummaryMD()) == 3) {
//               return grayIndex / 3;
//            }
//         }
      }
      return grayIndex;
   }

   protected abstract void applyPixelSizeCalibration();

   public MMCompositeImage getHyperImage() {
      return mmCompositeImage_;
   }

   public int getStackSize() {
      if (mmCompositeImage_ == null) {
         return -1;
      }
      int s = mmCompositeImage_.getNSlices();
      int c = mmCompositeImage_.getNChannels();
      int f = mmCompositeImage_.getNFrames();
      if ((s > 1 && c > 1) || (c > 1 && f > 1) || (f > 1 && s > 1)) {
         return s * c * f;
      }
      return Math.max(Math.max(s, c), f);
   }

   public void updateAndDraw(boolean force) {
      imageChangedUpdate();
      if (mmCompositeImage_ != null && mmCompositeImage_.isVisible()) {
         if (mmCompositeImage_ instanceof MMCompositeImage) {
            ((MMCompositeImage) mmCompositeImage_).updateAndDraw(force);
         } else {
            mmCompositeImage_.updateAndDraw();
         }
      }
   }

   protected abstract void updateWindowTitleAndStatus();

   private void windowToFront() {
      if (mmCompositeImage_ == null || mmCompositeImage_.getWindow() == null) {
         return;
      }
      mmCompositeImage_.getWindow().toFront();
   }

   private void imageAcquiredDisplayUpdate(final JSONObject tags) {
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            if (shouldStopDisplayThread_.get()) {
               // Time to stop.
               return;
            }
            updateWindowTitleAndStatus();

            if (tags == null) {
               return;
            }

            if (mmCompositeImage_ == null) {
               startup(null);
            }

            int frame = MD.getFrameIndex(tags);
            int slice = MD.getSliceIndex(tags);
            int channel = MD.getChannelIndex(tags);
            int position = MD.getPositionIndex(tags);
            // Construct a mapping of axis to position so we can post an 
            // event informing others of the new image.
            HashMap<String, Integer> axisToPosition = new HashMap<String, Integer>();
            axisToPosition.put("channel", rgbToGrayChannel(channel));
            axisToPosition.put("position", position);
            axisToPosition.put("time", frame);
            if (((MagellanDisplay) VirtualAcquisitionDisplay.this).getAcquisition() != null) {
               //intercept event and edit slice index
               //make slice index >= 0 for viewer   
               axisToPosition.put("z", slice - (((MagellanDisplay) VirtualAcquisitionDisplay.this).getAcquisition()).getMinSliceIndex());
            } else if (((MagellanDisplay) VirtualAcquisitionDisplay.this).getAcquisition() == null) {
               axisToPosition.put("z", slice - ((MagellanDisplay) VirtualAcquisitionDisplay.this).getStorage().getMinSliceIndexOpenedDataset());
            }

//            bus_.post(new NewImageEvent(axisToPosition, MD.getChannelName(tags)));
            ((MagellanDisplay) VirtualAcquisitionDisplay.this).updateDisplay(true);
         }
      });

   }

   public void setSliceIndex(int i) {
      final int f = mmCompositeImage_.getFrame();
      final int c = mmCompositeImage_.getChannel();
      mmCompositeImage_.setPosition(c, i + 1, f);
   }

   /**
    * used by checkboxes on contrast panel when in color mode
    *
    * @param c
    */
   public void setChannel(int c) {
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

   final public MMCompositeImage createHyperImage(MMImagePlus mmIP, int slices, int frames) {
      mmIP.setNChannelsUnverified(2);
      mmIP.setNFramesUnverified(frames);
      mmIP.setNSlicesUnverified(slices);
      final MMCompositeImage hyperImage = new MMCompositeImage(mmIP, CompositeImage.COMPOSITE, title_, bus_);
      hyperImage.setOpenAsHyperStack(true);
      return hyperImage;
   }

//   private void createWindows() {
//      DisplayWindow win = new DisplayWindow(mmCompositeImage_, bus_, (MagellanDisplay) this);
//      dispWindControls_ = win.getDisplayWindowControls();
////      subImageControls_ = win.getSubImageControls();
//      imageChangedUpdate();
//   }

   public boolean isClosing() {
      return amClosing_;
   }

   @Subscribe
   public void onWindowClose(DisplayWindow.RequestToCloseEvent event) {
//      bus_.unregister(this); //Dont do this bc Display pluus does it
      // Go ahead with closing.
      amClosing_ = true;
      // Tell our display thread to stop what it's doing.
      shouldStopDisplayThread_.set(true);
      displayThread_.interrupt();
      // Wait for the display thread to exit.
      try {
         displayThread_.join();
      } catch (InterruptedException e) {
         // Wait, what? This should never happen.
         Log.log(e);
      }
      // Remove us from the CanvasPaintPending system; prevents a memory leak.
      // We could equivalently do this in the display thread, but it has
      // multiple exit points depending on what it was doing when we
      // interrupted it.
      CanvasPaintPending.removeAllPaintPending(mmCompositeImage_.getCanvas());
//      imageCache_.finished();

      // Shut down our controls.
//      subImageControls_.prepareForClose();

      // Now that we have shut down everything that may access the images,
      // we can close the dataset.
      imageCache_.close();

      // Finally, tell the window to close now.
      DisplayWindow window = event.window_;
      window.forceClosed();
   }
   public ImagePlus getImagePlus() {
      return mmCompositeImage_;
   }

   public MagellanImageCache getImageCache() {
      return imageCache_;
   }

//   public final JSONObject getSummaryMD() {
//      return imageCache_.getSummaryMetadata();
//   }

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
               Runtime.getRuntime().exec(new String[]{"open", location.getAbsolutePath()});
            }
         } catch (IOException ex) {
            Log.log(ex);
         }
      }
   }

   public boolean isDiskCached() {
      MagellanImageCache imageCache = imageCache_;
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
   protected void show(final AcquisitionVirtualStack virtualStack) {
      if (mmCompositeImage_ == null) {
         startup(virtualStack);
      }
      mmCompositeImage_.show();
      mmCompositeImage_.getWindow().toFront();
   }

   /*
    * Called just before image is drawn.  Notifies metadata panel to update
    * metadata or comments if this display is the active window.  Notifies histograms
    * that image is change to create appropriate LUTs and to draw themselves if this
     is the active window
    */
   private void imageChangedUpdate() {
//      if (mmCompositeImage_ != null) {
//         applyPixelSizeCalibration();
//      }
//      if (dispWindControls_ != null) {
//         dispWindControls_.imageChangedUpdate(((MagellanDisplay) this).getCurrentMetadata());
//      }
   }


   /*
    * Called when contrast changes as a result of user or programmtic input, but underlying pixels 
    * remain unchanges
    */
   public void drawWithoutUpdate() {
      if (mmCompositeImage_ != null) {
         ((IMMImagePlus) mmCompositeImage_).drawWithoutUpdate();
      }
   }
}
