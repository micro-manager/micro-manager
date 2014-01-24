/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import HDF.*;
import com.imaging100x.twophoton.SettingsDialog;
import com.imaging100x.twophoton.TwoPhotonControl;
import ij.IJ;
import ij.plugin.filter.GaussianBlur;
import ij.process.ByteProcessor;
import java.awt.Color;
import java.io.File;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 * A class that holds a tagged image storage which dynamically stitches images,
 * and an otional imaris writing image storage class
 *
 * @author Henry
 */
public class DoubleTaggedImageStorage implements TaggedImageStorage {

   private DynamicStitchingImageStorage storage_;
   private boolean makeImarisFile_, gaussianFilter_;
   private double filterWidth_;
   private int numPositions_, numSlices_, numChannels_, slicesPerWrite_;
   private volatile LinkedList<TaggedImage> hdfPreprocessQueue_;
   private volatile LinkedList<PipelineImage> hdfQueue_;
   private HDFWriter hdfWriter_;
   private HDFPreprocessor hdfPreprocessor_;
   private Thread hdfWritingThread_, hdfProcessingThread_;
   private boolean finished_ = false;
   private boolean preprocessFinished_ = false;
   private ByteProcessor imageProcessor_;
   private GaussianBlur filter_ = new GaussianBlur();
   private Preferences prefs_;

   public DoubleTaggedImageStorage(JSONObject summaryMetadata, String savingDir, Preferences prefs) {
      prefs_ = prefs;
      String imarisDirectory = prefs_.get(SettingsDialog.STITCHED_DATA_DIRECTORY, "");
      makeImarisFile_ = prefs_.getBoolean(SettingsDialog.CREATE_IMS_FILE, false);
      gaussianFilter_ = prefs_.getBoolean(SettingsDialog.FILTER_IMS, false);
      filterWidth_ = prefs_.getDouble(SettingsDialog.FILTER_SIZE, 2.0);

      storage_ = new DynamicStitchingImageStorage(summaryMetadata, savingDir);

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
      } catch (JSONException ex) {
      }
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


      hdfQueue_ = new LinkedList<PipelineImage>();
      hdfPreprocessQueue_ = new LinkedList<TaggedImage>();
      try {
         numPositions_ = MDUtils.getNumPositions(summaryMetadata);
         numSlices_ = MDUtils.getNumSlices(summaryMetadata);
         numChannels_ = MDUtils.getNumChannels(summaryMetadata);
      } catch (JSONException ex) {
         ReportingUtils.showError("Coulndt get num positions from summary metadata");
      }


      hdfProcessingThread_ = new Thread(new Runnable() {

         @Override
         public void run() {
            hdfPreprocessing();
         }
      }, "HDF Preprocessing thread");
      hdfProcessingThread_.start();

