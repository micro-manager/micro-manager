package org.micromanager.imagedisplay;

import com.google.common.eventbus.EventBus;

import ij.ImagePlus;

import java.lang.Thread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import javax.swing.SwingUtilities;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;

import org.micromanager.imagedisplay.events.FPSEvent;

import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.ReportingUtils;

/**
 * This class handles the logic related to updating the image displayed on
 * a DisplayWindow's canvas.
 */
public class CanvasUpdateThread extends Thread {
   /**
    * This class signifies that we just updated the pixels that the canvas is
    * displaying, and thus any associated widgets (e.g. histograms and
    * metadata) also need to be updated.
    */
   public class PixelsSetEvent {
      private Image image_;
      public PixelsSetEvent(Image image) {
         image_ = image;
      }
      public Image getImage() {
         return image_;
      }
   }
   
   private Datastore store_;
   private MMVirtualStack stack_;
   private ImagePlus plus_;
   private EventBus displayBus_;
   
   private LinkedBlockingQueue<Coords> coordsQueue_;
   private AtomicBoolean shouldStop_;
   private Thread displayThread_;
   private int imagesDisplayed_ = 0;
   private long lastImageIndex_ = 0;
   private long lastFPSUpdateTimestamp_ = -1;

   public CanvasUpdateThread(Datastore store, MMVirtualStack stack,
         ImagePlus plus, EventBus bus) {
      setName("Canvas display thread for store " + store.hashCode());
      store_ = store;
      stack_ = stack;
      plus_ = plus;
      displayBus_ = bus;
      coordsQueue_ = new LinkedBlockingQueue<Coords>();
      shouldStop_ = new AtomicBoolean(false);
   }

   public void setImagePlus(ImagePlus plus) {
      plus_ = plus;
   }
   
   @Override
   public void run() {
      Coords coords = null;
      while (!shouldStop_.get()) {
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
               if (shouldStop_.get()) {
                  // Time to stop.
                  return;
               }
            }
         } while (coordsQueue_.peek() != null);

         if (coords == null || !haveValidImage) {
            // Nothing to show. 
            continue;
         }

         if (plus_ != null && plus_.getCanvas() != null) {
            // Wait for the canvas to be available. If we don't do this,
            // then our framerate tanks, possibly because of repaint
            // events piling up in the EDT. It's hard to tell. 
            while (!shouldStop_.get() && CanvasPaintPending.isMyPaintPending(
                  plus_.getCanvas(), plus_)) {
               try {
                  Thread.sleep(10);
               }
               catch (InterruptedException e) {
                  if (shouldStop_.get()) {
                     // Time to stop.
                     return;
                  }
               }
            }
            CanvasPaintPending.setPaintPending(
                  plus_.getCanvas(), plus_);
         }
         final Image image = store_.getImage(coords);
         if (image != null) {
            // This must be on the EDT because drawing is not thread-safe.
            SwingUtilities.invokeLater(new Runnable() {
               @Override
               public void run() {
                  showImage(image);
                  imagesDisplayed_++;
                  sendFPSUpdate(image);
               }
            });
         }
         else {
            ReportingUtils.logError("Attempted to draw null image at " + coords);
            // Manually clear the paint pending; otherwise the display breaks.
            CanvasPaintPending.removePaintPending(plus_.getCanvas(), plus_);
         }
      } // End while loop
   }

   /**
    * Show an image -- set the pixels of the canvas and update the display.
    */
   private void showImage(Image image) {
      // This null check protects us against harmless exception spew when
      // e.g. live mode is running and the user closes the window.
      if (plus_.getProcessor() != null) {
         stack_.setCoords(image.getCoords());
         plus_.getProcessor().setPixels(image.getRawPixels());
         plus_.updateAndDraw();
         displayBus_.post(new PixelsSetEvent(image));
      }
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

   public void stopDisplayUpdates() {
      shouldStop_.set(true);
   }

   /**
    * Add image coordinates for an image to display (i.e. request that the
    * display show the indicated image).
    */
   public void addCoords(Coords coords) {
      try {
         coordsQueue_.put(coords);
      }
      // Ignore being interrupted, since this is not happening in our primary
      // display thread.
      catch (InterruptedException e) {}
   }
}
