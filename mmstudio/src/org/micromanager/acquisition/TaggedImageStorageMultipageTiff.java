package org.micromanager.acquisition;

import java.io.File;
import java.util.HashMap;
import java.util.Set;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MMException;




public class TaggedImageStorageMultipageTiff implements TaggedImageStorage {

   private JSONObject summaryMetadata_;
   private boolean newDataSet_;
   private String filepath_;
//   private MultiPageTiffWriter_
   
   //Map of image labels to file & byteoffset
   private HashMap<String,ImageAddress> imageLocations_;
   
   
   public TaggedImageStorageMultipageTiff(String filepath, boolean newDataSet, JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      newDataSet_ = newDataSet;
      filepath_ = filepath;
      
      imageLocations_ = new HashMap<String,ImageAddress>();
      
      
   }
   
   private File getFile(String filename) {
      File f = new File(filepath_);
      if( f.isDirectory() ) {
         for (File file : f.listFiles()) {
            if (file.getName().equals(filename)) {
               return file;
            }
         }
         return null;
      } else {
         return f;
      }     
   }
   
   
   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return null;
   }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return null;
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      
      
      
   }

   @Override
   public Set<String> imageKeys() {
      return null;
   }

   @Override
   public void finished() {
      newDataSet_ = false;
   }

   @Override
   public boolean isFinished() {
      return !newDataSet_;
   }

   @Override
   public void setSummaryMetadata(JSONObject md) {
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return null;
   }

   @Override
   public void close() {
   }

   @Override
   public String getDiskLocation() {
      return null;
   }

   @Override
   public int lastAcquiredFrame() {
      return 0;
   }

   public long getDataSetSize() {
      return 0;
   }
   
   
   
   
   private class ImageAddress {
      public String filename;
      public int byteOffset;
              
       public ImageAddress(String fname, int offset) {
          filename = fname;
          byteOffset = offset;         
       }
   }
   
}