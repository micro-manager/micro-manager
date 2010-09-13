/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import org.micromanager.api.TaggedImageStorage;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import mmcorej.TaggedImage;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author arthur
 */
public class OldImageFileManager implements TaggedImageStorage {
   private final String dir_;

   OldImageFileManager(String dir) {
      dir_ = dir;
   }

   private void readMetadata(String dir) {
      File metadataFile = new File(dir + "/Metadata.txt");
      try {
         FileReader reader = new FileReader(metadataFile);
         reader.read();
      } catch (IOException ex) {
         ReportingUtils.showError(ex);
      }

   }

   public String putImage(TaggedImage taggedImage) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public TaggedImage getImage(String label) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void finished() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setSummaryMetadata(Map<String, String> md) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public Map<String, String> getSummaryMetadata() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setSystemMetadata(Map<String, String> md) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public Map<String, String> getSystemMetadata() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public ArrayList<Map<String, String>> getMetadataIterator() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public void setComment(String text) {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public String getComment() {
      throw new UnsupportedOperationException("Not supported yet.");
   }

   public TaggedImage getImage(int channelIndex, int sliceIndex,
                               int frameIndex, int positionIndex) {
      throw new UnsupportedOperationException("Not supported yet.");
   }


}
