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

            int originalChannelCount = tags.getJSONObject("Summary").getInt("Channels");
            tags.getJSONObject("Summary").put("Channels", 2 * originalChannelCount);
            tags.getJSONObject("Summary").put("ChColors", parent_.getColors());
            
            TaggedImage firstIm = new TaggedImage(tmpImg.crop().getPixels(), tags);
            produce(firstIm);

            // second channel
            JSONObject tags2 = new JSONObject(taggedImage.tags.toString());
            tags2.getJSONObject("Summary").put("Channels", 2 * originalChannelCount);
            tags2.getJSONObject("Summary").put("ChColors", parent_.getColors());
            
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
         }
      } catch (MMScriptException ex) {
         ReportingUtils.logError("SplitViewProcessor, MMSCriptException");
         produce(taggedImage);
      } catch (JSONException ex) {
         ReportingUtils.logError("SplitViewProcessor, JSON Exception");
         produce(taggedImage);
      }
   }
}
