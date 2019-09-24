/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.magellan.imagedisplaynew;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 *
 * @author henrypinkard
 */
class MagellanDataViewCoords {

   public double zoom_ = 1.0;
   public int displayImageWidth_, displayImageHeight_;
   public long xView_, yView_; //top left pixel of view in current res
   public int zIndex_, tIndex_, rIndex_, cIndex_; //channel index for scrollbar display
   public HashMap<Integer, Boolean> channelsActive_ = new HashMap<Integer, Boolean>();

   //Parameters that track what part of the dataset is being viewed
   private long xMax_, yMax_, xMin_, yMin_;

   MagellanDataViewCoords(long xView, long yView, int channel,
           int slice, int frame) {
      xView_ = 0;
      yView_ = 0;
      zIndex_ = slice;
      tIndex_ = frame;
      cIndex_ = channel;
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
      MagellanDataViewCoords view = new MagellanDataViewCoords(xView_, yView_, cIndex_, zIndex_, tIndex_);
      for (Integer channel : channelsActive_.keySet()) {
         view.channelsActive_.put(channel, channelsActive_.get(channel));
      }
      view.zoom_ = zoom_;
      view.displayImageWidth_ = displayImageWidth_;
      view.displayImageHeight_ = displayImageHeight_;
      view.xView_ = xView_;
      view.yView_ = yView_;
      return view;
   }

}
