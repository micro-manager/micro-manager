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
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.NewImageEvent;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.NewDisplaySettingsEvent;
import org.micromanager.display.NewImagePlusEvent;

import org.micromanager.data.internal.DefaultCoords;

import org.micromanager.display.internal.events.LUTUpdateEvent;
import org.micromanager.display.internal.link.ContrastEvent;
import org.micromanager.display.internal.link.ContrastLinker;
import org.micromanager.display.internal.ChannelSettings;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.DisplayDestroyedEvent;
import org.micromanager.display.internal.inspector.HistogramsPanel;
import org.micromanager.display.internal.MMCompositeImage;
import org.micromanager.display.internal.MMVirtualStack;
import org.micromanager.display.internal.LUTMaster;

import org.micromanager.internal.utils.HistogramUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This is the model for a single channel's histogram. It contains the
 * information that the ChannelControlPanel class makes use of. It's also
 * responsible for actually making changes to how the image is displayed,
 * though it does not handle drawing the histogram.
 */
public class ChannelHistogramModel {

   public static final int NUM_BINS = 256;
   private final int channelIndex_;
   private final Datastore store_;
   private DisplayWindow display_;
   private final MMVirtualStack stack_;
   private ImagePlus plus_;
   private CompositeImage composite_;

   private double binSize_;
   private String histMaxLabel_;
   private int histMax_;
   private Integer contrastMin_ = -1;
   private Integer contrastMax_ = -1;
   private Double gamma_ = 1.0;
   private Boolean isFirstLUTUpdate_ = true;
   private int minAfterRejectingOutliers_;
   private int maxAfterRejectingOutliers_;
   private int pixelMin_ = 0;
   private int pixelMax_ = 255;
   private int pixelMean_ = 0;
   private int maxIntensity_;
   private int bitDepth_;
   private Color color_;
   private String name_;
   private final AtomicBoolean haveInitialized_;

   public ChannelHistogramModel(int channelIndex,
         Datastore store, DisplayWindow display, MMVirtualStack stack,
         ImagePlus plus) {
      haveInitialized_ = new AtomicBoolean(false);
      channelIndex_ = channelIndex;
      store_ = store;
      display_ = display;
      stack_ = stack;
      setImagePlus(plus);

      name_ = store_.getSummaryMetadata().getSafeChannelName(channelIndex_);

      // Must be registered for events before we start modifying images, since
      // that relies on LUTUpdateEvent.
      store.registerForEvents(this);
      display.registerForEvents(this);
      // This won't be available until there's at least one image in the 
      // Datastore for our channel.
      bitDepth_ = -1;
      List<Image> images = store_.getImagesMatching(
            new DefaultCoords.Builder().channel(channelIndex_).build()
      );
      if (images != null && images.size() > 0) {
         // Found an image for our channel
         bitDepth_ = images.get(0).getMetadata().getBitDepth();
         initialize();
      }
   }

   private void setImagePlus(ImagePlus plus) {
      plus_ = plus;

      // We may be for a single-channel system or a multi-channel one; the two
      // require different backing objects (ImagePlus vs. CompositeImage).
      // Hence why we have both the plus_ and composite_ objects, and use the
      // appropriate one depending on context.
      if (plus_ instanceof CompositeImage) {
         composite_ = (CompositeImage) plus_;
      }
   }

   private void initialize() {
      maxIntensity_ = (int) Math.pow(2, bitDepth_) - 1;
      histMax_ = maxIntensity_ + 1;
      binSize_ = histMax_ / NUM_BINS;
      histMaxLabel_ = "" + histMax_;
      reloadDisplaySettings();
      // Default to "camera depth" mode.
      int index = 0;

      haveInitialized_.set(true);
   }

   // Update the maximum value of the histogram to be the specified power of 2.
   public void updateHistMax(int power) {
      if (power == 0) {
         power = bitDepth_;
      }
      // Update the histogram display.
      histMax_ = (int) (Math.pow(2, power) - 1);
      if (power == 0) {
         // User selected the "Camera depth" option.
         histMax_ = maxIntensity_;
      }
      binSize_ = ((double) (histMax_ + 1)) / ((double) NUM_BINS);
      histMaxLabel_ = histMax_ + "";
//      updateHistogram();
//      calcAndDisplayHistAndStats(true);
   }

