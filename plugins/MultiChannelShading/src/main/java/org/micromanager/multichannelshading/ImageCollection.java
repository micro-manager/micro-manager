///////////////////////////////////////////////////////////////////////////////
//FILE:          ImageCollection.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     MultiChannelShading plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco 2014
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

package org.micromanager.multichannelshading;

import ij.ImagePlus;
import ij.process.ByteProcessor;
import ij.process.ColorProcessor;
import ij.process.FloatProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Rectangle;
import java.util.HashMap;
import org.micromanager.Studio;

/**
 * Utility class for Shading plugin that holds the background and flatfield
 * images.  The images are stored as ImagePlusInfo objects.
 * Images should be provided as full frame, unbinned images.  When binned
 * images, and/or ROIs of (binned) images are requested, these are provided
 * on the fly, and a copy is cached for later use.
 *
 * @author nico
 */
public class ImageCollection {
   private final Studio gui_;
   private final HashMap<String, ImagePlusInfo> background_;
   private final HashMap<String, HashMap<String, ImagePlusInfo>> flatFields_;
   private final HashMap<String, String> presetFiles_;
   private String backgroundFilePath_;

   private final String baseImage_ = "base";

   public ImageCollection(Studio gui) {
      gui_ = gui;
      background_ = new HashMap<>();
      flatFields_ = new HashMap<>();
      presetFiles_ = new HashMap<>();
   }

   public void setBackground(String file) throws ShadingException {
      background_.clear();
      if (!file.equals("")) {
         ij.io.Opener opener = new ij.io.Opener();
         ImagePlus ip = opener.openImage(file);
         if (ip == null) {
            throw new ShadingException("Failed to open file: " + file);
         }
         if (ip.getType() != ImagePlus.GRAY8 && ip.getType() != ImagePlus.GRAY16
               && ip.getType() != ImagePlus.GRAY32 && ip.getType() != ImagePlus.COLOR_RGB) {
            throw new ShadingException(
                  "Background images must be 8-bit, 16-bit, 32-bit grayscale, or RGB");
         }
         ImagePlusInfo bg = new ImagePlusInfo(ip);
         background_.put(baseImage_, bg);
         background_.put(makeKey(1, bg.getOriginalRoi()), bg);
      }
      backgroundFilePath_ = file;
   }

   public String getBackgroundFile() {
      return backgroundFilePath_;
   }

   public ImagePlusInfo getBackground() {
      return background_.get(baseImage_);
   }

   public ImagePlusInfo getBackground(int binning, Rectangle roi)
         throws ShadingException {
      String key = makeKey(binning, roi);
      if (background_.containsKey(key)) {
         return background_.get(key);
      }
      // key not found, so derive the image from the original
      ImagePlusInfo bg = getBackground();
      if (bg == null) {
         return null;
      }

      ImagePlusInfo derivedBg = makeDerivedImage(bg, binning, roi);
      // put it in our cache
      background_.put(makeKey(binning, roi), derivedBg);
      return derivedBg;
   }

