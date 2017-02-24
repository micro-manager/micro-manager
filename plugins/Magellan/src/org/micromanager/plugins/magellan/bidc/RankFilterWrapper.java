/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.plugins.magellan.bidc;

import com.google.common.collect.TreeMultiset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.Random;
import java.util.TreeMap;

/**
 * Class that wraps rank filtering
 */
public class RankFilterWrapper extends FrameIntegrationMethod {

   //first index: pixel index, second index: 9*numFrames values to be sorted
   //has to be short to get rid of signed byte for sorting
   private short[][] pixelValues_;
   private double rank_;
   private int c;

   private static int WIDTH = 400, HEIGHT = 400;
   private static double RANK = 0.9;
   private static int NUMFRAMES = 3;
   private static short[][][] PIX = new short[(WIDTH + 2)][(HEIGHT + 2)][NUMFRAMES];
   private static short[][] TEMPPIX = new short[WIDTH * HEIGHT][9 * NUMFRAMES];

   public static void main(String[] args) {
      //initialize random array
      Random r = new Random();
      RankFilterWrapper rf = new RankFilterWrapper(0, 0, 0, 0);
      for (int x = 0; x < WIDTH + 2; x++) {
         for (int y = 0; y < HEIGHT + 2; y++) {
            for (int f = 0; f < NUMFRAMES; f++) {
               PIX[x][y][f] = (short) r.nextInt(256);
            }
         }
      }

      int numtrials = 10;
      long sum = 0;
      for (int i = 0; i < numtrials; i++) {
         long start = System.nanoTime();
         rf.constructImageNoReverse();
         long elapsed = System.nanoTime() - start;
         sum += elapsed;
//         System.out.println(elapsed);
      }
      System.out.println("elapsed time no reverse:  " + sum / numtrials / 100000 + "ms");

      sum = 0;
      for (int i = 0; i < numtrials; i++) {
         long start = System.nanoTime();
         rf.constructImage();
         long elapsed = System.nanoTime() - start;
         sum += elapsed;
//         System.out.println(elapsed);
      }
      System.out.println("elapsed time with reverse:  " + sum / numtrials / 100000 + "ms");

      sum = 0;
      for (int i = 0; i < numtrials; i++) {
         long start = System.nanoTime();
         rf.constructImageNoReverseFast();
         long elapsed = System.nanoTime() - start;
         sum += elapsed;
//         System.out.println(elapsed);
      }
      System.out.println("elapsed time (fast?):  " + sum / numtrials / 100000 + "ms");

   }

   public RankFilterWrapper(int offset, int doubleWidth, int numFrames, double rank) {
      super(doubleWidth, offset, numFrames);
      pixelValues_ = new short[width_ * height_][numFrames_ * 9];
      rank_ = rank;
   }

   private TreeMap<Integer, Short> pixelMap = new TreeMap<Integer, Short>();

   private class SpecialSet {
      //maintains sorted set of elements and also a FIFO bounded capacity
      private LinkedList<Object> addedObjects_ = new LinkedList<Object>();
      private TreeMultiset<Short> values_ = TreeMultiset.create();
      private int capacity_;
      
      public SpecialSet(int capacity) {
         capacity_ = capacity;
      }
      
      //add new element and remove stale one if neccessary
      public void add(Short s) {
         if (addedObjects_.size() + 1 == capacity_) {
            values_.remove(addedObjects_.removeFirst());
         }
         addedObjects_.add(s);
         values_.add(s);
      }
      
      
   }
   
