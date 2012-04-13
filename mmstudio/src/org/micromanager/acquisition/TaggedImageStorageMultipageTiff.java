package org.micromanager.acquisition;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;




public class TaggedImageStorageMultipageTiff implements TaggedImageStorage {

   private JSONObject summaryMetadata_;
   private JSONObject displaySettings_;
   private boolean newDataSet_;
   private String filepath_;
   private Thread shutdownHook_;
   private int lastFrame_ = -1;

   
//   private MultiPageTiffWriter_
   
   //Map of image labels to file 
   private HashMap<String,MultipageTiffReader> tiffFileReaders_;
   
   
   //General TODO:
   //set lastFrame_ when getting new images
   
   
   public TaggedImageStorageMultipageTiff(String filepath, Boolean newDataSet, JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      displaySettings_ = new JSONObject();
      newDataSet_ = newDataSet;
      filepath_ = filepath;
      tiffFileReaders_ = new HashMap<String,MultipageTiffReader>();
      
      // TODO: throw erroe if no existing dataset
      if (!newDataSet_) {
         openExistingDataSet();
      } 
      
      
      //add shutdown hook --> thread to be run when JVM shuts down
      shutdownHook_ = new Thread() {
         public void run() {
            //Nothing yet
         }
      };
      
      Runtime.getRuntime().addShutdownHook(this.shutdownHook_);
      
   }
   
   private void openExistingDataSet() {
      //Need to throw error if file not found
      
      //For now assume only a single file
      String pathname = filepath_ + "/" + "testTif2Channel.tif";
      if (!new File(pathname).exists()) {
         //TODO: throw some exception
      }
      File f = new File(pathname);
              
       
      
      MultipageTiffReader reader = new MultipageTiffReader(f);
      Set<String> labels = reader.getIndexKeys();
      for (String label : labels) {
         tiffFileReaders_.put(label, reader);
         int frameIndex = Integer.parseInt(label.substring(4, 5));
         lastFrame_ = Math.max(frameIndex, lastFrame_);
      }
      
      try {
         summaryMetadata_ = reader.readSummaryMD();
      } catch (IOException ex) {
         ReportingUtils.logError("Error reading summary metadata");
      }
   }
   
   
   
   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);
      if (!tiffFileReaders_.containsKey(label)) {
         return null;
      }
      return tiffFileReaders_.get(label).readImage(label);   
   }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);
      if (!tiffFileReaders_.containsKey(label)) {
         return null;
      }
      return tiffFileReaders_.get(label).readImage(label).tags;   
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      if (!newDataSet_) {
            throw new MMException("This ImageFileManager is read-only.");
         }
      
      //TODO: add much more for if data is being written
      
   }

   @Override
   public Set<String> imageKeys() {
      return tiffFileReaders_.keySet();
   }

   /**
    * Call this function when no more images are expected
    * Finishes writing the metadata file and closes it.
    * After calling this function, the imagestorage is read-only
    */
   @Override
   public void finished() {
      newDataSet_ = false;
      //TODO: maybe other stuff?
   }

   @Override
   public boolean isFinished() {
      return !newDataSet_;
   }

   @Override
   public void setSummaryMetadata(JSONObject md) {
      summaryMetadata_ = md;
      //TODO: Does this need to be written to file here?
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      //TODO: come back to this one...
   }

   @Override
   public JSONObject getDisplayAndComments() {
      //TODO: come back to this one...
      return VirtualAcquisitionDisplay.getDisplaySettingsFromSummary(summaryMetadata_);
   }

   /**
    * Disposes of the tagged images in the imagestorage
    */
   @Override
   public void close() {
      shutdownHook_.run();
      Runtime.getRuntime().removeShutdownHook(shutdownHook_);
      
   }

   @Override
   public String getDiskLocation() {
      return filepath_;
   }

   @Override
   public int lastAcquiredFrame() {
      return lastFrame_;
   }

   public long getDataSetSize() {
      //TODO: verify this is correct
      return new File(filepath_).getTotalSpace();
   }
   
   
   
}