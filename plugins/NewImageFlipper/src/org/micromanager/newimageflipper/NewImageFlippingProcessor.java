package org.micromanager.newimageflipper;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class NewImageFlippingProcessor extends DataProcessor<TaggedImage> {

   static public enum Rotation {R0, R90, R180, R270}
   
   NewImageFlipperControls controls_;

   public NewImageFlippingProcessor(NewImageFlipperControls controls) {
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
            
            produce(proccessTaggedImage(nextImage, controls_.getMirror(), 
                    controls_.getRotate()));
            int width = MDUtils.getWidth(nextImage.tags);
            int height = MDUtils.getHeight(nextImage.tags);
            String type = MDUtils.getPixelType(nextImage.tags);
            int ijType = ImagePlus.GRAY8;
            if (type.equals("GRAY16")) {
               ijType = ImagePlus.GRAY16;
            }

            ImageProcessor proc = ImageUtils.makeProcessor(ijType, width, height, nextImage.pix);

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