      hdfWritingThread_ = new Thread(new Runnable() {

         @Override
         public void run() {
            hdfWriting();
         }
      }, "HDF writing thread");
      hdfWritingThread_.start();
   }

   private Object gaussianFilter(Object pixels) {
      imageProcessor_.setPixels(Arrays.copyOf((byte[]) pixels, ((byte[]) pixels).length));
      filter_.blurGaussian(imageProcessor_, filterWidth_, filterWidth_, 0.002);
      return imageProcessor_.getPixels();
   }

   private void hdfPreprocessing() {
      try {
         while (!preprocessFinished_ && makeImarisFile_) {
            int size = 0;
            synchronized (hdfPreprocessQueue_) {
               size = hdfPreprocessQueue_.size();
            }
            TwoPhotonControl.updateFilterQueueSize(size, (int) (numChannels_ * numSlices_ * 2.5));
            //check if Imaris writing is too slow and abort if needed
            if (size > 2.5 * numChannels_ * numSlices_) {
               hdfWriter_.finish();
               hdfWriter_.close();
               makeImarisFile_ = false;
               ReportingUtils.showMessage("Imaris writing couldn't keep up with rate of data acquisition."
                       + " Imaris writing aborted.");
               break;
            }


            //break loop if images done coming in preprocess queue is empty 
            if (finished_) {
               synchronized (hdfPreprocessQueue_) {
                  if (hdfPreprocessQueue_.isEmpty()) {
                     preprocessFinished_ = true;
                     break;
                  } else if (hdfPreprocessQueue_.size() < slicesPerWrite_ * numChannels_) {
                     //abort has occured
                     preprocessFinished_ = true;
                     break;
                  }
                  //else still waiting to take images out of queue
               }
            }

            //add slicesPerWrite * numChannels images to a batch
            LinkedList<TaggedImage> batch;
            int hppqSize = 0;
            synchronized (hdfPreprocessQueue_) {
               hppqSize = hdfPreprocessQueue_.size();
            }
            if (hppqSize >= slicesPerWrite_ * numChannels_) {
               //filter slices, add to batch, hdfPreprocess
               batch = new LinkedList<TaggedImage>();
               synchronized (hdfPreprocessQueue_) {
                  for (int i = 0; i < slicesPerWrite_ * numChannels_; i++) {
                     batch.add(hdfPreprocessQueue_.removeFirst());
                  }
               }
            } else {
               Thread.sleep(10);
               continue;
            }


            //Filter
            if (gaussianFilter_) {
               if (imageProcessor_ == null) {
                  try {
                     imageProcessor_ = new ByteProcessor(MDUtils.getWidth(batch.getFirst().tags),
                             MDUtils.getHeight(batch.getFirst().tags));
                  } catch (JSONException ex) {
                     ReportingUtils.showError("Problem with image tags");
                  }
               }
               for (int i = 0; i < batch.size(); i++) {
                  if (batch.get(i).pix != null) {
                     //filter if it's not a dummy image
                     batch.add(i, new TaggedImage(gaussianFilter(batch.get(i).pix), batch.get(i).tags));
                     batch.remove(i + 1);
                  }
               }
            }

            //process images in groups corrseponding to a single channel at a time
            for (int c = 0; c < numChannels_; c++) {
               LinkedList<TaggedImage> singleChannelBatch = new LinkedList<TaggedImage>();
               for (int s = 0; s < slicesPerWrite_; s++) {
                  singleChannelBatch.add(batch.get(c + s * numChannels_));
               }
               PipelineImage pi = hdfPreprocessor_.process(singleChannelBatch);
               synchronized (hdfQueue_) {
                  hdfQueue_.add(pi);
               }
            }

         }
      } catch (Exception e) {
         ReportingUtils.showError("Exception in HDF preprocessing: cancelling Imaris file writing");
         e.printStackTrace();
         makeImarisFile_ = false;
      }
   }

   //Occurs on HDF writing thread
   private void hdfWriting() {
      try {
         while (!hdfWriter_.isFinished() && makeImarisFile_) {
            //check if Imaris writing is too slow and abort if needed
            if (hdfQueue_.size() * slicesPerWrite_ > 2.5 * numChannels_ * numSlices_) {
               hdfWriter_.finish();
               hdfWriter_.close();
               makeImarisFile_ = false;
               ReportingUtils.showMessage("Imaris writing couldn't keep up with rate of data acquisition."
                       + " Imaris writing aborted.");
               break;
            }


            //Check if finished or aborted
            //TODO: properly finish aborted acqs
            if (preprocessFinished_) {
               synchronized (hdfQueue_) {
                  if (hdfQueue_.size() == 0) {
                     //may be leftover images
                     hdfWriter_.finish();
                     //close this as soon as it is done because don't need to read data from it
                     hdfWriter_.close();
                     break;
                  } else {
                     //finished, but preprocessing incomplete

                     Thread.sleep(5);

                  }
               }
            }

            PipelineImage toWrite = null;
            boolean isEmpty = true;
            synchronized (hdfQueue_) {
               isEmpty = hdfQueue_.isEmpty();
            }
            if (!isEmpty) {
               synchronized (hdfQueue_) {
                  toWrite = hdfQueue_.removeFirst();
               }
            } else {
               //wait for more
               try {
                  Thread.sleep(2);
               } catch (InterruptedException ex) {
                  ReportingUtils.showError("Couldn't sleep thread");
               }
               continue;
            }

            //write batch
            hdfWriter_.writeImage(toWrite);
            int size;
            synchronized (hdfQueue_) {
               size = hdfQueue_.size();
            }
            TwoPhotonControl.updateHDFQueueSize(size * slicesPerWrite_, (int) (numChannels_ * numSlices_ * 2.5));

         }
      } catch (Exception e) {
         ReportingUtils.showError("Exception in HDF preprocessing: cancelling Imaris file writing");
         makeImarisFile_ = false;
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

      //when fully stitched, add to HDF preprocesser
      if (position == numPositions_ - 1) {
         TaggedImage image = storage_.getImage(channel, slice, frame, 0);
         synchronized (hdfPreprocessQueue_) {
            hdfPreprocessQueue_.add(image);
            //add dummy slices  if needed after the last image at the time point has arrived
            if (slice == numSlices_ - 1 && channel == numChannels_ - 1 && slicesPerWrite_ > 1) {
               //Last slice for this time point in this channel-send dummy images as needed
               int s = slice + 1;
               while (s % slicesPerWrite_ != 0) {
                  try {
                     //add dummy slices first by channel then by slice
                     for (int c = 0; c < numChannels_; c++) {
                        //copy tags
                        JSONObject tags = storage_.getImageTags(c, slice, frame, 0);
                        TaggedImage dummyImage = new TaggedImage(null, new JSONObject(tags.toString()));
                        MDUtils.setSliceIndex(dummyImage.tags, s);
                        hdfPreprocessQueue_.add(dummyImage);
                     }
                  } catch (JSONException ex) {
                     ReportingUtils.showError("Problem generating dummy slice");
                  }
                  s++;
               }

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
