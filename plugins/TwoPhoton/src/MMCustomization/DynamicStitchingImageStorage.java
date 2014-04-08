/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package MMCustomization;

import com.imaging100x.twophoton.SettingsDialog;
import com.imaging100x.twophoton.Util;
import java.awt.Point;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Set;
import java.util.TreeSet;
import mmcorej.TaggedImage;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.acquisition.TaggedImageStorageRam;
import org.micromanager.api.TaggedImageStorage;
import org.micromanager.utils.MDUtils;
import org.micromanager.utils.MMException;
import org.micromanager.utils.ReportingUtils;

/**
 * This class acts as an intermediary between the underlying data storage, 
 * which stores images without stitching and with metadata intact and
 * the display, which will either request a full res subimage or downsampled
 * version of the fully stitched view
 * @author Henry
 */
public class DynamicStitchingImageStorage implements TaggedImageStorage {

    private TaggedImageStorage imageStorage_;
    private TaggedImageStorageMultipageTiff loResStitchedStorage_;
    //local copy of summary MD with different info about positions than underlying storage
    private JSONObject summaryMetadata_;
    private int stitchedImageWidth_, stitchedImageHeight_, tileWidth_, tileHeight_, downsampledWidth_, downsampledHeight_;
    private int dsFactor_;
    private TreeSet<String> imageKeys_;
    private JSONArray positionList_;
    private int xOverlap_, yOverlap_, numRows_, numCols_;

   public DynamicStitchingImageStorage(JSONObject summaryMetadata, String savingDir, String loResSavingDir) {
      xOverlap_ = SettingsDialog.getXOverlap();
      yOverlap_ = SettingsDialog.getYOverlap();
      imageKeys_ = new TreeSet<String>();
      try {
         numRows_ = 1;
         numCols_ = 1;
         if (summaryMetadata.has("InitialPositionList") && !summaryMetadata.isNull("InitialPositionList")) {
            positionList_ = summaryMetadata.getJSONArray("InitialPositionList");
            for (int i = 0; i < positionList_.length(); i++) {
               long colInd = positionList_.getJSONObject(i).getLong("GridColumnIndex");
               long rowInd = positionList_.getJSONObject(i).getLong("GridRowIndex");
               if (colInd >= numCols_) {
                  numCols_ = (int) (colInd + 1);
               }
               if (rowInd >= numRows_) {
                  numRows_ = (int) (rowInd + 1);
               }
            }
         }
         tileHeight_ = MDUtils.getHeight(summaryMetadata);
         tileWidth_ = MDUtils.getWidth(summaryMetadata);
         stitchedImageHeight_ = numRows_ * tileHeight_ - (numRows_ - 1) * yOverlap_;
         stitchedImageWidth_ = numCols_ * tileWidth_ - (numCols_ - 1) * xOverlap_;
         dsFactor_ = Math.max(1,
                 Math.max(stitchedImageWidth_, stitchedImageHeight_) / ZoomableVirtualStack.WIDTH_HEIGHT_MAX);
         downsampledWidth_ = stitchedImageWidth_ / dsFactor_;
         downsampledHeight_ = stitchedImageHeight_ / dsFactor_;
         //change summary metadata fields seen by display classes              
         summaryMetadata_ = new JSONObject(summaryMetadata.toString());
         summaryMetadata_.put("Positions", 1);
         summaryMetadata_.put("Width", downsampledWidth_);
         summaryMetadata_.put("Height", downsampledHeight_);

         //create storage for raw data
         if (savingDir == null) {
            //RAM storage
            imageStorage_ = new TaggedImageStorageRam(summaryMetadata);
         } else {
            //disk storage
            imageStorage_ = new TaggedImageStorageMultipageTiff(savingDir, true, summaryMetadata, false, true, true);
         }

         //create storage for downsampled stitched image
         //Modify summary metadata as needed 

         loResStitchedStorage_ = new TaggedImageStorageMultipageTiff(loResSavingDir, true, summaryMetadata_, false, false, true);
      } catch (IOException ex) {
         ReportingUtils.showError("Unable to create disk storage");
         ex.printStackTrace();
      } catch (JSONException j) {
         ReportingUtils.showError("Problem with summary metadata");
         j.printStackTrace();
      }
   }

