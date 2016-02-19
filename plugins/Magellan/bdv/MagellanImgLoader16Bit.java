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
package bdv;

import acq.MultiResMultipageTiffStorage;
import bdv.img.cache.CachedCellImg;
import net.imglib2.img.basictypeaccess.volatiles.array.VolatileShortArray;
import net.imglib2.type.numeric.integer.UnsignedShortType;
import net.imglib2.type.volatiles.VolatileUnsignedShortType;

public class MagellanImgLoader16Bit extends MagellanImgLoader< UnsignedShortType, VolatileUnsignedShortType, VolatileShortArray> {

   public MagellanImgLoader16Bit(MultiResMultipageTiffStorage storage) {
      super(storage, new MultiResMPTiffVolatileShortArrayLoader(storage), new UnsignedShortType(), new VolatileUnsignedShortType());
   }

   @Override
   protected void linkType(final CachedCellImg< UnsignedShortType, VolatileShortArray> img) {
      img.setLinkedType(new UnsignedShortType(img));
   }

   @Override
   protected void linkVolatileType(final CachedCellImg< VolatileUnsignedShortType, VolatileShortArray> img) {
      img.setLinkedType(new VolatileUnsignedShortType(img));
   }
}