   /**
    * Stores flatfield image in the internal data store
    * If a background image is present, it will be subtracted first
    * Then, the average pixel value will be calculated
    * A normalized image will be calculated by dividing the mean through the
    * pixel value.  Therefore, use these flatfield images by multiplying
    * background corrected images, rather than by dividing.  This behavior
    * improves performance.
    *
    * @param preset Configuration preset to be associated with this image
    * @param file   Path to TIFF file with flatfield image
    * @throws ShadingException
    */
   public void addFlatField(String preset, String file) throws ShadingException {
      ij.io.Opener opener = new ij.io.Opener();
      ImagePlus ip = opener.openImage(file);
      if (ip == null) {
         throw new ShadingException("Failed to open flatfield file: " + file);
      }
      if (ip.getType() != ImagePlus.GRAY8 && ip.getType() != ImagePlus.GRAY16
            && ip.getType() != ImagePlus.GRAY32 && ip.getType() != ImagePlus.COLOR_RGB) {
         throw new ShadingException(
               "Flatfield images must be 8-bit, 16-bit, 32-bit grayscale, or RGB");
      }
      ImagePlusInfo bg = getBackground();
      ImagePlusInfo flatField;
      try {
         if (bg != null) {
            if (bg.getWidth() != ip.getWidth() || bg.getHeight() != ip.getHeight()) {
               gui_.getAlertManager().postAlert("Flatfield Error", this.getClass(),
                     preset + " flatfield image size differs from background image size.");
               throw new ShadingException("Faltfield image and background image differ in size");
            }
         }

         int width = ip.getWidth();
         int height = ip.getHeight();
         int nrPixels = width * height;

         if (ip.getType() == ImagePlus.COLOR_RGB) {
            flatField = new ImagePlusInfo(ip.getProcessor());
            flatField.setRgbFlatFieldProcessors(
                  normalizeRgbFlatField(ip.getProcessor(), bg));
         } else {
            ImageProcessor dp;
            if (bg != null) {
               dp = ImageUtils.subtractImageProcessors(
                     ip.getProcessor(), bg.getProcessor());
            } else {
               dp = ip.getProcessor();
            }
            long total = 0;
            if (ip.getType() == ImagePlus.GRAY8 || ip.getType() == ImagePlus.GRAY16) {
               for (int i = 0; i < nrPixels; i++) {
                  total += dp.get(i);
               }
            } else {
               for (int i = 0; i < nrPixels; i++) {
                  total += dp.getf(i);
               }
            }
            float mean = (float) total / nrPixels;
            float[] fPixels = new float[nrPixels];
            if (dp instanceof FloatProcessor) {
               for (int i = 0; i < nrPixels; i++) {
                  float pValue = dp.getf(i);
                  fPixels[i] = (pValue > 0 && mean > 0) ? mean / pValue : 1.0f;
               }
            } else {
               for (int i = 0; i < nrPixels; i++) {
                  int pValue = dp.get(i);
                  if (dp instanceof ShortProcessor) {
                     pValue &= 0x0000ffff;
                  } else if (dp instanceof ByteProcessor) {
                     pValue &= 0x000000ff;
                  }
                  fPixels[i] = (pValue > 0 && mean > 0) ? mean / (float) pValue : 1.0f;
               }
            }
            FloatProcessor fp = new FloatProcessor(width, height, fPixels);
            flatField = new ImagePlusInfo(fp);
         }

         HashMap<String, ImagePlusInfo> newFlatField = new HashMap<String, ImagePlusInfo>();
         newFlatField.put(baseImage_, flatField);
         newFlatField.put(makeKey(1, new Rectangle(0, 0, width, height)), flatField);
         flatFields_.put(preset, newFlatField);
      } catch (ShadingException ex) {
         gui_.logs().logError("Shading plugin, addFlatField in ImageCollection: "
               + ex.getMessage());
      }
      presetFiles_.put(preset, file);
   }

   public String getFileForPreset(String preset) {
      if (presetFiles_.containsKey(preset)) {
         return presetFiles_.get(preset);
      }
      return null;
   }

   public void clearFlatFields() {
      flatFields_.clear();
   }

   public void removeFlatField(String preset) {
      flatFields_.remove(preset);
   }

   public ImagePlusInfo getFlatField(String preset) {
      HashMap<String, ImagePlusInfo> map = flatFields_.get(preset);
      return map != null ? map.get(baseImage_) : null;
   }

   public ImagePlusInfo getFlatField(String preset, int binning, Rectangle roi)
         throws ShadingException {
      HashMap<String, ImagePlusInfo> map = flatFields_.get(preset);
      if (map == null) {
         return null;
      }
      String key = makeKey(binning, roi);
      if (map.containsKey(key)) {
         return map.get(key);
      }
      // key not found, so derive the image from the original
      ImagePlusInfo ff = getFlatField(preset);
      if (ff == null) {
         return null;
      }
      ImagePlusInfo derivedIp = makeDerivedImage(ff, binning, roi);
      // add derived image into our cache
      map.put(makeKey(binning, roi), derivedIp);
      return derivedIp;
   }

