///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Data API
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

package org.micromanager.data.internal;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;

import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.ImageJConverter;
import org.micromanager.data.Metadata;

import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.ReportingUtils;

public class DefaultImageJConverter implements ImageJConverter {
   private static final DefaultImageJConverter staticInstance_;
   static {
      staticInstance_ = new DefaultImageJConverter();
   }

   // Our API does not expose the original pixels so the caller cannot modify
   // the image buffer.
   @Override
   public ImageProcessor createProcessor(Image image) {
      return createProcessor(image, true);
   }

   public ImageProcessor createProcessor(Image image, boolean shouldCopy) {
      int width = image.getWidth();
      int height = image.getHeight();
      int bytesPerPixel = image.getBytesPerPixel();
      int numComponents = image.getNumComponents();
      Object pixels = image.getRawPixels();
      if (shouldCopy) {
         pixels = image.getRawPixelsCopy();
      }
      if (bytesPerPixel == 4 && numComponents == 3) {
         // Micro-Manager RGB32 images are generally composed of byte
         // arrays, but ImageJ only takes int arrays.
         if (pixels instanceof byte[]) {
            pixels = ImageUtils.convertRGB32BytesToInt((byte[]) pixels);
         }
         return new ColorProcessor(width, height, (int[]) pixels);
      }
      else if (bytesPerPixel == 1 && numComponents == 1) {
         return new ByteProcessor(width, height, (byte[]) pixels, null);
      }
      else if (bytesPerPixel == 2 && numComponents == 1) {
         return new ShortProcessor(width, height, (short[]) pixels, null);
      }
      else if (bytesPerPixel == 4 && numComponents == 1) {
         return new FloatProcessor(width,height, (float[]) pixels, null);
      }
      return null;
   }

   @Override
   public ImageProcessor createProcessorFromComponent(Image image,
         int component) {
      int numComponents = image.getNumComponents();
      if (numComponents == 1) {
         return createProcessor(image);
      }
      int bytesPerPixel = image.getBytesPerPixel();
      Object pixels = image.getRawPixels();
      // This is the only multi-component image type we know how to support
      // currently.
      if (bytesPerPixel == 4 && numComponents == 3 &&
            pixels instanceof byte[]) {
         byte[] subPixels = ImageUtils.singleChannelFromRGB32(
               (byte[]) pixels, component);
         return new ByteProcessor(image.getWidth(), image.getHeight(),
               subPixels);
      }
      else {
         ReportingUtils.logError(String.format("Unknown image format with %d bytes per pixel, %d components, and pixel type %s", bytesPerPixel, numComponents, pixels.getClass().getName()));
         return null;
      }
   }

   @Override
   public Image createImage(ImageProcessor processor, Coords coords,
         Metadata metadata) {
      int bytesPerPixel = -1;
      int numComponents = -1;
      if (processor instanceof ByteProcessor) {
         bytesPerPixel = 1;
         numComponents = 1;
      }
      else if (processor instanceof ShortProcessor) {
         bytesPerPixel = 2;
         numComponents = 1;
      }
      else if (processor instanceof FloatProcessor) {
         bytesPerPixel = 4;
         numComponents = 1;
      }
      else if (processor instanceof ColorProcessor) {
         bytesPerPixel = 1;
         numComponents = 3;
      }
      else {
         ReportingUtils.logError("Unrecognized processor type " + processor.getClass().getName());
      }
      return DefaultDataManager.getInstance().createImage(
            processor.getPixels(), processor.getWidth(), processor.getHeight(),
            bytesPerPixel, numComponents, coords, metadata);
   }

   public static DefaultImageJConverter getInstance() {
      return staticInstance_;
   }
}
