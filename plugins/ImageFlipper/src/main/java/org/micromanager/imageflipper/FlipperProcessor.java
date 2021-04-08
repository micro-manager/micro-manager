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

package org.micromanager.imageflipper;

import ij.process.ImageProcessor;


import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorContext;

import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;


public class FlipperProcessor implements Processor {

   // Valid rotation values.
   public static final int R0 = 0;
   public static final int R90 = 90;
   public static final int R180 = 180;
   public static final int R270 = 270;

   private final Studio studio_;
   String camera_;
   boolean isMirrored_;
   int rotation_;

   public FlipperProcessor(Studio studio, String camera, int rotation,
         boolean isMirrored) {
      studio_ = studio;
      camera_ = camera;
      if (rotation != R0 && rotation != R90 && rotation != R180 &&
            rotation != R270) {
         // Invalid rotation.
         throw new RuntimeException("Invalid rotation " + rotation + "; must be a multiple of 90 degrees");
      }
      rotation_ = rotation;
      isMirrored_ = isMirrored;
   }

   /**
    * Process one image.
    */
   @Override
   public void processImage(Image image, ProcessorContext context) {
      // to allow processing old data, we do not check for the camera when no 
      // camera was selected
      if (!camera_.isEmpty()) {
         String imageCam = image.getMetadata().getCamera();
         if (imageCam == null || !imageCam.equals(camera_)) {
            // Image is for the wrong camera; just pass it along unmodified.
            context.outputImage(image);
            return;
         }
      }
      context.outputImage(
              transformImage(studio_, image, isMirrored_, rotation_));
   }

   /**
    * Executes image transformation
    * First mirror the image if requested, than rotate as requested
    * 
    * @param studio
    * @param image Image to be transformed.
    * @param isMirrored Whether or not to mirror the image.
    * @param rotation Degrees to rotate by (R0, R90, R180, R270)
    * @return - Transformed Image, otherwise a copy of the input
    */
   public static Image transformImage(Studio studio, Image image,
         boolean isMirrored, int rotation) {
      
      ImageProcessor proc = studio.data().ij().createProcessor(image);

      if (isMirrored) {
         proc.flipHorizontal();
      }
      if (rotation == R90) {
         proc = proc.rotateRight();
      }
      if (rotation == R180) {
         proc = proc.rotateRight();
         proc = proc.rotateRight();
      }
      if (rotation == R270) {
         proc = proc.rotateLeft();
      }
      // Insert some metadata to indicate what we did to the image.
      PropertyMap.Builder builder;
      PropertyMap userData = image.getMetadata().getUserData();
      if (userData != null) {
         builder = userData.copyBuilder();
      }
      else {
         builder = PropertyMaps.builder();
      }
      builder.putInteger("ImageFlipper-Rotation", rotation);
      builder.putString("ImageFlipper-Mirror", isMirrored ? "On" : "Off");
      Metadata newMetadata = image.getMetadata().copyBuilderPreservingUUID().userData(builder.build()).build();
      Image result = studio.data().ij().createImage(proc, image.getCoords(),
            newMetadata);
      return result;
   }
}
