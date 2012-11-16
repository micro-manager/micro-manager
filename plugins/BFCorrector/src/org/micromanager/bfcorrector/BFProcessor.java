
package org.micromanager.bfcorrector;

import ij.ImagePlus;
import ij.process.ImageProcessor;
import ij.process.ImageStatistics;
import ij.measure.Measurements;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author nico
 */
class BFProcessor extends DataProcessor<TaggedImage> {
   private ImagePlus flatField_;
   private ImageStatistics flatFieldStats_;
   private int flatFieldWidth_;
   private int flatFieldHeight_;
   private int flatFieldType_;
   
   
   /**
    * Set the flatfield image that will be used in flatfielding
    * Set to null if no fltafielding is desired
    * 
    * @param flatField ImagePlus object representing the flatfield image 
    */
   public void setFlatField(ImagePlus flatField) {
      flatField_ = flatField;
      
      if (flatField != null) {
         flatFieldStats_ = ImageStatistics.getStatistics(flatField.getProcessor(),
                 ImageStatistics.MEAN + ImageStatistics.MIN_MAX, null);
         flatFieldWidth_ = flatField_.getWidth();
         flatFieldHeight_ = flatField_.getHeight();
         flatFieldType_ = flatField_.getType();
      }
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

               produce(proccessTaggedImage(nextImage));

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
    * Executes flatfielding
    * 
    * First mirror the image if requested, than rotate as requested
    * 
    * @return - Transformed tagged image, otherwise a copy of the input
    * @throws JSONException
    * @throws MMScriptException 
    */
   public  TaggedImage proccessTaggedImage(TaggedImage nextImage) throws JSONException, MMScriptException {

      if (flatField_ == null) {
         return nextImage;
      }
      
      int width = MDUtils.getWidth(nextImage.tags);
      int height = MDUtils.getHeight(nextImage.tags);
      String type = MDUtils.getPixelType(nextImage.tags);
      int ijType = ImagePlus.GRAY8;
      if (type.equals("GRAY16")) {
         ijType = ImagePlus.GRAY16;
      }
      
      if (width != flatFieldWidth_ || height != flatFieldHeight_ || ijType != flatFieldType_) {
         ReportingUtils.logError("FlatField dimensions do not match image dimensions");
         return nextImage;
      }
      
      
      
      ImageProcessor proc = ImageUtils.makeProcessor(ijType, width, height, nextImage.pix);
      ImagePlus tip = new ImagePlus("MM", proc);
      
      ij.plugin.ImageCalculator ic = new ij.plugin.ImageCalculator();
      ImagePlus res = ic.run("divide 32-bit", tip, flatField_);
      // scale with average of the flatfield image
      ImageProcessor resProc = res.getProcessor();
      if (ijType == ImagePlus.GRAY8) {
         for (int x = 0; x < resProc.getWidth(); x++) {
            for (int y = 0; y < resProc.getHeight(); y++) {
               resProc.set(x, y, (int) (resProc.get(x, y) * flatFieldStats_.mean));
            }
         }
         resProc = resProc.convertToByte(false);
      }
      else if (ijType == ImagePlus.GRAY16) {
         for (int x = 0; x < resProc.getWidth(); x++) {
            for (int y = 0; y < resProc.getHeight(); y++) {
               resProc.set(x, y, (short) (resProc.get(x, y) * flatFieldStats_.mean));
            }
         }
         resProc = resProc.convertToShort(false);
      }
      
      
      JSONObject newTags = nextImage.tags;
      MDUtils.setWidth(newTags, proc.getWidth());
      MDUtils.setHeight(newTags, proc.getHeight());

      return new TaggedImage(resProc.getPixels(), newTags);
   }
   
}
