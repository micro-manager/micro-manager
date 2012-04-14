package org.micromanager.acquisition;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;




public class TaggedImageStorageMultipageTiff implements TaggedImageStorage {

   private JSONObject summaryMetadata_;
   private JSONObject displaySettings_;
   private boolean newDataSet_;
   private String directory_;
   private String filename_;
   private int numTifFiles_ = 1;
   private Thread shutdownHook_;
   private int lastFrame_ = -1;

   //current reader corresponds to the file that is currently being written
   private MultipageTiffReader currentReader_;
   private MultipageTiffWriter currentWriter_;
   
   
   //Map of image labels to file 
   private HashMap<String,MultipageTiffReader> tiffFileReaders_;
   
   
   //General TODO:
   //set lastFrame_ when getting new images
   
   //Verify that files dont get written bigger than max file size
   
   
   public TaggedImageStorageMultipageTiff(String dir, Boolean newDataSet, JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      displaySettings_ = new JSONObject();
      newDataSet_ = newDataSet;
      directory_ = dir;
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
      
      //TODO: get filename from variables rather than specifying here
      
      //For now assume only a single file
      String pathname = directory_ + "/" + "testTif2Channel.tif";
      if (!new File(pathname).exists()) {
         //TODO: throw some exception
      }
      File f = new File(pathname);
              
       
      
      MultipageTiffReader reader = new MultipageTiffReader(f,true);
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

   private void createFilename() {
      try {
         filename_ = summaryMetadata_.getString("Prefix");
         if (filename_.equals("")) {
            filename_ = "ImageData";
         }
      } catch (JSONException ex) {
         ReportingUtils.logError("Can't find Prefix in summary metadata");
         filename_ = "ImageData";
      }
      if (numTifFiles_ > 1) {
         filename_ += "_" +numTifFiles_;
      }
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      if (!newDataSet_) {
         throw new MMException("This ImageFileManager is read-only.");
      }

      if (currentWriter_ != null && currentWriter_.isClosed()) {
         numTifFiles_++;
      }
      if (currentWriter_ == null || currentWriter_.isClosed()) {
         //Create new Writer and new Reader corresponding to Writer
         createFilename();
         File dir = new File (directory_);
         if (!dir.exists()) {
            dir.mkdir();
         }
         File f = new File(directory_ + "/" + filename_ +".tif");
         currentWriter_ = new MultipageTiffWriter(f, summaryMetadata_);
         currentReader_ = new MultipageTiffReader(f, false);
      }

      if (currentWriter_.hasSpaceToWrite(taggedImage)) {
         try {
            long offset = currentWriter_.writeImage(taggedImage);
            currentReader_.addToIndexMap(taggedImage, offset);
            tiffFileReaders_.put(MDUtils.getLabel(taggedImage.tags), currentReader_);

         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      } else {
         try {
            currentWriter_.close();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
      int frame;
      try {
         frame = MDUtils.getFrameIndex(taggedImage.tags);
      } catch (JSONException ex) {
         frame = 0;
      }
      lastFrame_ = Math.max(lastFrame_, frame);
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
      try {
         currentWriter_.close();
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }
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
      //TODO: come back to this
   }

   @Override
   public String getDiskLocation() {
      return directory_;
   }

   @Override
   public int lastAcquiredFrame() {
      return lastFrame_;
   }

   public long getDataSetSize() {
      //TODO: verify this is correct
      return new File(directory_).getTotalSpace();
   }
   
   
   
}