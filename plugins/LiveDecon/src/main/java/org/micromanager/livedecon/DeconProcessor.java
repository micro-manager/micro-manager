///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2015
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

package org.micromanager.livedecon;

import ij.process.ImageProcessor;

import java.util.ArrayList;

import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.Image;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;
import org.micromanager.Studio;

public class DeconProcessor extends Processor {
   private Studio studio_;
   private ArrayList<Image> currentStack_;

   public DeconProcessor(Studio studio) {
      studio_ = studio;
      currentStack_ = new ArrayList<Image>();
   }

   @Override
   public void processImage(Image image, ProcessorContext context) {
      if (image.getNumComponents() != 1) {
         // Don't try to deconvolve multi-component (e.g. RGB) images. Just
         // pass them along.
         studio_.logs().logError("Unable to deconvolve multi-component images");
         context.outputImage(image);
         return;
      }
      if (image.getCoords().getZ() == 0) {
         // This image is the beginning of a new Z-stack.
         if (currentStack_.size() > 0) {
            processStack(currentStack_, context);
            currentStack_.clear();
         }
      }
      currentStack_.add(image);
   }

   @Override
   public void cleanup(ProcessorContext context) {
      // Process the final stack, if any.
      if (currentStack_.size() > 0) {
         processStack(currentStack_, context);
         currentStack_.clear();
      }
   }

   /**
    * Process a stack of images. Result images will be sent to the
    * given ProcessorContext.
    */
   private void processStack(ArrayList<Image> stack, ProcessorContext context) {
      for (Image image : stack) {
         // Example of accessing pixel data directly.
         Object pixels = image.getRawPixels();
         if (image.getBytesPerPixel() == 1) {
            // Byte array.
            byte[] pixelsArr = (byte[]) pixels;
         }
         else if (image.getBytesPerPixel() == 2) {
            // Short array.
            short[] pixelsArr = (short[]) pixels;
         }
         else {
            studio_.logs().logError("Unrecognized bytes per pixel: " + image.getBytesPerPixel());
         }
         // Can also create an ImageJ ImageProcessor for working with images
         ImageProcessor processor = studio_.data().ij().createProcessor(image);

         // Access pixel dimension information.
         Double pixelSize = image.getMetadata().getPixelSizeUm();
         Integer binning = image.getMetadata().getBinning();

         // Ultimately create a new image to save to disk.
         Image result = studio_.data().createImage(pixels, image.getWidth(),
               image.getHeight(), image.getBytesPerPixel(),
               image.getNumComponents(), image.getCoords(),
               image.getMetadata());
         // Or create it from the ImageProcessor.
         result = studio_.data().ij().createImage(processor, image.getCoords(),
               image.getMetadata());
         // And output it.
         context.outputImage(result);
      }
   }
}
