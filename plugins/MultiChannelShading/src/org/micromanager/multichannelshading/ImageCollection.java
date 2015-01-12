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

import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 * Utility class for Shading plugin that holds the background and flatfield
 * images.  The images are stored as ImagePlusInfo objects.
 * Images should be provided as full frame, unbinned images.  When binned 
 * images, and/or ROIs of (binned) images are requested, these are provided
 * on the fly, and a copy is cached for later use.
 * @author nico
 */
public class ImageCollection {
   private final HashMap<String, ImagePlusInfo> background_;
   private final HashMap<String, HashMap<String, ImagePlusInfo>> flatFields_;
   
   private final String BASEIMAGE = "base";
   
   public ImageCollection() {
      background_ = new HashMap<String, ImagePlusInfo>();
      flatFields_ = new HashMap<String, HashMap<String, ImagePlusInfo>>();
   }
   
   public void setBackground(String file) throws MMException {
      background_.clear();
      if (!file.equals("")) {
         ij.io.Opener opener = new ij.io.Opener();
         ImagePlus ip = opener.openImage(file);
         if (ip == null) {
            throw new MMException("Failed to open file: " + file);
         }
         ImagePlusInfo bg = new ImagePlusInfo(ip); 
         background_.put(BASEIMAGE, bg);
         background_.put(makeKey(1, bg.getOriginalRoi()), bg);
      }
   }
   
   public ImagePlusInfo getBackground() {
      return background_.get(BASEIMAGE);
   }
   
   public ImagePlusInfo getBackground(int binning, Rectangle roi) 
           throws MMException {
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
    * background corrected images, rather than by divding through them.  THis 
    * is for performance reasons.
    * @param preset
    * @param file
    * @throws MMException 
    */
   public void addFlatField(String preset, String file) throws MMException {
      ij.io.Opener opener = new ij.io.Opener();
      ImagePlus ip = opener.openImage(file);
      if (ip == null) {
         throw new MMException(
                 "Failed to open flatfield file: " + file);
      }
      if (ip.getType() != ImagePlus.GRAY8 && ip.getType() != ImagePlus.GRAY16
              && ip.getType() != ImagePlus.GRAY32 ) {
         throw new MMException(
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
                  pValue &= (int) pValue & 0x0000ffff;
               } else if (dp instanceof ByteProcessor) {
                  pValue &= (int) pValue & 0x000000ff;
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
      } catch (MMException ex) {
         ReportingUtils.logError("Shading plugin, addFlatField in ImageCollection: " + 
                 ex.getMessage());
      }
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
           throws MMException {
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
      String key = binning + "-" + roi.x + "-" + roi.y + "-" + roi.width + "-"
              + roi.height;
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
    * @throws org.micromanager.utils.MMException
    */
   private ImagePlusInfo makeDerivedImage(ImagePlusInfo ipi, int binning, Rectangle roi)
           throws MMException {
      if (ipi.getBinning() != 1) {
         throw new MMException("This is not an unbinned image.  " +
                 "Can not derive binned images from this one");
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
      //newIp.setProcessor(resultProcessor.duplicate());
      
      return newIp;         
   }
   
   public static Rectangle TagToRectangle(String search) throws MMException {
      String[] parts = search.split("-");
      // The Andor Zyla adds negative offsets to the ROI
      // these need to be caught and corrected here, or we will not 
      // generate the corect image.  
      // Once this is fixed in the Zyla, this code can go away
      if (parts.length > 4) {
         String[] realParts = new String[4];
         int counter = 0;
         int lowest = 0;
         for (int i = 0; i < parts.length; i++) {
            if ("".equals(parts[i])) {
               i++;
               realParts[counter] = "-" + parts[i];
               int val = Integer.valueOf(realParts[counter]);
               if (val < lowest) {
                  lowest = val;
               }
            } else {
               realParts[counter] = parts[i];
            }
            counter++;
         }
         for (int i=0; i < realParts.length; i++) {
            realParts[i] = Integer.toString( 
                    Integer.valueOf(realParts[i]) - lowest);
         }
         parts = realParts;
      }
      
      if (parts.length < 4) {
         throw new MMException("This String does not represent a Rectangle");
      }
      try {
         Rectangle result = new Rectangle(Integer.parseInt(parts[0]),  
              Integer.parseInt(parts[1]), Integer.parseInt(parts[2]),
              Integer.parseInt(parts[3]) );
         return result;
      } catch (NumberFormatException ex) {
         throw new MMException("This String does not represent a Rectangle");
      }
      
   }
}
