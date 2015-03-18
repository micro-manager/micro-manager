package imagedisplay;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
import acq.Acquisition;
import acq.ExploreAcquisition;
import acq.FixedAreaAcquisition;
import acq.MultiResMultipageTiffStorage;
import java.awt.Point;
import mmcorej.TaggedImage;
import org.micromanager.api.TaggedImageStorage;

/**
 * This class acts as an intermediary between display and multiresolution
 * storage. Since the number of pixels in the viewer does not change, this class
 * should track, the zoom level and the part of the larger stitched image that
 * is showing, and deliver appropriate images to the viewer as such
 *
 * @author henrypinkard
 */
public class ZoomableVirtualStack extends AcquisitionVirtualStack {

   private int type_, nSlices_;
   private TaggedImageStorage imageCache_;
   private volatile int resolutionIndex = 0;
   private int displayImageWidth_, displayImageHeight_;
   private volatile int xView_ = 0, yView_ = 0;  //top left pixel of view in current res
   private MultiResMultipageTiffStorage multiResStorage_;
   private final int tileWidth_, tileHeight_;
   private Acquisition acquisition_;
   private final boolean constrainPanning_;
   private int xMax_, yMax_;

   public ZoomableVirtualStack(int type, int width, int height, TaggedImageStorage imageCache,
           int nSlices, VirtualAcquisitionDisplay vad, MultiResMultipageTiffStorage multiResStorage,
           Acquisition acq) {
      super(width, height, type, null, imageCache, nSlices, vad);
      type_ = type;
      imageCache_ = imageCache;
      nSlices_ = nSlices;
      multiResStorage_ = multiResStorage;
      //display image could conceivably be bigger than a single FOV, but not smaller
      displayImageWidth_ = width;
      displayImageHeight_ = height;
      tileHeight_ = multiResStorage.getTileHeight();
      tileWidth_ = multiResStorage.getTileWidth();
      acquisition_ = acq;
      if (acq instanceof FixedAreaAcquisition) {
         constrainPanning_ = true;
         xMax_ = ((FixedAreaAcquisition) acq).getNumColumns() * tileWidth_;
         yMax_ = ((FixedAreaAcquisition) acq).getNumRows() * tileHeight_;         
      } else {
         constrainPanning_ = false;
         xView_ = (multiResStorage.getTileWidth() - displayImageWidth_) / 2;
         yView_ = (multiResStorage.getTileHeight() - displayImageHeight_) / 2;
      }
   }

   /**
    * Constructor for creating a new stack same as the old one but with a new
    * size for dynamic resizing of display
    *
    * @param oldStack
    * @param width
    * @param height
    */
   public ZoomableVirtualStack(ZoomableVirtualStack oldStack, int width, int height) {
      super(width, height, oldStack.type_, null, oldStack.imageCache_, oldStack.nSlices_, oldStack.vad_);
      type_ = oldStack.type_;
      imageCache_ = oldStack.imageCache_;
      nSlices_ = oldStack.nSlices_;
      multiResStorage_ = oldStack.multiResStorage_;
      //display image could conceivably be bigger than a single FOV, but not smaller
      displayImageWidth_ = width;
      displayImageHeight_ = height;
      tileHeight_ = multiResStorage_.getTileHeight();
      tileWidth_ = multiResStorage_.getTileWidth();
      acquisition_ = oldStack.acquisition_;
      xView_ = oldStack.xView_;
      yView_ = oldStack.yView_;
      if (acquisition_ instanceof FixedAreaAcquisition) {
         constrainPanning_ = true;
         xMax_ = ((FixedAreaAcquisition) acquisition_).getNumColumns() * tileWidth_;
         yMax_ = ((FixedAreaAcquisition) acquisition_).getNumRows() * tileHeight_;
         resolutionIndex = oldStack.resolutionIndex;
         //adjust view if change in canvas size has pushed this view out of bounds
         xView_ = (int) Math.max(0, Math.min(xView_, xMax_ / Math.pow(2, resolutionIndex) - displayImageWidth_));
         yView_ = (int) Math.max(0, Math.min(yView_, yMax_ / Math.pow(2, resolutionIndex) - displayImageHeight_));
      } else {
         constrainPanning_ = false;
         resolutionIndex = oldStack.resolutionIndex;
      }
   }
   
   public boolean equalDisplaySize(ZoomableVirtualStack otherStack) {
      return displayImageHeight_ == otherStack.displayImageHeight_ && displayImageWidth_ == otherStack.displayImageWidth_;
   }
   
   /**
    * Called to initialize fixed area acquisitions, so that they can start fully zoomed out
    * @param resIndex 
    */
   public void initializeUpToRes(int viewStartResIndex, int maxResIndex) {
      resolutionIndex = viewStartResIndex;
      multiResStorage_.initializeToLevel(maxResIndex);
   }

   public int getResolutionIndex() {
      return resolutionIndex;
   }

   public int getDownsampleFactor() {
      return (int) Math.pow(2, resolutionIndex);
   }

