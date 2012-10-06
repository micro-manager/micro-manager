///////////////////////////////////////////////////////////////////////////////
//FILE:          NewImageFlippingProcessor.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Arthur Edelstein, Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2012
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
package org.micromanager.newimageflipper;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class NewImageFlippingProcessor extends DataProcessor<TaggedImage> {

   static public enum Rotation {

      R0, R90, R180, R270
   }
   NewImageFlipperControls controls_;

   public NewImageFlippingProcessor(NewImageFlipperControls controls) {
      this.controls_ = controls;
   }

   /**
    * Polls for tagged images, and processes them if they are from the selected 
    * camera.
    * 
    */
   @Override
   public void process() {
      try {
         TaggedImage nextImage = poll();
         if (nextImage != TaggedImageQueue.POISON) {
            try {
               String camera = nextImage.tags.getString("Core-Camera");
               if (!camera.equals(controls_.getCamera())) {
                  if (nextImage.tags.has("Camera")) {
                     camera = nextImage.tags.getString("Camera");
                  }
               }
               if (!camera.equals(controls_.getCamera())) {
                  produce(nextImage);
                  return;

               }

               produce(proccessTaggedImage(nextImage, controls_.getMirror(),
                       controls_.getRotate()));

            } catch (Exception ex) {
               produce(nextImage);
               ReportingUtils.logError(ex);
            }
         } else {
            //Must produce Poison image so LiveAcq Thread terminates properly
            produce(nextImage);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   /**
    * Executes image transformation
    * First mirror the image if requested, than rotate as requested
    * 
    * @param nextImage - TaggedImage to be transformed
    * @param mirror - Whether or not to mirror
    * @param rotation - Rotation (R0, R90, R180, R270)
    * @return - Transformed tagged image, otherwise a copy of the input
    * @throws JSONException
    * @throws MMScriptException 
    */
   public static TaggedImage proccessTaggedImage(TaggedImage nextImage,
           boolean mirror, Rotation rotation) throws JSONException, MMScriptException {

      int width = MDUtils.getWidth(nextImage.tags);
      int height = MDUtils.getHeight(nextImage.tags);
      String type = MDUtils.getPixelType(nextImage.tags);
      int ijType = ImagePlus.GRAY8;
      if (type.equals("GRAY16")) {
         ijType = ImagePlus.GRAY16;
      }

      ImageProcessor proc = ImageUtils.makeProcessor(ijType, width, height, nextImage.pix);

      if (mirror) {
         proc.flipHorizontal();
      }
      if (rotation == Rotation.R90) {
         proc = proc.rotateRight();
      }
      if (rotation == Rotation.R180) {
         proc = proc.rotateRight();
         proc = proc.rotateRight();
      }
      if (rotation == Rotation.R270) {
         proc = proc.rotateLeft();
      }
      JSONObject newTags = nextImage.tags;
      MDUtils.setWidth(newTags, proc.getWidth());
      MDUtils.setHeight(newTags, proc.getHeight());

      return new TaggedImage(proc.getPixels(), newTags);
   }
}
