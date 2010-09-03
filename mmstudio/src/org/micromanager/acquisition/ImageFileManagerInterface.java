/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.util.ArrayList;
import java.util.Map;
import mmcorej.TaggedImage;
import org.micromanager.utils.MMException;

/**
 *
 * @author arthur
 */
public interface ImageFileManagerInterface {
   public String writeImage(TaggedImage taggedImage) throws MMException;
   public TaggedImage readImage(String label);
   public void finishWriting();
   public void setSummaryMetadata(Map<String,String> md);
   public Map<String,String> getSummaryMetadata();
   public ArrayList<Map<String,String>> getImageMetadata();
   public void setComment(String text);
   public String getComment();
}
