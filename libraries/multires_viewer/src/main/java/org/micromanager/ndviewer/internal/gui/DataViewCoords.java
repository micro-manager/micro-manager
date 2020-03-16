/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.internal.gui;

import java.awt.geom.Point2D;
import java.util.HashMap;
import java.util.HashSet;
import org.micromanager.ndviewer.api.DataSourceInterface;

/**
 *
 * @author henrypinkard
 */
public class DataViewCoords {

   private volatile int overlayMode_;

   private double displayImageWidth_, displayImageHeight_; //resolution of the image to be displayed 
   private double sourceDataFullResWidth_, sourceDataFullResHeight_; //resolution in pixels of the display image at full res
   private double xView_, yView_; //top left pixel in full res coordinates
   private HashMap<String, Integer> axes_ = new HashMap<String, Integer>();
   final private HashSet<String> channelNames_ = new HashSet<String>();
   private int resolutionIndex_;
   private DataSourceInterface cache_;
   private String currentChannel_;

   //Parameters that track what part of the dataset is being viewed
   public final int xMax_, yMax_, xMin_, yMin_;

   public DataViewCoords(DataSourceInterface cache, String currentChannel, double xView, double yView,
           double initialWidth, double initialHeight, int[] imageBounds) {
      currentChannel_ = currentChannel;
      cache_ = cache;
      xView_ = 0;
      yView_ = 0;
      sourceDataFullResWidth_ = initialWidth;
      sourceDataFullResHeight_ = initialHeight;
      if (imageBounds != null) {
         xMin_ = imageBounds[0];
         yMin_ = imageBounds[1];
         xMax_ = imageBounds[2];
         yMax_ = imageBounds[3];
      } else {
         xMin_ = Integer.MIN_VALUE;
         yMin_ = Integer.MIN_VALUE;
         xMax_ = Integer.MAX_VALUE;
         yMax_ = Integer.MAX_VALUE;
      }
   }

   /**
    * 
    * @return 
    */
   public Point2D.Double getSourceImageSizeAtResLevel() {
      return new Point2D.Double(sourceDataFullResWidth_ / getDownsampleFactor(), sourceDataFullResHeight_ / getDownsampleFactor());
   }
   
   public void setFullResSourceDataSize(double newWidth, double newHeight) {
      sourceDataFullResWidth_ = newWidth;
      sourceDataFullResHeight_ = newHeight;
      computeResIndex();
   }
   
    public Point2D.Double getFullResSourceDataSize() {
      return new Point2D.Double(sourceDataFullResWidth_, sourceDataFullResHeight_);
   }

   public Point2D.Double getDisplayImageSize() {
      return new Point2D.Double(displayImageWidth_, displayImageHeight_);
   }
   
   /**
    * Computes the scaling between display pixels and whatever pixels they were
    * derived from
    */
   public double getMagnificationFromResLevel() {
      //need this floor because it happens along the way to image creation
      return displayImageWidth_ / Math.floor(sourceDataFullResWidth_ / getDownsampleFactor());
   }
   
   public double getMagnification() {
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

   public void setAxisPosition(String axis, Integer position) {
      axes_.put(axis, position);
   }
   
   public int getAxisPosition(String axis) {
      if (!axes_.containsKey(axis)) {
         return 0;
      }
      return axes_.get(axis);
   }

   public DataViewCoords copy() {
      DataViewCoords view = new DataViewCoords(cache_, currentChannel_, xView_, yView_,
              sourceDataFullResWidth_, sourceDataFullResHeight_, new int[]{xMin_, yMin_, xMax_, yMax_});
      for (String channel : channelNames_) {
         view.channelNames_.add(channel);
      }
      for (String axisName : axes_.keySet()) {
         view.axes_.put(axisName, axes_.get(axisName));
      }
      view.displayImageHeight_ = displayImageHeight_;
      view.displayImageWidth_ = displayImageWidth_;
      view.currentChannel_ = currentChannel_;
      view.xView_ = xView_;
      view.yView_ = yView_;
      view.resolutionIndex_ = resolutionIndex_;
      view.overlayMode_ = overlayMode_;
      return view;
   }

   public String getActiveChannel() {
      return currentChannel_;
   }

   public int getOverlayMode() {
      return overlayMode_;
   }

   public void setOverlayMode(int mode) {
      overlayMode_ = mode;
   }

   public HashMap<String, Integer> getAxesPositions() {
      return axes_;
   }

   public void setActiveChannel(String channelName) {
      currentChannel_ = channelName;
   }

   public int[] getBounds() {
      return new int[]{xMin_, yMin_, xMax_, yMax_};
   }

}
