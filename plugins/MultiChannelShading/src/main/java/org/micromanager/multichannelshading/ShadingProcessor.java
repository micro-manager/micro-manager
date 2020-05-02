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

import clearcl.ClearCL;
import clearcl.ClearCLBuffer;
import clearcl.ClearCLContext;
import clearcl.ClearCLDevice;
import clearcl.ClearCLKernel;
import clearcl.ClearCLProgram;
import clearcl.backend.ClearCLBackends;
import clearcl.enums.BuildStatus;
import clearcl.exceptions.OpenCLException;
import coremem.enums.NativeTypeEnum;
import ij.process.ImageProcessor;

import java.awt.Rectangle;
import java.io.IOException;
import java.util.List;

import mmcorej.Configuration;
import mmcorej.PropertySetting;
import static org.junit.Assert.assertEquals;

import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.data.internal.DefaultImage;

/**
 *
 * @author nico, modified for MM2.0 by Chris Weisiger
 */
public class ShadingProcessor extends Processor {

   private final Studio studio_;
   private final String channelGroup_;
   private Boolean useOpenCL_;
   private final List<String> presets_;
   private final ImageCollection imageCollection_;
   private ClearCL ccl_;
   private ClearCLContext cclContext_;
   private ClearCLProgram cclProgram_;

   public ShadingProcessor(Studio studio, String channelGroup,
           Boolean useOpenCL, String backgroundFile, List<String> presets,
           List<String> files) {
      studio_ = studio;
      channelGroup_ = channelGroup;
      useOpenCL_ = useOpenCL;
      if (useOpenCL_) {
         ccl_ = new ClearCL(ClearCLBackends.getBestBackend()); 
         ClearCLDevice bestGPUDevice = ccl_.getBestGPUDevice();
         if (bestGPUDevice == null) { // assume that is what is returned if there is no GPU
            useOpenCL_ = false;
         } else {
            try {
            cclContext_ = bestGPUDevice.createContext();
            cclProgram_ = cclContext_.createProgram(ShadingProcessor.class,
                                                     "bufferMath.cl");
            BuildStatus lBuildStatus = cclProgram_.buildAndLog();

            assertEquals(lBuildStatus, BuildStatus.Success);
            } catch (IOException ioe) {
               studio_.alerts().postAlert(MultiChannelShading.MENUNAME, this.getClass(), 
                       "Failed to initialize OpenCL, falling back");
               useOpenCL_ = false;
            }
         }
      }
      presets_ = presets;
      imageCollection_ = new ImageCollection(studio_);
      if (backgroundFile != null && !backgroundFile.equals("")) {
         try {
            imageCollection_.setBackground(backgroundFile);
         } catch (ShadingException e) {
            studio_.logs().logError(e, "Unable to set background file to " + backgroundFile);
         }
      }
      try {
         for (int i = 0; i < presets.size(); ++i) {
            imageCollection_.addFlatField(presets.get(i), files.get(i));
         }
      } catch (ShadingException e) {
         studio_.logs().logError(e, "Error recreating ImageCollection");
      }

   }

   // Classes used to classify alerts in the processImage function below
   private class Not8or16BitClass {   }

   private class NoBinningInfoClass {   }

   private class NoRoiClass {   }

   private class NoBackgroundForThisBinModeClass {   }

   private class ErrorSubtractingClass {   }
   
   private class ErrorInOpenCLClass {}

