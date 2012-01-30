/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.util.HashMap;
import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 *
 * This class stores a collection of TaggedImages, all in ram.
 */
public class TaggedImageStorageRam implements TaggedImageStorage {
   public static String menuName_ = null;
   private boolean finished_ = false;

   protected HashMap<String, TaggedImage> imageMap_;
   private JSONObject summaryMetadata_;
   private JSONObject displaySettings_;
   private int lastFrame_ = -1;
   
   public TaggedImageStorageRam(JSONObject summaryMetadata) {
      imageMap_ = new HashMap<String,TaggedImage>();
      summaryMetadata_ = summaryMetadata;
      displaySettings_ = new JSONObject();
   }

   public void putImage(TaggedImage taggedImage) throws MMException {
      String label = MDUtils.getLabel(taggedImage.tags);
      imageMap_.put(label, taggedImage);
      try {
         lastFrame_ = Math.max(lastFrame_, MDUtils.getFrameIndex(taggedImage.tags));
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }

   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      return imageMap_.get(MDUtils.generateLabel(channel, slice, frame, position));
   }

   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return this.getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   public Set<String> imageKeys() {
      return imageMap_.keySet();
   }

   public void finished() {
      finished_ = true;
   }

   public boolean isFinished() {
      return finished_;
   }

   public void setSummaryMetadata(JSONObject md) {
      summaryMetadata_ = md;
   }

   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   public void setDisplayAndComments(JSONObject settings) {
      displaySettings_ = settings;
   }

   public JSONObject getDisplayAndComments() {
      return displaySettings_;
   }

   public void close() {
      imageMap_ = null;
      summaryMetadata_ = null;
      displaySettings_ = null;
      // do nothing for now.
   }

   public String getDiskLocation() {
      return null;
   }

   public int lastAcquiredFrame() {
      return lastFrame_;
   }



}
