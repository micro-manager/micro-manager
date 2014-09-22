package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;

import java.awt.Component;
import java.lang.Math;
import java.lang.Thread;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.NewImageEvent;
import org.micromanager.api.display.DrawEvent;

import org.micromanager.imagedisplay.DisplayWindow;
import org.micromanager.imagedisplay.FPSEvent;
import org.micromanager.imagedisplay.IMMImagePlus;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.MMImagePlus;

import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.ReportingUtils;


/**
 * This class is responsible for intermediating between the different
 * components that combine to form the image display.
 */
public class TestDisplay implements org.micromanager.api.data.DisplayWindow {
   private Datastore store_;
   private MMVirtualStack stack_;
   private ImagePlus ijImage_;
   private MMImagePlus plus_;

   private DisplayWindow window_;
   private HyperstackControls controls_;
   private MultiModePanel modePanel_;
   private HistogramsPanel histograms_;
   private MetadataPanel metadata_;
   private CommentsPanel comments_;

   // These objects are used by the display thread and for tracking our
   // display FPS.
   private LinkedBlockingQueue<Coords> coordsQueue_;
   private AtomicBoolean shouldStopDisplayThread_;
   private Thread displayThread_;
   private int imagesDisplayed_ = 0;
   private long lastImageIndex_ = 0;
   private long lastFPSUpdateTimestamp_ = -1;
   
   private EventBus displayBus_;
   
   public TestDisplay(Datastore store) {
      store_ = store;
      store_.registerForEvents(this, 100);
      store_.associateDisplay(this);
      displayBus_ = new EventBus();
      displayBus_.register(this);
      // Delay generating our UI until we have at least one image, because
      // otherwise ImageJ gets badly confused.
      if (store_.getNumImages() > 0) {
         makeWindowAndIJObjects();
      }
   }

   private void makeWindowAndIJObjects() {
      stack_ = new MMVirtualStack(store_);
      plus_ = new MMImagePlus(displayBus_);
      stack_.setImagePlus(plus_);
      plus_.setStack("foo", stack_);
      plus_.setOpenAsHyperStack(true);
      // The ImagePlus object needs to be pseudo-polymorphic, depending on
      // the number of channels in the Datastore. However, we may not
      // have all of the channels available to us at the time this display is
      // created, so we may need to re-create things down the road.
      ijImage_ = plus_;
      if (store_.getMaxIndex("channel") > 0) {
         // Have multiple channels.
         shiftToCompositeImage();
      }
      setIJBounds();
      if (ijImage_ instanceof MMCompositeImage) {
         ((MMCompositeImage) ijImage_).reset();
      }

      window_ = new DisplayWindow(ijImage_, displayBus_);
      setWindowControls();
      window_.zoomToPreferredSize();
      window_.setTitle("Micro-Manager image display prototype");
      histograms_.calcAndDisplayHistAndStats(true);

      shouldStopDisplayThread_ = new AtomicBoolean(false);
      startDisplayThread();
   }

   /**
    * Spawn a new thread to display images.
    */
   private void startDisplayThread() {
      coordsQueue_ = new LinkedBlockingQueue<Coords>();
      displayThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            Coords coords = null;
            while (!shouldStopDisplayThread_.get()) {
               boolean haveValidImage = false;
               // Extract images from the queue until we get to the end.
               do {
                  try {
                     // This will block until an image is available or we need
                     // to send a new FPS update.
                     coords = coordsQueue_.poll(500, TimeUnit.MILLISECONDS);
                     haveValidImage = (coords != null);
                     if (coords == null) {
                        try {
                           // We still need to generate an FPS update at 
                           // regular intervals; we just have to do it without
                           // any image coords.
                           sendFPSUpdate(null);
                        }
                        catch (Exception e) {
                           // Can't get image coords, apparently; give up.
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
               } while (coordsQueue_.peek() != null);

               if (coords == null || !haveValidImage) {
                  // Nothing to show. 
                  continue;
               }

               if (ijImage_ != null && ijImage_.getCanvas() != null) {
                  // Wait for the canvas to be available. If we don't do this,
                  // then our framerate tanks, possibly because of repaint
                  // events piling up in the EDT. It's hard to tell. 
                  while (CanvasPaintPending.isMyPaintPending(
                        ijImage_.getCanvas(), ijImage_)) {
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
                        ijImage_.getCanvas(), ijImage_);
               }
               final Image image = store_.getImage(coords);
               // This must be on the EDT because drawing is not thread-safe.
               SwingUtilities.invokeLater(new Runnable() {
                  @Override
                  public void run() {
                     showImage(image);
                     imagesDisplayed_++;
                     sendFPSUpdate(image);
                  }
               });
            } // End while loop
         }
      });
      displayThread_.start();
   }

