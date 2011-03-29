/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

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
   public void putImage(TaggedImage taggedImage) throws MMException;
   public void finished();
   public boolean isFinished();
   public void setSummaryMetadata(JSONObject md);
   public JSONObject getSummaryMetadata();
   public void setDisplayAndComments(JSONObject settings);
   public JSONObject getDisplayAndComments();
   public void close();
   public String getDiskLocation();
   public int lastAcquiredFrame();

}
