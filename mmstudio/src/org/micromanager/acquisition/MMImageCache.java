/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.lang.ref.SoftReference;
import org.micromanager.api.TaggedImageStorage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class MMImageCache implements TaggedImageStorage {

   private TaggedImageStorage imageStorage_;
   private Set<String> changingKeys_;
   private JSONObject firstTags_;
   private HashMap<String, SoftReference<TaggedImage>> softTable_;
   int lastFrame_ = -1;

   public MMImageCache(TaggedImageStorage imageStorage) {
      imageStorage_ = imageStorage;
      changingKeys_ = new HashSet<String>();
      softTable_ = new HashMap<String, SoftReference<TaggedImage>>();
   }

   public void finished() {
      imageStorage_.finished();
   }

   public int lastAcquiredFrame() {
      return lastFrame_;
   }

   public String getDiskLocation() {
      return imageStorage_.getDiskLocation();
   }

   public void setDisplayAndComments(JSONObject settings) {
      imageStorage_.setDisplayAndComments(settings);
   }

   public JSONObject getDisplayAndComments() {
      return imageStorage_.getDisplayAndComments();

   }

   public void close() {
      imageStorage_.close();
   }

   public void saveAs(TaggedImageStorage newImageFileManager) {
      if (newImageFileManager == null) {
         return;
      }
      for (String label : softTable_.keySet()) {
         int pos[] = MDUtils.getIndices(label);
         try {
            newImageFileManager.putImage(getImage(pos[0], pos[1], pos[2], pos[3]));
         } catch (MMException ex) {
            ReportingUtils.logError(ex);
         }
      }
      newImageFileManager.setDisplayAndComments(this.getDisplayAndComments());
      newImageFileManager.finished();
      imageStorage_ = newImageFileManager;
   }

   public String putImage(TaggedImage taggedImg) {
      try {
         softTable_.put(MDUtils.getLabel(taggedImg.tags), new SoftReference(taggedImg));
         taggedImg.tags.put("Summary",imageStorage_.getSummaryMetadata());
         checkForChangingTags(taggedImg);
         lastFrame_ = MDUtils.getFrameIndex(taggedImg.tags);
         return imageStorage_.putImage(taggedImg);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      String label = MDUtils.generateLabel(channel, slice, frame, position);
      TaggedImage taggedImg = null;
      if (softTable_.containsKey(label))
         taggedImg = softTable_.get(label).get();
      if (taggedImg == null) {
         taggedImg = imageStorage_.getImage(channel, slice, frame, position);
         if (taggedImg != null) {
            checkForChangingTags(taggedImg);
            softTable_.put(label, new SoftReference(taggedImg));
         }
      }
      return taggedImg;
   }

   private void checkForChangingTags(TaggedImage taggedImg) {
      if (firstTags_ == null) {
         firstTags_ = taggedImg.tags;
      } else {
         Iterator<String> keys = taggedImg.tags.keys();
         while (keys.hasNext()) {
            String key = keys.next();
            try {
               if (!taggedImg.tags.isNull(key)) {
                  if (!firstTags_.has(key))
                     changingKeys_.add(key);
                  else if (!firstTags_.getString(key)
                            .contentEquals(taggedImg.tags.getString(key))) {
                     changingKeys_.add(key);
                  }
               }
            } catch (Exception e) {
               ReportingUtils.logError(e);
            }
         }
      }
   }

   private JSONObject getCommentsJSONObject() {
      if (imageStorage_ == null) {
         ReportingUtils.logError("imageStorage_ is null in getCommentsJSONObject");
         return null;
      }

      JSONObject comments;
      try {
         comments = imageStorage_.getDisplayAndComments().getJSONObject("Comments");
      } catch (JSONException ex) {
         comments = new JSONObject();
         try {
            imageStorage_.getDisplayAndComments().put("Comments", comments);
         } catch (JSONException ex1) {
            ReportingUtils.logError(ex1);
         }
      }
      return comments;
   }

   public void setComment(String text) {
      JSONObject comments = getCommentsJSONObject();
      try {
         comments.put("Summary", text);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
   }

   void setImageComment(String comment, JSONObject tags) {
      JSONObject comments = getCommentsJSONObject();
      String label = MDUtils.getLabel(tags);
      try {
         comments.put(label,comment);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }

   }

   String getImageComment(JSONObject tags) {
      if (tags == null)
         return "";
      try {
         String label = MDUtils.getLabel(tags);
         return getCommentsJSONObject().getString(label);
      } catch (Exception ex) {
         return "";
      }
   }
   
   public String getComment() {
      try {
         return getCommentsJSONObject().getString("Summary");
      } catch (Exception ex) {
         return "";
      }
   }

   public JSONObject getSummaryMetadata() {
      if (imageStorage_ == null) {
         ReportingUtils.logError("imageStorage_ is null in getSummaryMetadata");
         return null;
      }
      return imageStorage_.getSummaryMetadata();
   }

   public void setSummaryMetadata(JSONObject tags) {
      if (imageStorage_ == null) {
         ReportingUtils.logError("imageStorage_ is null in setSummaryMetadata");
         return;
      }
      imageStorage_.setSummaryMetadata(tags);
   }

   public Set<String> getChangingKeys() {
      return changingKeys_;
   }
}