   private void postContrastEvent(DisplaySettings newSettings) {
      String[] channelNames = display_.getDatastore().getSummaryMetadata().getChannelNames();
      String name = null;
      if (channelNames != null && channelNames.length > channelIndex_) {
         name = channelNames[channelIndex_];
      }
      display_.postEvent(new ContrastEvent(channelIndex_, name, newSettings));
   }

   /**
    * Return whether or not the channel is currently being drawn in the
    * display.
    */
   public boolean getChannelEnabled() {
      if (composite_ != null) {
         if (((MMCompositeImage) composite_).getNChannelsUnverified() <= 7) {
            boolean active = composite_.getActiveChannels()[channelIndex_];
            if (!active) {
               return false;
            }
         }
         if (((MMCompositeImage) composite_).getMode() != CompositeImage.COMPOSITE &&
               composite_.getChannel() - 1 != channelIndex_) {
            return false;
         }
      }
      return true;
   }

   public void setChannelEnabled(boolean isEnabled) {
      boolean[] active = composite_.getActiveChannels();
      if (composite_.getMode() != CompositeImage.COMPOSITE) {
         // Change which channel the stack is pointing at.
         stack_.setCoords(stack_.getCurrentImageCoords().copy().channel(channelIndex_).build());
         composite_.updateAndDraw();
      }
      active[channelIndex_] = isEnabled;
      composite_.updateAndDraw();
   }

   public void setFullScale() {
      int origMin = contrastMin_;
      int origMax = contrastMax_;
      contrastMin_ = 0;
      contrastMax_ = histMax_;
      // Only send an event if we actually changed anything; otherwise we get
      // into an infinite loop.
      if (origMin != contrastMin_ || origMax != contrastMax_) {
         postNewSettings();
      }
      applyLUT(true);
   }

   public void autostretch() {
      int origMin = contrastMin_;
      int origMax = contrastMax_;

      contrastMin_ = pixelMin_;
      contrastMax_ = pixelMax_;
      if (pixelMin_ == pixelMax_) {
          if (pixelMax_ > 0) {
              contrastMin_--;
          } else {
              contrastMax_++;
          }
      }
      contrastMin_ = Math.max(0,
            Math.max(contrastMin_, minAfterRejectingOutliers_));
      contrastMax_ = Math.min(contrastMax_, maxAfterRejectingOutliers_);
      if (contrastMax_ <= contrastMin_) {
          if (contrastMax_ > 0) {
              contrastMin_ = contrastMax_ - 1;
          } else {
              contrastMax_ = contrastMin_ + 1;
          }
      }
      // Only send an event if we actually changed anything.
      if (origMin != contrastMin_ || origMax != contrastMax_) {
         postNewSettings();
      }
      applyLUT(true);
   }

   /**
    * Pull new color and contrast settings from our DisplayWindow's
    * DisplaySettings, or from the ChannelSettings for our channel name if our
    * DisplaySettings are deficient.
    * The priorities for values here are:
    * 1) If single-channel, then we use a white color
    * 2) Use the values in the display settings
    * 3) If those values aren't available, use the values in the
    *    ChannelSettings for our channel name.
    * 4) If *those* values aren't available, use hardcoded defaults.
    */
   public void reloadDisplaySettings() {
      DisplaySettings settings = display_.getDisplaySettings();
      // HACK: use a color based on the channel index: specifically, use the
      // colorblind-friendly color set.
      Color defaultColor = Color.WHITE;
      if (channelIndex_ < HistogramsPanel.COLORBLIND_COLORS.length) {
         defaultColor = HistogramsPanel.COLORBLIND_COLORS[channelIndex_];
      }
      ChannelSettings channelSettings = ChannelSettings.loadSettings(
            name_, store_.getSummaryMetadata().getChannelGroup(),
            defaultColor, contrastMin_, contrastMax_, true);

      contrastMin_ = channelSettings.getHistogramMin();
      Integer[] mins = settings.getChannelContrastMins();
      if (mins != null && mins.length > channelIndex_ &&
            mins[channelIndex_] != null) {
         contrastMin_ = mins[channelIndex_];
      }

      contrastMax_ = channelSettings.getHistogramMax();
      Integer[] maxes = settings.getChannelContrastMaxes();
      if (maxes != null && maxes.length > channelIndex_ &&
            maxes[channelIndex_] != null) {
         contrastMax_ = maxes[channelIndex_];
      }

      // TODO: gamma not stored in channel settings.
      Double[] gammas = settings.getChannelGammas();
      if (gammas != null && gammas.length > channelIndex_ &&
            gammas[channelIndex_] != null) {
         gamma_ = gammas[channelIndex_];
      }

      // TODO: no autoscale checkbox for individual channels, so can't apply
      // the autoscale property of ChannelSettings.

      // Use the ChannelSettings value (which incorporates the hardcoded
      // default when no color is set), or override with DisplaySettings
      // if available.
      color_ = channelSettings.getColor();
      Color[] colors = settings.getChannelColors();
      if (colors != null && colors.length > channelIndex_ &&
            colors[channelIndex_] != null) {
         color_ = colors[channelIndex_];
      }

      if (contrastMin_ == -1 || contrastMax_ == -1) {
         // Invalid settings; we'll have to autoscale.
         autostretch();
      }

      saveChannelSettings();
      applyLUT(true);
   }

