///////////////////////////////////////////////////////////////////////////////
// AUTHOR:       Henry Pinkard, henry.pinkard@gmail.com
//
// COPYRIGHT:    University of California, San Francisco, 2015
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.plugins.magellan.bdv;

import org.micromanager.plugins.magellan.acq.MagellanTaggedImage;
import org.micromanager.plugins.magellan.acq.MultiResMultipageTiffStorage;
import bdv.img.cache.CacheArrayLoader;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileByteArray;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;

/**
 * From Tobias:
 * Your CacheArrayLoader will be queried for blocks by the BDV cache (VolatileGlobalCellCache<VolatileIntArray>) and the loaded blocks will be cached in RAM.
 * The BDV cache is set up by your ImgLoader with your CacheArrayLoader.
 */
public class MultiResMPTiffVolatileShortArrayLoader implements CacheArrayLoader<VolatileShortArray> {

   private VolatileShortArray theEmptyArray_;
   private final MultiResMultipageTiffStorage tiffStorage_;

   public MultiResMPTiffVolatileShortArrayLoader(MultiResMultipageTiffStorage tiffStorage) {      
      theEmptyArray_ = new VolatileShortArray(tiffStorage.getTileWidth() * tiffStorage.getTileHeight(), false);
      tiffStorage_ = tiffStorage;
   }
   
   
   @Override
   public int getBytesPerElement() {
      return 1;
   }

   /**
    * 
    * @param timepoint
    * @param setup Setup = channel in our context
    * @param level resolution level
    * @param dimensions dimensions of the block to load  for your tiles this will be XxYx1
    * @param min starting coordinate of block in stack
    * @return
    * @throws InterruptedException 
    */
   @Override
   public VolatileShortArray loadArray(final int timepoint, final int setup, final int level, int[] dimensions, long[] min) throws InterruptedException {
      //From Tobias: To clarify that a bit better: 
      //You do not need to be able to load arbitrary blocks here. Just the ones that you will use from the images returned by your ImgLoader.
      //So this is the only "shape" of block that your CacheArrayLoader needs to be able to load (plus they will be aligned at multiples of tileWidth, tileHeight, 1).
      //c, z, f, ds, x, y, w, h
      MagellanTaggedImage img = tiffStorage_.getImageForDisplay(setup, (int)min[2], timepoint, level, min[0], min[1], dimensions[0], dimensions[1]);
      return new VolatileShortArray((short[])img.pix, true);
   }

   @Override
   public VolatileShortArray emptyArray(final int[] dimensions) {
      int numEntities = 1;
      for (int i = 0; i < dimensions.length; ++i) {
         numEntities *= dimensions[ i];
      }
      if (theEmptyArray_.getCurrentStorageArray().length < numEntities) {
         theEmptyArray_ = new VolatileShortArray(numEntities, false);
      }
      return theEmptyArray_;
   }
   
}
