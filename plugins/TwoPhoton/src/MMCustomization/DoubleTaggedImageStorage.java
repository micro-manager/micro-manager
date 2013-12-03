/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import HDF.*;
import com.imaging100x.twophoton.TwoPhotonControl;
import java.awt.Color;
import java.io.File;
import java.util.LinkedList;
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

/**
 * A class that holds a tagged image storage which dynamically stitches images, and an otional
 * imaris writing image storage class
 * @author Henry
 */
public class DoubleTaggedImageStorage implements TaggedImageStorage {

   private DynamicStitchingImageStorage storage_;
   private boolean makeImarisFile_;
   private int numPositions_, numSlices_, numChannels_, slicesPerWrite_;
   private volatile LinkedList<TaggedImage> hdfQueue_;
   private HDFWriter hdfWriter_;
   private HDFPreprocessor hdfPreprocessor_;
   private Thread hdfWritingThread_;
   private boolean finished_ = false;
   
   
   
   public DoubleTaggedImageStorage(JSONObject summaryMetadata, String savingDir, String imarisDirectory, boolean saveImaris) {
      storage_ = new DynamicStitchingImageStorage(summaryMetadata, savingDir);

      makeImarisFile_ = saveImaris;
      if (!makeImarisFile_) {
         return;
      }
      
      
      if (imarisDirectory.equals("") || !new File(imarisDirectory).exists()) {
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
      File newDir = new File(imarisDirectory + File.separator + prefix);
      if (newDir.exists()) {
         for (File f : newDir.listFiles()) {
            f.delete();
         }
         newDir.delete();
      }   
      //remake so date reflects acquisition date
      newDir.mkdir();


      Color[] channelColors = null;
      int width = storage_.getWidth();
      int height = storage_.getHeight();
      try {
         ResolutionLevel[] resLevels = ResolutionLevelMaker.calcLevels(width, height,
                 MDUtils.getNumSlices(summaryMetadata), MDUtils.getNumFrames(summaryMetadata), 1);
         hdfPreprocessor_ = new HDFPreprocessor(width, height, resLevels, MDUtils.getNumChannels(summaryMetadata));

         hdfWriter_ = new HDFWriter(newDir.getAbsolutePath(), prefix + ".ims", MDUtils.getNumChannels(summaryMetadata),
                 MDUtils.getNumFrames(summaryMetadata), MDUtils.getNumSlices(summaryMetadata),
                 summaryMetadata.getDouble("PixelSize_um"), Math.abs(summaryMetadata.getDouble("z-step_um")),
                 channelColors, width, height, resLevels);
               slicesPerWrite_ = resLevels[resLevels.length - 1].getReductionFactorZ();
      } catch (JSONException ex) {
         Logger.getLogger(DoubleTaggedImageStorage.class.getName()).log(Level.SEVERE, null, ex);
      }


      hdfQueue_ = new LinkedList<TaggedImage>();
      try {
         numPositions_ = MDUtils.getNumPositions(summaryMetadata);
         numSlices_ = MDUtils.getNumSlices(summaryMetadata);
         numChannels_ = MDUtils.getNumChannels(summaryMetadata);
      } catch (JSONException ex) {
         ReportingUtils.showError("Coulndt get num positions from summary metadata");
      }
      
      

      hdfWritingThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            hdfWriting();
         }
      }, "HDF writing thread");
      hdfWritingThread_.start();
   }
   
    //Occurs on HDF writing thread
   private void hdfWriting() {
      while (!hdfWriter_.isFinished()) {
         //check if Imaris writing is too slow and abort if needed
         if (hdfQueue_.size() > 2.5 * numChannels_ * numSlices_) {
            hdfWriter_.finish();
            hdfWriter_.close();
            makeImarisFile_ = false;
            ReportingUtils.showMessage("Imaris writing couldn't keep up with rate of data acquisition."
                    + " Imaris writing aborted.");
            break;
         }
         
         //wait for a batch of slices
         if (hdfQueue_.size() < slicesPerWrite_) {
            //by the time finished is set, all images have been added to hdfQueue
            
            //TODO check for finishing numSlices or what to do with leftovers form aborted acq
            if (finished_ )  {
               //may be leftover images
               hdfWriter_.finish();
               //close this as soon as it is done because don't need to read data from it
               hdfWriter_.close();
               break;
            }

            try {
               Thread.sleep(5);
            } catch (InterruptedException ex) {
               ReportingUtils.showError("Couldn't sleep thread");
            }
            continue;
         }
         
         //remove batch from HDF queue
         LinkedList<TaggedImage> batch = new LinkedList<TaggedImage>();
         synchronized (hdfQueue_) {       
            for (int i = 0; i < slicesPerWrite_; i++) {
               batch.add(hdfQueue_.removeFirst());
            }
         }
         
         //write batch
         try {
            PipelineImage img = hdfPreprocessor_.process(batch);
            hdfWriter_.writeImage(img);
            TwoPhotonControl.updateHDFQueueSize(hdfQueue_.size());
         } catch (Exception ex) {
            ReportingUtils.showError("HDF writing error");
            ex.printStackTrace();
         }
      }
   }
   
   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return storage_.getImage(channelIndex, sliceIndex, frameIndex, positionIndex);
   }

   @Override
   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return storage_.getImageTags(channelIndex, sliceIndex, frameIndex, positionIndex);
   }

   @Override
   public void putImage(TaggedImage taggedImage) throws MMException {
      storage_.putImage(taggedImage);
      if (!makeImarisFile_) {
         return;
      }  

      //check if stitched image is complete, and if so, send to imaris writing queue
      int position = 0, slice = 0, channel = 0, frame = 0;
      try {
         position = MDUtils.getPositionIndex(taggedImage.tags);
         slice = MDUtils.getSliceIndex(taggedImage.tags);
         channel = MDUtils.getChannelIndex(taggedImage.tags);
         frame = MDUtils.getFrameIndex(taggedImage.tags);
      } catch (JSONException ex) {
         ReportingUtils.showError("image tag index missing");
      }
      
      if (position == numPositions_ - 1) {
         if ((slice + 1 ) % slicesPerWrite_ == 0) {
            //add a group of slices in a single channel to batch
            synchronized (hdfQueue_) {
               for (int s = slice - slicesPerWrite_ + 1; s <= slice; s++) {
                  //add to HDFWriter in groups of 1 or more slices in a single channel
                  hdfQueue_.add(storage_.getImage(channel, s, frame, 0));
               }
            }
         } else if (slice == numSlices_ - 1) {
            //Send dummy images to close out time point in this channel
            LinkedList<TaggedImage> batch = new LinkedList<TaggedImage>();
            for (int s = slice; s < (numSlices_ / slicesPerWrite_ + 1) * slicesPerWrite_; s++) {
               TaggedImage img = storage_.getImage(channel, s, frame, 0);
               if (img == null) {
                  img = storage_.getImage(channel, slice, frame, 0);
                  try {
                     //copy tags
                     img = new TaggedImage(null, new JSONObject(img.tags.toString()));
                     MDUtils.setSliceIndex(img.tags, slice);
                  } catch (JSONException ex) {
                     ReportingUtils.showError("Problem generating dummy slice");
                  }
               }
               //add to HDFWriter in groups of 1 or more slices in a single channel
               batch.add(img);
            }
            synchronized(hdfQueue_) {
               hdfQueue_.addAll(batch);
            }
         }
      }

      
   }

   @Override
   public Set<String> imageKeys() {
      return storage_.imageKeys();
   }

   @Override
   public void finished() {
      storage_.finished();
      finished_ = true;
   }

   @Override
   public boolean isFinished() {
      return finished_;
   }

   @Override
   public void setSummaryMetadata(JSONObject md) {
      storage_.setSummaryMetadata(md);
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return storage_.getSummaryMetadata();
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      storage_.setDisplayAndComments(settings);
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return storage_.getDisplayAndComments();
   }

   @Override
   public void close() {
      storage_.close();
   }

   @Override
   public String getDiskLocation() {
      return storage_.getDiskLocation();
   }

   @Override
   public int lastAcquiredFrame() {
      return storage_.lastAcquiredFrame();
   }

   @Override
   public long getDataSetSize() {
      return storage_.getDataSetSize();
   }

   @Override
   public void writeDisplaySettings() {
      storage_.writeDisplaySettings();
   }
  
}
