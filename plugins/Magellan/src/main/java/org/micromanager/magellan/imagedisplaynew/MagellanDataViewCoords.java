/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.imagedisplaynew;

/**
 *
 * @author henrypinkard
 */
  class MagellanDataViewCoords {
  
   public final int resolutionIndex_;
   public final int displayImageWidth_, displayImageHeight_;
   public final long xView_, yView_;  //top left pixel of view in current res
   public final int zIndex_, tIndex_, channelIndex_; //channel index for scrollbar display
   public final int[] channelIndices_;

    MagellanDataViewCoords(int resIndex, int dispHeight, int dispWidth, long xView, long yView, int channel, int slice, int frame, int[] channelIndices) {
      resolutionIndex_ = resIndex;
      displayImageHeight_ = dispHeight;
      displayImageWidth_ = dispWidth;
      xView_ = xView;
      yView_ = yView;
      zIndex_ = slice;
      tIndex_ = frame;
      channelIndices_ = channelIndices;
      channelIndex_ = channel;
   }
   
   
}