   /**
    * 
    * @param mouseLocation
    * @param numLevels
    */
   public void zoom(Point mouseLocation, int numLevels) {
      //don't let fixed area acquisitions zoom out past the point where the area is too small
      if (!(acquisition_ instanceof ExploreAcquisition)) {
         int maxZoomIndex = multiResStorage_.getNumResLevels() - 1;
         if (maxZoomIndex != -1 && resolutionIndex + numLevels > maxZoomIndex) {
            numLevels = maxZoomIndex - resolutionIndex;
            if (numLevels == 0) {
               return;
            }
         }
      }


      if (mouseLocation == null) {
         //if mouse not over image zoom to center
         mouseLocation = new Point(displayImageWidth_ / 2, displayImageHeight_ / 2);
      }

      //If we haven't already gotten to this low of a resolution, create it
      while (resolutionIndex + numLevels >= multiResStorage_.getNumResLevels()) {
         //returns false when no images to downsample so no zooming takes place
         boolean success = multiResStorage_.addLowerResolution();
         if (!success) {
            return;
         }
      }

      //keep cursor in same location relative to full res data for fast zooming/unzooming
      if (resolutionIndex + numLevels >= 0 && resolutionIndex + numLevels < multiResStorage_.getNumResLevels()) {
         //get pixel location in full res image
         int dsFactor = (int) Math.pow(2, resolutionIndex);
         int fullResX = (int) (dsFactor * (mouseLocation.x + xView_));
         int fullResY = (int) (dsFactor * (mouseLocation.y + yView_));
         resolutionIndex += numLevels;
         dsFactor = (int) Math.pow(2, resolutionIndex);
         xView_ = (int) (fullResX / dsFactor - mouseLocation.x);
         yView_ = (int) (fullResY / dsFactor - mouseLocation.y);
         //make sure view doesn't go outside image bounds
         if (constrainPanning_) {
            xView_ = (int) Math.max(0, Math.min(xView_, xMax_ / Math.pow(2, resolutionIndex) - displayImageWidth_));
            yView_ = (int) Math.max(0, Math.min(yView_, yMax_ / Math.pow(2, resolutionIndex) - displayImageHeight_));
         }
      }
      
      
      if (acquisition_ instanceof FixedAreaAcquisition) {
         //change the canvas size, and shrink canvas only if moving out
         ((DisplayWindow) vad_.getHyperImage().getWindow()).resizeCanvas(numLevels > 0);
      }
   }

   public void translateView(int dx, int dy) {
      if (constrainPanning_) {
         xView_ = (int) Math.max(0, Math.min(xView_ + dx, xMax_ / Math.pow(2, resolutionIndex) - displayImageWidth_));
         yView_ = (int) Math.max(0, Math.min(yView_ + dy, yMax_ / Math.pow(2, resolutionIndex) - displayImageHeight_));
      } else {
         xView_ += dx;
         yView_ += dy;
      }
   }

   /**
    * return the absolute pixel coordinate of the image at full resolution
    *
    * @param x x pixel coordinate at current res level
    * @param y y pixel coordinate at current res level
    * @return
    */
   public Point getAbsoluteFullResPixelCoordinate(int x, int y) {
      //add view offsets and convert to full resolution to get pixel location in full res image
      int fullResX = (int) ((x + xView_) * Math.pow(2, resolutionIndex));
      int fullResY = (int) ((y + yView_) * Math.pow(2, resolutionIndex));
      return new Point(fullResX, fullResY);
   }

   /**
    * Return tile indices from pixel displayed in viewer
    *
    * @param x x pixel coordinate at current res level
    * @param y y pixel coordinate at current res level
    * @return
    */
   public Point getTileIndicesFromDisplayedPixel(int x, int y) {   
      //add view offsets and convert to full resolution to get pixel location in full res image
      int fullResX = (int) ((x + xView_) * Math.pow(2, resolutionIndex));
      int fullResY = (int) ((y + yView_) * Math.pow(2, resolutionIndex));
      int xTileIndex = fullResX / tileWidth_ - (fullResX >= 0 ? 0 : 1);
      int yTileIndex = fullResY / tileHeight_ - (fullResY >= 0 ? 0 : 1);
      return new Point(xTileIndex, yTileIndex);
   }

   /**
    * return the pixel location in coordinates at appropriate res level of the
    * top left pixel for the given row/column
    *
    * @param row
    * @param col
    * @return
    */
   public Point getDisplayedPixel(int row, int col) {
      int x = (col * tileWidth_) / getDownsampleFactor() - xView_;
      int y = (row * tileHeight_) / getDownsampleFactor() - yView_;
      return new Point(x, y);
   }

   /**
    * Account for zoom and viewing are to get the display image pixel
    * corresponding to the full res image pixel given
    *
    * @param fullImageCoords
    * @return
    */
   public Point getDisplayImageCoordsFromFullImageCoords(Point fullImageCoords) {
      return new Point(fullImageCoords.x / getDownsampleFactor() - xView_, fullImageCoords.y / getDownsampleFactor() - yView_);
   }

   /**
    *
    * @param sliceIndex
    * @return
    */
   public double getZCoordinateOfDisplayedSlice(int displayedSliceIndex, int displayedFrameIndex) {
      return acquisition_.getZCoordinateOfSlice(displayedSliceIndex, displayedFrameIndex);
   }

   public int getDisplaySliceIndexFromZCoordinate(double zPos, int displayedFrameIndex) {
      return acquisition_.getDisplaySliceIndexFromZCoordinate(zPos, displayedFrameIndex);
   }

   //this method is called to get the tagged image for display purposes only
   //return the zoomed or downsampled image here for fast performance
   @Override
   protected TaggedImage getTaggedImage(int channel, int slice, int frame) {
      //tags and images ultimately get split apart from this function, so it is okay
      //to alter image size and not change tags to reflect that

      //compensate for the possibility of negative slice indices in explore acquisition
      if (acquisition_ instanceof ExploreAcquisition) {
         slice += ((ExploreAcquisition) acquisition_).getLowestSliceIndex();
      }

      return multiResStorage_.getImageForDisplay(channel, slice, frame, resolutionIndex,
              xView_, yView_, displayImageWidth_, displayImageHeight_);

   }
}
