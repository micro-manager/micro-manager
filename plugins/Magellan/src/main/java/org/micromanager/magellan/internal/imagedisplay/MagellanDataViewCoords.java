/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.internal.imagedisplay;

import java.awt.geom.Point2D;
import java.util.HashMap;

/**
 *
 * @author henrypinkard
 */
class MagellanDataViewCoords {

   public static final int NONE = 0, EXPLORE = 1, SURFACE_AND_GRID = 2;

   private volatile int overlayMode_;

   private double displayImageWidth_, displayImageHeight_; //resolution of the image to be displayed 
   private double sourceDataFullResWidth_, sourceDataFullResHeight_; //resolution in pixels of the display image at full res
   private double xView_, yView_; //top left pixel in full res coordinates
   private int zIndex_, tIndex_, rIndex_, cIndex_; //channel index for scrollbar display
   final private HashMap<Integer, String> channelNames_ = new HashMap<Integer, String>();
   private int resolutionIndex_;
   private MagellanImageCache cache_;

   //Parameters that track what part of the dataset is being viewed
   public final long xMax_, yMax_, xMin_, yMin_;

   MagellanDataViewCoords(MagellanImageCache cache, double xView, double yView, int channel,
           int slice, int frame, double initialWidth, double initialHeight, long[] imageBounds) {
      cache_ = cache;
      xView_ = 0;
      yView_ = 0;
      sourceDataFullResWidth_ = initialWidth;
      sourceDataFullResHeight_ = initialHeight;
      zIndex_ = slice;
      tIndex_ = frame;
      cIndex_ = channel;
      if (imageBounds != null) {
         xMin_ = imageBounds[0];
         yMin_ = imageBounds[1];
         xMax_ = imageBounds[2];
         yMax_ = imageBounds[3];
      } else {
         xMin_ = Long.MIN_VALUE;
         yMin_ = Long.MIN_VALUE;
         xMax_ = Long.MAX_VALUE;
         yMax_ = Long.MAX_VALUE;
      }
   }

   public boolean addChannelIfNew(int channelIndex, String channelName) {
      if (!channelNames_.containsKey(channelIndex)) {
         channelNames_.put(channelIndex, channelName);
         return true;
      }
      return false;
   }

   /**
    * 
    * @return 
    */
   public Point2D.Double getDisplayImageSizeAtResLevel() {
      return new Point2D.Double(sourceDataFullResWidth_ / getDownsampleFactor(), sourceDataFullResHeight_ / getDownsampleFactor());
   }

   
   /**
    * Computes the scaling between display pixels and whatever pixels they were
    * derived from
    */
   public double getDisplayScaleFactor() {
      //need this floor because it happens along the way to image creation
      return displayImageWidth_ / Math.floor(sourceDataFullResWidth_ / getDownsampleFactor());
   }
   
   public double getDisplayToFullScaleFactor() {
     return displayImageWidth_ / (double) sourceDataFullResWidth_;
   }

   private void computeResIndex() {
      double resIndexFloat = Math.log(sourceDataFullResWidth_ / (double) displayImageWidth_) / Math.log(2);
//      sourceDataFullResHeight_ / (double) displayImageHeight_
      int newResIndexInt = (int) Math.min(cache_.getMaxResolutionIndex(), Math.max(0, Math.ceil(resIndexFloat)));
      resolutionIndex_ = newResIndexInt;
   }

   /**
    * Compute the resolution index used for gettting data based on zoom and
    * available resolution indices
    *
    * @return
    */
   public int getResolutionIndex() {
      return resolutionIndex_;
   }

   public double getDownsampleFactor() {
      return Math.pow(2, getResolutionIndex());
   }

   public Point2D.Double getSourceDataSize() {
      return new Point2D.Double(sourceDataFullResWidth_, sourceDataFullResHeight_);
   }

   public void setSourceDataSize(double newWidth, double newHeight) {
      sourceDataFullResWidth_ = newWidth;
      sourceDataFullResHeight_ = newHeight;
      computeResIndex();
   }

   public Point2D.Double getViewOffset() {
      return new Point2D.Double(xView_, yView_);
   }

   public void setViewOffset(double xOffset, double yOffset) {
      xView_ = xOffset;
      yView_ = yOffset;
   }

   public void setDisplayImageSize(int width, int height) {
      displayImageWidth_ = width;
      displayImageHeight_ = height;
      computeResIndex();
   }

   public Point2D.Double getDisplayImageSize() {
      return new Point2D.Double(displayImageWidth_, displayImageHeight_);
   }

   public int getAxisPosition(String axis) {
      if (axis.equals("z")) {
         return zIndex_;
      } else if (axis.equals("t")) {
         return tIndex_;
      } else if (axis.equals("c")) {
         return cIndex_;
      } else if (axis.equals("r")) {
         return rIndex_;
      } else {
         throw new RuntimeException("Unrecognized axis");
      }
   }

   public void setAxisPosition(String axis, int pos) {
      if (axis.equals("z")) {
         zIndex_ = pos;
      } else if (axis.equals("t")) {
         tIndex_ = pos;
      } else if (axis.equals("c")) {
         cIndex_ = pos;
      } else if (axis.equals("r")) {
         rIndex_ = pos;
      } else {
         throw new RuntimeException("Unrecognized axis");
      }
   }

   public MagellanDataViewCoords copy() {
      MagellanDataViewCoords view = new MagellanDataViewCoords(cache_, xView_, yView_, cIndex_, zIndex_, tIndex_,
              sourceDataFullResWidth_, sourceDataFullResHeight_, new long[]{xMin_, yMin_, xMax_, yMax_});
      for (Integer channel : channelNames_.keySet()) {
         view.channelNames_.put(channel, channelNames_.get(channel));
      }
      view.displayImageHeight_ = displayImageHeight_;
      view.displayImageWidth_ = displayImageWidth_;
      view.xView_ = xView_;
      view.yView_ = yView_;
      view.resolutionIndex_ = resolutionIndex_;
      view.overlayMode_ = overlayMode_;
      return view;
   }

   public String getChannelName(int index) {
      return channelNames_.get(index);
   }

   Iterable<Integer> getChannelIndices() {
      return channelNames_.keySet();
   }

   int getOverlayMode() {
      return overlayMode_;
   }

   void setOverlayMode(int mode) {
      overlayMode_ = mode;
   }

}
