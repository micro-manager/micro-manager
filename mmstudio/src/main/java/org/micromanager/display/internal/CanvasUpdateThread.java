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

package org.micromanager.display.internal;

import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.LinkedBlockingQueue;

import javax.swing.SwingUtilities;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;

import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.events.FPSEvent;
import org.micromanager.display.internal.events.DefaultPixelsSetEvent;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class handles the logic related to updating the image displayed on
 * a DisplayWindow's canvas.
 */
public class CanvasUpdateThread extends Thread {

   private final Datastore store_;
   private final MMVirtualStack stack_;
   private final ImagePlus plus_;
   private final DisplayWindow display_;
   
   private final LinkedBlockingQueue<Coords> coordsQueue_;
   private final AtomicBoolean shouldStop_;
   private int imagesDisplayed_ = 0;
   private long lastImageIndex_ = 0;
   private long lastFPSUpdateTimestamp_ = -1;

   public CanvasUpdateThread(Datastore store, MMVirtualStack stack,
         ImagePlus plus, DisplayWindow display) {
      setName("Canvas display thread for display " + display.getName());
      store_ = store;
      stack_ = stack;
      plus_ = plus;
      display_ = display;
      coordsQueue_ = new LinkedBlockingQueue<Coords>();
      shouldStop_ = new AtomicBoolean(false);
      display_.registerForEvents(this);
   }

   @Override
   public void run() {
      while (!shouldStop_.get()) {
         Coords coords = null;
         // Grab images from the queue until we get the last one, so all
         // others get ignored (because we don't have time to display them).
         while (!coordsQueue_.isEmpty()) {
            coords = coordsQueue_.poll();
         }
         if (coords == null) {
            // Nothing in the queue.
            // We still need to generate an FPS update at regular intervals; we
            // just have to do it without any image coords.
            sendFPSUpdate(null);
            // Then rest for a bit so we aren't busy-waiting.
            try {
               Thread.sleep(20);
            }
            catch (InterruptedException e) {} // Ignore it.
            continue;
         }

         try {
            waitForCanvas();
         }
         catch (InterruptedException e) {
            if (shouldStop_.get()) {
               // Time to stop.
               return;
            }
         }
         // The display may have gone away while we were waiting.
         if (plus_.getCanvas() == null) {
            continue;
         }
         final Image image = store_.getImage(coords);
         if (image == null) {
            // Odd; is this an error situation?
            continue;
         }
         plus_.getCanvas().setPaintPending(true);
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

   /**
    * Wait for the canvas to be available. If we don't do this, then our
    * framerate tanks, possibly because of repaint events piling up in the
    * EDT. It's hard to tell.
    */
   private void waitForCanvas() throws InterruptedException {
      int numTries = 0;
      // TODO: this conditional has a race condition, in that the canvas can
      // become null at any time (e.g. due to the window closing), including
      // between when we ask for the canvas and when we check its paint-pending
      // status.
      while (!shouldStop_.get() && plus_.getCanvas() != null &&
            plus_.getCanvas().getPaintPending()) {
         numTries++;
         if (numTries > 500) {
            // Been a few seconds; force the canvas to stop pending, on
            // the assumption that it's been broken.
            ReportingUtils.logError("Coercing canvas paint pending to false after waiting too long.");
            plus_.getCanvas().setPaintPending(false);
            return;
         }
         Thread.sleep(10);
      }
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
         display_.postEvent(new DefaultPixelsSetEvent(image, display_));
      }
   }

   /**
    * Send an update on our FPS, both data rate and image display rate. Only
    * if it has been at least 500ms since our last update.
    */
   private void sendFPSUpdate(Image image) {
      long curTimestamp = System.currentTimeMillis();
      if (lastFPSUpdateTimestamp_ == -1) {
         // No data to operate on yet.
         lastFPSUpdateTimestamp_ = curTimestamp;
      }
      else if (curTimestamp - lastFPSUpdateTimestamp_ >= 500) {
         // More than 500ms since last update; time to send an update.
         // Hack: if we have null image, then post a "blank" FPS event.
         if (image == null) {
            display_.postEvent(new FPSEvent(0, 0));
         }
         else {
            double elapsedTime = (curTimestamp - lastFPSUpdateTimestamp_) / 1000.0;
            try {
               Long imageIndex = image.getMetadata().getImageNumber();
               if (imageIndex != null) {
                  // HACK: Ignore the first FPS display event, to prevent us
                  // from showing FPS for the Snap window.
                  if (lastImageIndex_ != 0) {
                     display_.postEvent(new FPSEvent((
                              imageIndex - lastImageIndex_) / elapsedTime,
                              imagesDisplayed_ / elapsedTime));
                  }
                  lastImageIndex_ = imageIndex;
               }
            }
            catch (Exception e) {
               // Post a "blank" event. This likely happens because the image
               // image don't contain a sequence number (e.g. during an MDA).
               display_.postEvent(new FPSEvent(0, 0));
            }
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
    * @param coords Coordinates of the image to be displayed.
    */
   public void addCoords(Coords coords) {
      try {
         coordsQueue_.put(coords);
      }
      // Ignore being interrupted, since this is not happening in our primary
      // display thread.
      catch (InterruptedException e) {}
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      try {
         stopDisplayUpdates();
         display_.unregisterForEvents(this);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error cleaning up display thread");
      }
   }
}