   /**
    * Save our current settings into the profile, so they'll be used by default
    * by future displays for our channel name/group.
    */
   private void saveChannelSettings() {
      // HACK: because we override colors to white for singlechannel displays,
      // we need to try to "recover" the actual color from the profile or
      // display settings here; otherwise the first channel in every new
      // acquisition will always be white.
      Color color = color_;
      if (display_.getDatastore().getAxisLength(Coords.CHANNEL) == 1) {
         ChannelSettings channelSettings = ChannelSettings.loadSettings(
               name_, store_.getSummaryMetadata().getChannelGroup(),
               null, -1, -1, true);
         color = channelSettings.getColor();
         if (color == null) {
            Color[] colors = display_.getDisplaySettings().getChannelColors();
            if (colors != null && colors.length > channelIndex_) {
               color = colors[channelIndex_];
            }
         }
      }
      if (color == null) {
         color = Color.WHITE;
      }
      // TODO: no per-channel autoscale controls, so defaulting to true here.
      ChannelSettings settings = new ChannelSettings(name_,
            store_.getSummaryMetadata().getChannelGroup(),
            color, contrastMin_, contrastMax_, true);
      settings.saveToProfile();
   }

   public String getName() {
      return name_;
   }

   public String getHistMaxLabel() {
      return histMaxLabel_;
   }

   public int getBitDepth() {
      return bitDepth_;
   }

   public Color getColor() {
      return color_;
   }

   public void setColor(Color color) {
      color_ = color;
      applyLUT(true);
      postNewSettings();
   }

   public int getContrastMin() {
      return contrastMin_;
   }

   public void setContrastMin(int min) {
      disableAutostretch();
      contrastMin_ = min;
      sanitizeRange();
      applyLUT(true);
      postNewSettings();
   }

   public int getContrastMax() {
      return contrastMax_;
   }

   public void setContrastMax(int max) {
      disableAutostretch();
      contrastMax_ = max;
      sanitizeRange();
      applyLUT(true);
      postNewSettings();
   }

   public double getContrastGamma() {
      return gamma_;
   }

   public void setContrastGamma(double gamma) {
      if (gamma <= 0) {
         return;
      }
      gamma_ = gamma;
      if (0.9 <= gamma_ && gamma_ <= 1.1) {
         // Lock to 1.0.
         gamma_ = 1.0;
      }
      applyLUT(true);
      postNewSettings();
   }

   public void setContrast(int min, int max, double gamma) {
      contrastMin_ = min;
      contrastMax_ = max;
      gamma_ = gamma;
      sanitizeRange();
      applyLUT(true);
      postNewSettings();
   }

   public int getHistRangeIndex() {
      return display_.getDisplaySettings().getSafeBitDepthIndex(channelIndex_,
            0);
   }

   public double getBinSize() {
      return binSize_;
   }

   private void sanitizeRange() {
      if (contrastMax_ > maxIntensity_ ) {
         contrastMax_ = maxIntensity_;
      }
      if (contrastMax_ < 0) {
         contrastMax_ = 0;
      }
      if (contrastMin_ > contrastMax_) {
         contrastMin_ = contrastMax_;
      }
   }

