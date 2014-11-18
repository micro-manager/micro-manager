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
import java.awt.Rectangle;
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
   private MultiChannelShadingMigForm myFrame_;
   private ImageCollection imageCollection_;
    
   
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

    * @param nextImage - image to be processed
    * @return - Transformed tagged image, otherwise a copy of the input
    * @throws JSONException
    * @throws MMScriptException 
    */
   public  TaggedImage processTaggedImage(TaggedImage nextImage) throws 
           JSONException, MMScriptException, Exception {     
      myFrame_.setStatus("Processing image...");
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
         String msg = "Cannot flatfield correct images other than 8 or 16 bit grayscale";
         myFrame_.setStatus(msg);
         ReportingUtils.logError(msg);
         return nextImage;
      }
      JSONObject newTags = nextImage.tags;
      TaggedImage bgSubtracted = nextImage;
      
      // subtract background
      int binning = newTags.getInt("Binning");
      Rectangle rect = ImageCollection.TagToRectangle(newTags.getString("ROI"));
      ImagePlusInfo background = imageCollection_.getBackground(binning, rect);
      if (background != null) {
         ImageProcessor imp = ImageUtils.makeProcessor(nextImage);
         imp = ImageUtils.subtractImageProcessors(imp, background.getProcessor());
         bgSubtracted = new TaggedImage(imp.getPixels(), newTags);
      }
      
      ImagePlusInfo flatFieldImage = getMatchingFlatFieldImage(newTags);       

      //do not calculate flat field if we don't have a matching channel
      if (flatFieldImage == null) {
         String msg = "No matching flatfield image found";
         myFrame_.setStatus(msg);
         return bgSubtracted;
      }  
      
      if (ijType == ImagePlus.GRAY8) {
         byte[] newPixels = new byte[width * height];
         byte[] oldPixels = (byte[]) bgSubtracted.pix;
         int length = oldPixels.length;
         float[] flatFieldPixels = (float[]) flatFieldImage.getProcessor().getPixels();
         for (int index = 0; index < length; index++){
            float oldPixel = (float)((int)(oldPixels[index]) & 0x000000ff);
            float newValue = oldPixel * flatFieldPixels[index];
            if (newValue > 2 * Byte.MAX_VALUE) {
               newValue = 2 * Byte.MAX_VALUE;
            }
            newPixels[index] = (byte) (newValue);
         }
         newImage = new TaggedImage(newPixels, newTags);
         myFrame_.setStatus("Done");
         return newImage;
       
      } else if (ijType == ImagePlus.GRAY16) {
         short[] newPixels = new short[width * height];
         short[] oldPixels = (short[]) bgSubtracted.pix;
         int length = oldPixels.length;
         for (int index = 0; index < length; index++){
            // shorts are signed in java so have to do this conversion to get 
            // the right value
            float oldPixel = (float)((int)(oldPixels[index]) & 0x0000ffff);
            float newValue = (oldPixel * 
                    flatFieldImage.getProcessor().getf(index)) + 0.5f;
            if (newValue > 2 * Short.MAX_VALUE) {
               newValue = 2 * Short.MAX_VALUE;
            }
            newPixels[index] = (short) newValue;
         }
         newImage = new TaggedImage(newPixels, newTags);
         myFrame_.setStatus("Done");
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
   ImagePlusInfo getMatchingFlatFieldImage(JSONObject imgTags) {
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
                  try {
                     String tagKey = tagIterator.next();
                     String tagValue = imgTags.getString(tagKey);
                     if (key.equals(tagKey) && value.equals(tagValue)) {
                        settingMatch = true;
                     }
                  } catch (JSONException jex) {
                     ReportingUtils.logError(jex);
                  }
               }
               // if we do not have a settingMatch, this config can not match
               // so stop testing this config
               presetMatch = settingMatch;
            }
            if (presetMatch) {
               int binning = imgTags.getInt("Binning");
               Rectangle rect = ImageCollection.TagToRectangle(imgTags.getString("ROI"));
               return imageCollection_.getFlatField(preset, binning, rect);
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
         imageCollection_ = new ImageCollection();
         myFrame_ = new MultiChannelShadingMigForm(this, gui_);
         shadingTableModel_ = myFrame_.getShadingTableModel();
         gui_.addMMBackgroundListener(myFrame_);
      }
      myFrame_.setVisible(true);
   }
   
   public ImageCollection getImageCollection() {
      return imageCollection_;
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
