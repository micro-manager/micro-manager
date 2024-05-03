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

import static org.junit.Assert.assertEquals;

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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultImage;

/**
 * ImageProcessor that calculates background corrected and flatfield corrected image.
 *
 * @author nico, modified for MM2.0 by Chris Weisiger
 */
public class ShadingProcessor implements Processor {

   private final Studio studio_;
   private final String channelGroup_;
   private SummaryMetadata summaryMetadata_;
   private boolean match_ = true;
   private Boolean useOpenCL_;
   private final List<String> presets_;
   private final ImageCollection imageCollection_;
   private ClearCL ccl_;
   private ClearCLContext cclContext_;
   private ClearCLProgram cclProgram_;
   private Boolean isAcqRunning_ = false;

   private final Set<Class<?>> alertSet_ = new HashSet<>();

   /**
    * Constructor of the Image Processor.
    *
    * @param studio Always present studio object
    * @param channelGroup name of the configuration group that is used to set the image channels
    * @param useOpenCL Whether to use OpenCL for processing
    * @param backgroundFile File containing the background image
    * @param presets List of presets
    * @param files List of files, should be the same length as presets and provide the
    *              flatfield image corresponding to each preset
    */
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
               if (!alertSet_.contains(this.getClass())) {
                  studio_.alerts().postAlert(MultiChannelShading.MENUNAME, this.getClass(),
                          "Failed to initialize OpenCL, falling back");
                  alertSet_.add(this.getClass());
               }
               useOpenCL_ = false;
            }
         }
      }
      presets_ = presets;
      imageCollection_ = new ImageCollection(studio_);
      if (backgroundFile != null && !backgroundFile.isEmpty()) {
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
   private static class Not8or16BitClass {
   }

   private static class NoBinningInfoClass {
   }

   private static class NoRoiClass {
   }

   private static class NoBackgroundForThisBinModeClass {
   }

   private static class ErrorSubtractingClass {
   }

   private static class NotFlatFieldedClass {
   }

   private static class ErrorInOpenCLClass {
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata source) {
      isAcqRunning_ = studio_.acquisitions().isAcquisitionRunning();
      summaryMetadata_ = source;
      match_ = false;
      if (channelGroup_.equals(summaryMetadata_.getChannelGroup())) {
         if (!isAcqRunning_) {
            match_ = true;
         } else {
            for (String imagePreset : summaryMetadata_.getChannelNameList()) {
               for (String preset : presets_) {
                  if (preset.equals(imagePreset)) {
                     match_ = true;
                     break;
                  }
               }
            }
         }
      }
      if (!match_) {
         StringBuilder presetB = new StringBuilder();
         List<String> channelList = summaryMetadata_.getChannelNameList();
         for (int i = 0; i < channelList.size() - 1; i++) {
            presetB.append(channelList.get(i)).append(",");
         }
         if (!channelList.isEmpty()) {
            presetB.append(channelList.get(channelList.size() - 1));
         }
         String msg = "No matching channel and group found.  Add group "
               + summaryMetadata_.getChannelGroup() + " and preset(s): " + presetB.toString();
         studio_.logs().logError(msg);
         studio_.alerts().postAlert(MultiChannelShading.MENUNAME, this.getClass(), msg);
      }
      return source;
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      if (!match_) {
         context.outputImage(image);
         return;
      }
      final int width = image.getWidth();
      final int height = image.getHeight();

      // For now, this plugin only works with 8 or 16 bit grayscale images
      if (image.getNumComponents() > 1 || image.getBytesPerPixel() > 2) {
         if (!alertSet_.contains(Not8or16BitClass.class)) {
            String msg = "Cannot flatfield correct images other than 8 or 16 bit grayscale";
            studio_.alerts().postAlert(MultiChannelShading.MENUNAME, Not8or16BitClass.class, msg);
            alertSet_.add(Not8or16BitClass.class);
         }
         context.outputImage(image);
         return;
      }

      Metadata metadata = image.getMetadata();
      final Image result;

      // subtract background
      Integer binning = metadata.getBinning();
      if (binning == null) {
         if (!alertSet_.contains(NoBinningInfoClass.class)) {
            String msg = "MultiShadingPlugin: Image metadata did not contain Binning information.";
            studio_.alerts().postAlert(MultiChannelShading.MENUNAME, NoBinningInfoClass.class, msg);
            alertSet_.add(NoBinningInfoClass.class);
         }
         // Assume binning is 1
         binning = 1;
      }
      Rectangle rect = metadata.getROI();
      if (rect == null) {
         if (!alertSet_.contains(NoRoiClass.class)) {
            String msg = "MultiShadingPlugin: Image metadata did not list ROI.";
            studio_.alerts().postAlert(MultiChannelShading.MENUNAME, NoRoiClass.class, msg);
            alertSet_.add(NoRoiClass.class);
         }
      }
      ImagePlusInfo background = null;
      try {
         background = imageCollection_.getBackground(binning, rect);
      } catch (ShadingException e) {
         if (!alertSet_.contains(NoBackgroundForThisBinModeClass.class)) {
            String msg = "Error getting background for bin mode " + binning + " and rect " + rect;
            studio_.alerts().postAlert(MultiChannelShading.MENUNAME,
                  NoBackgroundForThisBinModeClass.class, msg);
            alertSet_.add(NoBackgroundForThisBinModeClass.class);
         }
      }

      ImagePlusInfo flatFieldImage = getMatchingFlatFieldImage(
            image, binning, rect);

      if (useOpenCL_) {
         try {
            ClearCLBuffer clImg;
            ClearCLBuffer clBackground;
            ClearCLBuffer clFlatField;
            String suffix;
            if (image.getBytesPerPixel() == 2) {
               clImg = cclContext_.createBuffer(NativeTypeEnum.UnsignedShort,
                       (long) image.getWidth() * image.getHeight());
               suffix = "US";
            } else { //(image.getBytesPerPixel() == 1) 
               clImg = cclContext_.createBuffer(NativeTypeEnum.UnsignedByte,
                       (long) image.getWidth() * image.getHeight());
               suffix = "UB";
            }

            // copy image to the GPU
            clImg.readFrom(((DefaultImage) image).getPixelBuffer(), false);
            // process with different kernels depending on availability of flatfield
            // and background:
            if (background != null && flatFieldImage == null) {
               clBackground = background.getCLBuffer(cclContext_);
               // need to use different kernels for different types
               ClearCLKernel lKernel = cclProgram_.createKernel("subtract" + suffix);
               lKernel.setArguments(clImg, clBackground);
               lKernel.setGlobalSizes(clImg);
               lKernel.run();
               if (!alertSet_.contains(NotFlatFieldedClass.class)) {
                  String msg = "MultiShadingPlugin: Only background subtracted "
                        + "(no flatfield found).";
                  studio_.alerts()
                          .postAlert(MultiChannelShading.MENUNAME, NotFlatFieldedClass.class, msg);
                  alertSet_.add(NotFlatFieldedClass.class);
               }
            } else if (background == null && flatFieldImage != null) {
               clFlatField = flatFieldImage.getCLBuffer(cclContext_);
               ClearCLKernel lKernel = cclProgram_.createKernel("multiply" + suffix + "F");
               lKernel.setArguments(clImg, clFlatField);
               lKernel.setGlobalSizes(clImg);
               lKernel.run();
            } else if (background != null) {
               clBackground = background.getCLBuffer(cclContext_);
               clFlatField = flatFieldImage.getCLBuffer(cclContext_);
               ClearCLKernel lKernel =
                     cclProgram_.createKernel("subtractAndMultiply" + suffix + "F");
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
            if (!alertSet_.contains(ErrorInOpenCLClass.class)) {
               studio_.alerts().postAlert(MultiChannelShading.MENUNAME,
                     ErrorInOpenCLClass.class, "Error using GPU: " + ocle.getMessage());
               alertSet_.add(ErrorInOpenCLClass.class);
            }
            useOpenCL_ = false;
         }
      }

      PropertyMap userData = metadata.getUserData();

      if (background != null) {
         ImageProcessor ip = studio_.data().ij().createProcessor(image);
         ImageProcessor ipBackground = background.getProcessor();
         try {
            ip = ImageUtils.subtractImageProcessors(ip, ipBackground);
            if (userData != null) {
               userData = userData.copyBuilder().putBoolean("Background-corrected", true).build();
            }
         } catch (ShadingException e) {
            if (!alertSet_.contains(ErrorSubtractingClass.class)) {
               String msg = "Unable to subtract background: " + e.getMessage();
               studio_.alerts().postAlert(MultiChannelShading.MENUNAME,
                     ErrorSubtractingClass.class, msg);
               alertSet_.add(ErrorSubtractingClass.class);
            }
         }
         image = studio_.data().ij().createImage(ip, image.getCoords(),
               metadata.copyBuilderWithNewUUID().userData(userData).build());
      } else {
         if (!alertSet_.contains(NoBackgroundForThisBinModeClass.class)) {
            String msg = "No background available...";
            studio_.alerts().postAlert(MultiChannelShading.MENUNAME,
                  NoBackgroundForThisBinModeClass.class, msg);
            alertSet_.add(NoBackgroundForThisBinModeClass.class);
         }
      }


      // do not calculate flat field if we don't have a matching channel;
      // just return the background-subtracted image (which is the unmodified
      // image if we also don't have a background subtraction file).
      if (flatFieldImage == null) {
         if (!alertSet_.contains(NotFlatFieldedClass.class)) {
            String msg = "No flatfield found...";
            studio_.alerts().postAlert(MultiChannelShading.MENUNAME,
                    NotFlatFieldedClass.class, msg);
            alertSet_.add(NotFlatFieldedClass.class);
         }
         context.outputImage(image);
         return;
      }

      if (userData != null) {
         userData = userData.copyBuilder().putBoolean("Flatfield-corrected", true).build();
         metadata = metadata.copyBuilderWithNewUUID().userData(userData).build();
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
         short[] oldPixels = (short[]) image.getRawPixels();
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
    * Given the metadata of the image currently being processed, find a match
    * in channelgroup and channelname in our tablemodel.
    *
    * @param image image being processed
    * @return matching flat field image
    */
   ImagePlusInfo getMatchingFlatFieldImage(Image image, int binning,
                                           Rectangle rect) {
      //PropertyMap scopeData = metadata.getScopeData();
      for (String preset : presets_) {
         // summary metadata is set when using an existing datastore, but not for
         // snap/live.
         if (isAcqRunning_) {
            String imageChannelGroup = summaryMetadata_.getChannelGroup();
            String imagePreset =
                  summaryMetadata_.getSafeChannelName(image.getCoords().getChannel());
            if (channelGroup_.equals(imageChannelGroup) && preset.equals(imagePreset)) {
               try {
                  return imageCollection_.getFlatField(preset, binning, rect);
               } catch (ShadingException e) {
                  studio_.logs().logError("No flatfield image defined for "
                        + imageChannelGroup + "-" + imagePreset);
               }
            }
         } else { // for snap/live we can rely on current settings
            String channelGroup = studio_.core().getChannelGroup();
            try {
               String corePreset = studio_.core().getCurrentConfig(channelGroup);
               if (corePreset.equals(preset)) {
                  try {
                     return imageCollection_.getFlatField(preset, binning, rect);
                  } catch (ShadingException e) {
                     studio_.logs().logError("No flatfield image defined for "
                           + channelGroup + "-" + preset);
                  }
               }
            } catch (Exception ex) {
               studio_.logs().logError(ex.getMessage());
            }

         }
         /*
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
         */
      }
      return null;
   }

   public ImageCollection getImageCollection() {
      return imageCollection_;
   }

}
