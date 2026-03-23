/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.displaywindow.imagej;

import com.google.common.base.Preconditions;
import ij.CompositeImage;
import ij.ImagePlus;
import ij.ImageStack;
import ij.process.ColorProcessor;
import ij.process.ImageProcessor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author mark
 */
class RGBColorModeStrategy implements ColorModeStrategy {
   private ImagePlus imagePlus_;

   private final List<Integer> minima_;
   private final List<Integer> maxima_;

   private final int[][] rgbLUTs_ = new int[3][]; // 256-element each
   private ImageProcessor unscaledRGBImage_;

   static ColorModeStrategy create() {
      return new RGBColorModeStrategy();
   }

   private RGBColorModeStrategy() {
      minima_ = new ArrayList<Integer>(Collections.nCopies(3, 0));
      maxima_ = new ArrayList<Integer>(Collections.nCopies(3, 255));
   }

   private int[][] getRGBLUTs() {
      for (int c = 0; c < 3; ++c) {
         if (rgbLUTs_[c] != null) {
            continue;
         }
         int[] lut = new int[256];
         float min = minima_.get(c);
         float max = Math.min(255, maxima_.get(c));
         for (int k = 0; k < 256; ++k) {
            float f = (float) Math.max(Math.min(1.0, (k - min) / (max - min)), 0.0);
            lut[k] = Math.round(255.0f * f);
         }
         rgbLUTs_[c] = lut;
      }
      return rgbLUTs_;
   }

   private void apply() {
      // ColorProcessor has no way of applying a LUT, so we modify the pixel
      // values directly, keeping a copy of the original image.
      // (We previously used a trick and stored the original image in the
      // ColorProcessor's "snapshot", but that leaks internal behavior if the
      // user should select Edit > Undo.)
      if (unscaledRGBImage_ == null) {
         unscaledRGBImage_ = imagePlus_.getProcessor();
      }
      // setProcessor will set the stack to null when stacksize == 1
      // so store a reference and reset the stack if needed
      ImageStack stack = imagePlus_.getStack();
      imagePlus_.setProcessor(unscaledRGBImage_.duplicate());
      // imagePlus_.getStack() creates a new stack if stack is null, 
      // so can not be used here. Use a very indirect method:
      if (stack.size() <= 1) {
         imagePlus_.setStack(stack);
      }
      applyLUTs((ColorProcessor) imagePlus_.getProcessor(), getRGBLUTs());
   }

   private static void applyLUTs(ColorProcessor proc, int[][] rgbLUTs) {
      int[] rLUT = rgbLUTs[0];
      int[] gLUT = rgbLUTs[1];
      int[] bLUT = rgbLUTs[2];
      int[] pixelsARGB = (int[]) proc.getPixels();
      for (int i = 0; i < pixelsARGB.length; i++) {
         int argb = pixelsARGB[i];
         int b = bLUT[argb & 0xff];
         argb >>= 8;
         int g = gLUT[argb & 0xff];
         argb >>= 8;
         int r = rLUT[argb & 0xff];
         pixelsARGB[i] = (((r << 8) | g) << 8) | b;
      }
   }

   @Override
   public void applyModeToImagePlus(ImagePlus imagePlus) {
      Preconditions.checkArgument(!(imagePlus instanceof CompositeImage));
      Preconditions.checkArgument(
            imagePlus.getProcessor() instanceof ColorProcessor);

      unscaledRGBImage_ = null;
      imagePlus_ = imagePlus;
      apply();
   }

   @Override
   public void applyHiLoHighlight(boolean enable) {
      // Not supported
   }

   @Override
   public void applyColor(int component, Color color) {
   }

   @Override
   public void applyScaling(int component, int min, int max, boolean defer) {
      Preconditions.checkArgument(min >= 0);
      Preconditions.checkArgument(max >= min);
      if (min != minima_.get(component)) {
         minima_.set(component, min);
         rgbLUTs_[component] = null;
      }
      if (max != maxima_.get(component)) {
         maxima_.set(component, max);
         rgbLUTs_[component] = null;
      }
      if (!defer) {
         apply();
      }
   }

   @Override
   public void applyGamma(int component, double gamma) {
      if (gamma != 1.0) {
         throw new UnsupportedOperationException("Gamma not implemented for RGB");
      }
   }

   @Override
   public void applyVisibleInComposite(int component, boolean visible) {
   }

   @Override
   public void displayedImageDidChange() {
      unscaledRGBImage_ = null;
      apply();
   }

   @Override
   public void releaseImagePlus() {
      imagePlus_ = null;
      Arrays.fill(rgbLUTs_, null);
      unscaledRGBImage_ = null;
   }
}