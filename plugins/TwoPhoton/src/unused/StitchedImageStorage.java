/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package unused;

import com.imaging100x.twophoton.TwoPhotonControl;
import java.io.File;
import java.io.IOException;
import java.util.Set;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.TaggedImage;
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
public class StitchedImageStorage implements TaggedImageStorage{

   
   //holds stitched images
   private TaggedImageStorageMultipageTiff diskStorage_;
   //holds images where stiching is not yet complete
   private TreeMap<String, TaggedImage> ramStorage_;
   
   private int lastAcquiredFrame_ = 0;
   private int numPositions_;
   private boolean invertX_, invertY_, swapXandY_;
   private int tilesPerRow_, tilesPerColumn_;
   private String storageDir_;

   public StitchedImageStorage(JSONObject summaryMetadata, boolean invertX, boolean invertY, boolean swapXandY,
           int tilesPerRow, String savingDir) {

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
      } catch (JSONException ex) {
         ReportingUtils.showError("Couldn't get number of positions from summary metadata");
      }


      try {
         diskStorage_ = new TaggedImageStorageMultipageTiff(storageDir_, true, summaryMetadata, false, true, false);
      } catch (IOException ex) {
         ReportingUtils.showError("Unable to create disk storage");
      }
      invertX_ = invertX;
      invertY_ = invertY;
      swapXandY_ = swapXandY;

   }

   @Override
   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      //either get partially stitched images from RAM or read stitched images off of disk
      String label = MDUtils.generateLabel(channelIndex, sliceIndex, frameIndex, 0);
      if (ramStorage_.containsKey(label)) {
         return ramStorage_.get(label);
      } else {
         return diskStorage_.getImage(channelIndex, sliceIndex, frameIndex, 0);
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
         diskStorage_.putImage(ramStorage_.remove(label));
      }
   }

   @Override
   public Set<String> imageKeys() {
      Set<String> imageKeys = ramStorage_.keySet();
      imageKeys.addAll(diskStorage_.imageKeys());
      return imageKeys;
   }

   @Override
   public void finished() {
      diskStorage_.finished();
   }

   @Override
   public boolean isFinished() {
      return diskStorage_.isFinished();
   }

   @Override
   public void setSummaryMetadata(JSONObject md) {
      diskStorage_.setSummaryMetadata(md);
   }

   @Override
   public JSONObject getSummaryMetadata() {
      return diskStorage_.getSummaryMetadata();
   }

   @Override
   public void setDisplayAndComments(JSONObject settings) {
      diskStorage_.setDisplayAndComments(settings);
   }

   @Override
   public JSONObject getDisplayAndComments() {
      return diskStorage_.getDisplayAndComments();
   }

   @Override
   public void close() {
      diskStorage_.close();
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
      diskStorage_.writeDisplaySettings();
   }
   
}
