/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import java.io.IOException;
import java.util.Set;
import java.util.TreeSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import mmcorej.MMCoreJ;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.acquisition.TaggedImageStorageRam;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Henry
 */
public class DynamicStitchingImageStorage {

    //holds stitched images
    private TaggedImageStorage storage_;
    //local copy of summary MD with different info about positions than underlying storage
    private JSONObject summaryMetadata_;
    private int numPositions_, width_, height_, tileWidth_, tileHeight_;
    private boolean invertX_, invertY_, swapXandY_;
    private int tilesPerRow_, tilesPerColumn_;
    private TreeSet<String> imageKeys_;

    public DynamicStitchingImageStorage(JSONObject summaryMetadata, String savingDir) {
        imageKeys_ = new TreeSet<String>();
        try {
            summaryMetadata_ = new JSONObject(summaryMetadata.toString());
        } catch (JSONException ex) {
            ReportingUtils.showError("Couldn't copy summary MD");
        }

        try {
            if (savingDir == null) {
                //RAM storage
                storage_ = new TaggedImageStorageRam(summaryMetadata);
            } else {
                storage_ = new TaggedImageStorageMultipageTiff(savingDir, true, summaryMetadata, false, true, false);
            }
        } catch (IOException ex) {
            ReportingUtils.showError("Unable to create disk storage");
        }


        try {
            //TODO: change this to read position coordinates from metadata
            tilesPerRow_ = MMStudioMainFrame.getInstance().getPositionList().getPosition(0).getGridRow();
            tilesPerColumn_ = MMStudioMainFrame.getInstance().getPositionList().getPosition(0).getGridColumn();
            numPositions_ = tilesPerRow_ * tilesPerColumn_;
            ////////////////////////////////////////////////////
            tileHeight_ = MDUtils.getHeight(summaryMetadata);
            tileWidth_ = MDUtils.getWidth(summaryMetadata);
            height_ = tilesPerColumn_ * tileHeight_;
            width_ = tilesPerRow_ * tileWidth_;
            //change summary metadata fields
            summaryMetadata_.put("Positions", 1);
            summaryMetadata_.put("Width", width_);
            summaryMetadata_.put("Height", height_);
        } catch (Exception ex) {
            ReportingUtils.showError("Couldn't get number of positions from summary metadata");
        }

        try {
            MMStudioMainFrame gui = MMStudioMainFrame.getInstance();
            String camera = gui.getCore().getCameraDevice();
            swapXandY_ = gui.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_SwapXY()).equals("1");
            invertX_ = gui.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorX()).equals("1");
            invertY_ = gui.getCore().getProperty(camera, MMCoreJ.getG_Keyword_Transpose_MirrorY()).equals("1");
        } catch (Exception ex) {
            ReportingUtils.showError(ex.toString());
        }
    }
   
   public int getWidth() {
      return width_;
   }
   
   public int getHeight() {
      return height_;
   }

   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int p) {
      //read as many tiles from underlying storage as available, fill in the rest blank      
      JSONObject tags = null;
      byte[] pixels = new byte[width_*height_];
      for (int positionIndex = 0; positionIndex < numPositions_; positionIndex++) {
         TaggedImage tile = storage_.getImage(channelIndex, sliceIndex, frameIndex, positionIndex);
         if (tile != null) {
            tags = tile.tags;
            int xTileIndex = positionIndex % tilesPerRow_;
            int yTileIndex = positionIndex / tilesPerRow_;
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
            for (int y = 0; y < tileHeight_; y++) {
               int destinationIndex = width_ * (tileHeight_ * yTileIndex + y) + xTileIndex * tileWidth_;
               System.arraycopy(tile.pix, y * tileWidth_, pixels, destinationIndex, tileWidth_);
            }
         }
      }
      if (tags == null) {
         //if no tiles present
         return null;
      }
      
      try {
         if (storage_ instanceof TaggedImageStorageRam) {
            //make copy so original image tags are unaffected
            tags = new JSONObject(tags.toString()); 
         }
         tags.put("Width", width_);
         tags.put("Height", height_);
         tags.put("PositionIndex", 0);         
         tags.put("PositionName", "Stitched");
      } catch (JSONException ex) {
         ReportingUtils.showError("Problem manipulating Image tags");
      }
      return new TaggedImage(pixels, tags);
   }

   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   public void putImage(TaggedImage taggedImage) throws MMException {
      try {
         imageKeys_.add(MDUtils.generateLabel(MDUtils.getChannelIndex(taggedImage.tags),
                 MDUtils.getSliceIndex(taggedImage.tags), MDUtils.getFrameIndex(taggedImage.tags), 0));
      } catch (JSONException ex) {
         ReportingUtils.showError("Indices missing from image tags");
      }
      storage_.putImage(taggedImage);
   }

   public Set<String> imageKeys() {
      return imageKeys_;
   }

   public void finished() {
      storage_.finished();
   }

   public boolean isFinished() {
      return storage_.isFinished();
   }

   public void setSummaryMetadata(JSONObject md) {
      storage_.setSummaryMetadata(md);
   }

   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   public void setDisplayAndComments(JSONObject settings) {
      storage_.setDisplayAndComments(settings);
   }

   public JSONObject getDisplayAndComments() {
      return storage_.getDisplayAndComments();
   }

   public void close() {
      storage_.close();
   }

   public String getDiskLocation() {
      return storage_.getDiskLocation();
   }

   public int lastAcquiredFrame() {
      return storage_.lastAcquiredFrame();
   }

   public long getDataSetSize() {
      return storage_.getDataSetSize();
   }

   public void writeDisplaySettings() {
      storage_.writeDisplaySettings();
   }
   
}
