///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger,  2015
//
// COPYRIGHT:    University of California,  San Francisco,  2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT,  INDIRECT,
//               INCIDENTAL,  SPECIAL,  EXEMPLARY,  OR CONSEQUENTIAL DAMAGES.

package org.micromanager.internal.utils;

import net.imglib2.display.ColorTable8;
import org.apache.commons.math3.analysis.UnivariateFunction;
import org.apache.commons.math3.analysis.interpolation.LinearInterpolator;
import org.micromanager.internal.utils.imageanalysis.ImageUtils;

/**
 * Micro-Manager LUTS.
 */
public final class ColorMaps {
   private ColorMaps() {
   }

   private static final ColorTable8 FIRE_MAP = makeFireColorMap();
   private static final ColorTable8 RED_HOT_MAP = makeRedHotColorMap();

   public static ColorTable8 fireColorMap() {
      return FIRE_MAP;
   }

   public static ColorTable8 redHotColorMap() {
      return RED_HOT_MAP;
   }

   // Please don't (re)introduce the Rainbow (aka Spectrum aka Jet) color map
   // here. It should not be used to view quantitative data. For reasons why,
   // see,  for example,
   // "Rainbow Color Map Critiques: An Overview and Annotated Bibliography"
   // by Steve Eddins,  MathWorks,
   // https://www.mathworks.com/tagteam/81137_92238v00_RainbowColorMap_57312.pdf
   // and references therein.
   //
   // Or,
   // Borland and Taylor,  2007. "Rainbow Color Map (Still) Considered Harmful"
   // IEEE Computer Graphics and Applications,  27(2):14-17.
   // https://doi.org/10.1109/MCG.2007.323435
   // in which the following quote appears:
   // "The rainbow color map confuses viewers through its lack of perceptual
   // ordering,  obscures data through its uncontrolled luminance variation,  and
   // actively misleads interpretation through the introduction of non-data-
   // dependent gradients."


   private static ColorTable8 makeFireColorMap() {
      // Fire. Interpolated from 32 control points,  copied from ImageJ.
      int[] r = {0, 0, 1, 25, 49, 73, 98, 122, 146, 162, 173, 184, 195, 207, 217, 229, 240,
            252, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255};
      int[] g = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 14, 35, 57, 79, 101, 117, 133, 147,
            161, 175, 190, 205, 219, 234, 248, 255, 255, 255, 255};
      int[] b = {0, 61, 96, 130, 165, 192, 220, 227, 210, 181, 151, 122, 93, 64, 35, 5,
            0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 35, 98, 160, 223, 255};
      return makeColorTable8(32, r, g, b);
   }

   private static ColorTable8 makeRedHotColorMap() {
      // Redhot; developed by Nico Stuurman based on ImageJ. 32 control points.
      int[] r = {0, 1, 27, 52, 78, 103, 130, 155, 181, 207, 233, 255, 255, 255, 255, 255, 255,
            255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255};
      int[] g = {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 3, 29, 55, 81, 106, 133,
            158, 184, 209, 236, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255, 255};
      int[] b = {0, 1, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0,
            0, 0, 0, 0, 6, 32, 58, 84, 110, 135, 161, 187, 160, 213, 255};
      return makeColorTable8(32, r, g, b);
   }

   private static ColorTable8 makeColorTable8(int len, int[] r, int[] g, int[] b) {
      byte[] rBytes = new byte[len];
      byte[] gBytes = new byte[len];
      byte[] bBytes = new byte[len];
      for (int i = 0; i < len; ++i) {
         rBytes[i] = (byte) r[i];
         gBytes[i] = (byte) g[i];
         bBytes[i] = (byte) b[i];
      }
      return new InterpolatedColorTable8(rBytes, gBytes, bBytes);
   }

   private static final class InterpolatedColorTable8 extends ColorTable8 {
      private InterpolatedColorTable8(byte[]... values) {
         super(interpolateComponents(values));
      }

      private static byte[][] interpolateComponents(byte[]... values) {
         byte[][] ret = new byte[values.length][];
         for (int c = 0; c < values.length; ++c) {
            ret[c] = interpolateTo256Bins(values[c]);
         }
         return ret;
      }

      private static byte[] interpolateTo256Bins(byte[] table) {
         int len = table.length;
         double[] x = new double[len];
         double[] y = new double[len];
         for (int i = 0; i < len; ++i) {
            x[i] = 255.0 * i / (len - 1);
            y[i] = ImageUtils.unsignedValue(table[i]);
         }
         UnivariateFunction interp = new LinearInterpolator().interpolate(x, y);
         byte[] ret = new byte[256];
         for (int i = 0; i < 256; ++i) {
            ret[i] = (byte) (int) interp.value(i);
         }
         return ret;
      }
   }
}