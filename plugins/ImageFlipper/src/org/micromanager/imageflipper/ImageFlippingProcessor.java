package org.micromanager.examples.imageflipper;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

public class ImageFlippingProcessor extends DataProcessor<TaggedImage> {

   ImageFlipperControls controls_;

   public ImageFlippingProcessor(ImageFlipperControls controls) {
      this.controls_ = controls;
   }

   @Override
   public void process() {
      try {
         TaggedImage nextImage = poll();
         int width = MDUtils.getWidth(nextImage.tags);
         int height = MDUtils.getHeight(nextImage.tags);
         String type = MDUtils.getPixelType(nextImage.tags);
         ImageProcessor proc = ImageUtils.makeProcessor(ImagePlus.GRAY8, width, height, nextImage.pix);
         if (controls_.getFlip()) {
            proc.flipVertical();
         }
         if (controls_.getMirror()) {
            proc.flipHorizontal();
         }
         if (controls_.getRotate()) {
            proc = proc.rotateLeft();
         }
         JSONObject newTags = nextImage.tags;
         MDUtils.setWidth(newTags, proc.getWidth());
         MDUtils.setHeight(newTags, proc.getHeight());
         produce(new TaggedImage(proc.getPixels(), newTags));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }
}
