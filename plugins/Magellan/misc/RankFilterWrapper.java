/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package misc;

import java.util.ArrayList;

/**
 * Class that wraps rank filtering
 */
public class RankFilterWrapper {
   
   private ArrayList<RawBufferWrapper> rawBuffers_;
   private int offset_;
   private int doubleWidth_;
   private int height_, width_;
   private int numFrames_;
   
   public RankFilterWrapper(int offset, int doubleWidth, int numFrames) {
      rawBuffers_ = new ArrayList<>();
      offset_ = offset;
      doubleWidth_ = doubleWidth;
      width_ = RawBufferWrapper.getWidth();
      height_ = RawBufferWrapper.getHeight();
      numFrames_ = numFrames;
   }
   
   public void addBuffer(byte[] buffer) {
      rawBuffers_.add(new RawBufferWrapper(buffer, offset_, doubleWidth_));
   }
   
   public byte[] constructImage() {
      //iterate through every pixel location in final image
      for (int i = 0; i < width_ * height_; i++) {
         int x = i %width_;
         int y = i / width_;
         for (int frame = 0; frame < numFrames_; frame++) {
            
            
         }
         
         int pixPerFrame;
         if (x == 0 || x == width_ - 1) {
            if (y == 0 || y == height_ - 1) {
               pixPerFrame = 4; //corner
            } else {
               pixPerFrame = 6; //left or right edge
            }             
         } else {
            if (y == 0 || y == height_ - 1) {
               pixPerFrame = 6; //top or bottom edge
            } else {
               pixPerFrame = 9;
            }
         }
         byte[] pix = new byte[pixPerFrame * numFrames_];
         for (int xSource = )

      }
   }
   
   
           
           
}
