/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.displaywindow.imagej;

import com.google.common.base.Preconditions;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import ij.process.LUT;
import ij.process.ShortProcessor;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Common base implementation for non-LUT (continuous) color modes.
 *
 * @author Mark A. Tsuchida
 */
abstract class AbstractColorModeStrategy implements ColorModeStrategy {
   private ImagePlus imagePlus_;

   private final List<Integer> minima_ = new ArrayList<Integer>();
   private final List<Integer> maxima_ = new ArrayList<Integer>();
   private final List<Double> gammas_ = new ArrayList<Double>();
   private boolean highlightHiLo_ = false;

   private List<LUT> cachedLUTs_;

   protected AbstractColorModeStrategy(int nColors) {
   }

   private int getMinimum(int index) {
      if (index >= minima_.size()) {
         return 0;
      }
      return minima_.get(index);
   }

   private int getMaximum(int index) {
      if (index >= maxima_.size()) {
         return getSampleMax();
      }
      return Math.min(getSampleMax(), maxima_.get(index));
   }

   private double getGamma(int index) {
      if (index >= gammas_.size()) {
         return 1.0;
      }
      return gammas_.get(index);
   }

   protected final int getSampleMax() {
      // We assume that if the ImagePlus is a CompositeImage, then all channels
      // have the same format.
      ImageProcessor proc = imagePlus_.getChannelProcessor();
      if (proc instanceof ByteProcessor) {
         return 255;
      }
      if (proc instanceof ShortProcessor) {
         return 65535;
      }
      if (proc instanceof ColorProcessor) {
         return 255;
      }
      throw new UnsupportedOperationException("Unsupported ImageProcessor type");
   }

   protected abstract LUT getLUT(int index, double gamma);

   protected boolean isVisibleInComposite(int index) {
      return true;
   }

   protected abstract int getModeForCompositeImage();

   private LUT getCachedLUT(int index) {
      if (cachedLUTs_ == null) {
         cachedLUTs_ = new ArrayList<LUT>();
      }
      if (index >= cachedLUTs_.size()) {
         cachedLUTs_.addAll(Collections.nCopies(index + 1 - cachedLUTs_.size(),
               (LUT) null));
      }

      if (cachedLUTs_.get(index) == null) {
         LUT lut = getLUT(index, getGamma(index));
         if (highlightHiLo_) {
            byte[] r = new byte[256];
            byte[] g = new byte[256];
            byte[] b = new byte[256];
            lut.getReds(r);
            lut.getGreens(g);
            lut.getBlues(b);
            // Set 0 to blue
            b[0] = (byte) 0xff;
            r[0] = g[0] = (byte) 0x00;
            // Set 255 to red
            r[255] = (byte) 0xff;
            g[255] = b[255] = (byte) 0x00;
            lut = new LUT(r, g, b);
         }
         cachedLUTs_.set(index, lut);
      }
      return cachedLUTs_.get(index);
   }

   protected final void flushCachedLUTs() {
      cachedLUTs_ = null;
   }

   private void applyToMonochromeImagePlus() {
      LUT lut = getCachedLUT(0);
      lut.min = getMinimum(0);
      lut.max = getMaximum(0);
      imagePlus_.getProcessor().setLut(lut);
   }

   private void applyToCompositeImage() {
      CompositeImage compositeImage = (CompositeImage) imagePlus_;
      compositeImage.setMode(getModeForCompositeImage());
      int nChannels = ((IMMImagePlus) compositeImage).getNChannelsWithoutSideEffect();
      for (int i = 0; i < nChannels; ++i) {
         LUT lut = getCachedLUT(i);
         lut.min = getMinimum(i);
         lut.max = getMaximum(i);
         compositeImage.setChannelLut(lut, i + 1);
         if (compositeImage.getMode() == CompositeImage.COMPOSITE) {
            ImageProcessor proc = compositeImage.getProcessor(i + 1);
            if (proc != null) { // ImageJ may not have allocated it yet
               proc.setMinAndMax(getMinimum(i), getMaximum(i));
            }
         }
      }
      if (compositeImage.getMode() == CompositeImage.COMPOSITE) {
         boolean[] active = compositeImage.getActiveChannels();
         for (int i = 0; i < active.length; i++) {
            active[i] = isVisibleInComposite(i);
         }
      }
      // We _also_ need to apply the current channel's setting to the
      // non-composite ImageProcessor, which is used when mode is GRAYSCALE
      // (This may not be strictly necessary but is harmless.)
      int channel = compositeImage.getChannel() - 1;
      LUT lut = getCachedLUT(channel);
      lut.min = getMinimum(channel);
      lut.max = getMaximum(channel);
      compositeImage.getProcessor().setLut(lut);
   }

   protected final void apply() {
      if (imagePlus_ instanceof CompositeImage) {
         applyToCompositeImage();
      }
      else {
         applyToMonochromeImagePlus();
      }
   }

   @Override
   public void applyModeToImagePlus(ImagePlus imagePlus) {
      imagePlus_ = imagePlus;
      apply();
   }

   @Override
   public void applyHiLoHighlight(boolean enable) {
      if (enable == highlightHiLo_) {
         return;
      }
      flushCachedLUTs();
      highlightHiLo_ = enable;
      apply();
   }

   @Override
   public void applyScaling(int index, int min, int max) {
      Preconditions.checkArgument(min >= 0);
      Preconditions.checkArgument(max >= min);
      if (minima_.size() <= index) {
         minima_.addAll(Collections.nCopies(index + 1 - minima_.size(), 0));
      }
      if (maxima_.size() <= index) {
         maxima_.addAll(Collections.nCopies(index + 1 - maxima_.size(),
               Integer.MAX_VALUE));
      }
      minima_.set(index, min);
      maxima_.set(index, max);
      apply();
   }

   @Override
   public void applyGamma(int index, double gamma) {
      Preconditions.checkArgument(gamma >= 0.0);
      if (getGamma(index) == gamma) {
         return; // Avoid flushing cached LUTs
      }
      flushCachedLUTs();
      if (gammas_.size() <= index) {
         gammas_.addAll(Collections.nCopies(index + 1 - gammas_.size(), 1.0));
      }
      gammas_.set(index, gamma);
      apply();
   }

   @Override
   public void applyVisibleInComposite(int index, boolean visible) {
   }

   @Override
   public void displayedImageDidChange() {
      apply();
   }

   @Override
   public void releaseImagePlus() {
      imagePlus_ = null;
      flushCachedLUTs();
   }
}