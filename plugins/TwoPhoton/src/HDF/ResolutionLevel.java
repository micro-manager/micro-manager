package HDF;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;


public class ResolutionLevel {
   
   private static final long BYTES_PER_MB = 1048576;
   
   private int imageByteDepth_;
   private int resIndex_;
   private long blockSizeX_, blockSizeY_, blockSizeZ_, blockSizeT_;
   private int imageSizeX_, imageSizeY_, imageSizeZ_, imageSizeT_;
   private int containerX_, containerY_, containerZ_;
   private int reductionFactorX_, reductionFactorY_, reductionFactorZ_;

   public ResolutionLevel(int index, int baseWidth, int baseHeight, int baseDepth,
           int imageWidth, int imageHeight, int imageDepth, int numTimePoints, int byteDepth) {
      resIndex_ = index;
      imageSizeX_ = imageWidth;
      imageSizeY_ = imageHeight;
      imageSizeZ_ = imageDepth;
      imageSizeT_ = numTimePoints;
      imageByteDepth_ = byteDepth;
      reductionFactorX_ = (int) Math.ceil(baseWidth / (double)imageSizeX_);
      reductionFactorY_ = (int) Math.ceil(baseHeight / (double) imageSizeY_);
      reductionFactorZ_ = (int) Math.ceil(baseDepth / (double) imageSizeZ_);
      calculateBlockSize();
   }

   //The following function is adapted from the c++ code used in Imaris to write .ims 5.5 files
   private void calculateBlockSize() {

//  bpUInt64 vBlockSize = (bpInt64)pow(2.0f, floor(log((bpFloat)aImageBlockSize) / log(2.0f)));

      //Image blocks must be 1 MB
      long blockSize = (long) Math.pow(2.0, Math.floor(Math.log(BYTES_PER_MB / imageByteDepth_) / Math.log(2.0)));
      long minBlockSizeX = 1, minBlockSizeY = 1, minBlockSizeZ = 1, minBlockSizeT = 1;
      long imageSizeXYZT = imageSizeX_ * imageSizeY_ * imageSizeZ_ * imageSizeT_;

      //compile a list of all possible layouts
      ArrayList<BlockLayoutCost> blockLayoutCosts = new ArrayList<BlockLayoutCost>();

      //Layouts must have a power of two sides
      for (long blockSizeX = 1; blockSizeX <= blockSize; blockSizeX *= 2) {
         for (long blockSizeY = 1; blockSizeY * blockSizeX <= blockSize; blockSizeY *= 2) {
            for (long blockSizeZ = 1; blockSizeZ * blockSizeX * blockSizeY <= blockSize; blockSizeZ *= 2) {
               long blockSizeT = blockSize / (blockSizeX * blockSizeY * blockSizeZ);
               if ((imageSizeZ_ > 1 && blockSizeX == blockSizeY && blockSizeZ > 2) || //some graphics boards want square texttures and z > 2 for 3D
                       (imageSizeZ_ == 1 && (blockSizeX <= 4 * blockSizeY) && (blockSizeY <= 4 * blockSizeX) && blockSizeZ == 1)) {
                  if (blockSizeX * blockSizeY * blockSizeZ * blockSizeT == blockSize) {
                     if (blockSizeX >= minBlockSizeX && blockSizeY >= minBlockSizeY
                             && blockSizeZ >= minBlockSizeZ && blockSizeT >= minBlockSizeT) {
                        long numBlocksX = 1 + (imageSizeX_ - 1) / blockSizeX;
                        long numBlocksY = 1 + (imageSizeY_ - 1) / blockSizeY;
                        long numBlocksZ = 1 + (imageSizeZ_ - 1) / blockSizeZ;
                        long numBlocksT = 1 + (imageSizeT_ - 1) / blockSizeT;

                        BlockLayoutCost cost = new BlockLayoutCost();
                        cost.sizeX = blockSizeX;
                        cost.sizeY = blockSizeY;
                        cost.sizeZ = blockSizeZ;
                        cost.sizeT = blockSizeT;

                        //The costs for rendering the three main orthagonal slices together
                        cost.costSlice = 1 + numBlocksX * numBlocksY + numBlocksX * numBlocksZ + numBlocksY * numBlocksZ
                                - numBlocksX - numBlocksY - numBlocksZ;

                        //Surface area (minimum is a cube), important for clipping
                        cost.costGeometry = numBlocksX * numBlocksY + numBlocksX * numBlocksZ + numBlocksY * numBlocksZ;

                        //memory usage: 1 means that no memory is wasted / larger values indicate blocks with unused voxels
                        cost.costMemory = (numBlocksX * blockSizeX * numBlocksY * blockSizeY * numBlocksZ * blockSizeZ
                                * numBlocksT * blockSizeT) / imageSizeXYZT;
                        blockLayoutCosts.add(cost);

                     }
                  }
               }
            }
         }
      }
      if (!blockLayoutCosts.isEmpty()) {
         //Sort the list by slice cost, then by geometry cost, then by memory cost
         Collections.sort(blockLayoutCosts,getLayoutCostComparator());
         
         //First is the best...
         BlockLayoutCost blcOptimum = blockLayoutCosts.get(0);
         //...if it doesn't waste too much memory
         double memoryCostMax = 2.0;
         if ( blcOptimum.costMemory > memoryCostMax) {
            for ( int i = 1; i < blockLayoutCosts.size(); i++) {
               if (blockLayoutCosts.get(i).costMemory <= memoryCostMax) {
                  blcOptimum = blockLayoutCosts.get(i);
                  break;
               }
            }
         } 
         blockSizeX_ = blcOptimum.sizeX;
         blockSizeY_ = blcOptimum.sizeY;
         blockSizeZ_ = blcOptimum.sizeZ;
         blockSizeT_ = blcOptimum.sizeT;
      } else {
         //paranoid: divide equally if no optimum available
         int log2BlockSize = 0;
         while ( Math.pow(2, log2BlockSize) <BYTES_PER_MB / imageByteDepth_ ) {
            log2BlockSize++;
         }
         int log2BlockSizeZ = log2BlockSize/3;
         int log2BlockSizeY = (log2BlockSize-log2BlockSizeZ)/2;
         int log2BlockSizeX = (log2BlockSize-log2BlockSizeZ - log2BlockSizeY);
         blockSizeZ_ = (long) Math.pow(2, log2BlockSizeZ);
         blockSizeX_ = (long) Math.pow(2, log2BlockSizeX);
         blockSizeY_ = (long) Math.pow(2, log2BlockSizeY);
         blockSizeT_ = 1;
      }
      containerX_ = (int) (blockSizeX_ * Math.ceil(imageSizeX_ / (double) blockSizeX_));
      containerY_ = (int) (blockSizeY_ * Math.ceil(imageSizeY_ / (double) blockSizeY_));
      containerZ_ = (int) (blockSizeZ_ * Math.ceil(imageSizeZ_ / (double) blockSizeZ_));
   }
   
