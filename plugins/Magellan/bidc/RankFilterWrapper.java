/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package bidc;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Class that wraps rank filtering
 */
public class RankFilterWrapper extends FrameIntegrationMethod{

   //first index: pixel index, second index: 9*numFrames values to be sorted
   //has to be short to get rid of signed byte for sorting
   private short[][] pixelValues_;
   private double rank_; 

   public RankFilterWrapper(int offset, int doubleWidth, int numFrames, double rank) {
      super(doubleWidth, offset, numFrames);
      pixelValues_ = new short[width_ * height_][numFrames_ * 9];
      rank_ = rank;
   }


    @Override
   public byte[] constructImage() {
        long start = System.currentTimeMillis();
      //iterate through every pixel location in final image
      for (int x = -1; x < width_ + 1; x++) {
         for (int y = -1; y < height_ + 1; y++) {
            for (int frame = 0; frame < numFrames_; frame++) {
               //get value of pixel or value of relevant edge
               short val = rawBuffers_.get(frame).getUnwarpedImageValue(Math.max(0, Math.min(width_ - 1, x)), Math.max(0, Math.min(height_ - 1, y)));
               //add to all 9 positions at the appropriate index            
               addValToRelevantArrays(frame, val, x, y);
            }
         }
      }
      //sort all lists and construct final image
      byte[] filteredPix = new byte[width_ * height_];
      for (int i = 0; i < filteredPix.length; i++ ) {
         Arrays.sort(pixelValues_[i]);
         filteredPix[i] = (byte) pixelValues_[i][(int)((numFrames_*9 -1) *rank_)];
      }
//      System.out.println("rank filtering time: " + (System.currentTimeMillis() - start));
      return filteredPix;
   }
   
   private void addValToRelevantArrays(int frameIndex, short val, int x, int y) {
      if (y > 0) {
         if (x > 0) {
            pixelValues_[x - 1 + (y - 1) * width_][frameIndex*9 + 0] = val;
         }
         if (x >= 0 && x < width_) {
            pixelValues_[x + (y - 1) * width_][frameIndex*9 + 1] = val;
         }
         if (x < width_ - 1) {
            pixelValues_[x + 1 + (y - 1) * width_][frameIndex*9 + 2] = val;
         }
      }
      if (y >= 0 && y < height_) {
         if (x > 0) {
            pixelValues_[x - 1 + y * width_][frameIndex*9 + 3] = val;
         }
         if (x >= 0 && x < width_) {
            pixelValues_[x + y * width_][frameIndex*9 + 4] = val;
         }
         if (x < width_ - 1) {
            pixelValues_[x + 1 + y * width_][frameIndex*9 + 5] = val;
         }
      }
      if (y < height_ - 1) {
         if (x > 0) {
            pixelValues_[x - 1 + (y + 1) * width_][frameIndex*9 + 6] = val;
         }
         if (x >= 0 && x < width_) {
            pixelValues_[x + (y + 1) * width_][frameIndex*9 + 7] = val;
         }
         if (x < width_ - 1) {
            pixelValues_[x + 1 + (y + 1) * width_][frameIndex*9 + 8] = val;
         }
      }
   }

  
}
