/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.utils.MMException;

/**
 *
 * @author arthur
 */
public interface TaggedImageStorage {
   public static String storagePluginName = null;
   public TaggedImage getImage(int channelIndex, int sliceIndex,
                               int frameIndex, int positionIndex);
   public JSONObject getImageTags(int channelIndex, int sliceIndex,
                               int frameIndex, int positionIndex);
   public void putImage(TaggedImage taggedImage) throws MMException;
   public Set<String> imageKeys();
   /**
    * Call this function when no more images are expected
    * Finishes writing the metadata file and closes it.
    * After calling this function, the imagestorage is read-only
    */
   public void finished();
   public boolean isFinished();
   public void setSummaryMetadata(JSONObject md);
   public JSONObject getSummaryMetadata();
   public void setDisplayAndComments(JSONObject settings);
   public JSONObject getDisplayAndComments();
   /**
    * Disposes of the tagges images in the imagestorage
    */
   public void close();
   public String getDiskLocation();
   public int lastAcquiredFrame();
}