   //Return a subimage of the larger stitched image, loading only the tiles neccesary to form the subimage
   public TaggedImage getFullResStitchedSubImage(int channel, int slice, int frame, int x, int y, int width, int height) {
      byte[] pixels = new byte[width * height];
      //go line by line through one column of tiles at a time, then move to next column
      JSONObject topLeftMD = null;
      //first calculate how many columns and rows of tiles are relevant
      int lastCol = -1;
      LinkedList<Integer> lineWidths = new LinkedList<Integer>();
      for (int i = x; i < x + width; i++) {
         int colIndex = Util.stitchedPixelToTile(i, xOverlap_, tileWidth_, numCols_, stitchedImageWidth_);                 
         if (colIndex != lastCol) {
            lineWidths.add(0);
         }
         //increment by 1
         lineWidths.add(lineWidths.removeLast() + 1);
         lastCol = colIndex;
      }
      int lastRow = -1;
      LinkedList<Integer> lineHeights = new LinkedList<Integer>();
      for (int i = y; i < y + height; i++) {
         int rowIndex = Util.stitchedPixelToTile(i, yOverlap_, tileHeight_, numRows_, stitchedImageHeight_);                 
         if (rowIndex != lastRow) {
            lineHeights.add(0);
         }
         //increment by 1
         lineHeights.add(lineHeights.removeLast() + 1);
         lastRow = rowIndex;
      }
      //get starting row and column
      int r = Util.stitchedPixelToTile(y, yOverlap_, tileHeight_, numRows_, stitchedImageHeight_);
      int c = Util.stitchedPixelToTile(x, xOverlap_, tileWidth_, numCols_, stitchedImageWidth_);
      int xOffset = 0;
      for (int col = 0; col < lineWidths.size(); col++) {
         int yOffset = 0;
         for (int row = 0; row < lineHeights.size(); row++) {
            TaggedImage tile = imageStorage_.getImage(channel, slice, frame, Util.getPosIndex(positionList_, row + r, col + c));
            if (tile == null) {
               continue; //If no data present for this tile go on to next one
            }
            //take top left tile for metadata
            if (topLeftMD == null) {
               topLeftMD = tile.tags;
            }
            for (int line = yOffset; line < lineHeights.get(row) + yOffset; line++) {
               int tileYPix = Util.stitchedPixelToTilePixel(y + line, yOverlap_, tileHeight_, numRows_);
               int tileXPix = Util.stitchedPixelToTilePixel(x + xOffset, xOverlap_, tileWidth_, numCols_);
               System.arraycopy(tile.pix, tileYPix * tileWidth_ + tileXPix,
                       pixels, xOffset + width * line, lineWidths.get(col));
            }
            yOffset += lineHeights.get(row);
         }
         xOffset += lineWidths.get(col);
      }
      return new TaggedImage(pixels, topLeftMD);      
   }

//   public TaggedImage getImage(int channelIndex, int sliceIndex, int frameIndex, int p) {
//      if (stitchedImageWidth_ == tileWidth_ && stitchedImageHeight_ == tileHeight_) {
//          //one position, no stitching
//          return imageStorage_.getImage(channelIndex, sliceIndex, frameIndex, 0);
//      }
//       //read as many tiles from underlying storage as available, fill in the rest blank      
//      JSONObject tags = null;
//      byte[] pixels = new byte[stitchedImageWidth_*stitchedImageHeight_];
//      for (int positionIndex = 0; positionIndex < positionList_.length(); positionIndex++) {
//         TaggedImage tile = imageStorage_.getImage(channelIndex, sliceIndex, frameIndex, positionIndex);
//         if (tile != null) {
//            tags = tile.tags;
//            JSONObject posInfo;
//            int xTileIndex = 0, yTileIndex = 0;
//            //Get indices in stitched image 
//            try {
//                 posInfo = positionList_.getJSONObject(positionIndex);
//                 xTileIndex = (int) posInfo.getLong("GridColumnIndex");
//                 yTileIndex = (int) posInfo.getLong("GridRowIndex");
//             } catch (JSONException ex) {
//                 ReportingUtils.showError("Couldnt find tile indices");
//             }
//             //do stitching
//            int startLine = 0, endLine = tileHeight_;
//            if (yTileIndex > 0 ) {
//               startLine = yOverlap_ / 2;
//            }
//            if (yTileIndex < numRows_ - 1) {
//               endLine = tileHeight_ - (yOverlap_ + 1) / 2;
//            }
//            int startPix = 0, endPix = tileWidth_;
//            if (xTileIndex > 0 ) {
//               startPix = xOverlap_ / 2;
//            }
//            if (xTileIndex < numCols_ - 1) {
//               endPix = tileWidth_ - (xOverlap_ + 1) / 2;
//            }
//            int pixPerLine = endPix - startPix;
//            
//            for (int y = startLine; y < endLine; y++) {
//               int destIndex = ((tileHeight_ - yOverlap_) * yTileIndex + y) * stitchedImageWidth_ + 
//                       ((tileWidth_ - xOverlap_) * xTileIndex + startPix);             
//              try {
//               System.arraycopy(tile.pix, y * tileWidth_ + startPix, pixels, destIndex, pixPerLine);
//              } catch (Exception e) {
//                 System.out.println();
//              }
//            }
//         }
//      }
//      if (tags == null) {
//         //if no tiles present
//         return null;
//      }
//      
//      try {
//         if (imageStorage_ instanceof TaggedImageStorageRam) {
//            //make copy so original image tags are unaffected
//            tags = new JSONObject(tags.toString()); 
//         }
//         tags.put("Width", stitchedImageWidth_);
//         tags.put("Height", stitchedImageHeight_);
//         tags.put("PositionIndex", 0);         
//         tags.put("PositionName", "Stitched");
//      } catch (JSONException ex) {
//         ReportingUtils.showError("Problem manipulating Image tags");
//      }
//      return new TaggedImage(pixels, tags);
//   }

