/*
 * DataProcessor that splits images as instructed in SplitViewFrame
 */
package org.micromanager.splitview;

import ij.process.ByteProcessor;
import ij.process.ImageProcessor;
import ij.process.ShortProcessor;
import mmcorej.TaggedImage;
import org.json.JSONArray;
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
public class SplitViewProcessor extends DataProcessor<TaggedImage> {

   private SplitViewFrame parent_;

   public SplitViewProcessor(SplitViewFrame frame) {
      parent_ = frame;
   }

   private String getChannelSuffix(int channelIndex) {
      String token;
      if (parent_.getOrientation().equals(SplitViewFrame.LR)) {

         if ((channelIndex % 2) == 0) {
            token = "Left";
         } else {
            token = "Right";
         }
      } else { // if (parent_.getOrientation().equals(SplitViewFrame.TB)) {
         if ((channelIndex % 2) == 0) {
            token = "Top";
         } else {
            token = "Bottom";
         }
      }
      return token;
   }

   @Override
   public void process() {

      
      TaggedImage taggedImage = poll();
      try {
         
         if (TaggedImageQueue.isPoison(taggedImage)) {
            produce(taggedImage);
            return;
         }

         if (taggedImage != null && taggedImage.tags != null) {
            ImageProcessor tmpImg;
            int imgDepth = MDUtils.getDepth(taggedImage.tags);
            int width = MDUtils.getWidth(taggedImage.tags);
            int height = MDUtils.getHeight(taggedImage.tags);
            int channelIndex = MDUtils.getChannelIndex(taggedImage.tags);

            //System.out.println("Processed one");

            if (imgDepth == 1) {
               tmpImg = new ByteProcessor(width, height);
            } else if (imgDepth == 2) {
               tmpImg = new ShortProcessor(width, height);
            } else // TODO throw error
            {
               produce(taggedImage);
               return;
            }

            tmpImg.setPixels(taggedImage.pix);

            height = parent_.calculateHeight(height);
            width = parent_.calculateWidth(width);

            tmpImg.setRoi(0, 0, width, height);
            
            
            // first channel

            // Weird way of copying a JSONObject
            JSONObject tags = new JSONObject(taggedImage.tags.toString());
            MDUtils.setWidth(tags, width);
            MDUtils.setHeight(tags, height);
            MDUtils.setChannelIndex(tags, channelIndex * 2);

            tags.put("Channel", MDUtils.getChannelName(taggedImage.tags) + getChannelSuffix(channelIndex*2));
            
            TaggedImage firstIm = new TaggedImage(tmpImg.crop().getPixels(), tags);

            // second channel
            JSONObject tags2 = new JSONObject(tags.toString());
            tags2.put("Channel", MDUtils.getChannelName(taggedImage.tags)  + getChannelSuffix(channelIndex*2+1));

            if (parent_.getOrientation().equals(SplitViewFrame.LR)) {
               tmpImg.setRoi(width, 0, width, height);
            } else if (parent_.getOrientation().equals(SplitViewFrame.TB)) {
               tmpImg.setRoi(0, height, width, height);
            }
            MDUtils.setWidth(tags2, width);
            MDUtils.setHeight(tags2, height);
            MDUtils.setChannelIndex(tags2, channelIndex * 2 + 1);

            TaggedImage secondIm = new TaggedImage(tmpImg.crop().getPixels(), tags2);

            produce(secondIm);
            produce(firstIm);
         }
      } catch (MMScriptException ex) {
         ReportingUtils.logError(ex);
         produce(taggedImage);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
         produce(taggedImage);
      }
   }
}
