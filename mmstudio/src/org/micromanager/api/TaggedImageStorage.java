/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import java.util.Map;
import mmcorej.TaggedImage;
import org.micromanager.utils.MMException;

/**
 *
 * @author arthur
 */
public interface TaggedImageStorage {
   public TaggedImage getImage(int channelIndex, int sliceIndex,
                               int frameIndex, int positionIndex);
   public String putImage(TaggedImage taggedImage) throws MMException;
   public void finished();
   public void setSummaryMetadata(Map<String,String> md);
   public Map<String,String> getSummaryMetadata();
   public void setComment(String text);
   public String getComment();
   public void setDisplaySettings(Map<String,String> settings);
   public Map<String,String> getDisplaySettings();
}
