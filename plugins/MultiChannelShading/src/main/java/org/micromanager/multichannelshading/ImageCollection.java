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
 * @author nico
 */
public class ImageCollection {
   private final Studio gui_;
   private final HashMap<String, ImagePlusInfo> background_;
   private final HashMap<String, HashMap<String, ImagePlusInfo>> flatFields_;
   private final HashMap<String, String> presetFiles_;
   private String backgroundFilePath_;

   private final String BASEIMAGE = "base";
   
   public ImageCollection(Studio gui) {
      gui_ = gui;
      background_ = new HashMap<String, ImagePlusInfo>();
      flatFields_ = new HashMap<String, HashMap<String, ImagePlusInfo>>();
      presetFiles_ = new HashMap<String, String>();
   }
   
   public void setBackground(String file) throws ShadingException {
      background_.clear();
      if (!file.equals("")) {
         ij.io.Opener opener = new ij.io.Opener();
         ImagePlus ip = opener.openImage(file);
         if (ip == null) {
            throw new ShadingException("Failed to open file: " + file);
         }
         ImagePlusInfo bg = new ImagePlusInfo(ip); 
         background_.put(BASEIMAGE, bg);
         background_.put(makeKey(1, bg.getOriginalRoi()), bg);
         backgroundFilePath_ = file;
      }
   }

   public String getBackgroundFile() {
      return backgroundFilePath_;
   }

   public ImagePlusInfo getBackground() {
      return background_.get(BASEIMAGE);
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
    * @param preset Configuration preset to be associated with this image
    * @param file Path to TIFF file with flatfield image
    * @throws ShadingException 
    */
   public void addFlatField(String preset, String file) throws ShadingException {
      ij.io.Opener opener = new ij.io.Opener();
      ImagePlus ip = opener.openImage(file);
      if (ip == null) {
         throw new ShadingException(
                 "Failed to open flatfield file: " + file);
      }
      if (ip.getType() != ImagePlus.GRAY8 && ip.getType() != ImagePlus.GRAY16
              && ip.getType() != ImagePlus.GRAY32 ) {
         throw new ShadingException(
                 "This plugin only works with gray scale flatfield images of 1 or 2 byte size");
      }
      ImagePlusInfo bg = getBackground();
      ImagePlusInfo flatField;
      try {
         ImageProcessor dp;
         if (bg != null) {
            dp = ImageUtils.subtractImageProcessors(
                    ip.getProcessor(), bg.getProcessor());
         } else {
            dp = ip.getProcessor();
         }
         long total = 0;
         int width = dp.getWidth();
         int height = dp.getHeight();
         int nrPixels = width * height;
         if (ip.getType() == ImagePlus.GRAY8 || ip.getType() == ImagePlus.GRAY16) {
            for (int i = 0; i < nrPixels; i++) {
               total += dp.get(i);
            }
         } else {
            for (int i = 0; i < nrPixels; i++) {
               total += dp.getf(i);
            }
         }
         float mean = total / nrPixels;
         float[] fPixels = new float[nrPixels];
          if (dp instanceof FloatProcessor) {
             for (int i = 0; i < nrPixels; i++) {
               fPixels[i] = mean / dp.getf(i);
            }
         } else {
            for (int i = 0; i < nrPixels; i++) {

               int pValue = dp.get(i);
               if (dp instanceof ShortProcessor) {
                  pValue &= pValue & 0x0000ffff;
               } else if (dp instanceof ByteProcessor) {
                  pValue &= pValue & 0x000000ff;
               }
               fPixels[i] = mean / (float) pValue;
            }
         }
          
         FloatProcessor fp = new FloatProcessor (width, height, fPixels);

         flatField = new ImagePlusInfo(fp);

         HashMap<String, ImagePlusInfo> newFlatField =
                 new HashMap<String, ImagePlusInfo>();
         newFlatField.put(BASEIMAGE, flatField);
         newFlatField.put(makeKey(1, fp.getRoi()), flatField);
         flatFields_.put(preset, newFlatField);
      } catch (ShadingException ex) {
         gui_.logs().logError("Shading plugin, addFlatField in ImageCollection: " + 
                 ex.getMessage());
      }
      presetFiles_.put(preset, file);
   }

   public String getFileForPreset(String preset) {
      if (presetFiles_.containsKey(preset)) {
         return presetFiles_.get(preset);
      }
      return null;
   }

   public ImagePlusInfo getFlatField(String preset) {
      return flatFields_.get(preset).get(BASEIMAGE);
   }

   public void clearFlatFields() {
      flatFields_.clear();
   }

   public void removeFlatField(String preset) {
      flatFields_.remove(preset);
   }

   public ImagePlusInfo getFlatField(String preset, int binning, Rectangle roi)
           throws ShadingException {
      String key = makeKey(binning, roi);
      if (flatFields_.get(preset).containsKey(key)) {
         return flatFields_.get(preset).get(key);
      }
      // key not found, so derive the image from the original
      ImagePlusInfo ff = getFlatField(preset);
      if (ff == null) {
         return null;
      }
      ImagePlusInfo derivedIp = makeDerivedImage(ff, binning, roi);
      // add derived image into our cache
      HashMap<String, ImagePlusInfo> tmp = flatFields_.get(preset);
      tmp.put(makeKey(binning, roi), derivedIp);
      return derivedIp;
   }

   private String makeKey(int binning, Rectangle roi) {
      if (binning == 1 && (roi == null || roi.width == 0)) {
         return BASEIMAGE;
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
    * @return 
    * @throws org.micromanager.internal.utils.MMException 
    */
   private ImagePlusInfo makeDerivedImage(ImagePlusInfo ipi, int binning, Rectangle roi)
           throws ShadingException {
      if (ipi.getBinning() != 1) {
         throw new ShadingException("This is not an unbinned image.  " +
                 "Can not derive binned images from this one");
      }
      ImageProcessor resultProcessor;
      if (binning != 1) {
         resultProcessor = ipi.getProcessor().bin(binning);
      } else {
         resultProcessor = ipi.getProcessor().duplicate();
      }
      // HACK/Fix: The Andor Zyla often returns an ROI with roi.x ==-1 pr roi.y == -1
      // That creates problems because the image after setRoi will be one pixel
      // to small (i.e., the image should always have the correct height and width
      // This can be removed once ROIs can be trusted to have all number >= 0
      if (roi.x < 0) {
         roi.x = 0;
      }
      if (roi.y < 0) {
         roi.y = 0;
      }
      resultProcessor.setRoi(roi);
      ImagePlusInfo newIp = new ImagePlusInfo(new ImagePlus("", resultProcessor.crop()), 
              binning, roi);
      
      return newIp;         
   }
   
}