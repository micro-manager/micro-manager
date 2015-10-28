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
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
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
   private int histMax_;
   private String histMaxLabel_;
   private Double gamma_ = 1.0;
   private Boolean isFirstLUTUpdate_ = true;
   private int numComponents_;
   private Integer[] contrastMins_ = null;
   private Integer[] contrastMaxes_ = null;
   private int[] minsAfterRejectingOutliers_;
   private int[] maxesAfterRejectingOutliers_;
   private int[] pixelMins_ = null;
   private int[] pixelMaxes_ = null;
   private int[] pixelMeans_ = null;
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
         // Found an image for our channel; set our initial settings from it.
         initialize(images.get(0));
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

   private void initialize(Image image) {
      bitDepth_ = image.getMetadata().getBitDepth();
      numComponents_ = image.getNumComponents();
      contrastMins_ = new Integer[numComponents_];
      contrastMaxes_ = new Integer[numComponents_];
      pixelMins_ = new int[numComponents_];
      pixelMaxes_ = new int[numComponents_];
      pixelMeans_ = new int[numComponents_];
      minsAfterRejectingOutliers_ = new int[numComponents_];
      maxesAfterRejectingOutliers_ = new int[numComponents_];
      for (int i = 0; i < numComponents_; ++i) {
         contrastMins_[i] = -1;
         contrastMaxes_[i] = -1;
         pixelMins_[i] = 0;
         pixelMaxes_[i] = 255;
         pixelMeans_[i] = 0;
         minsAfterRejectingOutliers_[i] = -1;
         maxesAfterRejectingOutliers_[i] = -1;
      }
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
   }

   private void postContrastEvent(DisplaySettings newSettings) {
      String name = display_.getDatastore().getSummaryMetadata().getSafeChannelName(channelIndex_);
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
      // Only send an event if we actually changed anything; otherwise we get
      // into an infinite loop.
      boolean didChange = false;
      for (int i = 0; i < contrastMins_.length; ++i) {
         if (contrastMins_[i] != 0 || contrastMaxes_[i] != histMax_) {
            didChange = true;
         }
         contrastMins_[i] = 0;
         contrastMaxes_[i] = histMax_;
      }
      if (didChange) {
         updateDisplaySettings();
      }
   }

   public void autostretch() {
      // Only send an event if we actually changed anything; otherwise we get
      // into an infinite loop.
      boolean didChange = false;
      for (int i = 0; i < numComponents_; ++i) {
         if (maxesAfterRejectingOutliers_[i] == -1) {
            // Haven't calculated stats yet; do that before autostretching,
            // so that pixelMins_, etc. are set.
            didChange = true;
            calcHistogramStats();
            break;
         }
      }
      for (int i = 0; i < numComponents_; ++i) {
         if (contrastMins_[i] != pixelMins_[i] ||
               contrastMaxes_[i] != pixelMaxes_[i]) {
            didChange = true;
         }
         contrastMins_[i] = pixelMins_[i];
         contrastMaxes_[i] = pixelMaxes_[i];
         contrastMins_[i] = Math.max(0,
               Math.max(contrastMins_[i], minsAfterRejectingOutliers_[i]));
         contrastMaxes_[i] = Math.min(contrastMaxes_[i],
               maxesAfterRejectingOutliers_[i]);
         // Correct for a max that's less than the min.
         if (contrastMins_[i] >= contrastMaxes_[i]) {
            if (contrastMins_[i] == 0) {
               // Bump up the max.
               contrastMaxes_[i] = contrastMins_[i] + 1;
            }
            else {
               contrastMins_[i] = contrastMaxes_[i] - 1;
            }
         }
      }
      // Only send an event if we actually changed anything.
      if (didChange) {
         updateDisplaySettings();
      }
   }

   /**
    * Pull new color and contrast settings from our DisplayWindow's
    * DisplaySettings, or from the RememberedChannelSettings for our channel
    * name if our DisplaySettings are deficient.
    * The priorities for values here are:
    * 1) If single-channel, then we use a white color
    * 2) Use the values in the display settings
    * 3) If those values aren't available, use the values in the
    *    RememberedChannelSettings for our channel name.
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
      RememberedChannelSettings channelSettings = RememberedChannelSettings.loadSettings(
            name_, store_.getSummaryMetadata().getChannelGroup(),
            defaultColor, contrastMins_, contrastMaxes_, true);

      DisplaySettings.ContrastSettings defaults = DefaultDisplayManager
         .getInstance().getContrastSettings(
               channelSettings.getHistogramMins(),
               channelSettings.getHistogramMaxes(),
               new Double[] {gamma_});
      Integer[] newMins = settings.getSafeContrastSettings(channelIndex_,
            defaults).getContrastMins();

      Integer[] newMaxes = settings.getSafeContrastSettings(channelIndex_,
            defaults).getContrastMaxes();
      // Sanity check: stale channel settings could cause our arrays to now
      // be too short. Hence the arraycopy.
      if (newMins.length <= contrastMins_.length) {
         System.arraycopy(newMins, 0, contrastMins_, 0, newMins.length);
      }
      else {
         contrastMins_ = newMins;
      }
      if (newMaxes.length <= contrastMaxes_.length) {
         System.arraycopy(newMaxes, 0, contrastMaxes_, 0, newMaxes.length);
      }
      else {
         contrastMaxes_ = newMaxes;
      }

      // TODO: gamma not stored in channel settings.
      gamma_ = settings.getSafeContrastSettings(channelIndex_,
            defaults).getSafeContrastGamma(0, 1.0);

      // TODO: no autoscale checkbox for individual channels, so can't apply
      // the autoscale property of RememberedChannelSettings.

      // Use the RememberedChannelSettings value (which incorporates the
      // hardcoded default when no color is set), or override with
      // DisplaySettings if available.
      color_ = settings.getSafeChannelColor(channelIndex_,
            channelSettings.getColor());

      for (int i = 0; i < numComponents_; ++i) {
         if (contrastMins_[i] == -1 || contrastMaxes_[i] == -1) {
            // Invalid settings; we'll have to autoscale.
            autostretch();
         }
      }

      saveChannelSettings();
      applyLUT();
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
         RememberedChannelSettings channelSettings = RememberedChannelSettings.loadSettings(
               name_, store_.getSummaryMetadata().getChannelGroup(),
               Color.WHITE, null, null, true);
         color = display_.getDisplaySettings().getSafeChannelColor(
               channelIndex_, channelSettings.getColor());
      }
      if (color == null) {
         color = Color.WHITE;
      }
      // TODO: no per-channel autoscale controls, so defaulting to true here.
      RememberedChannelSettings settings = new RememberedChannelSettings(
            name_, store_.getSummaryMetadata().getChannelGroup(),
            color, contrastMins_, contrastMaxes_, true);
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
      if (color_ != color) {
         color_ = color;
         updateDisplaySettings();
      }
   }

   public int getNumComponents() {
      return numComponents_;
   }

   public int getContrastMin(int component) {
      return contrastMins_[component];
   }

   public void setContrastMin(int component, int min) {
      disableAutostretch();
      if (contrastMins_[component] != min) {
         contrastMins_[component] = min;
         sanitizeRange();
         updateDisplaySettings();
      }
   }

   public int getContrastMax(int component) {
      return contrastMaxes_[component];
   }

   public void setContrastMax(int component, int max) {
      disableAutostretch();
      if (contrastMaxes_[component] != max) {
         contrastMaxes_[component] = max;
         sanitizeRange();
         updateDisplaySettings();
      }
   }

   public double getContrastGamma() {
      return gamma_;
   }

   public void setContrastGamma(double gamma) {
      if (gamma <= 0) {
         return;
      }
      if (gamma_ != gamma) {
         gamma_ = gamma;
         if (0.9 <= gamma_ && gamma_ <= 1.1) {
            // Lock to 1.0.
            gamma_ = 1.0;
         }
         updateDisplaySettings();
      }
   }

   public void setContrast(int component, int min, int max, double gamma) {
      if (contrastMins_[component] != min ||
            contrastMaxes_[component] != max || gamma_ != gamma) {
         contrastMins_[component] = min;
         contrastMaxes_[component] = max;
         gamma_ = gamma;
         sanitizeRange();
         updateDisplaySettings();
      }
   }

   public int getHistRangeIndex() {
      return display_.getDisplaySettings().getSafeBitDepthIndex(channelIndex_,
            0);
   }

   public int getNumBins() {
      return NUM_BINS;
   }

   public double getBinSize() {
      return binSize_;
   }

   private void sanitizeRange() {
      for (int i = 0; i < numComponents_; ++i) {
         if (contrastMaxes_[i] > maxIntensity_) {
            contrastMaxes_[i] = maxIntensity_;
         }
         if (contrastMaxes_[i] < 0) {
            contrastMaxes_[i] = 0;
         }
         if (contrastMins_[i] > contrastMaxes_[i]) {
            contrastMins_[i] = contrastMaxes_[i];
         }
      }
   }

   @Subscribe
   public void onLUTUpdate(LUTUpdateEvent event) {
      if (event.getSource() == this) {
         // We originated this event.
         return;
      }
      try {
         boolean didChange = false;
         Integer[] eventMins = event.getMins();
         Integer[] eventMaxes = event.getMaxes();
         Double eventGamma = event.getGamma();
         // Receive new settings from the event, if applicable.
         if (eventMins != null &&
               !Arrays.deepEquals(eventMins, contrastMins_)) {
            contrastMins_ = eventMins;
            didChange = true;
         }
         if (eventMaxes != null &&
               !Arrays.deepEquals(eventMaxes, contrastMaxes_)) {
            contrastMaxes_ = eventMaxes;
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
            applyLUT();
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error updating LUT");
      }
   }

   public int getChannelIndex() {
      return channelIndex_;
   }

   public int[][] calcHistogramStats() {
      if (plus_ == null || plus_.getProcessor() == null) {
         // No image to work with.
         return null;
      }
      ImageProcessor[] processors = new ImageProcessor[numComponents_];
      ImageProcessor processor = plus_.getProcessor();
      if (processor instanceof ColorProcessor) {
         // Multi-component mode; extract separate processors for each
         // component because ColorProcessor.getHistogram operates on the
         // components together (using a weighting factor to combine them
         // together).
         // Note: we calculate these processors manually from the "snapshot
         // pixels" of the ColorProcessor, because prior adjustments of the
         // ColorProcessor's contrast values directly adjust its backing data,
         // creating potential feedback loops if we calculate stats based on
         // the adjusted data.
         // This is effectively an adjusted copy of
         // ColorProcessor.getChannel().
         ColorProcessor colorProc = (ColorProcessor) processor;
         if (colorProc.getSnapshotPixels() == null) {
            // Create the snapshot (a backup copy of the current pixels) now.
            colorProc.snapshot();
         }
         int[] pixels = (int[]) colorProc.getSnapshotPixels();
         for (int i = 0; i < numComponents_; ++i) {
            ByteProcessor byteProc = new ByteProcessor(processor.getWidth(),
                  processor.getHeight());
            byte[] bytes = (byte[]) byteProc.getPixels();
            for (int j = 0; j < processor.getWidth() * processor.getHeight(); ++j) {
               bytes[j] = (byte) (pixels[j] >> (i * 8));
            }
            processors[i] = byteProc;
         }
      }
      else if (composite_ == null) {
         // Single-component mode.
         processors[0] = processor;
      }
      else {
         // Composite mode; we have one (TODO assumed single-component)
         // processor from this batch.
         if (composite_.getMode() == CompositeImage.COMPOSITE) {
            processors[0] = composite_.getProcessor(channelIndex_ + 1);
            if (processors[0] != null) {
               processors[0].setRoi(composite_.getRoi());
            }
         }
         else {
            MMCompositeImage ci = (MMCompositeImage) composite_;
            int flatIndex = 1 + channelIndex_ + 
                  (composite_.getSlice() - 1) * ci.getNChannelsUnverified() +
                  (composite_.getFrame() - 1) * ci.getNSlicesUnverified() * ci.getNChannelsUnverified();
            processors[0] = composite_.getStack().getProcessor(flatIndex);
         }
      }
      if (processors[0] == null ) {
         // Tried to get an image that doesn't exist.
         return null;
      }

      ArrayList<int[]> histograms = new ArrayList<int[]>();
      for (int i = 0; i < numComponents_; ++i) {
         processors[i].setRoi(processor.getRoi());
         histograms.add(calcStats(i, processors[i]));
      }
      return histograms.toArray(new int[0][]);
   }

   /**
    * Calculate histogram and stat information for a single processor.
    */
   private int[] calcStats(int component, ImageProcessor processor) {
      int[] rawHistogram = processor.getHistogram();
      int imgWidth = plus_.getWidth();
      int imgHeight = plus_.getHeight();

      if (rawHistogram[0] == imgWidth * imgHeight) {
         // Image data is invalid/blank.
         return null;
      }

      DisplaySettings settings = display_.getDisplaySettings();
      // Determine what percentage of the histogram range to autotrim.
      maxesAfterRejectingOutliers_[component] = rawHistogram.length;
      int totalPoints = imgHeight * imgWidth;
      Double extremaPercentage = settings.getExtremaPercentage();
      if (extremaPercentage == null) {
         extremaPercentage = 0.0;
      }
      HistogramUtils hu = new HistogramUtils(rawHistogram, totalPoints,
            0.01 * extremaPercentage);
      minsAfterRejectingOutliers_[component] = hu.getMinAfterRejectingOutliers();
      maxesAfterRejectingOutliers_[component] = hu.getMaxAfterRejectingOutliers();

      pixelMins_[component] = -1;
      pixelMaxes_[component] = 0;
      pixelMeans_[component] = (int) plus_.getStatistics(ImageStatistics.MEAN).mean;

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
               pixelMaxes_[component] = rawHistIndex;
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
      pixelMins_[component] = rawHistogram.length-1;
      for (int i = rawHistogram.length-1; i > 0; i--) {
         if (rawHistogram[i] > 0 && i > pixelMaxes_[component]) {
            pixelMaxes_[component] = i;
         }
         if (rawHistogram[i] > 0 && i < pixelMins_[component]) {
            pixelMins_[component] = i;
         }
      }

      // work around what is apparently a bug in ImageJ
      // TODO: what is the bug in question?
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
    * our new settings to the profile (via the RememberedChannelSettings).
    */
   public void updateDisplaySettings() {
      DisplaySettings settings = display_.getDisplaySettings();
      DisplaySettings.DisplaySettingsBuilder builder = settings.copy();

      DisplaySettings.ContrastSettings newContrast =
         DefaultDisplayManager.getInstance().getContrastSettings(
               contrastMins_, contrastMaxes_, new Double[] {gamma_});
      DisplaySettings.ContrastSettings[] contrasts =
         settings.getChannelContrastSettings();
      if (contrasts == null || contrasts.length <= channelIndex_) {
         // Need to create a new ContrastSettings array.
         DisplaySettings.ContrastSettings[] newContrasts =
            new DisplaySettings.ContrastSettings[channelIndex_ + 1];
         // Copy old values over.
         if (contrasts != null) {
            for (int i = 0; i < contrasts.length; ++i) {
               newContrasts[i] = contrasts[i];
            }
         }
         contrasts = newContrasts;
      }
      contrasts[channelIndex_] = newContrast;
      builder.channelContrastSettings(contrasts);

      Color[] colors = settings.getChannelColors();
      if (colors == null || colors.length <= channelIndex_) {
         // As above, we need to create a new array.
         Color[] newColors = new Color[channelIndex_ + 1];
         // Copy old values over.
         if (colors != null) {
            for (int i = 0; i < colors.length; ++i) {
               newColors[i] = colors[i];
            }
         }
         colors = newColors;
      }
      colors[channelIndex_] = color_;
      builder.channelColors(colors);

      settings = builder.build();
      display_.setDisplaySettings(settings);
      saveChannelSettings();
      postContrastEvent(settings);
   }

   /**
    * Post a LUTUpdateEvent, which will a) synchronize other histogram models
    * if synchornization is on, and b) cause the display to apply our LUT
    * to the onscreen image.
    */
   public void applyLUT() {
      DisplaySettings settings = display_.getDisplaySettings();
      if (settings.getShouldSyncChannels() != null &&
            settings.getShouldSyncChannels()) {
         display_.postEvent(
               new LUTUpdateEvent(this, contrastMins_, contrastMaxes_,
                  gamma_));
      }
      else {
         display_.postEvent(new LUTUpdateEvent(this, null, null, null));
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
            initialize(event.getImage());
         }
         // Only reload display settings if we really have to, as this forces
         // redraws which can slow the display way down.
         Color targetColor = display_.getDisplaySettings().getSafeChannelColor(
               channelIndex_, null);
         if (!color_.equals(targetColor)) {
            reloadDisplaySettings();
         }
         // Autostretch, if necessary.
         DisplaySettings settings = display_.getDisplaySettings();
         if (settings.getShouldAutostretch() != null &&
               settings.getShouldAutostretch()) {
            autostretch();
         }
         applyLUT();
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
