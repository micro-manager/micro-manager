
package org.micromanager.multichannelshading;

import ij.ImagePlus;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageQueue;
import org.micromanager.api.DataProcessor;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author nico
 */
public class ShadingProcessor extends DataProcessor<TaggedImage> {
   private final ShadingTableModel shadingTableModel_;
   private ImagePlus background_;
   private MultiChannelShadingMigForm myFrame_;
   
   public ShadingProcessor(ShadingTableModel shadingTableModel) {
      shadingTableModel_ = shadingTableModel;
   }
   
   public void setBackground(ImagePlus background){
      background_ = background;
   }  
   
      @Override
   public void setEnabled(boolean enabled) {
      super.setEnabled(enabled);
      if (myFrame_ != null) {
         myFrame_.updateProcessorEnabled(enabled);
      }
   }
   
   /**
    * Polls for tagged images, and processes them if their size and type matches
    * 
    */
   @Override
   public void process() {
      try {
         TaggedImage nextImage = poll();
         if (nextImage != TaggedImageQueue.POISON) {
            try {

               produce(processTaggedImage(nextImage));

            } catch (Exception ex) {
               produce(nextImage);
               ReportingUtils.logError(ex);
            }
         } else {
            // Must produce Poison (sentinel) image to terminate tagged image pipeline
            produce(nextImage);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   /**
    * Executes flat-fielding
    * 
    * 
    * 
    * @param nextImage
    * @return - Transformed tagged image, otherwise a copy of the input
    * @throws JSONException
    * @throws MMScriptException 
    */
   public  TaggedImage processTaggedImage(TaggedImage nextImage) throws JSONException, MMScriptException, Exception {     
      int width = MDUtils.getWidth(nextImage.tags);
      int height = MDUtils.getHeight(nextImage.tags);
      TaggedImage newImage;
      Float [] flatFieldImage_;
      String type = MDUtils.getPixelType(nextImage.tags);
      String imageChannel;
      String CHANNELNAME = "Channel"; //name of Channel tag
      
      int ijType = ImagePlus.GRAY8;
      if (type.equals("GRAY16")) {
         ijType = ImagePlus.GRAY16;
      }
      
      // For now, this plugin only works with 8 or 16 bit grayscale images
      if (! (ijType == ImagePlus.GRAY8 || ijType == ImagePlus.GRAY16) ) {
         ReportingUtils.logError("Cannot flatfield correct images other than 8 or 16 bit grayscale");
         return nextImage;
      }
      JSONObject newTags = nextImage.tags;
      
      //check tags and identify appropriate flatfielding image
      /*
      if (newTags.has(CHANNELNAME)){
        imageChannel = (String) newTags.get(CHANNELNAME);
      } else {
        //work out channel from core; get channel corresponding to selected group
        imageChannel = gui_.getMMCore().getCurrentConfig(channelGroup_);
      }      
      //get flat field image; returns null if no image found
      if (flatFieldImages.getFlatFieldNormalize(imageChannel)){
          flatFieldImage_ = flatFieldImages.getNormalizedFlatField(imageChannel);
      } else {
          flatFieldImage_ = flatFieldImages.getFlatField(imageChannel); 
      }
      int flatFieldHeight = flatFieldImages.getImageHeight(imageChannel);
      int flatFieldWidth = flatFieldImages.getImageWidth(imageChannel);
     
      // subtract background
      if (background_ != null) {
         ImageProcessor differenceProcessor =
                 ImageUtils.subtractImageProcessors(ImageUtils.makeProcessor(nextImage),
                 background_.getProcessor());        
         nextImage = new TaggedImage(differenceProcessor.getPixels(), newTags);
      }
      
      //do not calculate flat field if we don't have a matching channel
      if (flatFieldImage_ == null) {
         return nextImage;
      }      
      // do not calculate if image size differs
      if (width != flatFieldWidth || height != flatFieldHeight) {
         ReportingUtils.logError
            ("FlatField dimensions do not match image dimensions");
         return nextImage;
      }      
      
      if (ijType == ImagePlus.GRAY8) {
         byte[] newPixels = new byte[width * height];
         byte[] oldPixels = (byte[]) nextImage.pix;
         int length = oldPixels.length;
         for (int index = 0; index < length; index++){
            newPixels[index] = (byte) ( (float) oldPixels[index] 
                * flatFieldImage_[index]);
         }
         newImage = new TaggedImage(newPixels, newTags);
         return newImage;
       
      } else if (ijType == ImagePlus.GRAY16) {
         short[] newPixels = new short[width * height];
         short[] oldPixels = (short[]) nextImage.pix;
         int length = oldPixels.length;
         for (int index = 0; index < length; index++){
            //shorts are signed in java so have to do this conversion to get the right value
            float oldPixel = (float)((int)(oldPixels[index]) & 0x0000ffff);
            newPixels[index] = (short) ((oldPixel * flatFieldImage_[index]) + 0.5f);
         }
         newImage = new TaggedImage(newPixels, newTags);
         return newImage;         
         
      } else {
          return nextImage;
      }
               */
      return nextImage;
   }
   
   SimpleFloatImage getMatchingFlatFieldImage(JSONObject imgTags) {
      String channelGroup = shadingTableModel_.getChannelGroup();
      String[] presets = shadingTableModel_.getUsedPresets();
      for (String preset : presets) {
         try {
            Configuration config = gui_.getMMCore().getConfigData(
                    channelGroup, preset);
            for (int i = 0; i < config.size(); i++) {
               PropertySetting ps = config.getSetting(i);
               //imgTags.
               //ps.
            }
         } catch (Exception ex) {
            //TODO
         }
      }
      
      return null;
   }
   
   
   @Override
   public void makeConfigurationGUI() {
      if (myFrame_ == null) {
         myFrame_ = new MultiChannelShadingMigForm(this, gui_);
         gui_.addMMBackgroundListener(myFrame_);
      }
      myFrame_.setVisible(true);
   }

   @Override
   public void dispose() {
      if (myFrame_ != null) {
         myFrame_.dispose();
         myFrame_ = null;
      }
   }
   
}
