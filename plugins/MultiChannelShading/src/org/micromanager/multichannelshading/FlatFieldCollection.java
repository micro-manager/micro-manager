/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.multichannelshading;

import ij.ImagePlus;
import ij.process.ImageStatistics;
import java.util.HashMap;


/**
 *
 * @author kthorn
 */
public class FlatFieldCollection {
   private static final int nFlatFields_ = 5;
   private final HashMap<Integer, Float[]> flatFieldList_;
   private final HashMap<Integer, Float[]> normalizedFlatFieldList_;
   private final String[] channelList_ = new String[nFlatFields_];
   private final boolean[] normalizeList_ = new boolean[nFlatFields_];
   private final int[] flatFieldWidths_ = new int [nFlatFields_];
   private final int[] flatFieldHeights_ = new int [nFlatFields_];
   private ImageStatistics flatFieldStats_;
   private int flatFieldWidth_;
   private int flatFieldHeight_;
   private Integer index_;

    public FlatFieldCollection() {
        this.normalizedFlatFieldList_ = new HashMap<Integer, Float[]>(nFlatFields_);
        this.flatFieldList_ = new HashMap<Integer, Float[]>(nFlatFields_);
    }
   
   public Float[] getFlatField(String channelName){
       index_ = getIndex(channelName);
       if (index_ == -1){
           return null;
       } else {       
           return (Float[]) flatFieldList_.get(index_);
       }
   }
   
   public Float[] getNormalizedFlatField(String channelName){
       index_ = getIndex(channelName);
       if (index_ == -1){
           return null;
       } else {       
           return (Float[]) normalizedFlatFieldList_.get(index_);
       }
   }
   
   public int getImageHeight(String channelName){
       index_ = getIndex(channelName);
       if (index_ == -1){
           return 0;
       } else {       
           return flatFieldHeights_[index_];
       }
   }
   
   public int getImageWidth(String channelName){
       index_ = getIndex(channelName);
       if (index_ == -1){
           return 0;
       } else {       
           return flatFieldWidths_[index_];
       }
   }
   
   public boolean getFlatFieldNormalize(String channelName){
       index_ = getIndex(channelName);
       if (index_ == -1){
           return true;
       } else {       
           return normalizeList_[index_];
       }
   }
   
   public void setFlatFieldNormalize(int index, boolean normalized){
       normalizeList_[index] = normalized;
   }
   public boolean hasChannel(String channelName){
       return getIndex(channelName) != -1;       
   }
   
   public void setChannelName(int index, String channel){
       channelList_[index] = channel;
   }
   
   public void setFlatField(int index, ImagePlus flatField){
       if (flatField != null) {       
         flatFieldStats_ = ImageStatistics.getStatistics(flatField.getProcessor(),
                 ImageStatistics.MEAN + ImageStatistics.MIN_MAX, null);
         flatFieldWidth_ = flatField.getWidth();
         flatFieldHeight_ = flatField.getHeight();
         Float[] normalizedFlatField_ = new Float[flatFieldWidth_ * flatFieldHeight_];
         Float[] flatField_ = new Float[flatFieldWidth_ * flatFieldHeight_];
         float mean = (float) flatFieldStats_.mean;
         float maxval = (float) Math.pow(2, flatField.getBitDepth()) - 1;         
        
         /* store images as reciprocals to speed up flat fielding later
            normalized image is normalized so mean = 1
            un-normalized flatfield is divided by 2^bitdepth - 1 to put it on a 0 - 1 scale
         */
         for (int x = 0; x < flatFieldWidth_; x++) {
            for (int y = 0; y < flatFieldHeight_; y++) {
               int pixelindex = (y * flatFieldWidth_) + x;
               normalizedFlatField_[pixelindex] =  
                  mean / flatField.getProcessor().getf(pixelindex);
               
               flatField_[pixelindex] = 
                  maxval / flatField.getProcessor().getf(pixelindex);
            }
         }

         flatFieldList_.put(index, flatField_);
         normalizedFlatFieldList_.put(index, normalizedFlatField_);
         flatFieldWidths_[index] = flatFieldWidth_;
         flatFieldHeights_[index] = flatFieldHeight_;
      } else {
         flatFieldList_.put(index, null);
         normalizedFlatFieldList_.put(index, null);
      }
   }
   
   private int getIndex(String channelName){
       for (Integer n=0; n<channelList_.length; n++){
           if (channelName.equals(channelList_[n])){
               return n;
           }
       }
       return -1;
   }
}