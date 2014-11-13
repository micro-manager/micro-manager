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

import ij.ImagePlus;
import ij.process.ImageProcessor;
import java.util.Iterator;
import mmcorej.Configuration;
import mmcorej.PropertySetting;
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
public class ShadingProcessor extends DataProcessor<TaggedImage> {
   private ShadingTableModel shadingTableModel_;
   private ImagePlus background_;
   private MultiChannelShadingMigForm myFrame_;
   
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
      String type = MDUtils.getPixelType(nextImage.tags);
      
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
      SimpleFloatImage flatFieldImage = getMatchingFlatFieldImage(newTags);       

      // subtract background
      if (background_ != null) {
         if (background_.getType() != ijType) {
            ReportingUtils.logError(
                    "MultiShading Plugin: Background image is of different type than experimental image");
         } else {
            ImageProcessor differenceProcessor =
                    ImageUtils.subtractImageProcessors(ImageUtils.makeProcessor(nextImage),
                    background_.getProcessor());
            nextImage = new TaggedImage(differenceProcessor.getPixels(), newTags);
         }
      }
      
      //do not calculate flat field if we don't have a matching channel
      if (flatFieldImage == null) {
         ReportingUtils.logMessage(
                    "MultiShading Plugin: No matching flatfield image found");
         return nextImage;
      }      
      // do not calculate if image size differs
      if (width != flatFieldImage.getWidth() || 
              height != flatFieldImage.getHeight()) {
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
                * flatFieldImage.getNormalizedPixels()[index]);
         }
         newImage = new TaggedImage(newPixels, newTags);
         return newImage;
       
      } else if (ijType == ImagePlus.GRAY16) {
         short[] newPixels = new short[width * height];
         short[] oldPixels = (short[]) nextImage.pix;
         int length = oldPixels.length;
         for (int index = 0; index < length; index++){
            // shorts are signed in java so have to do this conversion to get 
            // the right value
            float oldPixel = (float)((int)(oldPixels[index]) & 0x0000ffff);
            newPixels[index] = (short) ((oldPixel * 
                    flatFieldImage.getNormalizedPixels()[index]) + 0.5f);
         }
         newImage = new TaggedImage(newPixels, newTags);
         return newImage;         
         
      } 
      
      return nextImage;
     
   }
   /**
    * Given the tags of the image currently being processed,
    * find a matching preset from the channelgroup used by the tablemodel
    * @param imgTags - image tags in JSON format
    * @return matching flat field image
    */
   SimpleFloatImage getMatchingFlatFieldImage(JSONObject imgTags) {
      String channelGroup = shadingTableModel_.getChannelGroup();
      String[] presets = shadingTableModel_.getUsedPresets();
      for (String preset : presets) {
         try {
            Configuration config = gui_.getMMCore().getConfigData(
                    channelGroup, preset);
            boolean presetMatch = true;
            for (int i = 0; i < config.size() && presetMatch; i++) {
               boolean settingMatch = false;
               PropertySetting ps = config.getSetting(i);
               String key = ps.getKey();
               String value = ps.getPropertyValue();
               Iterator<String> tagIterator = imgTags.keys();
               while (tagIterator.hasNext() && !settingMatch) {
                  String tagKey = tagIterator.next();
                  String tagValue = imgTags.getString(tagKey);
                  if (key.equals(tagKey) && value.equals(tagValue)) {
                     settingMatch = true;
                  }
               }
               // if we do not have a settingMatch, this config can not match
               // so stop testing this config
               presetMatch = settingMatch;
            }
            if (presetMatch) {
               return shadingTableModel_.getFlatFieldImage(channelGroup, preset);
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex, "Exception in tag matching");
         }
      }
      
      return null;
   }
   
   
   @Override
   public void makeConfigurationGUI() {
      if (myFrame_ == null) {
         myFrame_ = new MultiChannelShadingMigForm(this, gui_);
         shadingTableModel_ = myFrame_.getShadingTableModel();
         gui_.addMMBackgroundListener(myFrame_);
      }
      myFrame_.setVisible(true);
   }
   
   public void setMyFrameToNull() {
      myFrame_ = null;
   }

   @Override
   public void dispose() {
      if (myFrame_ != null) {
         myFrame_.dispose();
         myFrame_ = null;
      }
   }
   
}
