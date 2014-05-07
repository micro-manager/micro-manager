/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import HDF.ImarisWriter;
import com.imaging100x.twophoton.SettingsDialog;
import com.imaging100x.twophoton.TwoPhotonControl;
import ij.IJ;
import ij.process.ByteProcessor;
import java.awt.Color;
import java.io.File;
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
 * and an optional Imaris writing image storage class
 *
 * @author Henry
 */
public class DoubleTaggedImageStorage implements TaggedImageStorage {

   private DynamicStitchingImageStorage storage_;
   private boolean makeImarisFile_, gaussianFilter_;
   private double filterWidth_;
   private int numPositions_, numSlices_, numChannels_, numFrames_, slicesPerWrite_;
   private volatile LinkedList<TaggedImage> imarisWritingQueue_;
   private Thread imarisWritingThread_;
   private boolean finished_ = false;
   private ByteProcessor imageProcessor_;
   private SingleThreadedGaussianFilter filter_;
   private Preferences prefs_;
   private int startS_ = -1, startMin_ = -1, startHour_ = -1;
   private String acqStartDate_;
   private ImarisWriter imarisWriter_;

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
         numPositions_ = MDUtils.getNumPositions(summaryMetadata);
         numSlices_ = MDUtils.getNumSlices(summaryMetadata);
         numChannels_ = MDUtils.getNumChannels(summaryMetadata);
         numFrames_ = MDUtils.getNumFrames(summaryMetadata);
         double pixelSizeXY = summaryMetadata.getDouble("PixelSize_um");
         double pixelSizeZ = summaryMetadata.getDouble("z-step_um");
         imarisWriter_ = new ImarisWriter(newDir.getAbsolutePath(), prefix, width, height, numSlices_,
                 numChannels_, numFrames_, pixelSizeXY, pixelSizeZ, channelColors);
      } catch (JSONException ex) {
         ReportingUtils.showError("Problem with summary metadata: couldnt make imaris writer");
      }

      imarisWritingQueue_ = new LinkedList<TaggedImage>();
      imarisWritingThread_ = new Thread(new Runnable() {
         @Override
         public void run() {
            imarisWriting();
         }
      }, "Imaris writing queue thread");
      imarisWritingThread_.start();
   }

   private void imarisWriting() {
      while (true) {
         TaggedImage toAdd = null;
         int size = 0;
         synchronized (imarisWritingQueue_) {
            size = imarisWritingQueue_.size();
            if (size != 0) {
               toAdd = imarisWritingQueue_.removeFirst();
            }
         }
         if (toAdd != null) {
            //gaussian Filter
            if (gaussianFilter_) {
               if (filter_ == null) {
                  try {
                     filter_ = new SingleThreadedGaussianFilter(MDUtils.getWidth(toAdd.tags),
                             MDUtils.getHeight(toAdd.tags), filterWidth_);
                  } catch (JSONException ex) {
                     ReportingUtils.showError("couldnt get width and height from tags");
                  }
               }
               //filter
               toAdd = new TaggedImage(filter_.gaussianFilter(toAdd.pix), toAdd.tags);
            }

            
            try {
               int channel = MDUtils.getChannelIndex(toAdd.tags);
               int frame = MDUtils.getFrameIndex(toAdd.tags);
               int slice = MDUtils.getSliceIndex(toAdd.tags);
               String time = convertMMToImsTime(toAdd.tags);
               imarisWriter_.addImage(toAdd.pix, slice, channel, frame, acqStartDate_, time);
            } catch (Exception e) {
               ReportingUtils.showError(e.toString());
            }

         } else if (finished_) {
            imarisWriter_.close();
            break;
         }
         TwoPhotonControl.updateHDFQueueSize(size, (int) (numChannels_ * numSlices_ * 2.5));
         //TODO check if lagging behind and cancel if needed
      }
   }

   private String convertMMToImsTime(JSONObject tags) throws JSONException {
      //get starting time
      if (startS_ == -1) {
         //first image
         String[] timeInfo = tags.getString("Time").split(" ");
         startHour_ = Integer.parseInt(timeInfo[1].split(":")[0]);
         startMin_ = Integer.parseInt(timeInfo[1].split(":")[1]);
         startS_ = Integer.parseInt(timeInfo[1].split(":")[2]);
         acqStartDate_ = timeInfo[0];
      }

      int elapsedMs = 0;
      try {
         elapsedMs = tags.getInt("ElapsedTime-ms") + startS_ * 1000
                 + startMin_ * 1000 * 60 + startHour_ * 60 * 60 * 1000;
      } catch (JSONException e) {
      }
      int h = elapsedMs / (60 * 60 * 1000);
      int min = (elapsedMs / (60 * 1000)) % 60;
      int s = (elapsedMs / 1000) % 60;
      int ms = elapsedMs % 1000;

      String timeMD = "";
      try {
         timeMD = tags.getString("Time");
      } catch (JSONException e) {}
      String date = timeMD.split(" ")[0];
      if (!date.equals(acqStartDate_)) {
         //for data sets spanning multiple days, only use number of hours into this day
         h = h % 24;
      }
      return date + " " + twoDigitFormat(h) + ":" + twoDigitFormat(min) + ":"
              + twoDigitFormat(s) + "." + threeDigitFormat(ms);
   }
   
   private String threeDigitFormat(int i) {
      String ret = i + "";
      if (ret.length() == 1) {
         ret = "00" + ret;
      } else if (ret.length() == 2) {
         ret = "0" + ret;
      }
      return ret;
   }

   private String twoDigitFormat(int i) {
      String ret = i + "";
      if (ret.length() == 1) {
         ret = "0" + ret;
      }
      return ret;
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
         synchronized (imarisWritingQueue_) {
            imarisWritingQueue_.add(image);         
         }
      }
   }

   @Override
   public Set<String> imageKeys() {
      return storage_.imageKeys();
   }

   @Override
    public void finished() {
        //Why are things closing before they are supposed to?
        StackTraceElement[] e = Thread.currentThread().getStackTrace();
        for (StackTraceElement s : e) {
            ReportingUtils.logError(s.toString());
        }
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
