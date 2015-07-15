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
import org.micromanager.display.NewImagePlusEvent;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;
import org.micromanager.display.internal.events.FPSEvent;
import org.micromanager.display.internal.events.DefaultPixelsSetEvent;

import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class handles the queue of images that have been requested to be drawn.
 * The queue is necessary so that a large influx of draw requests (e.g. due
 * to a slow computer or a high rate of data acquisition) do not cause the
 * display to become bogged down: we use the queue to effectively reject
 * all but the newest display request. The workflow is as follows:
 * 1) Client code calls DisplayWindow.setDisplayedImageTo() to choose the
 *    displayed image.
 * 2) setDisplayedImageTo() calls CanvasUpdateQueue.enqueue() to add the
 *    image to the queue of images waiting to be displayed.
 * 3) enqueue() adds the image to the queue, and then adds an event to the
 *    Event Dispatch Thread (EDT) to call consumeImages()
 * 4) consumeImages() pulls the most recent image off of the queue (throwing
 *    away all older images in the process), draws it, and posts a
 *    PixelsSetEvent (to allow other code to draw things) and a
 *    CanvasDoneDrawingEvent afterwards (to notify that drawing is complete).
 *    Then, if the queue is not empty, it adds an event to the EDT to draw
 *    itself.
 */
public class CanvasUpdateQueue {

   private final Datastore store_;
   private final MMVirtualStack stack_;
   private ImagePlus plus_;
   private final DisplayWindow display_;
   private final Runnable consumer_;

   private final Object queueLock_ = new Object();
   private final LinkedBlockingQueue<Coords> coordsQueue_;
   private boolean shouldAcceptNewCoords_ = true;

   public static CanvasUpdateQueue makeQueue(DisplayWindow display,
         MMVirtualStack stack) {
      CanvasUpdateQueue queue = new CanvasUpdateQueue(display, stack);
      display.registerForEvents(queue);
      return queue;
   }

   private CanvasUpdateQueue(DisplayWindow display, MMVirtualStack stack) {
      display_ = display;
      stack_ = stack;
      store_ = display_.getDatastore();
      plus_ = display_.getImagePlus();
      coordsQueue_ = new LinkedBlockingQueue<Coords>();
      consumer_ = new Runnable() {
         @Override
         public void run() {
            consumeImages();
         }
      };
   }

   /**
    * Add an image's coords to the queue, and post a request to the EDT to
    * consume the corresponding image.
    * TODO: hypothetically this could jam up the EDT with consume requests,
    * though in practice they ought to fizzle out as soon as they're called
    * if the queue is empty.
    */
   public void enqueue(Coords coords) {
      if (!shouldAcceptNewCoords_) {
         // Additions are currently blocked
         throw new RuntimeException("Attempted to add images to canvas update queue when it is blocked.");
      }
      try {
         coordsQueue_.put(coords);
      }
      catch (InterruptedException e) {
         // Ignore it.
      }
      SwingUtilities.invokeLater(consumer_);
   }

   /**
    * Draw the most recent image on the queue, if any, and then re-call
    * ourselves if there are still more images to draw once that finishes.
    * Only called on the EDT.
    */
   private void consumeImages() {
      Coords coords = null;
      // Grab images from the queue until we get the last one, so all
      // others get ignored (because we don't have time to display them).
      synchronized(queueLock_) {
         while (!coordsQueue_.isEmpty()) {
            coords = coordsQueue_.poll();
         }
      }
      if (coords == null) {
         // No images in the queue; nothing to do.
         return;
      }
      if (plus_.getCanvas() == null) {
         // The display may have gone away while we were waiting.
         return;
      }
      final Image image = store_.getImage(coords);
      if (image == null) {
         // Odd; is this an error situation?
         return;
      }
      showImage(image);
   }

   @Subscribe
   public void onCanvasDrawComplete(CanvasDrawCompleteEvent event) {
      if (!coordsQueue_.isEmpty()) {
         // New image(s) arrived while we were drawing; repeat.
         SwingUtilities.invokeLater(consumer_);
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
    * Wait for the coords queue to become empty, and block any additions to
    * it.
    */
   public synchronized void halt() {
      shouldAcceptNewCoords_ = false;
      synchronized(queueLock_) {
         coordsQueue_.clear();
      }
   }

   /**
    * Allow additions to the coords queue again. Since halt() empties the queue
    * and may potentially result in there being nothing drawn at all, we add a
    * redraw now.
    */
   public void resume() {
      shouldAcceptNewCoords_ = true;
      display_.requestRedraw();
   }

   @Subscribe
   public void onNewImagePlus(NewImagePlusEvent event) {
      plus_ = event.getImagePlus();
   }
}
