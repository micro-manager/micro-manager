///////////////////////////////////////////////////////////////////////////////
//FILE:          ShadingProcessor.java
//PROJECT:       Micro-Manager  
//SUBSYSTEM:     MultiChannelShading plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Kurt Thorn, Nico Stuurman
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
import ij.process.ImageProcessor;
import java.awt.Rectangle;
import java.util.Iterator;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.TaggedImage;
import org.micromanager.data.Coords;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.internal.utils.ImageUtils;
import org.micromanager.internal.utils.MMException;

/**
 *
 * @author nico, modified for MM2.0 by Chris Weisiger
 */
public class ShadingProcessor extends Processor {
   private Studio studio_;
   private ImageCollection imageCollection_;
   private String channelGroup_;
   private String[] presets_;

   public ShadingProcessor(Studio studio, String channelGroup,
         String backgroundFile, String[] presets,
         String[] files) {
      studio_ = studio;
      channelGroup_ = channelGroup;
      presets_ = presets;
      imageCollection_ = new ImageCollection(studio_);
      if (backgroundFile != null && !backgroundFile.equals("")) {
         try {
            imageCollection_.setBackground(backgroundFile);
         }
         catch (MMException e) {
            studio_.logs().logError(e, "Unable to set background file to " + backgroundFile);
         }
      }
      try {
         for (int i = 0; i < presets.length; ++i) {
            imageCollection_.addFlatField(presets[i], files[i]);
         }
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Error recreating ImageCollection");
      }
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      int width = image.getWidth();
      int height = image.getHeight();
      
      // For now, this plugin only works with 8 or 16 bit grayscale images
      if (image.getNumComponents() > 1 || image.getBytesPerPixel() > 2) {
         String msg = "Cannot flatfield correct images other than 8 or 16 bit grayscale";
         studio_.logs().logError(msg);
         context.outputImage(image);
         return;
      }

      Metadata metadata = image.getMetadata();
      PropertyMap userData = metadata.getUserData();

      Image bgSubtracted = image;
      Image result;
      // subtract background
      Integer binning = metadata.getBinning();
      if (binning == null) {
         // Assume binning is 1
         binning = 1;
      }
      Rectangle rect = metadata.getROI();
      ImagePlusInfo background = null;
      try {
         background = imageCollection_.getBackground(binning, rect);
      }
      catch (MMException e) {
         studio_.logs().logError(e, "Error getting background for bin mode " + binning + " and rect " + rect);
      }
      if (background != null) {
         ImageProcessor ip = studio_.data().ij().createProcessor(image);
         ImageProcessor ipBackground = background.getProcessor();
         try {
            ip = ImageUtils.subtractImageProcessors(ip, ipBackground);
            userData = userData.copy().putBoolean("Background-corrected", true).build();
         }
         catch (MMException e) {
            studio_.logs().logError(e, "Unable to subtract background");
         }
         bgSubtracted = studio_.data().ij().createImage(ip, image.getCoords(),
               metadata.copy().userData(userData).build());
      }
      
      ImagePlusInfo flatFieldImage = getMatchingFlatFieldImage(
            metadata, binning, rect);       

      // do not calculate flat field if we don't have a matching channel;
      // just return the background-subtracted image (which is the unmodified
      // image if we also don't have a background subtraction file).
      if (flatFieldImage == null) {
         context.outputImage(bgSubtracted);
         return;
      }

      userData = userData.copy().putBoolean("Flatfield-corrected", true).build();
      metadata = metadata.copy().userData(userData).build();
      if (image.getBytesPerPixel() == 1) {
         byte[] newPixels = new byte[width * height];
         byte[] oldPixels = (byte[]) image.getRawPixels();
         int length = oldPixels.length;
         float[] flatFieldPixels = (float[]) flatFieldImage.getProcessor().getPixels();
         for (int index = 0; index < length; index++){
            float oldPixel = (float)((int)(oldPixels[index]) & 0x000000ff);
            float newValue = oldPixel * flatFieldPixels[index];
            if (newValue > 2 * Byte.MAX_VALUE) {
               newValue = 2 * Byte.MAX_VALUE;
            }
            newPixels[index] = (byte) (newValue);
         }
         result = studio_.data().createImage(newPixels, width, height,
               1, 1, image.getCoords(), metadata);
         context.outputImage(result);       
      } else if (image.getBytesPerPixel() == 2) {
         short[] newPixels = new short[width * height];
         short[] oldPixels = (short[]) bgSubtracted.getRawPixels();
         int length = oldPixels.length;
         for (int index = 0; index < length; index++){
            // shorts are signed in java so have to do this conversion to get 
            // the right value
            float oldPixel = (float)((int)(oldPixels[index]) & 0x0000ffff);
            float newValue = (oldPixel * 
                    flatFieldImage.getProcessor().getf(index)) + 0.5f;
            if (newValue > 2 * Short.MAX_VALUE) {
               newValue = 2 * Short.MAX_VALUE;
            }
            newPixels[index] = (short) (((int) newValue) & 0x0000ffff);
         }
         result = studio_.data().createImage(newPixels, width, height,
               2, 1, image.getCoords(), metadata);
         context.outputImage(result);       
      }
   }

   /**
    * Given the metadata of the image currently being processed,
    * find a matching preset from the channelgroup used by the tablemodel
    * @param metadata Metadata of image being processed
    * @return matching flat field image
    */
   ImagePlusInfo getMatchingFlatFieldImage(Metadata metadata, int binning, 
           Rectangle rect) {
      PropertyMap scopeData = metadata.getScopeData();
      for (String preset : presets_) {
         try {
            Configuration config = studio_.getCMMCore().getConfigData(
                    channelGroup_, preset);
            boolean presetMatch = false;
            for (int i = 0; i < config.size(); i++) {
               PropertySetting ps = config.getSetting(i);
               String key = ps.getKey();
               String value = ps.getPropertyValue();
               if (scopeData.containsKey(key) &&
                     scopeData.getPropertyType(key) == String.class) {
                  String tagValue = scopeData.getString(key);
                  if (value.equals(tagValue)) {
                     presetMatch = true;
                     break;
                  }
               }
            }
            if (presetMatch) {
               return imageCollection_.getFlatField(preset, binning, rect);
            }
         } catch (Exception ex) {
            studio_.logs().logError(ex, "Exception in tag matching");
         }
      }      
      return null;
   }
   
   public ImageCollection getImageCollection() {
      return imageCollection_;
   }
}