   /**
    * Show an image.
    */
   private void showImage(Image image) {
      if (ijImage_ instanceof MMCompositeImage) {
         stack_.setCoords(image.getCoords());
         MMCompositeImage composite = (MMCompositeImage) ijImage_;
      }
      ijImage_.getProcessor().setPixels(image.getRawPixels());
      ijImage_.updateAndDraw();
      histograms_.calcAndDisplayHistAndStats(true);
      metadata_.imageChangedUpdate(image);
   }

   /**
    * Send an update on our FPS, both data rate and image display rate. Only
    * if it has been at least 500ms since our last update.
    */
   private void sendFPSUpdate(Image image) {
      long curTimestamp = System.currentTimeMillis();
      // Hack: if we have null image, then post a "blank" FPS event.
      if (image == null) {
         displayBus_.post(new FPSEvent(0, 0));
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
            Integer imageIndex = image.getMetadata().getImageNumber();
            if (imageIndex != null) {
               // HACK: Ignore the first FPS display event, to prevent us from
               // showing FPS for the Snap window.
               if (lastImageIndex_ != 0) {
                  displayBus_.post(new FPSEvent((imageIndex - lastImageIndex_) / elapsedTime,
                           imagesDisplayed_ / elapsedTime));
               }
               lastImageIndex_ = imageIndex;
            }
         }
         catch (Exception e) {
            // Post a "blank" event. This likely happens because the image
            // image don't contain a sequence number (e.g. during an MDA).
            displayBus_.post(new FPSEvent(0, 0));
         }
         imagesDisplayed_ = 0;
         lastFPSUpdateTimestamp_ = curTimestamp;
      }
   }


   /**
    * We've discovered that we need to represent a multichannel image.
    */
   private void shiftToCompositeImage() {
      // TODO: assuming mode 1 for now.
      ijImage_ = new MMCompositeImage(plus_, 1, "foo", displayBus_);
      ijImage_.setOpenAsHyperStack(true);
      MMCompositeImage composite = (MMCompositeImage) ijImage_;
      int numChannels = store_.getMaxIndex("channel") + 1;
      composite.setNChannelsUnverified(numChannels);
      stack_.setImagePlus(ijImage_);
      composite.reset();

      if (window_ != null) {
         setWindowControls();
         histograms_.calcAndDisplayHistAndStats(true);
      }
   }

   // Ensure that our ImageJ object has the correct number of channels, 
   // frames, and slices.
   private void setIJBounds() {
      IMMImagePlus temp = (IMMImagePlus) ijImage_;
      int numChannels = Math.max(1, store_.getMaxIndex("channel") + 1);
      int numFrames = Math.max(1, store_.getMaxIndex("time") + 1);
      int numSlices = Math.max(1, store_.getMaxIndex("z") + 1);
      temp.setNChannelsUnverified(numChannels);
      temp.setNFramesUnverified(numFrames);
      temp.setNSlicesUnverified(numSlices);
      // TODO: VirtualAcquisitionDisplay folds "components" into channels;
      // what are components used for?
      // TODO: calling this causes ImageJ to create its own additional
      // window, which looks terrible, so we're leaving it out for now.
      //plus_.setDimensions(numChannels, numSlices, numFrames);
   }

   /**
    * Generate the controls that we'll stuff into the DisplayWindow, along
    * with the rules that will be used to lay them out.
    */
   private void setWindowControls() {
      ArrayList<Component> widgets = new ArrayList<Component>();
      ArrayList<String> rules = new ArrayList<String>();
      controls_ = new HyperstackControls(store_, stack_, displayBus_,
            false, false);
      widgets.add(controls_);
      rules.add("align center, wrap, growx");
      modePanel_ = new MultiModePanel(displayBus_);
      
      DisplaySettingsPanel settings = new DisplaySettingsPanel(
            store_, ijImage_);
      modePanel_.addMode("Settings", settings);

      histograms_ = new HistogramsPanel(store_, stack_, ijImage_, displayBus_);
      histograms_.setMinimumSize(new java.awt.Dimension(280, 0));
      modePanel_.addMode("Contrast", histograms_);

      metadata_ = new MetadataPanel(store_);
      modePanel_.addMode("Metadata", metadata_);

      comments_ = new CommentsPanel(store_);
      modePanel_.addMode("Comments", comments_);

      modePanel_.addMode("Overlays",
            new OverlaysPanel(store_, stack_, ijImage_, displayBus_));

      widgets.add(modePanel_);
      rules.add("dock east, growy");
      window_.setupLayout(widgets, rules);
   }

   /**
    * Datastore has received a new image.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      ReportingUtils.logError("Display caught new image at " + event.getCoords());
      if (window_ == null) {
         // Now we have some images with which to set up our display.
         makeWindowAndIJObjects();
      }
      try {
         // Check if we're transitioning from grayscale to multi-channel at this
         // time.
         if (!(ijImage_ instanceof MMCompositeImage) &&
               event.getImage().getCoords().getPositionAt("channel") > 0) {
            // Have multiple channels.
            shiftToCompositeImage();
            setWindowControls();
         }
         if (ijImage_ instanceof MMCompositeImage) {
            // Verify that ImageJ has the right number of channels.
            int numChannels = store_.getMaxIndex("channel");
            MMCompositeImage composite = (MMCompositeImage) ijImage_;
            composite.setNChannelsUnverified(numChannels);
            composite.reset();
            for (int i = 0; i < numChannels; ++i) {
               if (composite.getProcessor(i + 1) != null) {
                  composite.getProcessor(i + 1).setPixels(event.getImage().getRawPixels());
               }
            }
         }
         setIJBounds();
         coordsQueue_.put(event.getCoords());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't display new image");
      }
   }

   /**
    * Our layout has changed and we need to repack.
    */
   @Subscribe
   public void onLayoutChanged(LayoutChangedEvent event) {
      // This is necessary to get the window to notice changes to components
      // contained within it (due to AWT/Swing mixing?).
      window_.validate();
      window_.pack();
   }

   /**
    * Something on our display bus (i.e. not the Datastore bus) wants us to
    * redisplay.
    */
   @Subscribe
   public void onDrawEvent(DrawEvent event) {
      Coords drawCoords = stack_.getCurrentImageCoords();
      coordsQueue_.add(drawCoords);
   }

   /**
    * Manually display the image at the specified coordinates.
    */
   @Override
   public void setDisplayedImageTo(Coords coords) {
      coordsQueue_.add(coords);
   }

   @Override
   public void addControlPanel(String label, Component widget) {
      modePanel_.addMode(label, widget);
   }

   // TODO: this should be redundant once this module is merged with 
   // DisplayWindow.
   public DisplayWindow getWindow() {
      return window_;
   }

   @Override
   public ImagePlus getImagePlus() {
      return ijImage_;
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public void close() {
      window_.close();
      store_.removeDisplay(this);
   }
}
