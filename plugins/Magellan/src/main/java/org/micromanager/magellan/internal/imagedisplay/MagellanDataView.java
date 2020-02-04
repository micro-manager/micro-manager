
package org.micromanager.magellan.internal.imagedisplay;

import java.awt.Dimension;
import java.awt.Point;
import org.micromanager.magellan.internal.misc.LongPoint;

/**
 * The successor of VirtualStack. Gives a view into a Magellan Dataset on disk with a 
 * particular zoom, and display offset
 */
 class MagellanDataView {

   private volatile int resolutionIndex_ = 0;
   private volatile int displayImageWidth_, displayImageHeight_;
   private volatile long xView_ = 0, yView_ = 0;  //top left pixel of view in current res
   private  long xMax_, yMax_, xMin_, yMin_;
   
   private volatile int slice_, frame_, position_; //TODO: replace this with better mechanism

   
   private final int tileWidth_, tileHeight_;
   private final boolean boundedImage_;

   private boolean rgb_;
   private MagellanImageCache provider_;
   
   
    MagellanDataView(MagellanImageCache provider, int viewWidth, int viewHeight) {
      displayImageHeight_ = viewHeight;
      displayImageWidth_ = viewWidth;
      rgb_ = false; //TODO: could re add alter
      provider_ = provider;
      tileWidth_ = provider.getTileWidth();
      tileHeight_ = provider.getTileHeight();
      boundedImage_ = provider.isXYBounded();
//      int[] bounds = provider.getImageBounds();
//      xMax_ = bounds[0];
//      yMax_ = bounds[1];
//      xMin_ = bounds[2];
//      yMin_ = bounds[3];
   }
   
   public int getResolutionIndex() {
      return resolutionIndex_;
   }
   
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
   
   
   public int getDownsampleFactor() {
      int dsFactor = (int) Math.pow(2, resolutionIndex_);
      return dsFactor;
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

   void updateView(MagellanDataViewCoords view_) {
//      position_ = view_.position_;
      slice_ = view_.getAxisPosition("z");
      frame_ = view_.getAxisPosition("t");
//      displayImageHeight_ = view_.displayImageHeight_;
//      displayImageWidth_ = view_.displayImageWidth_;
//      resolutionIndex_ = view_.resolutionIndex_;
//      xView_ = view_.xView_;
//      yView_ = view_.yView_;
   }
   
}