   private Comparator<BlockLayoutCost> getLayoutCostComparator() {
      return new Comparator<BlockLayoutCost>() {
         @Override
         public int compare(BlockLayoutCost c1, BlockLayoutCost c2) {
            if (c1.costSlice != c2.costSlice) {
               if (c1.costSlice < c2.costSlice) {
                  return -1;
               } else {
                  return 1;
               }
            }
            if (c1.costGeometry != c2.costGeometry) {
               if (c1.costGeometry < c2.costGeometry) {
                  return -1;
               } else {
                  return 1;
               }
            }
            if (c1.costMemory < c2.costMemory) {
               return -1;
            } else if (c1.costMemory > c2.costMemory) {
               return 1;
            }
            return 0;
         }
      };
   }

   private class BlockLayoutCost {
      public long sizeX, sizeY, sizeZ, sizeT;
      public double costSlice, costGeometry, costMemory;
   }
   
   public int getImageNumBytes() {
      return imageByteDepth_ * imageSizeX_ * imageSizeY_ * imageSizeZ_;
   }
   
   public int getImageByteDepth() {
      return imageByteDepth_;
   }
   
   public int getIndex() {
      return resIndex_;
   }
   
   public int getXBlockSize() {
      return (int) blockSizeX_;    
   }
   
   public int getYBlockSize() {
      return (int) blockSizeY_;
   }
   
   public int getZBlockSize() {
      return (int) blockSizeZ_;
   }
   
   public int getTBlockSize() {
      return (int) blockSizeT_;
   }
   
   public int getImageSizeX() {
      return imageSizeX_;
   }
   
   public int getImageSizeY() {
      return imageSizeY_;
   }
   
   public int getImageSizeZ() {
      return imageSizeZ_;
   }
   
   public int getContainerSizeX() {
      return containerX_;
   }
   
   public int getContainerSizeY() {
      return containerY_;
   }
   
   public int getContainerSizeZ() {
      return containerZ_;
   }
   
   public int getReductionFactorX() {
      return reductionFactorX_;
   }

   public int getReductionFactorY() {
      return reductionFactorY_;
   }

   public int getReductionFactorZ() {
      return reductionFactorZ_;
   }

   
}
