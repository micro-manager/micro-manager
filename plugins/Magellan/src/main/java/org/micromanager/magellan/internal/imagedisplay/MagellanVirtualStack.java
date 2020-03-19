package org.micromanager.magellan.internal.imagedisplay;

///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

/*
 * 
 */
import java.awt.Dimension;
import java.awt.Point;
import mmcorej.TaggedImage;
import org.micromanager.magellan.internal.misc.LongPoint;

/**
 * This class acts as an intermediary between display and multiresolution
 * storage. Since the number of pixels in the viewer does not change, this class
 * should track the zoom level, the part of the larger stitched image that
 * is showing, the current channel/slice/frame, and deliver appropriate images to the viewer as such
 *
 * @author henrypinkard
 */
 class MagellanVirtualStack extends ij.VirtualStack {

   private volatile int resolutionIndex_ = 0;
   private volatile int displayImageWidth_, displayImageHeight_;
   private volatile long xView_ = 0, yView_ = 0;  //top left pixel of view in current res
   private final int tileWidth_, tileHeight_;
   private final boolean boundedImage_;
   private final long xMax_, yMax_, xMin_, yMin_;
   
   private volatile int channel_, slice_, frame_, position_; //TODO: replace this with better mechanism

   final protected int width_, height_, type_;
   private int positionIndex_ = 0;
   private boolean rgb_;
   private MagellanImageCache provider_;

   public MagellanVirtualStack(int type, int width, int height, MagellanImageCache provider) {
      super(width, height, null, "");
      width_ = width;
      height_ = height;
      provider_ = provider;
      rgb_ = provider.isRGB();

      type_ = type;

      //display image could conceivably be bigger than a single FOV, but not smaller
      if (width < 1 || height < 1) {
         throw new RuntimeException();
      }
      displayImageWidth_ = width;
      displayImageHeight_ = height;
      tileHeight_ = provider.getTileHeight();
      tileWidth_ = provider.getTileWidth();
      boundedImage_ = provider_.isXYBounded();
      long[] bounds = provider.getImageBounds();
      xMax_ = bounds[0];
      yMax_ = bounds[1];
      xMin_ = bounds[2];
      yMin_ = bounds[3];
   }
   
   @Override
   public int getSize() {
      return 2;
   }

   /**
    * Constructor for creating a new stack same as the old one but with a new
    * size for dynamic resizing of display
    *
    * @param oldStack
    * @param width
    * @param height
    */
//   public ZoomableVirtualStack(ZoomableVirtualStack oldStack, int width, int height) {
//      super(width, height, oldStack.type_, null, oldStack.imageCache_, oldStack.nSlices_, oldStack.vad_, oldStack.multiResStorage_.isRGB());
//      imageCache_ = oldStack.imageCache_;
//      nSlices_ = oldStack.nSlices_;
//      multiResStorage_ = oldStack.multiResStorage_;
//      disp_ = oldStack.disp_;
//      //display image could conceivably be bigger than a single FOV, but not smaller
//      displayImageWidth_ = width;
//      displayImageHeight_ = height;
//      tileHeight_ = multiResStorage_.getTileHeight();
//      tileWidth_ = multiResStorage_.getTileWidth();
//      acquisition_ = oldStack.acquisition_;
//      xView_ = oldStack.xView_;
//      yView_ = oldStack.yView_;
//      if (acquisition_ instanceof MagellanGUIAcquisition || acquisition_ == null) {
//         boundedImage_ = true;
//         xMax_ = oldStack.xMax_;
//         yMax_ = oldStack.yMax_;
//         xMin_ = oldStack.xMin_;
//         yMin_ = oldStack.yMin_;
//         resolutionIndex_ = oldStack.resolutionIndex_;
//         //adjust view if change in canvas size has pushed this view out of bounds
//         xView_ = (int) Math.max(xMin_ / Math.pow(2, resolutionIndex_), Math.min(xView_, xMax_ / Math.pow(2, resolutionIndex_) - displayImageWidth_));
//         yView_ = (int) Math.max(yMin_ / Math.pow(2, resolutionIndex_), Math.min(yView_, yMax_ / Math.pow(2, resolutionIndex_) - displayImageHeight_));
//      } else {
//         boundedImage_ = false;
//         resolutionIndex_ = oldStack.resolutionIndex_;
//         //these dont maatter in explore mode
//         xMin_ = 0;
//         xMax_ = 0;
//         yMin_ = 0;
//         yMax_ = 0;
//      }
//   }

//   public boolean equalDisplaySize(ZoomableVirtualStack otherStack) {
//      return displayImageHeight_ == otherStack.displayImageHeight_ && displayImageWidth_ == otherStack.displayImageWidth_;
//   }

//   /**
//    * Called to initialize fixed area acquisitions, so that they can start fully
//    * zoomed out
//    *
//    * @param resIndex
//    */
//   public void initializeUpToRes(int viewStartResIndex, int maxResIndex) {
//      resolutionIndex_ = viewStartResIndex;
//      multiResStorage_.initializeToLevel(maxResIndex);
//   }

   /**
    * called when opening explore acqs on disk
    *
    * @param resIndex
    */
   public void setInitialResolutionIndex(int resIndex) {
      resolutionIndex_ = resIndex;
   }

   public int getResolutionIndex() {
      return resolutionIndex_;
   }

   public int getDownsampleFactor() {
      int dsFactor = (int) Math.pow(2, resolutionIndex_);
      return dsFactor;
   }

   /**
    *
    * @param mouseLocation
    * @param numLevels
    */
   public synchronized void zoom(Point mouseLocation, int numLevels) {
      //don't let fixed area acquisitions zoom out past the point where the area is too small
//      if (!(acquisition_ instanceof ExploreAcquisition)) {
//         int maxZoomIndex = multiResStorage_.getNumResLevels() - 1;
//         if (maxZoomIndex != -1 && resolutionIndex_ + numLevels > maxZoomIndex) {
//            numLevels = maxZoomIndex - resolutionIndex_;
//            if (numLevels == 0) {
//               return;
//            }
//         }
//      }
//
//      if (mouseLocation == null) {
//         //if mouse not over image zoom to center
//         mouseLocation = new Point(displayImageWidth_ / 2, displayImageHeight_ / 2);
//      }
//
//      if (acquisition_ != null && !acquisition_.anythingAcquired()) {
//         return; //dont want to zoom when theres not reference
//      }
//
//      if (acquisition_ != null) {
//         //If we haven't already gotten to this low of a resolution, create it
//         acquisition_.addResolutionsUpTo(resolutionIndex_ + numLevels);
//      }
//
//      int previousDSFactor = (int) Math.pow(2, resolutionIndex_);
//      //keep cursor in same location relative to full res data for fast zooming/unzooming
//      if (resolutionIndex_ + numLevels >= 0) {
//         if (resolutionIndex_ + numLevels < multiResStorage_.getNumResLevels()) {
//            //get pixel location in full res image
//            int dsFactor = (int) Math.pow(2, resolutionIndex_);
//            int fullResX = (int) (dsFactor * (mouseLocation.x + xView_));
//            int fullResY = (int) (dsFactor * (mouseLocation.y + yView_));
//            //do actual zooming
//            resolutionIndex_ += numLevels;
//            dsFactor = (int) Math.pow(2, resolutionIndex_);
//            xView_ = (fullResX / dsFactor - mouseLocation.x);
//            yView_ = (fullResY / dsFactor - mouseLocation.y);
//            //make sure view doesn't go outside image bounds
//            if (boundedImage_) {
//               xView_ = (int) Math.max(xMin_ / Math.pow(2, resolutionIndex_), Math.min(xView_, xMax_ / Math.pow(2, resolutionIndex_) - displayImageWidth_));
//               yView_ = (int) Math.max(yMin_ / Math.pow(2, resolutionIndex_), Math.min(yView_, yMax_ / Math.pow(2, resolutionIndex_) - displayImageHeight_));
//            }
//         } else {
//            final int levels = numLevels;
//            final Point loc = mouseLocation;
//            //Its not ready yet, try again later
//            SwingUtilities.invokeLater(new Runnable() {
//               @Override
//               public void run() {
//                  zoom(loc, levels);
//               }
//            });
//            return;
//         }
//      }
//
//      //explore acquisition must have some area you've already explored in view
//      if (acquisition_ instanceof ExploreAcquisition) {
//         moveViewToVisibleArea();
//      }
//
//      if (acquisition_ instanceof MagellanGUIAcquisition || acquisition_ == null) {
//         //change the canvas size, and shrink canvas only if moving out
//         ((DisplayWindow) vad_.getHyperImage().getWindow()).resizeCanvas(numLevels > 0, previousDSFactor, true);
//      }
//      //make sure it gets redrawn once new data appears
//      disp_.updateDisplay(true);
   }

//   private void moveViewToVisibleArea() {
//      //compensate for the possibility of negative slice indices 
//      int slice = disp_.getVisibleSliceIndex() + ((ExploreAcquisition) acquisition_).getMinSliceIndex();
//      //check for valid tiles (at lowest res) at this slice        
//      Set<Point> tiles = multiResStorage_.getTileIndicesWithDataAt(slice);
//      if (tiles.size() == 0) {
//         return;
//      }
//      //center of one tile must be within corners of current view 
//      double minDistance = Integer.MAX_VALUE;
//      //do all calculations at full resolution
//      long newXView = xView_ * getDownsampleFactor();
//      long newYView = yView_ * getDownsampleFactor();
//
//      for (Point p : tiles) {
//         //calclcate limits on margin of tile that must remain in view
//         long tileX1 = (long) ((0.1 + p.x) * tileWidth_);
//         long tileX2 = (long) ((0.9 + p.x) * tileWidth_);
//         long tileY1 = (long) ((0.1 + p.y) * tileHeight_);
//         long tileY2 = (long) ((0.9 + p.y) * tileHeight_);
//         long visibleWidth = (long) (0.8 * tileWidth_);
//         long visibleHeight = (long) (0.8 * tileHeight_);
//         //get bounds of viewing area
//         long fovX1 = getAbsoluteFullResPixelCoordinate(0, 0).x_;
//         long fovY1 = getAbsoluteFullResPixelCoordinate(0, 0).y_;
//         long fovX2 = fovX1 + displayImageWidth_ * getDownsampleFactor();
//         long fovY2 = fovY1 + displayImageHeight_ * getDownsampleFactor();
//
//         //check if tile and fov intersect
//         boolean xInView = fovX1 < tileX2 && fovX2 > tileX1;
//         boolean yInView = fovY1 < tileY2 && fovY2 > tileY1;
//         boolean intersection = xInView && yInView;
//
//         if (intersection) {
//            return; //at least one tile is in view, don't need to do anything
//         }
//         //tile to fov corner to corner distances
//         double tl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY1 - fovY2) * (tileY1 - fovY2)); //top left tile, botom right fov
//         double tr = ((tileX2 - fovX1) * (tileX2 - fovX1) + (tileY1 - fovY2) * (tileY1 - fovY2)); // top right tile, bottom left fov
//         double bl = ((tileX1 - fovX2) * (tileX1 - fovX2) + (tileY2 - fovY1) * (tileY2 - fovY1)); // bottom left tile, top right fov
//         double br = ((tileX1 - fovX1) * (tileX1 - fovX1) + (tileY2 - fovY1) * (tileY2 - fovY1)); //bottom right tile, top left fov
//
//         double closestCornerDistance = Math.min(Math.min(tl, tr), Math.min(bl, br));
//         if (closestCornerDistance < minDistance) {
//            minDistance = closestCornerDistance;
//            if (tl <= tr && tl <= bl && tl <= br) { //top left tile, botom right fov
//               newXView = xInView ? newXView : tileX1 - displayImageWidth_ * getDownsampleFactor();
//               newYView = yInView ? newYView : tileY1 - displayImageHeight_ * getDownsampleFactor();
//            } else if (tr <= tl && tr <= bl && tr <= br) { // top right tile, bottom left fov
//               newXView = xInView ? newXView : tileX2;
//               newYView = yInView ? newYView : tileY1 - displayImageHeight_ * getDownsampleFactor();
//            } else if (bl <= tl && bl <= tr && bl <= br) { // bottom left tile, top right fov
//               newXView = xInView ? newXView : tileX1 - displayImageWidth_ * getDownsampleFactor();
//               newYView = yInView ? newYView : tileY2;
//            } else { //bottom right tile, top left fov
//               newXView = xInView ? newXView : tileX2;
//               newYView = yInView ? newYView : tileY2;
//            }
//         }
//      }
//      //readjust to current res level
//      xView_ = newXView / getDownsampleFactor();
//      yView_ = newYView / getDownsampleFactor();
//   }

   public void pan(int dx, int dy) {
//      if (boundedImage_) {
//         xView_ = (int) Math.max(xMin_ / Math.pow(2, resolutionIndex_), Math.min(xView_ + dx, xMax_ / Math.pow(2, resolutionIndex_) - displayImageWidth_));
//         yView_ = (int) Math.max(yMin_ / Math.pow(2, resolutionIndex_), Math.min(yView_ + dy, yMax_ / Math.pow(2, resolutionIndex_) - displayImageHeight_));
//      } else {
//         xView_ += dx;
//         yView_ += dy;
//         //only accept pan if it keeps some portion of explored area in view
//         //explore acquisition must have some area you've already explored in view
//         moveViewToVisibleArea();
//      }
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
      long fullResX = (long) ((x + xView_) * Math.pow(2, resolutionIndex_));
      long fullResY = (long) ((y + yView_) * Math.pow(2, resolutionIndex_));
      return new LongPoint(fullResX, fullResY);
   }

   public LongPoint getFullResPixelCoordsOfDisplayedCenter() {
      long x = (long) ((xView_ + displayImageWidth_ / 2) * Math.pow(2, resolutionIndex_));
      long y = (long) ((yView_ + displayImageHeight_ / 2) * Math.pow(2, resolutionIndex_));
      return new LongPoint(x, y);
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

   public LongPoint getTopLeftPixel() {
      return new LongPoint(xMin_, yMin_);
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
   public LongPoint getDisplayedPixel(long row, long col) {
      long x = Math.round((col * tileWidth_) / (double) getDownsampleFactor()) - xView_;
      long y = Math.round((row * tileHeight_) / (double) getDownsampleFactor()) - yView_;
      return new LongPoint(x, y);
   }

   /**
    * Account for zoom and viewing are to get the display image pixel
    * corresponding to the full res image pixel given
    *
    * @param fullImageCoords
    * @return
    */
   public LongPoint getDisplayImageCoordsFromFullImageCoords(LongPoint fullImageCoords) {
      return new LongPoint(fullImageCoords.x_ / getDownsampleFactor() - xView_, fullImageCoords.y_ / getDownsampleFactor() - yView_);
   }

//   /**
//    *
//    * @param displayedSliceIndex - 0 based
//    * @param displayedFrameIndex - 0 based
//    * @return
//    */
//   public double getZCoordinateOfDisplayedSlice(int displayedSliceIndex) {
//      return acquisition_.getZCoordinateOfDisplaySlice(displayedSliceIndex);
//   }
//
//   /**
//    *
//    * @param zPos
//    * @param displayedFrameIndex - 0 based
//    * @return
//    */
//   public int getSliceIndexFromZCoordinate(double zPos) {
//      return acquisition_.getDisplaySliceIndexFromZCoordinate(zPos);
//   }

   
   /**
    * 
    * @return 
    */
   protected TaggedImage getMagellanTaggedImage() {
//      return provider_.getImageForDisplay(channel_, slice_, frame_, resolutionIndex_,
//              xView_, yView_, displayImageWidth_, displayImageHeight_);
      throw new UnsupportedOperationException();
   }
 


   public static byte[] singleChannelFromRGB32(byte[] pixels, int channel) {
      if (channel != 0 && channel != 1 && channel != 2) {
         return null;
      }

      byte[] p = new byte[pixels.length / 4];

      for (int i = 0; i < p.length; ++i) {
         p[i] = pixels[(2 - channel) + 4 * i]; //B,G,R
      }
      return p;
   }



}