   @Subscribe
   public void onLUTUpdate(LUTUpdateEvent event) {
      try {
         boolean didChange = false;
         Integer eventMin = event.getMin();
         Integer eventMax = event.getMax();
         Double eventGamma = event.getGamma();
         // Receive new settings from the event, if applicable.
         if (eventMin != null && eventMin != contrastMin_) {
            contrastMin_ = eventMin;
            didChange = true;
         }
         if (eventMax != null && eventMax != contrastMax_) {
            contrastMax_ = eventMax;
            didChange = true;
         }
         if (eventGamma != null && eventGamma != gamma_) {
            gamma_ = eventGamma;
            didChange = true;
         }

         if (isFirstLUTUpdate_) {
            // Haven't initialized contrast yet; do so by autostretching.
            autostretch();
            isFirstLUTUpdate_ = false;
         }
         if (didChange) {
            applyLUT(true);
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error updating LUT");
      }
   }

   public int getChannelIndex() {
      return channelIndex_;
   }

   public int[] calcHistogramStats() {
      if (plus_ == null || plus_.getProcessor() == null) {
         // No image to work with.
         return null;
      }
      ImageProcessor processor;
      if (composite_ == null) {
         // Single-channel mode.
         processor = plus_.getProcessor();
      }
      else {
         // Multi-channel mode.
         if (composite_.getMode() == CompositeImage.COMPOSITE) {
            processor = composite_.getProcessor(channelIndex_ + 1);
            if (processor != null) {
               processor.setRoi(composite_.getRoi());
            }
         } else {
            MMCompositeImage ci = (MMCompositeImage) composite_;
            int flatIndex = 1 + channelIndex_ + 
                  (composite_.getSlice() - 1) * ci.getNChannelsUnverified() +
                  (composite_.getFrame() - 1) * ci.getNSlicesUnverified() * ci.getNChannelsUnverified();
            processor = composite_.getStack().getProcessor(flatIndex);
         }
      }
      if (processor == null ) {
         // Tried to get an image that doesn't exist.
         return null;
      }

      int[] rawHistogram = processor.getHistogram();
      int imgWidth = plus_.getWidth();
      int imgHeight = plus_.getHeight();

      if (rawHistogram[0] == imgWidth * imgHeight) {
         // Image data is invalid/blank.
         return null;
      }

      DisplaySettings settings = display_.getDisplaySettings();
      // Determine what percentage of the histogram range to autotrim.
      maxAfterRejectingOutliers_ = rawHistogram.length;
      int totalPoints = imgHeight * imgWidth;
      Double extremaPercentage = settings.getExtremaPercentage();
      if (extremaPercentage == null) {
         extremaPercentage = 0.0;
      }
      HistogramUtils hu = new HistogramUtils(rawHistogram, totalPoints, 
            0.01 * extremaPercentage);
      minAfterRejectingOutliers_ = hu.getMinAfterRejectingOutliers();
      maxAfterRejectingOutliers_ = hu.getMaxAfterRejectingOutliers();

      pixelMin_ = -1;
      pixelMax_ = 0;
      pixelMean_ = (int) plus_.getStatistics(ImageStatistics.MEAN).mean;

      int numBins = (int) Math.min(rawHistogram.length / binSize_, NUM_BINS);
      int[] histogram = new int[NUM_BINS];
      int total = 0;
      for (int i = 0; i < numBins; i++) {
         histogram[i] = 0;
         for (int j = 0; j < binSize_; j++) {
            int rawHistIndex = (int) (i * binSize_ + j);
            int rawHistVal = rawHistogram[rawHistIndex];
            histogram[i] += rawHistVal;
            if (rawHistVal > 0) {
               pixelMax_ = rawHistIndex;
               if (pixelMin_ == -1) {
                  pixelMin_ = rawHistIndex;
               }
            }
         }
         total += histogram[i];
         if (settings.getShouldUseLogScale() != null &&
               settings.getShouldUseLogScale()) {
            histogram[i] = histogram[i] > 0 ? (int) (1000 * Math.log(histogram[i])) : 0;
         }
      }
      //Make sure max has correct value is hist display mode isnt auto
      // TODO: what does the above comment mean?
      pixelMin_ = rawHistogram.length-1;
      for (int i = rawHistogram.length-1; i > 0; i--) {
         if (rawHistogram[i] > 0 && i > pixelMax_ ) {
            pixelMax_ = i;
         }
         if (rawHistogram[i] > 0 && i < pixelMin_ ) {
            pixelMin_ = i;
         }
      }

      // Autostretch, if necessary.
      if (settings.getShouldAutostretch() != null &&
            settings.getShouldAutostretch()) {
         autostretch();
      }

      // work around what is apparently a bug in ImageJ
      if (total == 0) {
         if (plus_.getProcessor().getMin() == 0) {
            histogram[0] = imgWidth * imgHeight;
         } else {
            histogram[numBins - 1] = imgWidth * imgHeight;
         }
      }
      return histogram;
   }
   
   /**
    * Update our parent's DisplaySettings, post a ContrastEvent, and save
    * our new settings to the profile (via the ChannelSettings).
    */
   private void postNewSettings() {
      if (!haveInitialized_.get()) {
         // Don't send out anything until after we're done initializing, since
         // our settings are probably in an inconsistent state right now.
         return;
      }
      DisplaySettings settings = display_.getDisplaySettings();
      DisplaySettings.DisplaySettingsBuilder builder = settings.copy();

      Object[] channelSettings = DefaultDisplaySettings.getPerChannelArrays(settings);
      // TODO: ordering of these values is closely tied to the above function!
      Object[] ourParams = new Object[] {color_, contrastMin_, contrastMax_,
         gamma_};
      // For each of the above parameters, ensure that there's an array in
      // the display settings that's at least long enough to hold our channel,
      // and that our values are represented in that array.
      for (int i = 0; i < channelSettings.length; ++i) {
         Object[] oldVals = DefaultDisplaySettings.makePerChannelArray(i,
               (Object[]) channelSettings[i], channelIndex_ + 1);
         // HACK: for the specific case of a single-channel setup, our nominal
         // color is white -- but we should not force this into the
         // DisplaySettings as our color should change if a new channel is
         // added.
         if (!(oldVals instanceof Color[]) ||
               store_.getAxisLength(Coords.CHANNEL) > 1) {
            oldVals[channelIndex_] = ourParams[i];
         }
         DefaultDisplaySettings.updateChannelArray(i, oldVals, builder);
      }
      settings = builder.build();
      display_.setDisplaySettings(settings);
      saveChannelSettings();
      postContrastEvent(settings);
   }

   /**
    * We provide the boolean mostly so that we don't get into cyclic draw
    * events when our drawing code calls this method.
    */
   public void applyLUT(boolean shouldRedisplay) {
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getShouldSyncChannels() != null &&
            settings.getShouldSyncChannels()) {
         display_.postEvent(
               new LUTUpdateEvent(contrastMin_, contrastMax_, gamma_));
      } else {
         display_.postEvent(new LUTUpdateEvent(null, null, null));
      }
      if (shouldRedisplay) {
         LUTMaster.updateDisplayLUTs(display_);
      }
   }

