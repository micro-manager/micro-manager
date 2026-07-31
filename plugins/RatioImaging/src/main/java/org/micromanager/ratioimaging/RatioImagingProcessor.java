///////////////////////////////////////////////////////////////////////////////
//FILE:          RatioImagingProcessor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2018
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



package org.micromanager.ratioimaging;

import ij.ImagePlus;
import ij.process.Blitter;
import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import java.awt.Rectangle;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.data.SummaryMetadata;
// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.internal.utils.NumberUtils;

/**
 * DataProcessor that creates a ratio image as instructed in the UI.
 *
 * <p>The ratio is computed and emitted as 32-bit float.  Since Micro-Manager
 * datastores can not hold images of differing pixel type, the float ratio
 * images can not be added as an extra channel to the (usually 8 or 16-bit
 * integer) acquisition datastore.  They are therefore written to a datastore of
 * their own, created and managed by the RatioAcqManager.
 *
 * @author nico
 */
public class RatioImagingProcessor implements Processor {

   private final Studio studio_;
   private final PropertyMap settings_;
   private final RatioAcqManager ratioAcqManager_;
   private final int bc1Constant_;
   private final int bc2Constant_;
   private final String bc1Path_;
   private final String bc2Path_;
   private ImagePlus bc1_;
   private ImagePlus bc2_;
   private final List<Image> images_;
   private boolean process_;
   private int ch1Index_;
   private int ch2Index_;
   private String ratioChannelName_;
   private SummaryMetadata inputSummaryMetadata_;
   private Datastore ratioStore_;

   /**
    * Constructor of the Processor doing the heavy lifting.
    *
    * @param studio Studio object gives access to all we need.
    * @param settings Plugin Settings
    * @param ratioAcqManager Creates and tracks the ratio datastore and display.
    */
   public RatioImagingProcessor(Studio studio, PropertyMap settings,
                                RatioAcqManager ratioAcqManager) {
      studio_ = studio;
      settings_ = settings;
      ratioAcqManager_ = ratioAcqManager;
      images_ = new ArrayList<Image>();
      int bc1Constant = 0;
      int bc2Constant = 0;
      try {
         if (settings_.containsString(RatioImagingFrame.BACKGROUND1CONSTANT)) {
            bc1Constant = NumberUtils.displayStringToInt(
                    settings_.getString(RatioImagingFrame.BACKGROUND1CONSTANT, "0"));
         }
      } catch (ParseException pe) {
         studio_.logs().logError(pe);
      }
      try {
         if (settings_.containsString(RatioImagingFrame.BACKGROUND2CONSTANT)) {
            bc2Constant = NumberUtils.displayStringToInt(
                    settings_.getString(RatioImagingFrame.BACKGROUND2CONSTANT, "0"));
         }
      } catch (ParseException pe) {
         studio_.logs().logError(pe);
      }
      bc1Path_ = settings_.getString(RatioImagingFrame.BACKGROUND1, "");
      bc2Path_ = settings_.getString(RatioImagingFrame.BACKGROUND2, "");
      bc1Constant_ = bc1Constant;
      bc2Constant_ = bc2Constant;
   }

   @Override
   public SummaryMetadata processSummaryMetadata(SummaryMetadata summary) {

      // The ratio images go into a datastore of their own, so the incoming
      // SummaryMetadata is passed through unchanged.  Keep a copy of it as the
      // basis for the ratio datastore's SummaryMetadata.
      inputSummaryMetadata_ = summary;

      List<String> chNames = summary.getChannelNameList();
      if (chNames == null || chNames.isEmpty() || chNames.size() < 2) {
         // Can't do anything as we don't know how many names there'll be.
         return summary;
      }
      final String ch1Name = settings_.getString(RatioImagingFrame.CHANNEL1, "");
      final String ch2Name = settings_.getString(RatioImagingFrame.CHANNEL2, "");

      process_ = true;

      ch1Index_ = -1;
      ch2Index_ = -1;
      for (int i = 0; i < chNames.size(); i++) {
         if (chNames.get(i).equals(ch1Name)) {
            ch1Index_ = i;
         }
         if (chNames.get(i).equals(ch2Name)) {
            ch2Index_ = i;
         }
      }
      if (ch1Index_ < 0 || ch2Index_ < 0) {
         process_ = false;
         return summary;
      }

      ratioChannelName_ = "ratio " + ch1Name + "-" + ch2Name;

      return summary;
   }


   /**
    * Provides the background image, corrected for binning and ROI when needed.
    *
    * @param path Path on the file system to the Background image
    * @param binning Binning to be applied
    * @param roi Roi to be set
    * @param nrBytesPerPixel Desired Bytes Per Pixel
    * @return Corrected background image as an ImagePlus
    */
   public ImagePlus getBackground(String path, int binning, Rectangle roi, int nrBytesPerPixel) {

      if (path.equals("")) {
         return null;
      }
      ij.io.Opener opener = new ij.io.Opener();
      ImagePlus ip = opener.openImage(path);
      if (ip == null) {
         return null;
      }

      return makeDerivedImage(ip, binning, roi, nrBytesPerPixel);
   }
   