   public byte[] constructImageNoReverseFast() {
      byte[] outputImage = new byte[WIDTH * HEIGHT];
      Short[] sortedTemp = new Short[9 * NUMFRAMES];
      int rankedIndex = (int) (RANK * NUMFRAMES * 9);

      //iterate through all pixels in final image      
      for (int x = 0; x < WIDTH; x++) {
         for (int y = 0; y < HEIGHT; y++) {
            if (x == 0) {
               pixelMap.clear();
               for (int f = 0; f < NUMFRAMES; f++) {
                  //add dummy first col
                  pixelMap.put(-3 * NUMFRAMES + f, (short) 0);
                  pixelMap.put(-2 * NUMFRAMES + f, (short) 0);
                  pixelMap.put(-1 * NUMFRAMES + f, (short) 0);

                  if (y == 0) {
                     //add missing first row
                     pixelMap.put(f, (short) 0);
                     pixelMap.put(3 * NUMFRAMES + f, (short) 0);
                     //add real others
                     pixelMap.put(NUMFRAMES + f, PIX[x][y][f]);
                     pixelMap.put(2 * NUMFRAMES + f, PIX[x + 1][y][f]);
                     pixelMap.put(4 * NUMFRAMES + f, PIX[x][y + 1][f]);
                     pixelMap.put(5 * NUMFRAMES + f, PIX[x + 1][y + 1][f]);
                  } else if (y == HEIGHT) {
                     //add real first rows
                     pixelMap.put(0 * NUMFRAMES + f, PIX[x][y - 1][f]);
                     pixelMap.put(3 * NUMFRAMES + f, PIX[x + 1][y - 1][f]);
                     pixelMap.put(1 * NUMFRAMES + f, PIX[x][y][f]);
                     pixelMap.put(4 * NUMFRAMES + f, PIX[x][y + 1][f]);
                     //add dummy last row
                     pixelMap.put(2 * NUMFRAMES + f, (short) 0);
                     pixelMap.put(5 * NUMFRAMES + f, (short) 0);
                  } else {
                     //populate entire thing
                     pixelMap.put(0 * NUMFRAMES + f, PIX[x][y - 1][f]);
                     pixelMap.put(3 * NUMFRAMES + f, PIX[x + 1][y - 1][f]);
                     pixelMap.put(1 * NUMFRAMES + f, PIX[x][y][f]);
                     pixelMap.put(4 * NUMFRAMES + f, PIX[x][y + 1][f]);
                     pixelMap.put(2 * NUMFRAMES + f, PIX[x + 1][y][f]);
                     pixelMap.put(5 * NUMFRAMES + f, PIX[x + 1][y + 1][f]);
                  }
               }
            } else if (x == WIDTH - 1) {
               for (int f = 0; f < NUMFRAMES; f++) {
                  //remove leftmost column
                  pixelMap.pollFirstEntry();
                  pixelMap.pollFirstEntry();
                  pixelMap.pollFirstEntry();
                  //add dummy right column
                  pixelMap.put((x * 3 + 3) * NUMFRAMES + f, (short) 0);
                  pixelMap.put((x * 3 + 4) * NUMFRAMES + f, (short) 0);
                  pixelMap.put((x * 3 + 5) * NUMFRAMES + f, (short) 0);
               }
            } else { // x is neither first nor last column
               for (int f = 0; f < NUMFRAMES; f++) {

                  //remove leftmost column
                  pixelMap.pollFirstEntry();
                  pixelMap.pollFirstEntry();
                  pixelMap.pollFirstEntry();
                  //add data column
                  pixelMap.put((x * 3 + 3) * NUMFRAMES + f, y == 0 ? (short) 0 : PIX[x + 1][y - 1][f]);
                  pixelMap.put((x * 3 + 4) * NUMFRAMES + f, PIX[x + 1][y][f]);
                  pixelMap.put((x * 3 + 5) * NUMFRAMES + f, y == HEIGHT - 1 ? (short) 0 : PIX[x + 1][y + 1][f]);
               }
            }
            //add appropriate value to final image
            Short[] outArray = pixelMap.values().toArray(sortedTemp);
            outputImage[WIDTH * y + x] = (byte) (sortedTemp[rankedIndex].shortValue() & 0xff);
         }
      }

      return outputImage;
   }

   public byte[] constructImageNoReverse() {
      //iterate through every pixel location in final image
      for (int x = -1; x < WIDTH + 1; x++) {
         for (int y = -1; y < HEIGHT + 1; y++) {
            for (int frame = 0; frame < NUMFRAMES; frame++) {
               //get value of pixel or value of relevant edge
               short val = PIX[x + 1][y + 1][frame];
               //add to all 9 positions at the appropriate index           
               addValToRelevantArrays(frame, val, x, y, TEMPPIX);
            }
         }
      }
      //sort all lists and construct final image
      byte[] filteredPix = new byte[WIDTH * HEIGHT];
      for (int i = 0; i < filteredPix.length; i++) {
         Arrays.sort(TEMPPIX[i]);
         filteredPix[i] = (byte) TEMPPIX[i][(int) ((NUMFRAMES * 9 - 1) * RANK)];
      }
      return filteredPix;
   }

   @Override
   public byte[] constructImage() {
      //iterate through every pixel location in final image
      //duplicate edge pixels to get all 9 here
      for (int x = -1; x < WIDTH + 1; x++) {
         for (int y = -1; y < WIDTH + 1; y++) {
            for (int frame = 0; frame < NUMFRAMES; frame++) {
               //get value of pixel or value of relevant edge
               short val = PIX[x + 1][y + 1][frame];
               //add to all 9 positions at the appropriate index            
               addValToRelevantArrays(frame, val, x, y, TEMPPIX);
            }
         }
      }

      //rank filter on 3x3 window and all frames
      short[][] intermediatePixels = new short[WIDTH * HEIGHT][9];
      for (int x = 0; x < WIDTH; x++) {
         for (int y = 0; y < HEIGHT; y++) {
            int i = x + y * WIDTH;
            Arrays.sort(TEMPPIX[i]);
            short intermediatePixel = TEMPPIX[i][(int) ((NUMFRAMES * 9 - 1) * RANK)];
            //add to relvant arrays. Edge pixels get less than the full 9
            //add to all 9 positions at the appropriate index            
            addValToRelevantArrays(0, intermediatePixel, x, y, intermediatePixels);
         }
      }

      //reverse rank filter to construct final image
      byte[] doubleFiltered = new byte[WIDTH * HEIGHT];
      for (int i = 0; i < intermediatePixels.length; i++) {
         Arrays.sort(intermediatePixels[i]);
         doubleFiltered[i] = (byte) intermediatePixels[i][(int) (9 * (1 - RANK))];
      }
//      System.out.println("rank filtering time: " + (System.currentTimeMillis() - start));
      return doubleFiltered;
   }