   /**
    * Computes per-channel normalization factors for an RGB flatfield image.
    * If a background is present (RGB or grayscale), it is subtracted per channel first.
    * Returns three FloatProcessors: index 0=R, 1=G, 2=B, each containing mean/pixel factors.
    */
   private FloatProcessor[] normalizeRgbFlatField(ImageProcessor flatProc,
                                                   ImagePlusInfo bg) throws ShadingException {
      int width = flatProc.getWidth();
      int height = flatProc.getHeight();
      int nrPixels = width * height;

      // ColorProcessor pixels are int[] in ARGB order: bits 23-16=R, 15-8=G, 7-0=B
      int[] ffPixels = (int[]) flatProc.getPixels();
      int[] bgArgb = null;
      ImageProcessor bgProc = null;
      if (bg != null) {
         bgProc = bg.getProcessor();
         if (bgProc instanceof ColorProcessor) {
            bgArgb = (int[]) bgProc.getPixels();
         }
      }

      // First pass: compute per-channel totals (background-subtracted)
      long[] totals = new long[3];
      for (int i = 0; i < nrPixels; i++) {
         int pixel = ffPixels[i];
         int r = (pixel >> 16) & 0xff;
         int g = (pixel >> 8) & 0xff;
         int b = pixel & 0xff;
         if (bgArgb != null) {
            int bgPixel = bgArgb[i];
            r = Math.max(0, r - ((bgPixel >> 16) & 0xff));
            g = Math.max(0, g - ((bgPixel >> 8) & 0xff));
            b = Math.max(0, b - (bgPixel & 0xff));
         } else if (bgProc != null) {
            // Grayscale background: same offset subtracted from all channels
            int bgVal = Math.min(255, bgProc.get(i));
            r = Math.max(0, r - bgVal);
            g = Math.max(0, g - bgVal);
            b = Math.max(0, b - bgVal);
         }
         totals[0] += r;
         totals[1] += g;
         totals[2] += b;
      }

      // Second pass: compute per-channel normalization factors (mean / pixel)
      float meanR = (float) totals[0] / nrPixels;
      float meanG = (float) totals[1] / nrPixels;
      float meanB = (float) totals[2] / nrPixels;
      float[] factorsR = new float[nrPixels];
      float[] factorsG = new float[nrPixels];
      float[] factorsB = new float[nrPixels];
      for (int i = 0; i < nrPixels; i++) {
         int pixel = ffPixels[i];
         int r = (pixel >> 16) & 0xff;
         int g = (pixel >> 8) & 0xff;
         int b = pixel & 0xff;
         if (bgArgb != null) {
            int bgPixel = bgArgb[i];
            r = Math.max(0, r - ((bgPixel >> 16) & 0xff));
            g = Math.max(0, g - ((bgPixel >> 8) & 0xff));
            b = Math.max(0, b - (bgPixel & 0xff));
         } else if (bgProc != null) {
            int bgVal = Math.min(255, bgProc.get(i));
            r = Math.max(0, r - bgVal);
            g = Math.max(0, g - bgVal);
            b = Math.max(0, b - bgVal);
         }
         factorsR[i] = (r > 0 && meanR > 0) ? meanR / r : 1.0f;
         factorsG[i] = (g > 0 && meanG > 0) ? meanG / g : 1.0f;
         factorsB[i] = (b > 0 && meanB > 0) ? meanB / b : 1.0f;
      }
      return new FloatProcessor[] {
            new FloatProcessor(width, height, factorsR),
            new FloatProcessor(width, height, factorsG),
            new FloatProcessor(width, height, factorsB)
      };
   }

   private String makeKey(int binning, Rectangle roi) {
      if (binning == 1 && (roi == null || roi.width == 0)) {
         return baseImage_;
      }
      String key = "" + binning;
      if (roi != null) {
         key = binning + "-" + roi.x + "-" + roi.y + "-" + roi.width + "-"
               + roi.height;
      }
      return key;
   }

   /**
    * Generates a new ImagePlus from this one by applying the requested binning
    * and setting the desired ROI. Should only be called on the original image
    * (i.e. binning = 1, full field image) If the original image was normalized,
    * this one will be as well (as it is derived from the normalized image)
    *
    * @param ipi
    * @param binning
    * @param roi
    * @return ImagePlusInfo
    * @throws ShadingException
    */
   private ImagePlusInfo makeDerivedImage(ImagePlusInfo ipi, int binning, Rectangle roi)
         throws ShadingException {
      if (ipi.getBinning() != 1) {
         throw new ShadingException("This is not an unbinned image.  "
               + "Can not derive binned images from this one");
      }
      // HACK/Fix: The Andor Zyla often returns an ROI with roi.x ==-1 or roi.y == -1
      // That creates problems because the image after setRoi will be one pixel
      // too small.  This can be removed once ROIs can be trusted to have all numbers >= 0.
      if (roi.x < 0) {
         roi.x = 0;
      }
      if (roi.y < 0) {
         roi.y = 0;
      }
      ImageProcessor resultProcessor;
      if (binning != 1) {
         resultProcessor = ipi.getProcessor().bin(binning);
      } else {
         resultProcessor = ipi.getProcessor().duplicate();
      }
      resultProcessor.setRoi(roi);
      ImagePlusInfo newIp = new ImagePlusInfo(new ImagePlus("", resultProcessor.crop()),
            binning, roi);

      if (ipi.isRgbFlatField()) {
         FloatProcessor[] srcProcessors = ipi.getRgbFlatFieldProcessors();
         FloatProcessor[] derivedProcessors = new FloatProcessor[3];
         for (int c = 0; c < 3; c++) {
            ImageProcessor p;
            if (binning != 1) {
               p = srcProcessors[c].bin(binning);
            } else {
               p = srcProcessors[c].duplicate();
            }
            p.setRoi(roi);
            derivedProcessors[c] = (FloatProcessor) p.crop();
         }
         newIp.setRgbFlatFieldProcessors(derivedProcessors);
      }

      return newIp;
   }

}