/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import mmcorej.TaggedImage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */

public class MMImageCache {

   private ConcurrentLinkedQueue<TaggedImage> taggedImgQueue_;
   private int taggedImgQueueSize_ = 50;
   private final ImageFileManagerInterface imageFileManager_;
   private String comment_ = "";
   private Map<String, String> tags_;
   private ArrayList<String> changingKeys_;
   private Map<String, String> firstTags_;
   
   MMImageCache(ImageFileManagerInterface imageFileManager) {
      imageFileManager_ = imageFileManager;
      taggedImgQueue_ = new ConcurrentLinkedQueue<TaggedImage>();
      changingKeys_ = new ArrayList<String>();
   }

   public UUID putImage(Object img, Map<String,String> md) {
      return putImage(new TaggedImage(img, md));
   }

   public UUID putImage(TaggedImage taggedImg) {
      try {
         cacheImage(taggedImg);
         if (imageFileManager_ != null)
            return imageFileManager_.writeImage(taggedImg);
         else
            return MDUtils.getUUID(taggedImg.tags);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   public TaggedImage getImage(UUID uuid) {
      for (TaggedImage taggedImg:taggedImgQueue_) {
         if (MDUtils.getUUID(taggedImg.tags).equals(uuid)) {
            return taggedImg;
         }
      }
      TaggedImage taggedImg = imageFileManager_.readImage(uuid);
      if (taggedImg != null)
         cacheImage(taggedImg);
      return taggedImg;
   }

   
   private void cacheImage(TaggedImage taggedImg) {
      taggedImgQueue_.add(taggedImg);
      if (firstTags_ == null) {
         firstTags_ = taggedImg.tags;
      } else {
         for (String key:taggedImg.tags.keySet()) {
            if (!firstTags_.containsKey(key) || !firstTags_.get(key).contentEquals(taggedImg.tags.get(key)))
               changingKeys_.add(key);
         }
      }

      if (imageFileManager_ != null && taggedImgQueue_.size() > taggedImgQueueSize_) { // If the queue is full,
         taggedImgQueue_.poll();                       // remove the oldest image.
      }
   }

   void setComment(String text) {
      if (imageFileManager_ != null) {
         imageFileManager_.setComment(text);
      }
      comment_ = text;
   }

   String getComment() {
      if (comment_ != null && comment_.contentEquals(""))
         comment_ = imageFileManager_.getComment();
      return comment_;
   }
   
   public Map<String,String> getAcquisitionMetadata() {
      return tags_;
   }

   public void setAcquisitionMetadata(Map<String,String> tags) {
      tags_ = tags;
   }

   public ArrayList<String> getChangingKeys() {
      return changingKeys_;
   }



}