   private void addValToRelevantArrays(int frameIndex, short val, int x, int y, short[][] array) {
      if (y > 0) {
         if (x > 0) {
            array[x - 1 + (y - 1) * WIDTH][frameIndex * 9 + 0] = val;
         }
         if (x >= 0 && x < WIDTH) {
            array[x + (y - 1) * WIDTH][frameIndex * 9 + 1] = val;
         }
         if (x < WIDTH - 1) {
            array[x + 1 + (y - 1) * WIDTH][frameIndex * 9 + 2] = val;
         }
      }
      if (y >= 0 && y < HEIGHT) {
         if (x > 0) {
            array[x - 1 + y * WIDTH][frameIndex * 9 + 3] = val;
         }
         if (x >= 0 && x < WIDTH) {
            array[x + y * WIDTH][frameIndex * 9 + 4] = val;
         }
         if (x < WIDTH - 1) {
            array[x + 1 + y * WIDTH][frameIndex * 9 + 5] = val;
         }
      }
      if (y < HEIGHT - 1) {
         if (x > 0) {
            array[x - 1 + (y + 1) * WIDTH][frameIndex * 9 + 6] = val;
         }
         if (x >= 0 && x < WIDTH) {
            array[x + (y + 1) * WIDTH][frameIndex * 9 + 7] = val;
         }
         if (x < WIDTH - 1) {
            array[x + 1 + (y + 1) * WIDTH][frameIndex * 9 + 8] = val;
         }
      }
   }

//    @Override
//   public byte[] constructImage() {
////        long start = System.currentTimeMillis();
//      //iterate through every pixel location in final image
//      //duplicate edge pixels to get all 9 here
//      for (int x = -1; x < width_ + 1; x++) {
//         for (int y = -1; y < height_ + 1; y++) {
//            for (int frame = 0; frame < numFrames_; frame++) {
//               //get value of pixel or value of relevant edge
//               short val = rawBuffers_.get(frame).getUnwarpedImageValue(Math.max(0, Math.min(width_ - 1, x)), Math.max(0, Math.min(height_ - 1, y)));
//               //add to all 9 positions at the appropriate index            
//               addValToRelevantArrays(frame, val, x, y, pixelValues_);
//            }
//         }
//      }
//
//      //rank filter on 3x3 window and all frames
//      short[][] intermediatePixels = new short[width_ * height_][9];
//      for (int x = 0; x < width_ ; x++) {
//         for (int y = 0; y < height_; y++) {
//             int i = x + y*width_;
//               Arrays.sort(pixelValues_[i]);
//               short intermediatePixel = pixelValues_[i][(int)((numFrames_*9 -1) *rank_)];
//               //add to relvant arrays. Edge pixels get less than the full 9
//               //add to all 9 positions at the appropriate index            
//               addValToRelevantArrays(0, intermediatePixel, x, y,intermediatePixels); 
//         }
//      }
//      
//      //reverse rank filter to construct final image
//      byte[] doubleFiltered = new byte[width_*height_];
//      for (int i = 0; i < intermediatePixels.length; i++ ) {
//         Arrays.sort(intermediatePixels[i]);
//         doubleFiltered[i] = (byte) intermediatePixels[i][(int)(9 *(1-rank_))];
//      }
////      System.out.println("rank filtering time: " + (System.currentTimeMillis() - start));
//      return doubleFiltered;
//   }
//   
//   private void addValToRelevantArrays(int frameIndex, short val, int x, int y, short[][] array) {
//      if (y > 0) {
//         if (x > 0) {
//            array[x - 1 + (y - 1) * width_][frameIndex*9 + 0] = val;
//         }
//         if (x >= 0 && x < width_) {
//            array[x + (y - 1) * width_][frameIndex*9 + 1] = val;
//         }
//         if (x < width_ - 1) {
//            array[x + 1 + (y - 1) * width_][frameIndex*9 + 2] = val;
//         }
//      }
//      if (y >= 0 && y < height_) {
//         if (x > 0) {
//            array[x - 1 + y * width_][frameIndex*9 + 3] = val;
//         }
//         if (x >= 0 && x < width_) {
//            array[x + y * width_][frameIndex*9 + 4] = val;
//         }
//         if (x < width_ - 1) {
//            array[x + 1 + y * width_][frameIndex*9 + 5] = val;
//         }
//      }
//      if (y < height_ - 1) {
//         if (x > 0) {
//            array[x - 1 + (y + 1) * width_][frameIndex*9 + 6] = val;
//         }
//         if (x >= 0 && x < width_) {
//            array[x + (y + 1) * width_][frameIndex*9 + 7] = val;
//         }
//         if (x < width_ - 1) {
//            array[x + 1 + (y + 1) * width_][frameIndex*9 + 8] = val;
//         }
//      }
//   }
}
