///////////////////////////////////////////////////////////////////////////////
//FILE:          TaggedImageStorageMultipageTiff.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com, 2012
//
// COPYRIGHT:    University of California, San Francisco, 2012
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
package org.micromanager.acquisition;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
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
   private JSONObject displayAndComments_;
   private boolean newDataSet_;
   private String directory_;
   private Thread shutdownHook_;
   private int lastFrame_ = -1;
   private int numPositions_;
   private CachedImages cached_;
      
   //map of position indecies to number of tiff files written for that position
   private HashMap<Integer,Integer> numFiles_;
   //map of position indecies to writers/readers
   private HashMap<Integer, MultipageTiffWriter> tiffWritersByPosition_;
   private HashMap<Integer, MultipageTiffReader> tiffReadersByPosition_;
   //Map of image labels to file 
   private HashMap<String, MultipageTiffReader> tiffReadersByLabel_;

   public TaggedImageStorageMultipageTiff(String dir, Boolean newDataSet, JSONObject summaryMetadata) {
      summaryMetadata_ = summaryMetadata;

      newDataSet_ = newDataSet;
      directory_ = dir;
      tiffReadersByLabel_ = new HashMap<String, MultipageTiffReader>();
      cached_ = new CachedImages();

      System.out.println("MP Start: " + System.currentTimeMillis());

      if (summaryMetadata_ != null) {  
         displayAndComments_ = VirtualAcquisitionDisplay.getDisplaySettingsFromSummary(summaryMetadata);
         try {
            numPositions_ = MDUtils.getNumPositions(summaryMetadata);
         } catch (JSONException ex) {
            ReportingUtils.logError(ex);
         }
      }
     
      // TODO: throw error if no existing dataset
      if (!newDataSet_) {
         openExistingDataSet();
      }    
      
      //add shutdown hook --> thread to be run when JVM shuts down
      shutdownHook_ = new Thread() {
         @Override
         public void run() {
            writeDisplaySettings();
         }
      };    
      Runtime.getRuntime().addShutdownHook(this.shutdownHook_); 
   }
   
   private void openExistingDataSet() {
      //Need to throw error if file not found

      MultipageTiffReader reader = null;
      File dir = new File(directory_);
      for (File f : dir.listFiles()) {
         if (f.getName().endsWith(".tif")) {
            reader = new MultipageTiffReader(f);
            Set<String> labels = reader.getIndexKeys();
            for (String label : labels) {
               tiffReadersByLabel_.put(label, reader);
               int frameIndex = Integer.parseInt(label.split("_")[2]);
               lastFrame_ = Math.max(frameIndex, lastFrame_);
            }
         }
      }


      try {
         summaryMetadata_ = reader.getSummaryMetadata();
         numPositions_ = MDUtils.getNumPositions(summaryMetadata_);
         displayAndComments_ = reader.getDisplayAndComments();
      } catch (JSONException ex) {
         ReportingUtils.logError(ex);
      } 
   }
   
   
   
   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);
      TaggedImage img = cached_.get(label);
      if (img != null) {
         return img;
      }
      if (!tiffReadersByLabel_.containsKey(label)) {
         return null;
      }
      return tiffReadersByLabel_.get(label).readImage(label);   
   }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, positionIndex);
      TaggedImage img = cached_.get(label);
      if (img != null) {
         return img.tags;
      }
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
      String label = MDUtils.getLabel(taggedImage.tags);
      
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
            tiffReadersByPosition_.get(positionIndex).finishedWriting();
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

         MultipageTiffWriter writer = new MultipageTiffWriter(f, summaryMetadata_, taggedImage);    
         tiffWritersByPosition_.put(positionIndex, writer);
         tiffReadersByPosition_.put(positionIndex, new MultipageTiffReader(summaryMetadata_, writer));
      }


      try {
         long offset = tiffWritersByPosition_.get(positionIndex).writeImage(taggedImage);
         MultipageTiffReader currentReader = tiffReadersByPosition_.get(positionIndex);
         currentReader.addToIndexMap(taggedImage, offset);
         tiffReadersByLabel_.put(label, currentReader);
      } catch (IOException ex) {
         ReportingUtils.logError(ex);
      }

         
      int frame;
      try {
         frame = MDUtils.getFrameIndex(taggedImage.tags);
      } catch (JSONException ex) {
         frame = 0;
      }
      cached_.add(taggedImage, label);
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
                  tiffReadersByPosition_.get(i).finishedWriting();

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
      displayAndComments_ = VirtualAcquisitionDisplay.getDisplaySettingsFromSummary(summaryMetadata_);
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
   public JSONObject getDisplayAndComments() {
      return displayAndComments_;
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      displayAndComments_ = settings;
   }
          
   @Override   
   public void writeDisplaySettings() {
      for (MultipageTiffReader r : new HashSet<MultipageTiffReader>(tiffReadersByLabel_.values())) {
         try {
            r.rewriteDisplaySettings(displayAndComments_.getJSONArray("Channels"));
            r.rewriteComments(displayAndComments_.getJSONObject("Comments"));
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
      for (MultipageTiffReader r : new HashSet<MultipageTiffReader>(tiffReadersByLabel_.values())) {
         try {
            r.close();
         } catch (IOException ex) {
            ReportingUtils.logError(ex);
         }
      }              
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
      File dir = new File (directory_);
      LinkedList<File> list = new LinkedList<File>();
      for (File f : dir.listFiles()) {
         if (f.isDirectory()) {
            for (File fi : f.listFiles()) {
               list.add(f);
            }
         } else {
            list.add(f);
         }
      }
      long size = 0;
      for (File f : list) {
         size += f.length();
      }
      return size;
   }
   
   
   private class CachedImages {
      private static final int NUM_TO_CACHE = 10;
      
      private LinkedList<TaggedImage> images;
      private LinkedList<String> labels;
      
      public CachedImages() {
         images = new LinkedList<TaggedImage>();
         labels = new LinkedList<String>();
      }
      
      public void add(TaggedImage img, String label) {
         images.addFirst(img);
         labels.addFirst(label);
         while (images.size() > NUM_TO_CACHE) {
            images.removeLast();
            labels.removeLast();
         }
      }

      public TaggedImage get(String label) {
         int i = labels.indexOf(label);
         return i == -1 ? null : images.get(i);
      }
      
   }
   
   
}