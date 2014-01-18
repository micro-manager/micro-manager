/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package unused;

import HDF.*;
import com.imaging100x.twophoton.TwoPhotonControl;
import java.awt.Color;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.TaggedImage;
import ncsa.hdf.hdf5lib.exceptions.HDF5Exception;
import ncsa.hdf.hdf5lib.exceptions.HDF5LibraryException;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class StitchedImageStorageImaris implements TaggedImageStorage{

   private HDFPreprocessor hdfPreprocessor_;
   private HDFWriter hdfWriter_;
   //holds images where stiching is not yet complete
   private volatile TreeMap<String, TaggedImage> ramStorage_;
   private volatile LinkedList<TaggedImage> hdfQueue_;
   private TreeSet<String> hdfLabels_;
 
   
   private int lastAcquiredFrame_ = 0;
   private int numPositions_;
   private boolean invertX_, invertY_, swapXandY_;
   private int tilesPerRow_, tilesPerColumn_;
   private String storageDir_;
   private int slicesPerWrite_;
   private JSONObject displayAndComments_;
   private JSONObject summaryMetadata_;
   private Thread hdfWritingThread_;
   private boolean finished_ = false;

   public StitchedImageStorageImaris(JSONObject summaryMetadata, boolean invertX, boolean invertY, boolean swapXandY,
           int tilesPerRow, String savingDir) {
      hdfQueue_ = new LinkedList<TaggedImage>();
      hdfLabels_ = new TreeSet<String>();
      summaryMetadata_ = summaryMetadata;
      
      if (savingDir.equals("") || !new File(savingDir).exists()) {
         ReportingUtils.showError("Invalid stitched data directory");
         return;
      }
      //Create directory within saving directory
      String prefix = "";
      try {
         prefix = summaryMetadata.getString("Prefix");
      } catch (JSONException ex) {}
      if (prefix.equals("")) {
         prefix = "Untitled acquisition";
      }
      //delete all files in directory if already exists
      File newDir = new File(savingDir + File.separator + prefix);
      if (newDir.exists()) {
         for (File f : newDir.listFiles()) {
            f.delete();
         }
         newDir.delete();
      }   
      //remake so date reflects acquisition date
      newDir.mkdir();
      
      storageDir_ = newDir.getPath();
      
      ramStorage_ = new TreeMap<String, TaggedImage>();
      try {
         //TODO: change this to read position coordinates from metadata
         numPositions_ = 9;
         tilesPerRow_ = tilesPerRow;
         tilesPerColumn_ = numPositions_ / tilesPerRow;
         int newHeight = tilesPerColumn_ * MDUtils.getHeight(summaryMetadata);
         int newWidth = tilesPerRow_ * MDUtils.getWidth(summaryMetadata);
         //change summary metadata fields
         summaryMetadata.put("Positions", 1);
         summaryMetadata.put("Width", newWidth);
         summaryMetadata.put("Height", newHeight);
         
         Color[] channelColors = null;

         ResolutionLevel[] resLevels = ResolutionLevelMaker.calcLevels(newWidth, newHeight,
                 MDUtils.getNumSlices(summaryMetadata), MDUtils.getNumFrames(summaryMetadata), 1);
         hdfPreprocessor_ = new HDFPreprocessor(newWidth, newHeight, resLevels, MDUtils.getNumChannels(summaryMetadata));
         hdfWriter_ = new HDFWriter(storageDir_, prefix + ".ims", MDUtils.getNumChannels(summaryMetadata),
                 MDUtils.getNumFrames(summaryMetadata), MDUtils.getNumSlices(summaryMetadata),
                 summaryMetadata.getDouble("PixelSize_um"), Math.abs(summaryMetadata.getDouble("z-step_um")),
                 channelColors, newWidth, newHeight, resLevels);
         slicesPerWrite_ = resLevels[resLevels.length - 1].getReductionFactorZ();
      } catch (JSONException ex) {
         ReportingUtils.showError("Couldn't get number of positions from summary metadata");
      }

      invertX_ = invertX;
      invertY_ = invertY;
      swapXandY_ = swapXandY;
      
     hdfWritingThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            hdfWriting();
         }
      }, "HDF writing thread");
     hdfWritingThread_.start();
   }

   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      //either get partially stitched images from RAM or read stitched images off of disk
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, 0);
      if (ramStorage_.containsKey(label)) {
         return ramStorage_.get(label);
      } else if (hdfLabels_.contains(label))  {
         try {
            //read from HDF file
            return hdfWriter_.readAsTaggedImage(channelIndex, sliceIndex, frameIndex);
         } catch (Exception ex) {
            ReportingUtils.showError("HDF reading error");
            return null;
         }
      } else {
         return null;
      }   
   }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      int channel = 0, slice = 0, frame = 0, position = 0, width = 0, height = 0, newWidth = 0, newHeight = 0;
      try {
         channel = MDUtils.getChannelIndex(taggedImage.tags);
         frame = MDUtils.getFrameIndex(taggedImage.tags);
         slice = MDUtils.getSliceIndex(taggedImage.tags);
         position = MDUtils.getPositionIndex(taggedImage.tags);
         width = MDUtils.getWidth(taggedImage.tags);
         height = MDUtils.getHeight(taggedImage.tags);
         newWidth = width * tilesPerRow_;
         newHeight = height * tilesPerColumn_;
      } catch (JSONException ex) {
         ReportingUtils.showError("Couldn't find indices in image metadata");
      }
      String label = MDUtils.generateLabel(channel, slice, frame, 0);

      if (position == 0) {
         try {
            //create blank image for RAM storage, creating metadata as appropriate
            byte[] pixels = new byte[numPositions_ * width * height];
            JSONObject tags = new JSONObject(taggedImage.tags.toString());
            tags.put("Width", newWidth);
            tags.put("Height", newHeight);
            tags.put("PositionName", "Stitched");
            tags.put("PosiionIndex", 0);
            ramStorage_.put(label, new TaggedImage(pixels, tags));
         } catch (JSONException ex) {
            ReportingUtils.showError("metadata problems with stitched image");
         }
      }
      //get coordinates of tile within larger image
      byte[] pixels = (byte[]) ramStorage_.get(label).pix;
      int xTileIndex = position % tilesPerRow_;
      int yTileIndex = position / tilesPerRow_;
      if (swapXandY_) {
         int temp = xTileIndex;
         xTileIndex = yTileIndex;
         yTileIndex = temp;
      }
      if (invertX_) {
         xTileIndex = tilesPerRow_ - xTileIndex - 1;
      }
      if (invertY_) {
         yTileIndex = tilesPerColumn_ - yTileIndex - 1;
      }

      //copy pixels into stitched image
      for (int y = 0; y < height; y++) {
         int destinationIndex = newWidth * (height * yTileIndex + y) + xTileIndex * width;
         System.arraycopy(taggedImage.pix, y * width, pixels, destinationIndex, width);
      }


      if (frame > lastAcquiredFrame_) {
         lastAcquiredFrame_ = frame;
      }
      //if stitching of image is complete, remove from RAM and send to disk
      if (position == numPositions_ - 1) {
         if (slice % slicesPerWrite_ == 0) {
            //add to batch, but don't remove from ram storage until slice group has been written
            synchronized (hdfQueue_) {
               for (int s = slice - slicesPerWrite_ + 1; s <= slice; s++) {
                  //add to HDFWriter in groups of 1 or more slices in a single channel
                  hdfQueue_.add(ramStorage_.get(MDUtils.generateLabel(channel, s, frame, 0)));
               }
            }
         } else {
            //TODO deal with dummy slices situation
         }
      } 
   }

   //Occurs on HDF writing thread
   private void hdfWriting() {
      while (!hdfWriter_.isFinished()) {
         //wait for a batch of slices
         if (hdfQueue_.size() < slicesPerWrite_) {
            if (finished_ )  {
               //may be leftover images
               hdfWriter_.finish();
            }

            try {
               Thread.sleep(5);
            } catch (InterruptedException ex) {
               ReportingUtils.showError("Couldn't sleep thread");
            }
            continue;
         }
         
         //remove batch from HDF queue
         LinkedList<TaggedImage> batch = new LinkedList<TaggedImage>();;
         synchronized (hdfQueue_) {       
            for (int i = 0; i < slicesPerWrite_; i++) {
               batch.add(hdfQueue_.removeFirst());
            }
         }
         
         //write batch
         try {
            PipelineImage img = hdfPreprocessor_.process(batch);
            hdfWriter_.writeImage(img);
//            TwoPhotonControl.updateHDFQueueSize(hdfQueue_.size(), 1);
         } catch (Exception ex) {
            ReportingUtils.showError("HDF writing error");
            ex.printStackTrace();
         }

         //remove from ram storage
         synchronized (ramStorage_) {
            try {
               int channel = MDUtils.getChannelIndex(batch.getFirst().tags);
               int frame = MDUtils.getFrameIndex(batch.getFirst().tags);
               int slice = MDUtils.getSliceIndex(batch.getFirst().tags);
               for (int i = slice; i < slice + slicesPerWrite_; i++) {
                  String label = MDUtils.generateLabel(channel, i, frame, 0);
                  ramStorage_.remove(label);
                  hdfLabels_.add(label);
               }
            } catch (JSONException ex) {
               ReportingUtils.showError("Image tags missing");
            }     
         }
         
      }
   }

   @Override
   public Set<String> imageKeys() {
      //never going to resave one of these through MM
      return null;
   }

   @Override
   public void finished() {
      //can be called when actually done or when cancel request comes in
      finished_ = true;
   }

   @Override
   public boolean isFinished() {
      return hdfWriter_.isFinished();
   }

   @Override
   public void setSummaryMetadata(JSONObject md) {
      //shouldn't ever be called
   }

   @Override
   public JSONObject getSummaryMetadata() {
      //called by VAD to initialize
      return summaryMetadata_;
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      displayAndComments_ = settings;
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return displayAndComments_;
   }

   @Override
   public void close() {
      hdfWriter_.close();
   }

   @Override
   public String getDiskLocation() {
      return storageDir_;
   }

   @Override
   public int lastAcquiredFrame() {
      return lastAcquiredFrame_;
   }

   @Override
   public long getDataSetSize() {
      //doesn't matter
      return 0;
   }

   @Override
   public void writeDisplaySettings() {
      //maybe write in HDF file, probably just leave doing nothing
   }
   
}