   @Override
   public void processImage(Image image, ProcessorContext context) {
      int width = image.getWidth();
      int height = image.getHeight();

      // For now, this plugin only works with 8 or 16 bit grayscale images
      if (image.getNumComponents() > 1 || image.getBytesPerPixel() > 2) {
         String msg = "Cannot flatfield correct images other than 8 or 16 bit grayscale";
         studio_.alerts().postAlert(MultiChannelShading.MENUNAME, Not8or16BitClass.class, msg);
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
         String msg = "MultiShadingPlugin: Image metadata did not contain Binning information.";
         studio_.alerts().postAlert(MultiChannelShading.MENUNAME, NoBinningInfoClass.class, msg);
         // Assume binning is 1
         binning = 1;
      }
      Rectangle rect = metadata.getROI();
      if (rect == null) {
         String msg = "MultiShadingPlugin: Image metadata did not list ROI.";
         studio_.alerts().postAlert(MultiChannelShading.MENUNAME, NoRoiClass.class, msg);
      }
      ImagePlusInfo background = null;
      try {
         background = imageCollection_.getBackground(binning, rect);
      } catch (ShadingException e) {
         String msg = "Error getting background for bin mode " + binning + " and rect " + rect;
         studio_.alerts().postAlert(MultiChannelShading.MENUNAME,
                 NoBackgroundForThisBinModeClass.class, msg);
      }

      ImagePlusInfo flatFieldImage = getMatchingFlatFieldImage(
              metadata, binning, rect);

      if (useOpenCL_) {
         try {
            ClearCLBuffer clImg, clBackground, clFlatField;
            String suffix;
            if (image.getBytesPerPixel() == 2) {
               clImg = cclContext_.createBuffer(NativeTypeEnum.UnsignedShort,
                       image.getWidth() * image.getHeight());
               suffix = "US";
            } else { //(image.getBytesPerPixel() == 1) 
               clImg = cclContext_.createBuffer(NativeTypeEnum.UnsignedByte,
                       image.getWidth() * image.getHeight());
               suffix = "UB";
            }

            // copy image to the GPU
            clImg.readFrom(((DefaultImage) image).getPixelBuffer(), false);
            // process with different kernels depending on availability of flatfield
            // and background:
            if (background != null && flatFieldImage == null) {
               clBackground = background.getCLBuffer(cclContext_);
               // need to use different kernels for differe types
               ClearCLKernel lKernel = cclProgram_.createKernel("subtract" + suffix);
               lKernel.setArguments(clImg, clBackground);
               lKernel.setGlobalSizes(clImg);
               lKernel.run();
            } else if (background == null && flatFieldImage != null) {
               clFlatField = flatFieldImage.getCLBuffer(cclContext_);
               ClearCLKernel lKernel = cclProgram_.createKernel("multiply" + suffix + "F");
               lKernel.setArguments(clImg, clFlatField);
               lKernel.setGlobalSizes(clImg);
               lKernel.run();
            } else if (background != null && flatFieldImage != null) {
               clBackground = background.getCLBuffer(cclContext_);
               clFlatField = flatFieldImage.getCLBuffer(cclContext_);
               ClearCLKernel lKernel = cclProgram_.createKernel("subtractAndMultiply" + suffix + "F");
               lKernel.setArguments(clImg, clBackground, clFlatField);
               lKernel.setGlobalSizes(clImg);
               lKernel.run();
            }
            // copy processed image back from the GPU
            clImg.writeTo(((DefaultImage) image).getPixelBuffer(), true);
            // release resources.  If more GPU processing is desired, this should change
            clImg.close();
            context.outputImage(image);
            return;
         } catch (OpenCLException ocle) {
            studio_.alerts().postAlert(MultiChannelShading.MENUNAME,
                    ErrorInOpenCLClass.class,
                    "Error using GPU: " + ocle.getMessage());
            useOpenCL_ = false;
         }
      }


      if (background != null) {
         ImageProcessor ip = studio_.data().ij().createProcessor(image);
         ImageProcessor ipBackground = background.getProcessor();
         try {
            ip = ImageUtils.subtractImageProcessors(ip, ipBackground);
            if (userData != null) {
               userData = userData.copy().putBoolean("Background-corrected", true).build();
            }
         } catch (ShadingException e) {
            String msg = "Unable to subtract background: " + e.getMessage();
            studio_.alerts().postAlert(MultiChannelShading.MENUNAME, 
                 ErrorSubtractingClass.class, msg);
         }
         bgSubtracted = studio_.data().ij().createImage(ip, image.getCoords(),
                 metadata.copy().userData(userData).build());
      }


      // do not calculate flat field if we don't have a matching channel;
      // just return the background-subtracted image (which is the unmodified
      // image if we also don't have a background subtraction file).
      if (flatFieldImage == null) {
         context.outputImage(bgSubtracted);
         return;
      }

      if (userData != null) {
         userData = userData.copy().putBoolean("Flatfield-corrected", true).build();
         metadata = metadata.copy().userData(userData).build();
      }
      
      
      
      if (image.getBytesPerPixel() == 1) {
         byte[] newPixels = new byte[width * height];
         byte[] oldPixels = (byte[]) image.getRawPixels();
         int length = oldPixels.length;
         float[] flatFieldPixels = (float[]) flatFieldImage.getProcessor().getPixels();
         for (int index = 0; index < length; index++) {
            float oldPixel = (float) ((int) (oldPixels[index]) & 0x000000ff);
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
         for (int index = 0; index < length; index++) {
            // shorts are signed in java so have to do this conversion to get 
            // the right value
            float oldPixel = (float) ((int) (oldPixels[index]) & 0x0000ffff);
            float newValue = (oldPixel
                    * flatFieldImage.getProcessor().getf(index)) + 0.5f;
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
    * Given the metadata of the image currently being processed, find a matching
    * preset from the channelgroup used by the tablemodel
    *
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
            boolean presetMatch = true;
            for (int i = 0; i < config.size(); i++) {
               PropertySetting ps = config.getSetting(i);
               String key = ps.getKey();
               String value = ps.getPropertyValue();
               if (scopeData.containsKey(key)
                       && scopeData.getPropertyType(key) == String.class) {
                  String scopeSetting = scopeData.getString(key);
                  if (!value.equals(scopeSetting)) {
                     presetMatch = false;
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
