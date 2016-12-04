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
import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.ColorProcessor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.LinkedBlockingQueue;
import javax.swing.SwingUtilities;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.HistogramData;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.NewHistogramsEvent;
import org.micromanager.display.internal.events.CanvasDrawCompleteEvent;
import org.micromanager.display.internal.events.DefaultPixelsSetEvent;
import org.micromanager.display.internal.events.HistogramRecalcEvent;
import org.micromanager.display.internal.events.HistogramRequestEvent;
import org.micromanager.display.internal.link.ContrastEvent;
import org.micromanager.internal.utils.ImageUtils;
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
 *    away all older images in the process), recalculates histograms if
 *    necessary (posting a NewHistogramsEvent), draws it, and posts a
 *    PixelsSetEvent (to allow other code to draw things) and a
 *    CanvasDoneDrawingEvent afterwards (to notify that drawing is complete).
 *    Then, if the queue is not empty, it adds an event to the EDT to draw
 *    itself, thus starting the process of drawing new images again.
 */
public final class CanvasUpdateQueue {

   /**
    * Simple class for tracking our history with respect to calculating
    * histograms for a single channel.
    */
   private static class HistogramHistory {
      ArrayList<HistogramData> datas_ = null;
      long lastUpdateTime_ = 0;
      Timer timer_ = null;
      boolean needsUpdate_ = true;
      UUID imageUUID_ = null;
      int coordsHash_ = 0;
      DisplaySettings.ContrastSettings contrast_ = null;
      public HistogramHistory() {
         datas_ = new ArrayList<HistogramData>();
      }
   }

   private final DefaultDisplayWindow display_;
   private final MMVirtualStack stack_;

   // Coords of images to be drawn next. Only the last enqueued image is drawn,
   // and the queue is drained when scheduling a repaint.
   private final LinkedBlockingQueue<Coords> coordsQueue_;

   private volatile boolean shouldAcceptNewCoords_;
   private final Object shouldAcceptNewCoordsLock_;
   private final HashMap<Integer, HistogramHistory> channelToHistory_;

   // We use (through ImageJ's ImageCanvas) the normal way of drawing in
   // Swing/AWT, which is to issue a repaint request, which is enqueued on the
   // EDT to be processed later (as opposed to eagerly drawing). However, we
   // want to ensure we don't issue repaint requests when one is already
   // pending, because this can cause the EDT to become unresponsive when the
   // frame rate is not otherwise regulated. This flag indicates that a repaint
   // request is currently pending.
   private boolean isCanvasPaintPending_ = false; // Always accessed from EDT

   private boolean shouldReapplyLUTs_ = true;
   // This boolean is set when we call setDisplaySettings(), so we don't
   // erroneously respond to our own new display settings by redrawing
   // everything.
   private boolean amSettingDisplaySettings_;
   // These parameters are used to determine if we need to recalculate
   // histograms when display settings are changed.
   private Double cachedExtremaPercentage_ = null;
   private Boolean cachedShouldCalculateStdDev_ = null;
   private Boolean cachedShouldAutostretch_ = null;
   private Boolean cachedShouldScaleWithROI_ = null;

   public static CanvasUpdateQueue makeQueue(DefaultDisplayWindow display,
         MMVirtualStack stack) {
      CanvasUpdateQueue queue = new CanvasUpdateQueue(display, stack);
      display.registerForEvents(queue);
      return queue;
   }

   private CanvasUpdateQueue(DefaultDisplayWindow display,
         MMVirtualStack stack) {
      display_ = display;
      stack_ = stack;
      coordsQueue_ = new LinkedBlockingQueue<Coords>();
      channelToHistory_ = new HashMap<Integer, HistogramHistory>();
      shouldAcceptNewCoords_ = true;
      shouldAcceptNewCoordsLock_ = new Object();
   }

