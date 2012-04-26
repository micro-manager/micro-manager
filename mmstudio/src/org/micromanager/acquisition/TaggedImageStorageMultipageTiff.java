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
import org.micromanager.utils.JavaUtils;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;




public class TaggedImageStorageMultipageTiff implements TaggedImageStorage {

   private JSONObject summaryMetadata_;
   private JSONObject displaySettings_;
   private boolean newDataSet_;
   private String directory_;
   private Thread shutdownHook_;
   private int lastFrame_ = -1;
   private int numPositions_;
      
   //map of position indecies to number of tiff files written for that position
   private HashMap<Integer,Integer> numFiles_;
   //map of position indecies to writers/readers
   private HashMap<Integer,MultipageTiffWriter> tiffWritersByPosition_;
   private HashMap<Integer,MultipageTiffReader> tiffReadersByPosition_;
   //Map of image labels to file 
   private HashMap<String,MultipageTiffReader> tiffReadersByLabel_;
   
   
   public TaggedImageStorageMultipageTiff(String dir, Boolean newDataSet, JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;
      displaySettings_ = new JSONObject();
      newDataSet_ = newDataSet;
      directory_ = dir;
      tiffReadersByLabel_ = new HashMap<String,MultipageTiffReader>();

      
      System.out.println("MP Start: " + System.currentTimeMillis());

      if (summaryMetadata_ != null) {
         try {
            numPositions_ = MDUtils.getNumPositions(summaryMetadata);
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
      }
     
      
      // TODO: throw erroe if no existing dataset
      if (!newDataSet_) {
         openExistingDataSet();
      } 
      
      
      //add shutdown hook --> thread to be run when JVM shuts down
      shutdownHook_ = new Thread() {
         public void run() {
            writeDisplaySettings();
         }
      };
      
      Runtime.getRuntime().addShutdownHook(this.shutdownHook_);
      
   }
   
   private void openExistingDataSet() {
      //Need to throw error if file not found

      //TODO: get filename from variables rather than specifying here


      MultipageTiffReader reader = null;
      File dir = new File(directory_);
      for (File f : dir.listFiles()) {
         if (f.getName().endsWith(".tif")) {
            reader = new MultipageTiffReader(f, true);
            Set<String> labels = reader.getIndexKeys();
            for (String label : labels) {
               tiffReadersByLabel_.put(label, reader);
               int frameIndex = Integer.parseInt(label.split("_")[2]);
               lastFrame_ = Math.max(frameIndex, lastFrame_);
            }
         }
      }


      try {
         summaryMetadata_ = reader.readSummaryMD();
         numPositions_ = MDUtils.getNumPositions(summaryMetadata_);
         displaySettings_.put("Channels", reader.readDisplaySettings());
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      } catch (IOException ex) {
         ReportingUtils.logError("Error reading summary metadata");
      }
   }
   
   
   
   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);
      if (!tiffReadersByLabel_.containsKey(label)) {
         return null;
      }
      return tiffReadersByLabel_.get(label).readImage(label);   
   }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);
      if (!tiffReadersByLabel_.containsKey(label)) {
         return null;
      }
      return tiffReadersByLabel_.get(label).readImage(label).tags;   
   }

   private String createFilename(int positionIndex, int fileIndex) {
      String filename = "";
      try {
         String prefix = summaryMetadata_.getString("Prefix");
         if (prefix.length() == 0) {
            filename = "images";
         } else {
            filename = prefix + "_images";
         }
      } catch (JSONException ex) {
         ReportingUtils.logError("Can't find Prefix in summary metadata");
         filename = "images";
      }
      if (numPositions_ > 0 ) {
//TODO: put position name if it exists
         filename += "_" + positionIndex;
      }
      if (fileIndex > 0) {
         filename += "_" +fileIndex;
      }
      filename += ".tif";
      return filename;
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      if (!newDataSet_) {
         throw new MMException("This ImageFileManager is read-only.");
      }
      int positionIndex = 0;
      try {
         positionIndex = MDUtils.getPositionIndex(taggedImage.tags);
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      }
      
      if (tiffWritersByPosition_ == null) {
         try {
            tiffWritersByPosition_ = new HashMap<Integer, MultipageTiffWriter>();
            tiffReadersByPosition_ = new HashMap<Integer, MultipageTiffReader>();
            numFiles_ = new HashMap<Integer, Integer>();
            JavaUtils.createDirectory(directory_);
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
     
      //Create new writer if none exists for position or if existing one is full
      if ( tiffWritersByPosition_.get(positionIndex) != null && !tiffWritersByPosition_.get(positionIndex).hasSpaceToWrite(taggedImage) ) {
         try {
            tiffWritersByPosition_.get(positionIndex).close();
            tiffReadersByPosition_.get(positionIndex).fileFinished();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }           
              
      int fileIndex = 0;
      if (tiffWritersByPosition_.containsKey(positionIndex) && tiffWritersByPosition_.get(positionIndex).isClosed()) {
         fileIndex = numFiles_.get(positionIndex) + 1;
      }
      if (tiffWritersByPosition_.get(positionIndex) == null || tiffWritersByPosition_.get(positionIndex).isClosed()) {
         numFiles_.put(positionIndex, fileIndex);
         String filename = createFilename(positionIndex, fileIndex);
         File f = new File(directory_ + "/" + filename);
         tiffWritersByPosition_.put(positionIndex, new MultipageTiffWriter(f, summaryMetadata_));
         tiffReadersByPosition_.put(positionIndex, new MultipageTiffReader(f, false));
      }


      try {
         long offset = tiffWritersByPosition_.get(positionIndex).writeImage(taggedImage);
         MultipageTiffReader currentReader = tiffReadersByPosition_.get(positionIndex);
         currentReader.addToIndexMap(taggedImage, offset);
         tiffReadersByLabel_.put(MDUtils.getLabel(taggedImage.tags), currentReader);
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
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
      return tiffReadersByLabel_.keySet();
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
         if (tiffWritersByPosition_ != null) {
            for (Integer i : tiffWritersByPosition_.keySet()) {
               MultipageTiffWriter w = tiffWritersByPosition_.get(i);
               if (!w.isClosed()) {
                  w.close();
                  tiffReadersByPosition_.get(i).fileFinished();

               }
            }
         }
         System.out.println("MP End: " + System.currentTimeMillis());
                 
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
      try {
         numPositions_ = MDUtils.getNumPositions(md);
      } catch (JSONException ex) {
        ReportingUtils.logError(ex);
      }
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      displaySettings_ = settings;
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return displaySettings_;
   }

   private void writeDisplaySettings() {
      for (String label : tiffReadersByLabel_.keySet()) {
         try {
            tiffReadersByLabel_.get(label).rewriteDisplaySettings(displaySettings_.getJSONArray("Channels"));
         } catch (JSONException ex) {
            ReportingUtils.logError("Error writing display settings");
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }
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