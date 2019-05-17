/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.plugins.magellan.bidc;

import ij.ImagePlus;
import ij.ImageStack;
import org.micromanager.plugins.magellan.main.Magellan;

/**
 * Class that wraps raw, double wide, warped buffer to support fast access
 * without having to actually access anything
 * @author henrypinkard
 */
public class RawBufferWrapper {
   
   //set in Bitflow camera file or other A/D converter
   private static final int PIXELS_PER_LINE = 1256;
   private static final int TOTAL_BITFLOW_PIXELS_PER_ROW = 1270; 
   private static int FINAL_IMAGE_WIDTH = 406; 
   //-Bitflow must have a at least a 4 pixel difference between the total number of pixels per line and the number of pixels used
   //or else it mistakenly thinks two frames are one
   //number of pixels used must be a multiple of 8
   //mirroring goes away when moving to higher number of pixels per line, and comes back when moving to a lower number of pixels per line
   //but changing number of pixels used doesnt seem to affect it
   
   private static int[] warpedIndicesFromUnwarped_;
   private byte[] buffer_;
   private int offset_;
   private static int unwarpedWidth_;
   private static boolean unwarp_ = true; //for debugging
   
   public RawBufferWrapper(byte[] buffer, int offset, int doubleWidth) {
      if (warpedIndicesFromUnwarped_ == null) {
         warpedIndicesFromUnwarped_ = getCosineWarpLUT();
         unwarpedWidth_ = warpedIndicesFromUnwarped_.length;
      }
      
      offset_ = offset;
      buffer_ = buffer;
     
        //debugging: show double wide, warped iamge
//        ImageStack stack = new ImageStack(doubleWidth, buffer.length / doubleWidth);
//        stack.addSlice(null, buffer);
//        ImagePlus doubleWide = new ImagePlus("double wide", stack);
//        doubleWide.show();
//        doubleWide.close();
    }
   
     public short getUnwarpedImageValue(int x, int y) {
       //this gives you the single wide index in an interlaced image
//       unwarp_ = true;
    
       int warpedX;
       if (unwarp_) {
           warpedX = warpedIndicesFromUnwarped_[x];
       } else {
           warpedX = x;
       }    
         //apply offset to wrap indices arounfd
         int flatIndex = (y % 2 == 1 ? (PIXELS_PER_LINE - warpedX + offset_ % 2) : warpedX) + (y/2)*PIXELS_PER_LINE;
         //wrap around using offset
         flatIndex = Math.max(0,Math.min(flatIndex + offset_/2, buffer_.length - 1));    
        return (short) (buffer_[flatIndex] & 0xff);
    }
   

   public static int getWidth() {
      if (warpedIndicesFromUnwarped_ == null) {
         warpedIndicesFromUnwarped_ = getCosineWarpLUT();
         unwarpedWidth_ = warpedIndicesFromUnwarped_.length;
      }
      return unwarpedWidth_;
   }
   
   public static int getHeight() {
      //twice the raw image height
      return (int) (Magellan.getCore().getImageHeight() * 2);
   }
   
   /**
    * This function assumes an interlaced, but still warped image its
    * purpose is to generate a pixel LUT with unwarped pixel indices and 
    * warped pixel values
    * Warped indices are spaced equally in time. Unwarped indices are spaced equally in space
    */
     private static int[] getCosineWarpLUT() {
      int interlacedWidth = TOTAL_BITFLOW_PIXELS_PER_ROW / 2;
        
      int[] unwarpedFromWarped = new int[interlacedWidth];
      //warped indices are spaced equally in 
      for (int i = 0 ; i < interlacedWidth; i++) {
          //i is raw pixel index (or time index)
          double spatialPosition = (1 + Math.cos( (double)i / TOTAL_BITFLOW_PIXELS_PER_ROW*2*Math.PI))/2.0;
          //Convert from 0-1 (shifted slightly by phase) to a pixel value
          int spatialPixel = (int) (spatialPosition * FINAL_IMAGE_WIDTH);
          unwarpedFromWarped[i] = spatialPixel;
      }
       
      int[] warpedFromUnwarped = new int[FINAL_IMAGE_WIDTH];
      for (int i = 0; i < warpedFromUnwarped.length; i++) {
         int centerPixel = getCenterIndexOf(unwarpedFromWarped, i);
         //make sure you don't index a pixel that was thrown away by bitflow
         centerPixel = Math.min(centerPixel, TOTAL_BITFLOW_PIXELS_PER_ROW /2 - 1); 
         warpedFromUnwarped[i] = centerPixel;
      }
      return warpedFromUnwarped;
  }
     
   /**
    * @param array
    * @param val
    * @return the middle index of val in a sorted array with one or more occurrence of val
    */
   public static int getCenterIndexOf(int[] array, int val) {
      int first = -1;
      int last = -1;
      for (int i = 0; i < array.length; i++) {
         if (array[i] == val && first == -1) {
            first = i;
         }
         if (array[i] == val) {
            last = i;
         }
      }
      return (first + last) / 2;
   }
   
}
