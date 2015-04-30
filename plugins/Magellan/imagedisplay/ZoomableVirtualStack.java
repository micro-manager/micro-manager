package imagedisplay;

/*
 * To change this template, choose Tools | Templates and open the template in
 * the editor.
 */
import acq.Acquisition;
import acq.ExploreAcquisition;
import acq.FixedAreaAcquisition;
import acq.MMImageCache;
import acq.MultiResMultipageTiffStorage;
import java.awt.Dimension;
import java.awt.Point;
import java.util.Set;
import misc.LongPoint;
import mmcorej.TaggedImage;

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
   private MMImageCache imageCache_;
   private volatile int resolutionIndex_ = 0;
   private volatile int displayImageWidth_, displayImageHeight_;
   private volatile long xView_ = 0, yView_ = 0;  //top left pixel of view in current res
   private MultiResMultipageTiffStorage multiResStorage_;
   private final int tileWidth_, tileHeight_;
   private Acquisition acquisition_;
   private final boolean fixedAreaAcq_;
   private int xMax_, yMax_;
   private DisplayPlus disp_;

   public ZoomableVirtualStack(int type, int width, int height, MMImageCache imageCache,
           int nSlices, VirtualAcquisitionDisplay vad, MultiResMultipageTiffStorage multiResStorage,
           Acquisition acq) {
      super(width, height, type, null, imageCache, nSlices, vad);
      type_ = type;
      imageCache_ = imageCache;
      nSlices_ = nSlices;
      disp_ = (DisplayPlus) vad;
      multiResStorage_ = multiResStorage;
      //display image could conceivably be bigger than a single FOV, but not smaller
      displayImageWidth_ = width;
      displayImageHeight_ = height;
      tileHeight_ = multiResStorage.getTileHeight();
      tileWidth_ = multiResStorage.getTileWidth();
      acquisition_ = acq;
      if (acq instanceof FixedAreaAcquisition) {
         fixedAreaAcq_ = true;
         xMax_ = ((FixedAreaAcquisition) acq).getNumColumns() * tileWidth_;
         yMax_ = ((FixedAreaAcquisition) acq).getNumRows() * tileHeight_;         
      } else {
         fixedAreaAcq_ = false;
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
      disp_ = oldStack.disp_;
      //display image could conceivably be bigger than a single FOV, but not smaller
      displayImageWidth_ = width;
      displayImageHeight_ = height;
      tileHeight_ = multiResStorage_.getTileHeight();
      tileWidth_ = multiResStorage_.getTileWidth();
      acquisition_ = oldStack.acquisition_;
      xView_ = oldStack.xView_;
      yView_ = oldStack.yView_;
      if (acquisition_ instanceof FixedAreaAcquisition) {
         fixedAreaAcq_ = true;
         xMax_ = ((FixedAreaAcquisition) acquisition_).getNumColumns() * tileWidth_;
         yMax_ = ((FixedAreaAcquisition) acquisition_).getNumRows() * tileHeight_;
         resolutionIndex_ = oldStack.resolutionIndex_;
         //adjust view if change in canvas size has pushed this view out of bounds
         xView_ = (int) Math.max(0, Math.min(xView_, xMax_ / Math.pow(2, resolutionIndex_) - displayImageWidth_));
         yView_ = (int) Math.max(0, Math.min(yView_, yMax_ / Math.pow(2, resolutionIndex_) - displayImageHeight_));
      } else {
         fixedAreaAcq_ = false;
         resolutionIndex_ = oldStack.resolutionIndex_;
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
      resolutionIndex_ = viewStartResIndex;
      multiResStorage_.initializeToLevel(maxResIndex);
   }

   public int getResolutionIndex() {
      return resolutionIndex_;
   }

   public int getDownsampleFactor() {
       int dsFactor = (int) Math.pow(2, resolutionIndex_);
      return dsFactor ;
   }

   /**
    * 
    * @param mouseLocation
    * @param numLevels
    */
   public synchronized void zoom(Point mouseLocation, int numLevels) {
      //don't let fixed area acquisitions zoom out past the point where the area is too small
      if (!(acquisition_ instanceof ExploreAcquisition)) {
         int maxZoomIndex = multiResStorage_.getNumResLevels() - 1;
         if (maxZoomIndex != -1 && resolutionIndex_ + numLevels > maxZoomIndex) {
            numLevels = maxZoomIndex - resolutionIndex_;
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
      while (resolutionIndex_ + numLevels >= multiResStorage_.getNumResLevels()) {
         //returns false when no images to downsample so no zooming takes place
         boolean success = multiResStorage_.addLowerResolution();
         if (!success) {
            return;
         }
      }

      int previousDSFactor = (int) Math.pow(2, resolutionIndex_);
      //keep cursor in same location relative to full res data for fast zooming/unzooming
      if (resolutionIndex_ + numLevels >= 0 && resolutionIndex_ + numLevels < multiResStorage_.getNumResLevels()) {
         //get pixel location in full res image
         int dsFactor = (int) Math.pow(2, resolutionIndex_);
         int fullResX = (int) (dsFactor * (mouseLocation.x + xView_));
         int fullResY = (int) (dsFactor * (mouseLocation.y + yView_));
         //do actual zooming
         resolutionIndex_ += numLevels;
         dsFactor = (int) Math.pow(2, resolutionIndex_);
         xView_ = (int) (fullResX / dsFactor - mouseLocation.x);
         yView_ = (int) (fullResY / dsFactor - mouseLocation.y);
         //make sure view doesn't go outside image bounds
         if (fixedAreaAcq_) {
            xView_ = (int) Math.max(0, Math.min(xView_, xMax_ / Math.pow(2, resolutionIndex_) - displayImageWidth_));
            yView_ = (int) Math.max(0, Math.min(yView_, yMax_ / Math.pow(2, resolutionIndex_) - displayImageHeight_));
         }
      }

      //explore acquisition must have some area you've already explored in view
      if (acquisition_ instanceof ExploreAcquisition) {
         moveViewToVisibleArea();
      }


      if (acquisition_ instanceof FixedAreaAcquisition) {
         //change the canvas size, and shrink canvas only if moving out
         ((DisplayWindow) vad_.getHyperImage().getWindow()).resizeCanvas(numLevels > 0, previousDSFactor, true);
      }
   }

   private void moveViewToVisibleArea() {
      //compensate for the possibility of negative slice indices in explore acquisition
      int slice = disp_.getVisibleSliceIndex() + ((ExploreAcquisition) acquisition_).getLowestExploredSliceIndex();
      //check for valid tiles (at lowest res) at this slice        
      Set<Point> tiles = multiResStorage_.getExploredTilesAtSlice(slice);
      if (tiles.size() == 0) {
         return;
      }
      //center of one tile must be within corners of current view 
      double minDistance = Integer.MAX_VALUE;
      //do all calculations at full resolution
      long newXView = xView_ * getDownsampleFactor();
      long newYView = yView_ * getDownsampleFactor();
      for (Point p : tiles) {
         //translate row, col to x, y pixel coords of position center
         long xTileCenter = (long) ((0.5 + p.x) * tileWidth_);
         long yTileCenter = (long) ((0.5 + p.y) * tileHeight_);
         //get bounds of viewing area
         long xMin = getAbsoluteFullResPixelCoordinate(0, 0).x_;
         long yMin = getAbsoluteFullResPixelCoordinate(0, 0).y_;
         long xMax = xMin + displayImageWidth_ * getDownsampleFactor();
         long yMax = yMin + displayImageHeight_ * getDownsampleFactor();
         boolean xInView = xTileCenter >= xMin && xTileCenter <= xMax;
         boolean yInView = yTileCenter >= yMin && yTileCenter <= yMax;
         if (xInView && yInView) {
            return; //at least one tile is in view, don't need to do anything
         }
         //calculate min distance from among 4 corners
         double d1 = Math.sqrt((xTileCenter - xMin) * (xTileCenter - xMin) + (yTileCenter - yMin) * (yTileCenter - yMin)); //top left
         double d2 = Math.sqrt((xTileCenter - xMax) * (xTileCenter - xMax) + (yTileCenter - yMin) * (yTileCenter - yMin)); // top right
         double d3 = Math.sqrt((xTileCenter - xMin) * (xTileCenter - xMin) + (yTileCenter - yMax) * (yTileCenter - yMax)); // bottom left
         double d4 = Math.sqrt((xTileCenter - xMax) * (xTileCenter - xMax) + (yTileCenter - yMax) * (yTileCenter - yMax)); //bottom right
         double minOf4 = Math.min(Math.min(d1, d2), Math.min(d3, d4));
         if (minOf4 < minDistance) {
            minDistance = minOf4;
            if (d1 <= d2 && d1 <= d3 && d1 <= d4) { //top left
               newXView = xInView ? newXView : xTileCenter;
               newYView = yInView ? newYView : yTileCenter;
            } else if (d2 <= d1 && d2 <= d3 && d2 <= d4) { // top right
               newXView = xInView ? newXView : xTileCenter - displayImageWidth_ * getDownsampleFactor();
               newYView = yInView ? newYView : yTileCenter;
            } else if (d3 <= d1 && d3 <= d2 && d3 <= d4) { // bottom left
               newXView = xInView ? newXView : xTileCenter;
               newYView = yInView ? newYView : yTileCenter - displayImageHeight_ * getDownsampleFactor();
            } else { // bottom right
               newXView = xInView ? newXView : xTileCenter - displayImageWidth_ * getDownsampleFactor();
               newYView = yInView ? newYView : yTileCenter - displayImageHeight_ * getDownsampleFactor();
            }
         }
      }
      //readjust to current res level
      xView_ = newXView / getDownsampleFactor();
      yView_ = newYView / getDownsampleFactor();
   }
   
   public void pan(int dx, int dy) {
      if (fixedAreaAcq_) {
         xView_ = (int) Math.max(0, Math.min(xView_ + dx, xMax_ / Math.pow(2, resolutionIndex_) - displayImageWidth_));
         yView_ = (int) Math.max(0, Math.min(yView_ + dy, yMax_ / Math.pow(2, resolutionIndex_) - displayImageHeight_));
      } else {
         xView_ += dx;
         yView_ += dy;
         //only accept pan if it keeps some portion of explored area in view
         //explore acquisition must have some area you've already explored in view
         moveViewToVisibleArea();
      }
   }

   /**
    * return the absolute pixel coordinate of the image at full resolution
    *
    * @param x x pixel coordinate at current res level
    * @param y y pixel coordinate at current res level
    * @return
    */
   public LongPoint getAbsoluteFullResPixelCoordinate(long x, long y) {
      //add view offsets and convert to full resolution to get pixel location in full res image
      long fullResX =  (long) ((x + xView_) * Math.pow(2, resolutionIndex_));
      long fullResY =  (long) ((y + yView_) * Math.pow(2, resolutionIndex_));
      return new LongPoint(fullResX, fullResY);
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
      int fullResX = (int) ((x + xView_) * Math.pow(2, resolutionIndex_));
      int fullResY = (int) ((y + yView_) * Math.pow(2, resolutionIndex_));
      int xTileIndex = fullResX / tileWidth_ - (fullResX >= 0 ? 0 : 1);
      int yTileIndex = fullResY / tileHeight_ - (fullResY >= 0 ? 0 : 1);
      return new Point(xTileIndex, yTileIndex);
   }
   
   public LongPoint getZoomLocation() {
      return new LongPoint(xView_, yView_);
   }
   
   public Dimension getDisplayImageSize() {
      return new Dimension(displayImageWidth_, displayImageHeight_);
   }

   /**
    * return the pixel location in coordinates at appropriate res level of the
    * top left pixel for the given row/column
    *
    * @param row
    * @param col
    * @return
    */
   public LongPoint getDisplayedPixel(int row, int col) {
      long x = Math.round((col * tileWidth_) / (double)getDownsampleFactor()) - xView_;
      long y = Math.round((row * tileHeight_) / (double)getDownsampleFactor()) - yView_;
      return new LongPoint(x, y);
   }

   /**
    * Account for zoom and viewing are to get the display image pixel
    * corresponding to the full res image pixel given
    *
    * @param fullImageCoords
    * @return
    */
   public LongPoint getDisplayImageCoordsFromFullImageCoords(Point fullImageCoords) {
      return new LongPoint(fullImageCoords.x / getDownsampleFactor() - xView_, fullImageCoords.y / getDownsampleFactor() - yView_);
   }

   /**
    * 
    * @param displayedSliceIndex - 0 based
    * @param displayedFrameIndex - 0 based
    * @return 
    */
   public double getZCoordinateOfDisplayedSlice(int displayedSliceIndex) {
      return acquisition_.getZCoordinateOfSlice(displayedSliceIndex);
   }
   
   /**
    * 
    * @param zPos
    * @param displayedFrameIndex - 0 based
    * @return 
    */
   public int getSliceIndexFromZCoordinate(double zPos) {
      return acquisition_.getSliceIndexFromZCoordinate(zPos);
   }

   //this method is called to get the tagged image for display purposes only
   //return the zoomed or downsampled image here for fast performance
   @Override
   protected TaggedImage getTaggedImage(int channel, int slice, int frame) {
      //tags and images ultimately get split apart from this function, so it is okay
      //to alter image size and not change tags to reflect that
      
      //compensate for the possibility of negative slice indices in explore acquisition
      if (acquisition_ instanceof ExploreAcquisition) {
         slice += ((ExploreAcquisition) acquisition_).getLowestExploredSliceIndex();
      }

      return multiResStorage_.getImageForDisplay(channel, slice, frame, resolutionIndex_,
              xView_, yView_, displayImageWidth_, displayImageHeight_);

   }
   
}
