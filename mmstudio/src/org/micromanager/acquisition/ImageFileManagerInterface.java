/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import mmcorej.TaggedImage;
import org.micromanager.utils.MMException;

/**
 *
 * @author arthur
 */
public interface ImageFileManagerInterface {
   public UUID writeImage(TaggedImage taggedImage) throws MMException;
   public TaggedImage readImage(UUID uuid);
   public void finishWriting();
   public void setSummaryMetadata(Map<String,String> md);
   public Map<String,String> getSummaryMetadata();
   public void setSystemMetadata(Map<String,String> md);
   public Map<String,String> getSystemMetadata();
   public ArrayList<Map<String,String>> getImageMetadata();
   public void setComment(String text);
   public String getComment();
}
