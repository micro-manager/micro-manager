/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.display.internal.displaywindow.imagej;

import com.google.common.base.Preconditions;
import ij.CompositeImage;
import ij.process.LUT;
import java.awt.Color;
import net.imglib2.display.ColorTable8;

/**
 * @author mark
 */
class LUTColorModeStrategy extends AbstractColorModeStrategy {
   private final byte[] rLUT_, gLUT_, bLUT_;

   public static ColorModeStrategy create(ColorTable8 lut) {
      return new LUTColorModeStrategy(1, lut);
   }

   private LUTColorModeStrategy(int nChannels, ColorTable8 lut) {
      super(nChannels);
      Preconditions.checkArgument(lut.getComponentCount() == 3);
      Preconditions.checkArgument(lut.getLength() == 256);
      byte[][] tables = lut.getValues();
      rLUT_ = tables[0].clone();
      gLUT_ = tables[1].clone();
      bLUT_ = tables[2].clone();
   }

   @Override
   public void applyColor(int index, Color color) {
   }

   @Override
   protected LUT getLUT(int index, double gamma) {
      // Note: Same LUT for any index
      int len = rLUT_.length;
      if (gamma == 1.0) { // Intentionally exact equivalence
         return new LUT(8, len, rLUT_, gLUT_, bLUT_);
      }
      int retlen = 256;
      byte[] r = new byte[retlen];
      byte[] g = new byte[retlen];
      byte[] b = new byte[retlen];
      for (int i = 0; i < retlen; ++i) {
         // Linear interpolation
         double j = Math.pow((double) i / (retlen - 1), gamma) * (len - 1);
         int m = (int) Math.floor(j);
         int n = (int) Math.ceil(j);
         double p = j - m;
         double q = n - j;
         r[i] = (byte) Math.round(p * rLUT_[m] + q * rLUT_[n]);
         g[i] = (byte) Math.round(p * gLUT_[m] + q * gLUT_[n]);
         b[i] = (byte) Math.round(p * bLUT_[m] + q * bLUT_[n]);
      }
      return new LUT(8, len, r, g, b);
   }

   @Override
   protected int getModeForCompositeImage() {
      return CompositeImage.GRAYSCALE;
   }
}