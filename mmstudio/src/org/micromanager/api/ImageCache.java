/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONObject;

/**
 *
 * @author arthur
 */
public interface ImageCache extends TaggedImageStorage {
   void addImageCacheListener(ImageCacheListener l);
   Set<String> getChangingKeys();
   String getComment();
   String getDiskLocation();
   JSONObject getDisplayAndComments();
   TaggedImage getImage(int channel, int slice, int frame, int position);
   ImageCacheListener[] getImageStorageListeners();
   JSONObject getLastImageTags();
   JSONObject getSummaryMetadata();
   boolean isFinished();
   int lastAcquiredFrame();
   void removeImageStorageListener(ImageCacheListener l);
   void saveAs(TaggedImageStorage newImageFileManager);
   void setComment(String text);
   void setDisplayAndComments(JSONObject settings);
   void setSummaryMetadata(JSONObject tags);
   void setImageComment(String comment, JSONObject tags);
   String getImageComment(JSONObject tags);
}