   /**
    * Add an image's coords to the queue, and post a request to the EDT to
    * consume the corresponding image.
    * TODO: hypothetically this could jam up the EDT with consume requests,
    * though in practice they ought to fizzle out as soon as they're called
    * if the queue is empty.
    * @param coords Coords of newly added image
    */
   public void enqueue(Coords coords) {
      // We synchronize here even though the boolean and queue are each thread
      // safe.  We just want to be sure that the state of the boolean does not
      // change while we interpret it
      synchronized (shouldAcceptNewCoordsLock_) {
         if (!shouldAcceptNewCoords_) {
            return;
         }
         try {
            coordsQueue_.put(coords);
         } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
         }
      }
      SwingUtilities.invokeLater(new Runnable() {
         @Override
         public void run() {
            consumeImages();
         }
      });
   }

   /**
    * Draw the most recent image on the queue (or images when in composite
    * mode), if any. Only called on the EDT.
    */
   private void consumeImages() {
      if (!shouldAcceptNewCoords_) {
         // We are halted; display and image info may no longer be available
         return;
      }
      synchronized (this) {
         if (isCanvasPaintPending_) {
            // A repaint is currently pending on the EDT, so avoid triggering
            // another repaint. A new invocation of consumeImage() will be
            // scheduled when the repaint completes.
            return;
         }

         try {
            Datastore store = display_.getDatastore();
            ImagePlus plus = display_.getImagePlus();
            // Depending on if we're in composite view or not, we may need to draw
            // multiple images or just the most recent image.
            boolean isComposite = (plus instanceof CompositeImage
                    && ((CompositeImage) plus).getMode() == CompositeImage.COMPOSITE);
            Coords lastCoords = null;
            // Grab images from the queue until we get the last one, so all
            // others get ignored (because we don't have time to display them).
            while (!coordsQueue_.isEmpty()) {
               lastCoords = coordsQueue_.poll();
            }
            if (lastCoords == null) {
               // No images in the queue; nothing to do.
               return;
            }
            if (plus == null || plus.getCanvas() == null) {
               // The display may have gone away while we were waiting.
               return;
            }
            DisplaySettings settings = display_.getDisplaySettings();
            if (isComposite) {
               // In composite view mode, we need to update the images for every
               // channel.
               // TODO BUG We should update the histograms for all channels even
               // if we are not in composite view mode?
               for (int ch = 0; ch < store.getAxisLength(Coords.CHANNEL); ++ch) {
                  Coords coords = lastCoords.copy().channel(ch).build();
                  if (!settings.getSafeIsVisible(coords.getChannel(), true)) {
                     // Channel isn't visible, so no need to do anything with it.
                     // TODO BUG What if none of the channels are visible?
                     continue;
                  }
                  // TODO BUG What if none of the visible channels have an image?
                  if (store.hasImage(coords)) {
                     Image image = store.getImage(coords);
                     // TODO That this check was found to be necessary suggests
                     // that datastores have a race condition.
                     if (image != null) {
                        showImage(image);
                     } else {
                        ReportingUtils.logError("Unexpected null image at " + coords);
                     }
                  }
               }
            } // TODO BUG If there is no image, we should draw nothing instead of
            // keeping the previously drawn image
            else if (store.hasImage(lastCoords)) {
               Image image = store.getImage(lastCoords);
               // TODO That this check was found to be necessary suggests that
               // datastores have a race condition.
               if (image != null) {
                  showImage(image);
               } else {
                  ReportingUtils.logError("Unexpected null image at " + lastCoords);
               }
            }
         } catch (Exception e) {
            ReportingUtils.logError(e, "Error consuming images");
         }
      }
   }

   @Subscribe
   public void onCanvasDrawComplete(CanvasDrawCompleteEvent event) {
      synchronized (this) {
         isCanvasPaintPending_ = false;
         notify();
      }
      if (!coordsQueue_.isEmpty()) {
         // New image(s) arrived while we were drawing; repeat.
         SwingUtilities.invokeLater(new Runnable() {
            @Override
            public void run() {
               consumeImages();
            }
         });
      }
   }

   /**
    * Show an image -- set the pixels of the canvas and update the display.
    *
    * Always called on the EDT.
    */
   private synchronized void showImage(Image image) {
      ImagePlus plus = display_.getImagePlus();
      if (plus.getProcessor() == null) {
         // Display went away since we last checked.
         return;
      }
      isCanvasPaintPending_ = true;
      stack_.setCoords(image.getCoords());
      Object pixels = image.getRawPixels();
      // If we have an RGB byte array, we need to convert it to an
      // int array for ImageJ's consumption.
      if (plus.getProcessor() instanceof ColorProcessor
            && pixels instanceof byte[]) {
         pixels = ImageUtils.convertRGB32BytesToInt(
               (byte[]) pixels);
      }
      plus.getProcessor().setPixels(pixels);

      // Recalculate histogram data, if necessary (because the image
      // is different from the last one we've calculated or because we
      // need to force an update).
      int channel = image.getCoords().getChannel();
      if (!channelToHistory_.containsKey(channel)) {
         HistogramHistory history = new HistogramHistory();
         channelToHistory_.put(channel, history);
      }
      HistogramHistory history = channelToHistory_.get(channel);
      // We check two things to determine if this is the last image
      // we drew:
      // - UUID: should be unique per-image but can be duplicated if
      //   Metadata objects are copied.
      // - Coords hash: uniquely identifies a coordinate location, but
      //   it is allowed to replace images in Datastores so this does not
      //   mean that the image itself is the same.
      // Note that we do NOT check the image's hash, as disk-based
      // storage systems may create a new Image object every time
      // Datastore.getImage() is called, which would have a different
      // hash code.
      UUID oldUUID = history.imageUUID_;
      UUID newUUID = image.getMetadata().getUUID();
      boolean uuidMatch = (oldUUID == null && newUUID == null) ||
         (oldUUID != null && newUUID != null && oldUUID.equals(newUUID));
      if (history.needsUpdate_ || !uuidMatch ||
            history.coordsHash_ != image.getCoords().hashCode()) {
         scheduleHistogramUpdate(image, history);
         DisplaySettings settings = display_.getDisplaySettings();
         // After a histogram update, we may need to reapply LUTs.
         // TODO: This is pointless in situations where the histogram
         // update rate isn't "every image".
         shouldReapplyLUTs_ = (shouldReapplyLUTs_ ||
               (settings.getShouldAutostretch() != null &&
                settings.getShouldAutostretch()));
      }
      // RGB images need to have their LUTs reapplied, because the
      // image scaling is encoded into the pixel data. And in other
      // situations we also need to just reapply LUTs now.
      if (shouldReapplyLUTs_ ||
            plus.getProcessor() instanceof ColorProcessor) {
         if (plus.getProcessor() instanceof ColorProcessor) {
            // Create a new snapshot which will be used as a basis for
            // calculating image stats.
            ((ColorProcessor) plus.getProcessor()).snapshot();
         }
         // Must apply LUTs to the display now that it has pixels.
         LUTMaster.initializeDisplay(display_);
         shouldReapplyLUTs_ = false;
      }

      plus.updateAndDraw();
      display_.postEvent(new DefaultPixelsSetEvent(image, display_));
   }

   /**
    * Determine whether or not to recalculate histogram data for the provided
    * image. If we do need to calculate the histogram, we may need to delay
    * it until later.
    */
   private void scheduleHistogramUpdate(final Image image,
         final HistogramHistory history) {
      int channel = image.getCoords().getChannel();

      DisplaySettings settings = display_.getDisplaySettings();
      Double updateRate = settings.getHistogramUpdateRate();
      if (updateRate == null) {
         // Assume we always update.
         updateRate = 0.0;
      }

      if (updateRate > 0 &&
            System.currentTimeMillis() - history.lastUpdateTime_ <= updateRate * 1000) {
         // Calculate histogram sometime in the future. Only if a timer isn't
         // already running for this channel.
         synchronized(history) {
            if (history.timer_ != null) {
               // Already a timer, so don't do anything.
               return;
            }
            history.timer_ = new Timer(
                  "Histogram calculation delay for " + channel);
            TimerTask task = new TimerTask() {
               @Override
               public void run() {
                  updateHistogram(image, history);
               }
            };
            long waitTime = (long) (System.currentTimeMillis() +
               (updateRate * 1000) - history.lastUpdateTime_);
            history.timer_.schedule(task, waitTime);
         }
      }
      else if (updateRate >= 0) {
         // We either always update, or it's been too long since the last
         // update, so do it now.
         updateHistogram(image, history);
      }
      // Do not update if updateRate < 0   
   }

   /**
    * Generate new HistogramDatas for the provided image, and post a
    * NewHistogramsEvent.
    */
   private void updateHistogram(Image image, HistogramHistory history) {
      DisplaySettings settings = display_.getDisplaySettings();
      // HACK: if there's no valid channel axis, then use a coordinate of 0.
      int channel = Math.max(0, image.getCoords().getChannel());
      // If autostretch is on, then we need to apply our newly-calculated
      // values to the display contrast settings.
      boolean shouldUpdate = (settings.getShouldAutostretch() != null &&
            settings.getShouldAutostretch());
      Integer[] mins = new Integer[image.getNumComponents()];
      Integer[] maxes = new Integer[image.getNumComponents()];
      Double[] gammas = new Double[image.getNumComponents()];
      synchronized(history) {
         history.datas_.clear();
         for (int i = 0; i < image.getNumComponents(); ++i) {
            int bitDepth = settings.getSafeBitDepthIndex(channel, 0);
            if (bitDepth == 0) {
               try {
                  bitDepth = image.getMetadata().getBitDepth();
               }
               catch (NullPointerException e) {
                  switch (image.getBytesPerPixel()) {
                     case 1: bitDepth = 8; break;
                     case 2: bitDepth = 16; break;
                     case 4: bitDepth = 8; break;
                  }
               }
            }
            else {
               // Add 3 to convert from index to power of 2.
               bitDepth += 3;
            }
            // TODO Why a minimum of 256 bins?
            int binPower = Math.min(8, bitDepth);
            HistogramData data = ContrastCalculator.calculateHistogramWithSettings(
                  image, display_.getImagePlus(), i, settings);
            history.datas_.add(data);
            if (shouldUpdate) {
               mins[i] = data.getMinIgnoringOutliers();
               maxes[i] = data.getMaxIgnoringOutliers();
               gammas[i] = settings.getSafeContrastGamma(channel, i, 1.0);
            }
         }
         history.imageUUID_ = image.getMetadata().getUUID();
         history.coordsHash_ = image.getCoords().hashCode();
         history.needsUpdate_ = false;
         history.lastUpdateTime_ = System.currentTimeMillis();
         history.contrast_ = settings.getSafeContrastSettings(channel, null);
         display_.postEvent(new NewHistogramsEvent(channel, history.datas_));
         if (shouldUpdate) {
            // Check to see if we actually changed anything: there were no
            // contrast settings previously, or any of the old contrast values
            // doesn't match a new contrast value.
            boolean didChange;
            DisplaySettings.ContrastSettings oldContrast = settings.getSafeContrastSettings(channel, null);
            if (oldContrast == null) {
               didChange = true;
            }
            else {
               Integer[] oldMins = oldContrast.getContrastMins();
               Integer[] oldMaxes = oldContrast.getContrastMaxes();
               Double[] oldGammas = oldContrast.getContrastGammas();
               if (oldMins == null || oldMaxes == null || oldGammas == null) {
                  didChange = true;
               }
               else {
                  didChange = (!Arrays.equals(mins, oldMins) ||
                        !Arrays.equals(maxes, oldMaxes) ||
                        !Arrays.equals(gammas, oldGammas));
               }
            }
            if (didChange) {
               DisplaySettings.DisplaySettingsBuilder builder = settings.copy();
               builder.safeUpdateContrastSettings(
                     new DefaultDisplaySettings.DefaultContrastSettings(
                        mins, maxes, gammas, true),
                     channel);
               amSettingDisplaySettings_ = true;
               DisplaySettings newSettings = builder.build();
               // Normally calling setDisplaySettings forces a redraw, but
               // we're about to draw the display anyway.
               display_.setDisplaySettings(newSettings, false);
               // And post a contrast event so linked displays also get updated.
               display_.postEvent(new ContrastEvent(channel,
                        display_.getDatastore().getSummaryMetadata().getSafeChannelName(channel),
                        newSettings));
               amSettingDisplaySettings_ = false;
            }
         }
         // Allow future jobs to be scheduled.
         history.timer_ = null;
      }
   }

   /**
    * Wait for any ongoing repaints and stop accepting new coords.
    */
   public void halt() {
      synchronized(shouldAcceptNewCoordsLock_) {
         if (!shouldAcceptNewCoords_) {
            return;
         }
         shouldAcceptNewCoords_ = false;
      }
      synchronized (this) {
         coordsQueue_.clear(); // We will instead redraw when/if we resume

         if (SwingUtilities.isEventDispatchThread()) {
            // If we are on the EDT, consumeImages() will know to stop
            return;
         }

         // If we are not on the EDT (unlikely), we can safely wait for the
         // paint to complete.
         while (isCanvasPaintPending_ || !coordsQueue_.isEmpty()) {
            try {
               wait();
            }
            catch (InterruptedException e) {
               Thread.currentThread().interrupt();
            }
         }
      }
   }

   /**
    * Allow additions to the coords queue again.
    */
   public void resume() {
      synchronized (shouldAcceptNewCoordsLock_) {
         if (shouldAcceptNewCoords_) {
            return;
         }
         shouldAcceptNewCoords_ = true;
      }
      display_.requestRedraw();
   }

   /**
    * Force the display to reapply LUTs. This is used to deal with certain
    * bizarre situations in which the LUTs are "lost" for unknown reasons,
    * defaulting the display to grayscale.
    */
   public void reapplyLUTs() {
      shouldReapplyLUTs_ = true;
      redraw();
   }

   /**
    * Force refresh of the display.
    */
   public void redraw() {
      for (Image image : display_.getDisplayedImages()) {
         // TODO Do we need all coords?
         enqueue(image.getCoords());
      }
   }

   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      // The new settings may have new contrast settings, so check if the
      // contrast settings or other parameters governing histograms have been
      // updated.
      // Only if the new settings didn't originate from us of course.
      if (amSettingDisplaySettings_) {
         return;
      }
      DisplaySettings settings = event.getDisplaySettings();
      boolean shouldForceUpdate_ = false;
      if (!settings.getExtremaPercentage().equals(cachedExtremaPercentage_) ||
            !settings.getShouldCalculateStdDev().equals(cachedShouldCalculateStdDev_) ||
            !settings.getShouldAutostretch().equals(cachedShouldAutostretch_) ||
            !settings.getShouldScaleWithROI().equals(cachedShouldScaleWithROI_) 
         ) {
         shouldForceUpdate_ = true;
      }
      for (int channel : channelToHistory_.keySet()) {
         HistogramHistory history = channelToHistory_.get(channel);
         DisplaySettings.ContrastSettings newContrast = settings.getSafeContrastSettings(channel, null);
         if (shouldForceUpdate_ || history.contrast_ != newContrast) {
            history.needsUpdate_ = true;
         }
      }
      cachedExtremaPercentage_ = settings.getExtremaPercentage();
      cachedShouldCalculateStdDev_ = settings.getShouldCalculateStdDev();
      cachedShouldAutostretch_ = settings.getShouldAutostretch();
      cachedShouldScaleWithROI_ = settings.getShouldScaleWithROI();
      reapplyLUTs();
   }

   /**
    * Someone wants us to recalculate histograms.
    * @param event 
    */
   @Subscribe
   public void onHistogramRecalc(HistogramRecalcEvent event) {
      Integer channel = event.getChannel();
      if (channel != null) {
         channelToHistory_.get(channel).needsUpdate_ = true;
      }
      else {
         // Do it for all channels.
         for (Integer i : channelToHistory_.keySet()) {
            channelToHistory_.get(i).needsUpdate_ = true;
         }
      }
      redraw();
   }

   /**
    * Someone is requesting that the current histograms be posted.
    * @param event
    */
   @Subscribe
   public void onHistogramRequest(HistogramRequestEvent event) {
      int channel = event.getChannel();
      // Find or create a valid HistogramHistory to post.
      boolean haveValidHistory = false;
      HistogramHistory history = null;
      if (channelToHistory_.containsKey(channel)) {
         history = channelToHistory_.get(channel);
         haveValidHistory = history.datas_.size() > 0;
      }
      if (haveValidHistory && history != null) {
         display_.postEvent(new NewHistogramsEvent(channel, history.datas_));
      }
      else {
         // Must calculate a new histogram.
         history = new HistogramHistory();
         Coords coords = display_.getDisplayedImages().get(0).getCoords();
         coords = coords.copy().channel(channel).build();
         Datastore store = display_.getDatastore();
         if (store.hasImage(coords)) {
            // This posts the new histograms.
            updateHistogram(store.getImage(coords), history);
            channelToHistory_.put(channel, history);
         }
      }
   }
}