   /**
    * Generates a new ImagePlus from this one by applying the requested binning
    * and setting the desired ROI. Should only be called on the original image
    * (i.e. binning = 1, full field image) If the original image was normalized,
    * this one will be as well (as it is derived from the normalized image)
    *
    * @param ipi input ImagePlus object
    * @param binning Binning to be applied to the input ImagePlus
    * @param roi  To be applied to the output image
    * @return ImagePlus representation of the desired output image
    */
   private ImagePlus makeDerivedImage(ImagePlus ipi, int binning, Rectangle roi,
           int nrBytesPerPixel) {

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
      if (nrBytesPerPixel == 1) {
         resultProcessor.convertToByteProcessor(false);
      } else if (nrBytesPerPixel == 2) {
         resultProcessor.convertToShortProcessor(false);
      }
      resultProcessor.setRoi(roi);
      return new ImagePlus("", resultProcessor.crop());
   }
   

   @Override
   public void processImage(Image newImage, ProcessorContext context) {
      
      context.outputImage(newImage);
      
      if (newImage.getNumComponents() > 1) {
         return;
      }
      if (! (newImage.getBytesPerPixel() == 1 || newImage.getBytesPerPixel() == 2)) {
         return;
      }
      
      int binning = newImage.getMetadata().getBinning();
      Rectangle roi = newImage.getMetadata().getROI();
      int nrBytesPerPixel = newImage.getBytesPerPixel();
      if (bc1_ == null) {
         bc1_ = getBackground(bc1Path_, binning, roi, nrBytesPerPixel);
      }
      if (bc2_ == null) {
         bc2_ = getBackground(bc2Path_, binning, roi, nrBytesPerPixel);
      }
      
      if (!process_) {
         return;
      }

      Coords newCoords = newImage.getCoords();
      int c = newImage.getCoords().getC();
      if (!(c == ch1Index_ || c == ch2Index_)) {
         return;
      }

      for (Image oldImage : images_) {
         Coords oldCoords = oldImage.getCoords();
         if (newCoords.copyRemovingAxes(Coords.C).equals(oldCoords.copyRemovingAxes(Coords.C))) {
            if (newCoords.getC() == ch1Index_ && oldCoords.getC() == ch2Index_) {
               process(newImage, oldImage);
               images_.remove(oldImage);
               return;
            }

            if (oldCoords.getC() == ch1Index_ && newCoords.getC() == ch2Index_) {
               process(oldImage, newImage);
               images_.remove(oldImage);
               return;
            }
         }
      }
      
      // if we are still here, there was no match, so add this image to our list
      images_.add(newImage);

   }
      
   /**
    * Computes the float ratio of the two images and writes it to the ratio
    * datastore, creating that datastore (and its display) on first use.
    *
    * <p>The result is not handed to the ProcessorContext: it is 32-bit float
    * and so can not be added to the acquisition datastore alongside the
    * integer source channels.
    *
    * @param ch1Image Numerator image.
    * @param ch2Image Denominator image.
    */
   private void process(Image ch1Image, Image ch2Image) {

      // The ratio datastore holds a single channel, so the ratio image always
      // lives at channel index 0 there.
      final Coords ratioCoords = ch1Image.getCoords().copyBuilder().c(0).build();

      ImageProcessor ch1Proc = studio_.data().ij().createProcessor(ch1Image);
      ImageProcessor ch2Proc = studio_.data().ij().createProcessor(ch2Image);
      if (bc1_ != null) {
         ch1Proc = subtractImageProcessors(ch1Proc, bc1_.getProcessor());
      }
      if (bc2_ != null) {
         ch2Proc = subtractImageProcessors(ch2Proc, bc2_.getProcessor());
      }
      ch1Proc = ch1Proc.convertToFloat();
      ch2Proc = ch2Proc.convertToFloat();
      ch1Proc.subtract(bc1Constant_);
      ch2Proc.subtract(bc2Constant_);
      ImageProcessor ch3Proc = ch1Proc.createProcessor(ch1Proc.getWidth(),
              ch1Proc.getHeight());
      ch3Proc.insert(ch1Proc, 0, 0);
      ch3Proc.copyBits(ch2Proc, 0, 0, Blitter.DIVIDE);

      // ch3Proc is a FloatProcessor, and is deliberately left as such: the
      // ratio is output as 32-bit float rather than being scaled and rounded
      // into an integer range.  Where ch2 is zero the division yields NaN or
      // Infinity; these are left alone.  They mark "undefined" and are excluded
      // from the histogram and statistics by ImageStatsProcessor.
      Image ratioImage = studio_.data().ij().createImage(ch3Proc, ratioCoords,
              ch1Image.getMetadata().copyBuilderWithNewUUID().bitDepth(32)
                          .build());

      if (ratioStore_ == null) {
         String prefix = (inputSummaryMetadata_ == null
                 || inputSummaryMetadata_.getPrefix() == null
                 || inputSummaryMetadata_.getPrefix().isEmpty())
                 ? "Untitled" : inputSummaryMetadata_.getPrefix();
         try {
            ratioStore_ = ratioAcqManager_.createStoreAndDisplay(inputSummaryMetadata_,
                    ratioChannelName_, prefix + "-Ratio",
                    ratioImage.getWidth(), ratioImage.getHeight());
         } catch (IOException ioe) {
            studio_.logs().logError(ioe);
            process_ = false;
            return;
         }
         if (ratioStore_ == null) {
            process_ = false;
            return;
         }
      }

      try {
         ratioStore_.putImage(ratioImage);
      } catch (IOException ioe) {
         studio_.logs().logError(ioe);
      }
   }