   public JSONObject getImageTags(int channelIndex, int sliceIndex, int frameIndex, int positionIndex) {
      return getImage(channelIndex, sliceIndex, frameIndex, positionIndex).tags;
   }

   public TaggedImage getImage(int channel, int slice, int frame, int position) {
      return loResStitchedStorage_.getImage(channel, slice, frame, 0);
      
   }
   
   public void putImage(TaggedImage taggedImage) throws MMException {
      int position = 0, channel = 0, slice = 0, frame = 0;
      try {
         position = MDUtils.getPositionIndex(taggedImage.tags);
         channel = MDUtils.getChannelIndex(taggedImage.tags);
         slice = MDUtils.getSliceIndex(taggedImage.tags);
         frame = MDUtils.getFrameIndex(taggedImage.tags);
         imageKeys_.add(MDUtils.generateLabel(channel,slice,frame,0));
      } catch (JSONException ex) {
         ReportingUtils.showError("Indices missing from image tags");
         ex.printStackTrace();
      }
      imageStorage_.putImage(taggedImage);
      
      //add to lo res storage             
      if (position == 0) {
         byte[] dsPix = new byte[downsampledWidth_ * downsampledHeight_];
         downsampleAndCopyTilePixels(dsPix, (byte[]) taggedImage.pix, position);
         loResStitchedStorage_.putImage(new TaggedImage(dsPix, taggedImage.tags));
      } else {
         //read downsampled pixels and copy in new ones
         byte[] dsPix = (byte[]) loResStitchedStorage_.getImage(channel, slice, frame, 0).pix;
         downsampleAndCopyTilePixels(dsPix, (byte[]) taggedImage.pix, position);
         try {
            loResStitchedStorage_.overwritePixels(dsPix, channel, slice, frame);
         } catch (IOException e) {
            ReportingUtils.showError("Couldnt overwrite downsmpled image pixels");
         }
      }

   }

   public Set<String> imageKeys() {
      return imageKeys_;
   }

   public void finished() {
      imageStorage_.finished();
      loResStitchedStorage_.finished();
   }

   public boolean isFinished() {
      return imageStorage_.isFinished();
   }

   public void setSummaryMetadata(JSONObject md) {
      imageStorage_.setSummaryMetadata(md);
   }

   public JSONObject getSummaryMetadata() {
      return summaryMetadata_;
   }

   public void setDisplayAndComments(JSONObject settings) {
      imageStorage_.setDisplayAndComments(settings);
   }

   public JSONObject getDisplayAndComments() {
      return imageStorage_.getDisplayAndComments();
   }

   public void close() {
      imageStorage_.close();
      //TODO also close other one nd delete it???
      
   }

   public String getDiskLocation() {
      return imageStorage_.getDiskLocation();
   }

   public int lastAcquiredFrame() {
      return imageStorage_.lastAcquiredFrame();
   }

   public long getDataSetSize() {
      return imageStorage_.getDataSetSize();
   }

   public void writeDisplaySettings() {
      imageStorage_.writeDisplaySettings();
   }
   
   
   //new strategy: duplicate pixel values when at tile junctions 
   private void downsampleAndCopyTilePixels(byte[] stitchedPix, byte[] tilePix, int positionIndex) {
      int row = Util.rowFromPosIndex(positionList_, positionIndex);
      int col = Util.colFromPosIndex(positionList_, positionIndex);      
      
      for (int dstx = 0; dstx < (double)tileWidth_ / (double)dsFactor_; dstx++) { //dstx = downsampled tile x
         for (int dsty = 0; dsty < (double)tileHeight_ / (double)dsFactor_; dsty++) {
            long sum = 0;
            int numPix = 0;
            for (int tileX = dstx * dsFactor_; tileX < (dstx + 1) * dsFactor_; tileX++) {
               for (int tileY = dsty * dsFactor_; tileY < (dsty + 1) * dsFactor_; tileY++) {
                  if (tileX < tileWidth_ && tileY < tileHeight_ && //make sure only relevant pixels added
                          (tileX >= xOverlap_ / 2 || col == 0) && (tileY >= yOverlap_ / 2 || row == 0)) {
                     int tilei = tileY * tileWidth_ + tileX;
                     sum += tilePix[tilei] & 0xff;
                     numPix++;
                  }
               }
            }
            if (numPix == 0) {
               continue; // no pixels in this square relevant to final image
            }
            //get index in downsampled stitched image
            int dssx = Util.tilePixelToStitchedPixel(dstx * dsFactor_, xOverlap_, tileWidth_, col) / dsFactor_;
            int dssy = Util.tilePixelToStitchedPixel(dsty * dsFactor_, yOverlap_, tileHeight_, row) / dsFactor_;
            int dssi = dssx + dssy * downsampledWidth_;
            //average pixels and add them in
            stitchedPix[dssi] = (byte) (sum / numPix);
         }
      }
   }
      
}