   public void disableAutostretch() {
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getShouldAutostretch() != null &&
            settings.getShouldAutostretch()) {
         display_.setDisplaySettings(display_.getDisplaySettings().copy().shouldAutostretch(false).build());
      }
   }

   /**
    * Display settings have changed; update our color.
    * @param event
    */
   @Subscribe
   public void onNewDisplaySettings(NewDisplaySettingsEvent event) {
      if (!haveInitialized_.get()) {
         // TODO: there's a race condition here if we've already set the
         // values that reloadDisplaySettings() modify -- if
         // they aren't yet available, though, then that method will fail.
         return;
      }
      try {
         reloadDisplaySettings();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to update histogram display settings");
      }
   }

   @Subscribe
   public void onNewImagePlus(NewImagePlusEvent event) {
      setImagePlus(event.getImagePlus());
   }

   /**
    * A new image has arrived; if it's for our channel and we haven't set bit
    * depth yet, then do so now.
    * @param event
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      try {
         int channel = event.getCoords().getChannel();
         if (bitDepth_ == -1 && channel == channelIndex_) {
            bitDepth_ = event.getImage().getMetadata().getBitDepth();
            initialize();
         }
         // Only reload display settings if we really have to, as this forces
         // redraws which can slow the display way down.
         Color targetColor = display_.getDisplaySettings().getSafeChannelColor(
               channelIndex_, null);
         if (!color_.equals(targetColor)) {
            reloadDisplaySettings();
         }
         applyLUT(true);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error handling new image in histogram for channel " + channelIndex_);
      }
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      try {
         cleanup();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error when cleaning up histogram");
      }
   }

   public void cleanup() {
      store_.unregisterForEvents(this);
      if (display_ != null) {
         try {
            display_.unregisterForEvents(this);
         }
         catch (IllegalArgumentException e) {
            // We were already unregistered because cleanup() was called
            // from HistogramsPanel after it was called from
            // onDisplayDestroyed; ignore it.
         }
      }
   }
}
