/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.acquisition;

import java.util.ArrayList;
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

public class MMImageCache {

   private final int taggedImgQueueSize_ = 50;
   private ImageFileManagerInterface imageFileManager_;
   private String comment_ = "";
   private Map<String, String> tags_;
   private ArrayList<String> changingKeys_;
   private Map<String, String> firstTags_;
   private final ImageCollection coll_;

   
   MMImageCache(ImageFileManagerInterface imageFileManager) {
      imageFileManager_ = imageFileManager;
      changingKeys_ = new ArrayList<String>();
      coll_ = new ImageCollection();
   }

   private class ImageCollection {
      private ConcurrentLinkedQueue<String> LabelQueue_;
      private Set<String> LabelSet_;
      private HashMap<String, TaggedImage> taggedImgTable_;;

      public ImageCollection() {
         LabelQueue_ = new ConcurrentLinkedQueue<String>();
         taggedImgTable_ = new HashMap<String, TaggedImage>();
         LabelSet_ = new HashSet<String>();
      }  
      
      public void add(TaggedImage taggedImage) {
         String label = MDUtils.getLabel(taggedImage.tags);
         taggedImgTable_.put(label, taggedImage);
         LabelQueue_.add(label);
         if (imageFileManager_ != null && LabelQueue_.size() > taggedImgQueueSize_)
            dropOne();
         LabelSet_.add(label);
      }
      
      public void dropOne() {
         String label = LabelQueue_.poll();
         taggedImgTable_.remove(label);
      }

      public TaggedImage get(String label) {
         LabelQueue_.remove(label);
         LabelQueue_.add(label);
         return taggedImgTable_.get(label);
      }

      public Set<String> getLabelSet() {
         return LabelSet_;
      }

   }

   public void saveAs(ImageFileManagerInterface newImageFileManager) {
      if (newImageFileManager == null) {
         return;
      }
      for (String label:coll_.getLabelSet()) {
         try {
            newImageFileManager.writeImage(getImage(label));
         } catch (MMException ex) {
            ReportingUtils.logError(ex);
         }
      }
      imageFileManager_ = newImageFileManager;
   }

   public String putImage(Object img, Map<String,String> md) {
      return putImage(new TaggedImage(img, md));
   }

   public String putImage(TaggedImage taggedImg) {
      try {
         cacheImage(taggedImg);
         if (imageFileManager_ != null)
            return imageFileManager_.writeImage(taggedImg);
         else
            return MDUtils.getLabel(taggedImg.tags);
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
         return null;
      }
   }

   public TaggedImage getImage(String label) {
      TaggedImage taggedImg = coll_.get(label);
      if (taggedImg == null) {
         taggedImg = imageFileManager_.readImage(label);
         if (taggedImg != null) {
            cacheImage(taggedImg);
         }
      }
      return taggedImg;
   }

   
   private void cacheImage(TaggedImage taggedImg) {
      coll_.add(taggedImg);
      if (firstTags_ == null) {
         firstTags_ = taggedImg.tags;
      } else {
         for (String key:taggedImg.tags.keySet()) {
            if (!firstTags_.containsKey(key) || !firstTags_.get(key).contentEquals(taggedImg.tags.get(key)))
               changingKeys_.add(key);
         }
      }
   }

   void setComment(String text) {
      if (imageFileManager_ != null) {
         imageFileManager_.setComment(text);
      }
      comment_ = text;
   }

   String getComment() {
      if (imageFileManager_ != null && comment_ != null && comment_.contentEquals(""))
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