   @Override
   public void cleanup(ProcessorContext context) {
      images_.clear();
      if (ratioStore_ != null) {
         final Datastore store = ratioStore_;
         try {
            store.freeze();
            if (store.getNumImages() == 0) {
               // Nothing was produced; close the empty window rather than
               // leaving it behind.  Window work is deferred to the EDT since
               // cleanup() blocks Pipeline.halt() and may run on a Processor
               // thread.
               SwingUtilities.invokeLater(() -> {
                  ratioAcqManager_.closeViewerFor(store);
                  try {
                     store.close();
                  } catch (IOException ioe) {
                     studio_.logs().logError(ioe);
                  }
               });
            }
         } catch (IOException ioe) {
            studio_.logs().logError(ioe);
         }
         ratioStore_ = null;
      }
   }
   
   private static ByteProcessor subtractByteProcessors(ByteProcessor proc1, ByteProcessor proc2) {
      return new ByteProcessor(proc1.getWidth(), proc1.getHeight(),
              subtractPixelArrays((byte []) proc1.getPixels(), (byte []) proc2.getPixels()),
              null);
   }

   private static ShortProcessor subtractShortProcessors(ShortProcessor proc1,
                                                         ShortProcessor proc2) {
      return new ShortProcessor(proc1.getWidth(), proc1.getHeight(),
              subtractPixelArrays((short []) proc1.getPixels(), (short []) proc2.getPixels()),
              null);
   }

   /**
    * Subtracts array 2 from array 1 and returens the result.
    *
    * @param array1 Source array
    * @param array2 Array to subtract (member by member) from the source array
    * @return Resulting array
    */
   public static byte[] subtractPixelArrays(byte[] array1, byte[] array2) {
      int l = array1.length;
      byte[] result = new byte[l];
      for (int i = 0; i < l; ++i) {
         result[i] = (byte) Math.max(0, unsignedValue(array1[i])
               - unsignedValue(array2[i]));
      }
      return result;
   }

   /**
    * Subtracts array 2 from array 1 and returens the result.
    *
    * @param array1 Source array
    * @param array2 Array to subtract (member by member) from the source array
    * @return Resulting array
    */
   public static short[] subtractPixelArrays(short[] array1, short[] array2) {
      int l = array1.length;
      short[] result = new short[l];
      for (int i = 0; i < l; ++i) {
         result[i] = (short) Math.max(0, unsignedValue(array1[i]) - unsignedValue(array2[i]));
      }
      return result;
   }
   
   public static int unsignedValue(byte b) {
      // Sign-extend, then mask
      return ((int) b) & 0x000000ff;
   }

   public static int unsignedValue(short s) {
      // Sign-extend, then mask
      return ((int) s) & 0x0000ffff;
   }

   /**
    * Subtracts ImageProcessor 2 from 1 and returns the result.
    *
    * @param proc1 Source image
    * @param proc2 Image subtract (pixel by pixel) from the proc1
    * @return Resulting Image
    */
   public static ImageProcessor subtractImageProcessors(ImageProcessor proc1,
                                                        ImageProcessor proc2) {
      if ((proc1.getWidth() != proc2.getWidth())
              || (proc1.getHeight() != proc2.getHeight())) {
         return null;
      }

      if (proc1 instanceof ByteProcessor && proc2 instanceof ByteProcessor) {
         return subtractByteProcessors((ByteProcessor) proc1, (ByteProcessor) proc2);
      } else if (proc1 instanceof ShortProcessor && proc2 instanceof ShortProcessor) {
         return subtractShortProcessors((ShortProcessor) proc1, (ShortProcessor) proc2);
      }
      
      return null;
   }
}
