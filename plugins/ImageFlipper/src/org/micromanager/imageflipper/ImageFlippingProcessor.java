package org.micromanager.imageflipper;

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
         try {
            String camera = nextImage.tags.getString("Core-Camera");
            if (!camera.equals(controls_.getCamera())) {
               if (nextImage.tags.has("CameraChannelIndex")) {
                  camera = MDUtils.getChannelName(nextImage.tags);
               }
            }
            if (!camera.equals(controls_.getCamera())) {
               produce(nextImage);
               return;

            }
            int width = MDUtils.getWidth(nextImage.tags);
            int height = MDUtils.getHeight(nextImage.tags);
            String type = MDUtils.getPixelType(nextImage.tags);
            int ijType = ImagePlus.GRAY8;
            if (type.equals("GRAY16")) {
               ijType = ImagePlus.GRAY16;
            }

            ImageProcessor proc = ImageUtils.makeProcessor(ijType, width, height, nextImage.pix);

            if (controls_.getMirror()) {
               proc.flipHorizontal();
            }
            if (controls_.getRotate() == 1) {
               proc = proc.rotateLeft();
            }
            if (controls_.getRotate() == 2) {
               proc.flipVertical();
            }
            if (controls_.getRotate() == 3) {
               proc = proc.rotateRight();
            }
            JSONObject newTags = nextImage.tags;
            MDUtils.setWidth(newTags, proc.getWidth());
            MDUtils.setHeight(newTags, proc.getHeight());

            produce(new TaggedImage(proc.getPixels(), newTags));
         } catch (Exception ex) {
            produce(nextImage);
            ReportingUtils.logError(ex);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }
}
