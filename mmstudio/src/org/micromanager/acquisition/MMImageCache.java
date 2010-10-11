/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.lang.ref.SoftReference;
import org.micromanager.api.TaggedImageStorage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import mmcorej.TaggedImage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class MMImageCache implements TaggedImageStorage {

   private TaggedImageStorage imageFileManager_;
   private String comment_ = "";
   private Set<String> changingKeys_;
   private Map<String, String> firstTags_;
   private static ImageCollection coll_;

   MMImageCache(TaggedImageStorage imageFileManager) {
      imageFileManager_ = imageFileManager;
      changingKeys_ = new HashSet<String>();
      if (coll_ == null) {
         coll_ = new ImageCollection();
      }
   }

   public void finished() {
      imageFileManager_.finished();
   }

   public void setDisplaySettings(Map<String, String> settings) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public Map<String, String> getDisplaySettings() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   private class ImageCollection {
      private ConcurrentLinkedQueue<String> LabelQueue_;
      private Set<String> LabelSet_;
      private HashMap<String, SoftReference> taggedImgTable_;

      public ImageCollection() {
         LabelQueue_ = new ConcurrentLinkedQueue<String>();
         taggedImgTable_ = new HashMap<String, SoftReference>();
         LabelSet_ = new HashSet<String>();
      }

      public void add(MMImageCache cache, TaggedImage taggedImage) {
         String label = MDUtils.getLabel(taggedImage.tags) + "/" + cache.hashCode();
         taggedImgTable_.put(label, new SoftReference(taggedImage));
         LabelQueue_.add(label);
         LabelSet_.add(label);
      }

      public TaggedImage get(MMImageCache cache, String label) {
         label += "/" + cache.hashCode();
         LabelQueue_.remove(label);
         LabelQueue_.add(label);
         return (TaggedImage) taggedImgTable_.get(label).get();
      }

      public Set<String> getLabels(MMImageCache cache) {
         String hashCode = Long.toString(cache.hashCode());
         Set labelSubSet = new HashSet<String>();
         for (String label: LabelSet_) {
            if (label.endsWith(hashCode)) {
               labelSubSet.add(label.split("/")[0]);
            }
         }
         return labelSubSet;
      }
   }

   public void saveAs(TaggedImageStorage newImageFileManager) {
      if (newImageFileManager == null) {
         return;
      }
      for (String label : coll_.getLabels(this)) {
         int pos[] = MDUtils.getIndices(label);
         try {
            newImageFileManager.putImage(getImage(pos[0], pos[1], pos[2], pos[3]));
         } catch (MMException ex) {
            ReportingUtils.logError(ex);
         }
      }
      newImageFileManager.setComment(this.getComment());
      newImageFileManager.finished();
      imageFileManager_ = newImageFileManager;
   }

   public String putImage(TaggedImage taggedImg) {
      try {
         coll_.add(this, taggedImg);
         checkForChangingTags(taggedImg);
         return imageFileManager_.putImage(taggedImg);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      String label = MDUtils.generateLabel(channel, slice, frame, position);
      TaggedImage taggedImg = coll_.get(this, label);
      if (taggedImg == null) {
         taggedImg = imageFileManager_.getImage(channel, slice, frame, position);
         if (taggedImg != null) {
            checkForChangingTags(taggedImg);
         }
      }
      return taggedImg;
   }

   private void checkForChangingTags(TaggedImage taggedImg) {
      if (firstTags_ == null) {
         firstTags_ = taggedImg.tags;
      } else {
         for (String key : taggedImg.tags.keySet()) {
            if (!firstTags_.containsKey(key) || !firstTags_.get(key).contentEquals(taggedImg.tags.get(key))) {
               changingKeys_.add(key);
            }
         }
      }
   }

   public void setComment(String text) {
      if (comment_==null || !comment_.contentEquals(text)) {
         imageFileManager_.setComment(text);
         comment_ = text;
      }
   }

   public String getComment() {
      if (comment_ == null || comment_.contentEquals("")) {
         comment_ = imageFileManager_.getComment();
      }
      return comment_;
   }

   public Map<String, String> getSummaryMetadata() {
      return imageFileManager_.getSummaryMetadata();
   }

   public void setSummaryMetadata(Map<String, String> tags) {
      imageFileManager_.setSummaryMetadata(tags);
   }

   public Set<String> getChangingKeys() {
      return changingKeys_;
   }
}
