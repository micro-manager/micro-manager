/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.util.HashMap;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;

/**
 *
 * @author arthur
 *
 * This class stores a collection of TaggedImages, all in ram.
 */
public class TaggedImageStorageRam implements TaggedImageStorage {
   protected HashMap<String, TaggedImage> imageMap_;
   private JSONObject summaryMetadata_;
   private JSONObject displaySettings_;

   public TaggedImageStorageRam(JSONObject summaryMetadata) {
      imageMap_ = new HashMap<String,TaggedImage>();
      summaryMetadata_ = summaryMetadata;
      displaySettings_ = new JSONObject();
   }

   public String putImage(TaggedImage taggedImage) throws MMException {
      String label = MDUtils.getLabel(taggedImage.tags);
      imageMap_.put(label, taggedImage);
      return label;
   }

   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      return imageMap_.get(MDUtils.generateLabel(channel, slice, frame, position));
   }

   public void finished() {
      // Do nothing.
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
      // do nothing for now.
   }

   public String getDiskLocation() {
      return null;
   }

}
